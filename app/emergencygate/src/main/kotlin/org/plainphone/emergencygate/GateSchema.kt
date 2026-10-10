// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.emergencygate

/** How one build's result file is judged. REAL builds pass or not; controls are DETECTED or not. */
enum class Outcome { PASS, INCONCLUSIVE, FAIL, INCOMPLETE, ERROR, DETECTED, NOT_DETECTED }

data class Judgement(val outcome: Outcome, val reasons: List<String>)

/**
 * The required result schema (PLA-245). Every build's file must hold each required check exactly once,
 * with a verdict that check may have. `judge_build` in tools/emergency-gate.sh is the same table in awk;
 * both run `judge-cases.txt`.
 */
object GateSchema {
    /** Routes the gate never runs, with the reason it prints. SOS: AGENTS.md "Never do these" rule 3. */
    val NOT_COVERED = linkedMapOf(
        "sos" to "by design: the power-button SOS gesture is never triggered by a tool (AGENTS.md rule 3); Q14 real-device check",
        "setup" to "not yet implemented: setup",
        "pin-lockout" to "not yet implemented: PIN lockout",
        "supervised-session" to "not yet implemented: supervised session",
        "updater" to "not yet implemented: Plainphone update",
        "turn-off" to "not yet implemented: turning Plainphone off",
    )

    /** Checks a build may declare NOT_BUILT (`notBuilt=` on its capabilities line). */
    val OPTIONAL = setOf("more-emergency")

    val RUN = listOf(
        "home-phone", "app-home-phone", "more-emergency", "alert-display-ready", "alert-display-launch", "alert-shown",
        "telecom-emergency-call", "lock-screen", "locked-boot", "boot-checking",
    )

    /** Only the real build runs the can't-start scenario. */
    val REAL = listOf("capabilities") + RUN + "cannot-start" + NOT_COVERED.keys
    val CONTROL = listOf("capabilities") + RUN + NOT_COVERED.keys

    /** Each control's deliberate fault: checks that must FAIL, and checks that may also fail because of it. */
    data class Control(val mustFail: Set<String>, val mayFail: Set<String> = emptySet())

    val CONTROLS = mapOf(
        "no-phone" to Control(setOf("home-phone", "app-home-phone"), setOf("telecom-emergency-call", "boot-checking")),
        "no-dialer-lock-task" to Control(setOf("home-phone", "app-home-phone"), setOf("telecom-emergency-call", "boot-checking")),
        "no-alert" to Control(setOf("alert-display-launch"), setOf("alert-display-ready")),
        "no-alert-lock-task" to Control(setOf("alert-display-ready", "alert-display-launch")),
    )

