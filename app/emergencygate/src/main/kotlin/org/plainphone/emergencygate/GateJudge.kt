// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.emergencygate

/** One gate verdict. NOT_BUILT marks a route the build does not have; it never counts as a pass. */
enum class Verdict { PASS, FAIL, INCONCLUSIVE, NOT_BUILT }

/** One check's result, printed as one `GATE <check> <verdict> <detail>` line for the host script. */
data class CheckResult(val check: String, val verdict: Verdict, val detail: String) {
    fun line() = "GATE $check $verdict $detail".trimEnd()

    companion object {
        private val LINE = Regex("""^GATE (\S+) (PASS|FAIL|INCONCLUSIVE|NOT_BUILT) ?(.*)$""")

        fun parse(line: String): CheckResult? = LINE.find(line.trim())?.let { m ->
            CheckResult(m.groupValues[1], Verdict.valueOf(m.groupValues[2]), m.groupValues[3])
        }
    }
}

data class Activity(val packageName: String, val className: String) {
    override fun toString() = "$packageName/$className"
}

data class TelecomCall(val id: String, val state: String, val emergency: Boolean)

/** The alert-display package as Android reports it, for the structural alert check. */
data class AlertDisplay(
    val packageName: String,
    val found: Boolean,
    val enabled: Boolean,
    val hidden: Boolean,
    val suspended: Boolean,
    val allowedInLockTask: Boolean,
)

/** Reads the few `dumpsys` fields the gate needs. */
object Dumpsys {
    private val TOP = Regex("""topResumedActivity=ActivityRecord\{\S+ u\d+ ([^/\s]+)/(\S+)""")
    private val LOCK_TASK = Regex("""mLockTaskModeState=(\w+)""")
    private val CALL = Regex("""\[Call id=(TC@\d+), state=(\w+)""")
    private val BLOCK = Regex("""Call (TC@\d+): \{([^}]*)\}""")

    /** `dumpsys activity activities`: the top resumed activity, with a short class name expanded. */
    fun topResumed(activities: String): Activity? = TOP.find(activities)?.let { m ->
        val pkg = m.groupValues[1]
        val cls = m.groupValues[2]
        Activity(pkg, if (cls.startsWith(".")) pkg + cls else cls)
    }

    fun lockTaskState(activities: String): String? = LOCK_TASK.find(activities)?.groupValues?.get(1)

    /** `dumpsys telecom`: each call once, with the `isEmergency` flag from its own block. */
    fun calls(telecom: String): List<TelecomCall> {
        val emergency = BLOCK.findAll(telecom).associate { m ->
            m.groupValues[1] to m.groupValues[2].contains("isEmergency: true")
        }
        return CALL.findAll(telecom).map { m -> m.groupValues[1] to m.groupValues[2] }
            .distinctBy { it.first }
            .map { (id, state) -> TelecomCall(id, state, emergency[id] == true) }
            .toList()
    }
}

/** The pure verdicts. The instrumentation gathers the facts; these decide. */
object GateJudge {
    const val BLOCKED_APP = "com.android.internal.app.BlockedAppActivity"
    private val LIVE_CALL = setOf("CONNECTING", "DIALING", "ACTIVE")

    /** PASS only when one of [dialers] is the top resumed activity. Lock task's blocked screen always fails. */
    fun dialerInFront(check: String, top: Activity?, dialers: Set<String>): CheckResult = when {
        top == null -> CheckResult(check, Verdict.FAIL, "no resumed activity")
        top.className == BLOCKED_APP -> CheckResult(check, Verdict.FAIL, "blocked by lock task: $top")
        top.packageName in dialers -> CheckResult(check, Verdict.PASS, top.toString())
        else -> CheckResult(check, Verdict.FAIL, "foreground is $top")
    }

    fun emergencyCallPlaced(calls: List<TelecomCall>): CheckResult {
        val live = calls.firstOrNull { it.emergency && it.state in LIVE_CALL }
        return if (live != null) {
            CheckResult("telecom-emergency-call", Verdict.PASS, "${live.id} state=${live.state} isEmergency=true")
        } else {
            CheckResult("telecom-emergency-call", Verdict.FAIL, "no live emergency call: $calls")
        }
    }

    /** The behavioral alert check. Without injection it is INCONCLUSIVE, which is never a pass. */
    fun alertShown(injectionSupported: Boolean, shown: Boolean?): CheckResult = when {
        !injectionSupported -> CheckResult("alert-shown", Verdict.INCONCLUSIVE, "this image cannot inject a cell broadcast alert")
        shown == true -> CheckResult("alert-shown", Verdict.PASS, "alert displayed")
        else -> CheckResult("alert-shown", Verdict.FAIL, "injected alert not displayed")
    }

    /** The structural alert check: the display could run. It does not replace [alertShown]. */
    fun alertDisplayReady(display: AlertDisplay): CheckResult {
        val problems = buildList {
            if (!display.found) add("not found")
            if (!display.enabled) add("disabled")
            if (display.hidden) add("hidden")
            if (display.suspended) add("suspended")
            if (!display.allowedInLockTask) add("not allowed in lock task")
        }
        return if (problems.isEmpty()) {
            CheckResult("alert-display-ready", Verdict.PASS, display.packageName)
        } else {
            CheckResult("alert-display-ready", Verdict.FAIL, "${display.packageName}: ${problems.joinToString()}")
        }
    }

    /** Any FAIL fails; otherwise any INCONCLUSIVE is inconclusive. An empty run fails. */
    fun overall(results: List<CheckResult>): Verdict {
        val counted = results.filter { it.verdict != Verdict.NOT_BUILT }
        return when {
            counted.isEmpty() || counted.any { it.verdict == Verdict.FAIL } -> Verdict.FAIL
            counted.any { it.verdict == Verdict.INCONCLUSIVE } -> Verdict.INCONCLUSIVE
            else -> Verdict.PASS
        }
    }
}
