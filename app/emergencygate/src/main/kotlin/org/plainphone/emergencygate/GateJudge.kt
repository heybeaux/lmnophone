// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.emergencygate

/**
 * One gate verdict. FAIL is the build behaving wrongly; ERROR is the harness or emulator failing to run
 * the check. NOT_BUILT marks a route the build declares it does not have yet, and NOT_COVERED a route the
 * gate deliberately does not run (SOS, unbuilt product states). None of the last three is ever a pass.
 */
enum class Verdict { PASS, FAIL, ERROR, INCONCLUSIVE, NOT_BUILT, NOT_COVERED }

/** One check's result, printed as one `GATE <check> <verdict> <detail>` line for the host script. */
data class CheckResult(val check: String, val verdict: Verdict, val detail: String) {
    fun line() = "GATE $check $verdict $detail".trimEnd()

    companion object {
        private val LINE = Regex("""^GATE (\S+) (PASS|FAIL|ERROR|INCONCLUSIVE|NOT_BUILT|NOT_COVERED) ?(.*)$""")

        fun parse(line: String): CheckResult? = LINE.find(line.trim())?.let { m ->
            CheckResult(m.groupValues[1], Verdict.valueOf(m.groupValues[2]), m.groupValues[3])
        }
    }
}

data class Activity(val packageName: String, val className: String) {
    override fun toString() = "$packageName/$className"
}

data class TelecomCall(val id: String, val state: String, val emergency: Boolean)

/** Whether a call counts as ended ([GateJudge.callEnded]), and the evidence read. */
data class CallEnd(val ended: Boolean, val detail: String)

/** The alert-display package as Android reports it, for the structural alert check. Null means unreadable. */
data class AlertDisplay(
    val packageName: String,
    val found: Boolean,
    val enabled: Boolean,
    val hidden: Boolean?,
    val suspended: Boolean?,
    val allowedInLockTask: Boolean,
)

/** The `User 0:` flags of `dumpsys package`. Null when the line or the field is missing. */
data class PackageUserState(val hidden: Boolean?, val suspended: Boolean?)

/** Reads the few `dumpsys` fields the gate needs. */
object Dumpsys {
    private val TOP = Regex("""topResumedActivity=ActivityRecord\{\S+ u\d+ ([^/\s]+)/(\S+)""")
    private val LOCK_TASK = Regex("""mLockTaskModeState=(\w+)""")
    private val CALL = Regex("""\[Call id=(TC@\d+), state=(\w+)""")
    private val BLOCK = Regex("""Call (TC@\d+): \{([^}]*)\}""")
    private val USER0 = Regex("""(?m)^\s*User 0: (.*)$""")

    /** `dumpsys activity activities`: the top resumed activity, with a short class name expanded. */
    fun topResumed(activities: String): Activity? = TOP.find(activities)?.let { m ->
        val pkg = m.groupValues[1]
        val cls = m.groupValues[2]
        Activity(pkg, if (cls.startsWith(".")) pkg + cls else cls)
    }

    /** `dumpsys package P`: the first `User 0: <flags>` line (the Queries section has a bare `User 0:`). */
    fun userState(packageDump: String): PackageUserState {
        val flags = USER0.findAll(packageDump).map { it.groupValues[1] }.firstOrNull { "installed=" in it }
            ?: return PackageUserState(null, null)
        fun flag(name: String) = Regex("""\b$name=(true|false)\b""").find(flags)?.groupValues?.get(1)?.toBooleanStrict()
        return PackageUserState(flag("hidden"), flag("suspended"))
    }

    /** `pm list packages -u` ([all]) against `pm list packages` ([visible]): a hidden package is only in the first. */
    fun hiddenFromLists(packageName: String, all: String, visible: String): Boolean? {
        fun listed(list: String) = list.lines().any { it.trim() == "package:$packageName" }
        return if (!listed(all)) null else !listed(visible)
    }

    fun lockTaskState(activities: String): String? = LOCK_TASK.find(activities)?.groupValues?.get(1)

    private val STARTED = Regex("""(?m)^Activity: ([^/\s]+)/(\S+)""")

    /** `am start -W`: the activity it reports starting, with a short class name expanded. */
    fun startedActivity(amStart: String): Activity? = STARTED.find(amStart)?.let { m ->
        val pkg = m.groupValues[1]
        val cls = m.groupValues[2]
        Activity(pkg, if (cls.startsWith(".")) pkg + cls else cls)
    }

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

    /**
     * `dumpsys telecom`: the calls in CallsManager's own `mCalls:` list (not the history further down).
     * Null when the section is missing: an unreadable dump is never an empty list.
     */
    fun mCalls(telecom: String): List<TelecomCall>? {
        val lines = telecom.lines()
        val start = lines.indexOfFirst { it.trim() == "mCalls:" }
        if (start < 0) return null
        val indent = lines[start].indexOfFirst { !it.isWhitespace() }
        val section = lines.drop(start + 1).takeWhile { line -> line.isBlank() || line.indexOfFirst { !it.isWhitespace() } > indent }
        return calls(section.joinToString("\n"))
    }
}

/** The pure verdicts. The instrumentation gathers the facts; these decide. */
object GateJudge {
    const val BLOCKED_APP = "com.android.internal.app.BlockedAppActivity"
    private val LIVE_CALL = setOf("CONNECTING", "DIALING", "ACTIVE")
    private val DIAL_KEY = Regex("""^(\d)(?:[^\d].*)?$""")