    fun judge(control: String, results: List<CheckResult>): Judgement {
        val reasons = mutableListOf<String>()
        val caps = results.filter { it.check == "capabilities" }
        val declared = caps.singleOrNull()?.takeIf { it.verdict == Verdict.PASS }?.let { field(it.detail, "control") }
        val notBuilt = caps.singleOrNull()?.let { field(it.detail, "notBuilt") }.orEmpty()
            .split(',').filter { it.isNotEmpty() && it != "-" }.toSet()
        val real = control == "none"
        val spec = CONTROLS[control]
        if (!real && spec == null) return Judgement(Outcome.ERROR, listOf("unknown control '$control'"))
        if (declared == null) return Judgement(Outcome.ERROR, listOf("no passing capabilities line: build not identified") + errors(results))
        if (declared != control) return Judgement(Outcome.ERROR, listOf("build reports control=$declared, expected $control"))

        val required = if (real) REAL else CONTROL
        val counts = results.groupingBy { it.check }.eachCount()
        val missing = required.filter { it !in counts }
        val unknown = counts.keys.filter { it !in required }
        val twice = counts.filterValues { it > 1 }.keys
        val incomplete = mutableListOf<String>()
        if (missing.isNotEmpty()) incomplete += "missing: ${missing.joinToString()}"
        if (unknown.isNotEmpty()) incomplete += "unknown: ${unknown.joinToString()}"
        if (twice.isNotEmpty()) incomplete += "reported twice: ${twice.joinToString()}"

        val byCheck = results.associateBy { it.check }
        val errored = results.filter { it.verdict == Verdict.ERROR }.map { "${it.check} ERROR ${it.detail}".trim() }
        val failed = mutableListOf<String>()
        val inconclusive = mutableListOf<String>()
        for ((check, r) in byCheck) {
            if (check !in required || check == "capabilities" || r.verdict == Verdict.ERROR) continue
            if (spec != null && check in spec.mustFail) {
                if (r.verdict != Verdict.FAIL) failed += "$check ${r.verdict}: the control's fault was not detected"
                continue
            }
            if (spec != null && check in spec.mayFail && r.verdict == Verdict.FAIL) continue
            when (val problem = allowed(check, r.verdict, notBuilt)) {
                null -> if (check == "alert-shown" && r.verdict == Verdict.INCONCLUSIVE) inconclusive += "alert-shown INCONCLUSIVE"
                else -> if (r.verdict == Verdict.FAIL) failed += "$check FAIL ${r.detail}".trim() else incomplete += "$check: $problem"
            }
        }
        if (real) {
            reasons += failed + errored + incomplete + inconclusive
            val outcome = when {
                failed.isNotEmpty() -> Outcome.FAIL
                errored.isNotEmpty() -> Outcome.ERROR
                incomplete.isNotEmpty() -> Outcome.INCOMPLETE
                inconclusive.isNotEmpty() -> Outcome.INCONCLUSIVE
                else -> Outcome.PASS
            }
            return Judgement(outcome, reasons)
        }
        // A control: an ERROR anywhere, or a prerequisite that did not pass, means its failures prove nothing.
        val notDetected = failed.filter { it.endsWith("not detected") }
        val prerequisite = failed - notDetected.toSet()
        reasons += errored + prerequisite.map { "prerequisite $it" } + incomplete + notDetected
        val outcome = when {
            errored.isNotEmpty() || prerequisite.isNotEmpty() -> Outcome.ERROR
            incomplete.isNotEmpty() -> Outcome.INCOMPLETE
            notDetected.isNotEmpty() -> Outcome.NOT_DETECTED
            else -> Outcome.DETECTED
        }
        return Judgement(outcome, reasons)
    }

    /** Null when [verdict] is acceptable for [check] in a passing real build; otherwise why not. */
    private fun allowed(check: String, verdict: Verdict, notBuilt: Set<String>): String? = when {
        check in NOT_COVERED -> if (verdict == Verdict.NOT_COVERED) null else "$verdict where only NOT_COVERED is allowed"
        verdict == Verdict.PASS -> null
        verdict == Verdict.INCONCLUSIVE && check == "alert-shown" -> null
        verdict == Verdict.NOT_BUILT && check in OPTIONAL && check in notBuilt -> null
        verdict == Verdict.NOT_BUILT -> "NOT_BUILT but the build does not declare it optional"
        else -> "$verdict is not allowed"
    }

    private fun errors(results: List<CheckResult>) = results.filter { it.verdict == Verdict.ERROR }.map { "${it.check} ERROR ${it.detail}" }

    private fun field(detail: String, key: String): String? =
        detail.split(' ').firstOrNull { it.startsWith("$key=") }?.substringAfter('=')
}

/**
 * The only number the gate may dial (PLA-245 D2, AGENTS.md rule 3): the test-mode emergency number 7357,
 * on an emulator, after the host registered it with `cmd phone emergency-number-test-mode`. No override.
 */
object NumberFence {
    const val NUMBER = "7357"

    fun allowed(number: String) = number == NUMBER

    /**
     * Why the gate must not dial [number], or null. [qemu] is `ro.kernel.qemu` or `ro.boot.qemu`;
     * [testModeList] is `cmd phone emergency-number-test-mode -p` output, such as `[112, 911, 7357]`.
     */
    fun refusal(number: String, qemu: String, testModeList: String): String? = when {
        !allowed(number) -> "refusing to dial '$number': only $NUMBER is on the allowlist"
        qemu.trim() != "1" -> "refusing to dial: not an emulator (qemu='${qemu.trim()}')"
        NUMBER !in testModeList.split('[', ']', ',', ' ', '\n').map { it.trim() } ->
            "refusing to dial: $NUMBER is not in the test-mode emergency number list"
        else -> null
    }
}