    /** PASS only when the top resumed activity is one of [expected]. Lock task's blocked screen always fails. */
    fun inFront(check: String, top: Activity?, expected: Set<Activity>): CheckResult = when {
        top == null -> CheckResult(check, Verdict.FAIL, "no resumed activity")
        top.className == BLOCKED_APP -> CheckResult(check, Verdict.FAIL, "blocked by lock task: $top")
        expected.isEmpty() -> CheckResult(check, Verdict.FAIL, "no expected activity resolved for this route; foreground is $top")
        top in expected -> CheckResult(check, Verdict.PASS, top.toString())
        else -> CheckResult(check, Verdict.FAIL, "foreground is $top, expected one of ${expected.map { it.toString() }.sorted()}")
    }

    /**
     * A route reached the system dial pad: something else was in front before the tap ([before]), every
     * foreground sample after it is one of [expected], and the screen shows the ten dial keys ([labels]).
     */
    fun routeReached(check: String, before: Activity?, samples: List<Activity?>, expected: Set<Activity>, labels: Collection<String>): CheckResult {
        if (before != null && before in expected) return CheckResult(check, Verdict.FAIL, "$before was already in front before the tap")
        if (samples.isEmpty()) return CheckResult(check, Verdict.FAIL, "no foreground sample")
        val first = inFront(check, samples.first(), expected)
        if (first.verdict != Verdict.PASS) return first
        // By index, not firstOrNull: a missing (null) sample is itself a failing sample.
        val gone = samples.indexOfFirst { inFront(check, it, expected).verdict != Verdict.PASS }
        if (gone >= 0) return CheckResult(check, Verdict.FAIL, "${samples.first()} did not stay in front: then ${samples[gone] ?: "nothing"}")
        if (!dialPadUsable(labels)) return CheckResult(check, Verdict.FAIL, "${samples.first()} shows no usable dial pad")
        return first
    }

    /** All ten dial keys are on screen: labels such as `2`, `2,ABC` or `0 +`. */
    fun dialPadUsable(labels: Collection<String>): Boolean {
        val keys = labels.mapNotNull { DIAL_KEY.find(it.trim())?.groupValues?.get(1) }.toSet()
        return keys.containsAll((0..9).map(Int::toString))
    }

    /**
     * The alert screen's root launch (`am start -W`, [exit] its exit status, null when it was not read).
     * The started activity is judged by [inFront]. A launch that started nothing is FAIL only for the
     * expected unavailable-component result (`Error type 3 ... does not exist`, exit 1) and only when
     * [display], read back before the launch, shows a fault that explains it (hidden, disabled or
     * suspended). Anything else (empty output, an exception, a missing service, no exit status) is the
     * harness failing: ERROR, which no control can count as detection.
     */
    fun alertLaunched(display: AlertDisplay, exit: Int?, output: String, expected: Set<Activity>): CheckResult {
        val check = "alert-display-launch"
        val text = output.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" | ")
        val started = Dumpsys.startedActivity(output)
        return when {
            exit == null -> CheckResult(check, Verdict.ERROR, "launch command gave no exit status: $text")
            "Exception" in output || "Can't find service" in output ->
                CheckResult(check, Verdict.ERROR, "launch command failed exit=$exit: $text")
            started != null && exit != 0 -> CheckResult(check, Verdict.ERROR, "launch exit=$exit contradicts the started activity: $text")
            started != null -> inFront(check, started, expected)
            exit != 1 || "Error type 3" !in output || "does not exist" !in output || expected.none { it.className in output } ->
                CheckResult(check, Verdict.ERROR, "unrecognised launch result exit=$exit: $text")
            else -> {
                val faults = buildList {
                    if (!display.enabled) add("disabled")
                    if (display.hidden == true) add("hidden")
                    if (display.suspended == true) add("suspended")
                }
                if (faults.isEmpty()) {
                    CheckResult(check, Verdict.ERROR, "component unavailable, but no fault read back (hidden=${display.hidden} enabled=${display.enabled} suspended=${display.suspended}): $text")
                } else {
                    CheckResult(check, Verdict.FAIL, "unavailable: ${faults.joinToString(",")} (Error type 3, exit $exit) ${display.packageName}")
                }
            }
        }
    }

    /** Reads of `mCallState` 0.5 s apart that make the idle hold: 3 s. */
    const val HOLD_READS = 6

    /**
     * When the gate counts a call as ended (PLA-296 R2): every one of [HOLD_READS] reads of `mCallState`
     * ([hold]) was 0, Telecom's `mCalls` list ([telecom], read after the hold) holds no call that is not
     * DISCONNECTED, and a read [after] both was still 0. A hold with any other read is not ended, whatever
     * comes after it. This is an observation over about 3.5 s, not proof that the modem never brings a
     * call back later, so the gate repeats it before every call-capable step and at cleanup.
     */
    fun callEnded(hold: List<String>, telecom: List<TelecomCall>?, after: String): CallEnd {
        val detail = "hold=${hold.joinToString(",")} mCalls=${telecom?.joinToString(",") { "${it.id}:${it.state}" } ?: "unreadable"} after=$after"
        val ended = hold.size >= HOLD_READS && hold.all { it == "0" } &&
            telecom != null && telecom.all { it.state == "DISCONNECTED" } && after == "0"
        return CallEnd(ended, detail)
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
            when (display.hidden) { true -> add("hidden"); null -> add("hidden state unknown"); false -> {} }
            when (display.suspended) { true -> add("suspended"); null -> add("suspended state unknown"); false -> {} }
            if (!display.allowedInLockTask) add("not allowed in lock task")
        }
        return if (problems.isEmpty()) {
            CheckResult("alert-display-ready", Verdict.PASS, display.packageName)
        } else {
            CheckResult("alert-display-ready", Verdict.FAIL, "${display.packageName}: ${problems.joinToString()}")
        }
    }
}
