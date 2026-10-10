// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.emergencygate

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity as AndroidActivity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.os.UserManager
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityNodeInfo

/**
 * The emergency release gate (task 4.4), one check per run:
 * `am instrument -w -e check <name> org.plainphone.emergencygate/.EmergencyGate`.
 *
 * It self-instruments and drives the real Plainphone through UiAutomation and the shell, so the
 * Device Owner process and its lock task are never restarted. Each run prints `GATE` lines
 * ([CheckResult.line]); `tools/emergency-gate.sh` sets up each state (PIN, reboot, test number,
 * checking hold, can't-start) and collects them. Verdicts come from [GateJudge]; [GateSchema] judges a
 * whole build. It never triggers the power-button SOS gesture (AGENTS.md rule 3), and it dials only the
 * test-mode number [NumberFence.NUMBER] on an emulator.
 */
class EmergencyGate : Instrumentation() {
    private var arguments = Bundle()

    override fun onCreate(arguments: Bundle?) {
        this.arguments = arguments ?: Bundle()
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val check = arguments.getString("check").orEmpty()
        val results = try {
            uiAutomation.serviceInfo = uiAutomation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            run(check)
        } catch (e: Throwable) {
            listOf(CheckResult(check.ifEmpty { "unknown" }, Verdict.ERROR, "gate error: ${e.javaClass.simpleName}: ${e.message}"))
        }
        val report = results.joinToString("\n") { it.line() } + "\n"
        finish(AndroidActivity.RESULT_OK, Bundle().apply { putString(REPORT_KEY_STREAMRESULT, report) })
    }

    private fun run(check: String): List<CheckResult> {
        // Every check but these may lead to a call or an emergency dialer: start only from idle.
        if (check !in setOf("capabilities", "not-covered", "hangup")) {
            callState().let { if (it != "0") return listOf(CheckResult(check, Verdict.ERROR, "a call is live before the check (mCallState=$it)")) }
        }
        return when (check) {
            "capabilities" -> listOf(capabilities())
            // Declared, never run: SOS (AGENTS.md rule 3) and the product states not built yet.
            "not-covered" -> GateSchema.NOT_COVERED.map { (name, why) -> CheckResult(name, Verdict.NOT_COVERED, why) }
            "home-phone" -> listOf(phoneFromHome(check))
            "app-home-phone" -> listOf(phoneFromApp(check))
            "more-emergency" -> listOf(moreEmergency(check))
            "boot-checking" -> listOf(phoneFromChecking(check))
            "lock-screen" -> listOf(lockScreenEmergency(check, expectLocked = false))
            "locked-boot" -> listOf(lockScreenEmergency(check, expectLocked = true))
            "cannot-start" -> listOf(cannotStart(check))
            "telecom-emergency-call" -> listOf(emergencyCall(arguments.getString("number") ?: NumberFence.NUMBER))
            // The emulated modem keeps a live call through an Android reboot, so the host ends every test
            // call before going on.
            "hangup" -> listOf(hangUp().let { state ->
                CheckResult(check, if (state == "0") Verdict.PASS else Verdict.FAIL, "mCallState=$state")
            })
            "alert" -> listOf(alertDisplayReady(), alertLaunch(), GateJudge.alertShown(injectionSupported = false, shown = null)
                .let { it.copy(detail = "${it.detail} (emulator console has no cbs; modem has no +CBM injection)") })
            else -> listOf(CheckResult(check.ifEmpty { "unknown" }, Verdict.ERROR, "unknown check"))
        }
    }

    /** Which build this is: its control (from its own re-apply log) and the routes it declares not built. */
    private fun capabilities(): CheckResult {
        val reapply = shell("logcat -d -s PlainphoneBoot").lines().lastOrNull { "reapply" in it && "control=" in it }
            ?: return CheckResult("capabilities", Verdict.ERROR, "no Plainphone re-apply in the log")
        val control = Regex("""control=(\S+)""").find(reapply)!!.groupValues[1]
        val ready = Regex("""ready=(\S+)""").find(reapply)?.groupValues?.get(1)
        val notBuilt = declaredNotBuilt().ifEmpty { setOf("-") }.sorted().joinToString(",")
        return CheckResult("capabilities", Verdict.PASS, "control=$control notBuilt=$notBuilt ready=$ready")
    }

    /** The routes the Plainphone build itself declares it does not have yet (manifest meta-data). */
    private fun declaredNotBuilt(): Set<String> = runCatching {
        context.packageManager.getApplicationInfo(PLAINPHONE, PackageManager.GET_META_DATA).metaData
            ?.getString(NOT_BUILT_KEY).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }.getOrDefault(emptySet())

    // ---- routes ----

    private fun phoneFromHome(check: String): CheckResult {
        home()?.let { return CheckResult(check, Verdict.ERROR, it) }
        return tapAndExpect(check, PHONE, dialPad())
    }

    /** Messages is on every plan's lock-task list; it stands in for an approved app until approvals are wired. */
    private fun phoneFromApp(check: String): CheckResult {
        home()?.let { return CheckResult(check, Verdict.ERROR, it) }
        val launch = context.packageManager.getLaunchIntentForPackage(MESSAGES)?.component
            ?: return CheckResult(check, Verdict.ERROR, "approved app $MESSAGES has no launcher activity")
        shell("am start -n ${launch.flattenToShortString()}")
        if (!waitFor(10_000) { top()?.packageName == MESSAGES }) {
            return CheckResult(check, Verdict.FAIL, "approved app $MESSAGES did not open: top=${top()}")
        }
        return phoneFromHome(check).let { it.copy(detail = "from $MESSAGES: ${it.detail}") }
    }

    /** NOT_BUILT only when the build declares it; a declared-built route with no entry fails. */
    private fun moreEmergency(check: String): CheckResult {
        if (check in declaredNotBuilt()) return CheckResult(check, Verdict.NOT_BUILT, "declared not built by the build ($NOT_BUILT_KEY)")
        home()?.let { return CheckResult(check, Verdict.ERROR, it) }
        val more = waitNode(MORE, 5_000) ?: return CheckResult(check, Verdict.FAIL, "Home has no More; top=${top()}")
        tap(more)
        SystemClock.sleep(2_000)
        val entry = findNode(PLAINPHONE) { it.contains("Emergency", ignoreCase = true) }
            ?: return CheckResult(check, Verdict.FAIL, "More shows no emergency entry; top=${top()}")
        val before = top()
        tap(entry)
        return observe(check, before, emergencyDialPad())
    }

    /**
     * The checking screen's Phone. The host holds the checking screen (debug TestHook HOLD) before this
     * check; any other screen is a set-up error, not a pass.
     */
    private fun phoneFromChecking(check: String): CheckResult {
        if (!waitFor(30_000) { top()?.packageName == PLAINPHONE }) {
            return CheckResult(check, Verdict.FAIL, "Plainphone is not in front: top=${top()}")
        }
        SystemClock.sleep(SETTLE_MS)
        val checking = findNode(PLAINPHONE) { it.contains("is checking the phone") } != null
        val uptime = SystemClock.elapsedRealtime() / 1000
        if (!checking) return CheckResult(check, Verdict.ERROR, "screen=${if (findNode(PLAINPHONE) { it == "Messages" } != null) "home" else "unknown"}: the checking hold is not in effect")
        return tapAndExpect(check, PHONE, dialPad()).let { it.copy(detail = "screen=checking uptime=${uptime}s ${it.detail}") }
    }

    /** The keyguard's Emergency call. The host has set a PIN; [expectLocked] means before the first unlock. */
    private fun lockScreenEmergency(check: String, expectLocked: Boolean): CheckResult {
        val users = context.getSystemService(UserManager::class.java)
        if (expectLocked && users.isUserUnlocked) return CheckResult(check, Verdict.ERROR, "user is already unlocked")
        if (!expectLocked) {
            shell("input keyevent KEYCODE_SLEEP")
            SystemClock.sleep(1_500)
        }
        shell("input keyevent KEYCODE_WAKEUP")
        SystemClock.sleep(1_000)
        shell("wm dismiss-keyguard")
        val button = waitNode(EMERGENCY_CALL, 10_000) { it == EMERGENCY_CALL || it == "Emergency" }
            ?: return CheckResult(check, Verdict.FAIL, "no Emergency call on the lock screen; top=${top()}")
        val before = top()
        tap(button)
        return observe(check, before, emergencyDialPad()).let {
            it.copy(detail = "${it.detail} unlocked=${users.isUserUnlocked} lockTask=${lockTask()}")
        }
    }

    /**
     * Plainphone can't start: the host kills its process in a loop (`su 0 kill -9`; `am force-stop` is a
     * no-op for a Device Owner). The system's own route, the keyguard's Emergency call, must still work.
     */
    private fun cannotStart(check: String): CheckResult {
        val pids = (1..4).map { SystemClock.sleep(400); shell("pidof $PLAINPHONE").trim() }.filter { it.isNotEmpty() }
        if (pids.size > 1) return CheckResult(check, Verdict.ERROR, "Plainphone is running (pid ${pids.last()}): the can't-start fault is not in effect")
        return lockScreenEmergency(check, expectLocked = false).let { it.copy(detail = "plainphoneDown=${4 - pids.size}/4 ${it.detail}") }
    }

    /**
     * Dials the test-mode emergency number from the system dialer, then hangs up whatever happens.
     * Refuses (ERROR, before any dialing) unless [requested] is exactly [NumberFence.NUMBER], this is an
     * emulator and the host registered the number with `cmd phone emergency-number-test-mode`.
     */
    private fun emergencyCall(requested: String): CheckResult {
        val check = "telecom-emergency-call"
        val qemu = listOf("ro.kernel.qemu", "ro.boot.qemu").map { shell("getprop $it").trim() }.firstOrNull { it == "1" } ?: ""
        NumberFence.refusal(requested, qemu, shell("su 0 cmd phone emergency-number-test-mode -p"))?.let {
            return CheckResult(check, Verdict.ERROR, it)
        }
        var result = CheckResult(check, Verdict.ERROR, "not run")
        try {
            result = dialTestNumber(check)
        } catch (e: Throwable) {
            result = CheckResult(check, Verdict.ERROR, "gate error: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            val state = hangUp()
            result = if (state == "0") {
                result.copy(detail = "${result.detail} afterHangUp=mCallState=0")
            } else {
                CheckResult(check, Verdict.ERROR, "hang-up failed (mCallState=$state) after: ${result.verdict} ${result.detail}")
            }
        }
        return result
    }

    private fun dialTestNumber(check: String): CheckResult {
        val reached = phoneFromHome(check)
        if (reached.verdict != Verdict.PASS) return reached.copy(detail = "dialer not reached: ${reached.detail}")
        val number = NumberFence.NUMBER // Never the argument: it was only checked against this constant.
        shell("am start -a android.intent.action.DIAL -d tel:$number")
        waitNode(number, 8_000) { it.filter(Char::isDigit) == number }
            ?: return CheckResult(check, Verdict.FAIL, "the dialer did not show $number; top=${top()}")
        SystemClock.sleep(SETTLE_MS)
        val call = waitNode("dial", 8_000) { it == "dial" }
            ?: return CheckResult(check, Verdict.FAIL, "no call button in the dialer; top=${top()}")
        tap(call)
        var calls = emptyList<TelecomCall>()
        waitFor(15_000) {
            calls = Dumpsys.calls(shell("dumpsys telecom"))
            GateJudge.emergencyCallPlaced(calls).verdict == Verdict.PASS
        }
        val telecom = shell("dumpsys telecom")
        val service = Regex("""tpac=ComponentInfo\{([^}]+)\}""").find(telecom)?.groupValues?.get(1)
        val operator = shell("getprop gsm.operator.alpha").trim()
        val result = GateJudge.emergencyCallPlaced(calls)
        return result.copy(detail = "${result.detail} service=$service operator=$operator")
    }

    // ---- alert display ----

    private fun alertPackage(): String? = context.packageManager.queryBroadcastReceivers(
        Intent(EMERGENCY_CB), PackageManager.MATCH_SYSTEM_ONLY or PackageManager.MATCH_UNINSTALLED_PACKAGES,
    ).map { it.activityInfo.packageName }.firstOrNull { it.contains("cellbroadcastreceiver") }

    private fun alertDisplayReady(): CheckResult {
        val name = alertPackage() ?: return GateJudge.alertDisplayReady(
            AlertDisplay("cellbroadcastreceiver", found = false, enabled = false, hidden = null, suspended = null, allowedInLockTask = false),
        )
        // An unreadable hidden/suspended state stays null (unknown), which is never ready.
        val user = Dumpsys.userState(shell("dumpsys package $name"))
        val hidden = user.hidden ?: Dumpsys.hiddenFromLists(name, shell("pm list packages -u"), shell("pm list packages"))
        val packageDisabled = shell("pm list packages -d").lines().any { it.trim() == "package:$name" }
        val componentState = runCatching {
            context.packageManager.getComponentEnabledSetting(ComponentName(name, ALERT_DIALOG))
        }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        val componentOff = componentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            componentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        val lockTaskList = Regex("""mLockTaskPackages[^\n]*\n\s*u0:\[([^\]]*)\]""").find(shell("dumpsys activity activities"))
            ?.groupValues?.get(1).orEmpty().split(", ").map { it.trim() }.toSet()
        val lockTaskOn = lockTask() != "NONE"
        val display = AlertDisplay(
            name, found = true, enabled = !packageDisabled && !componentOff,
            hidden = hidden, suspended = user.suspended,
            allowedInLockTask = !lockTaskOn || name in lockTaskList,
        )
        return GateJudge.alertDisplayReady(display).let { it.copy(detail = "${it.detail} componentState=$componentState lockTask=${lockTask()}") }
    }

    /**
     * Starts the alert screen directly (root): the closest the emulator gets to a received alert. With no
     * alert in the intent the screen closes itself at once, so the verdict is the activity that
     * `am start -W` reports starting: exactly the alert screen, or anything else (lock task's blocked
     * screen) fails.
     */
    private fun alertLaunch(): CheckResult {
        val check = "alert-display-launch"
        val name = alertPackage() ?: return CheckResult(check, Verdict.FAIL, "no alert-display package")
        home()?.let { return CheckResult(check, Verdict.ERROR, it) }
        val out = shell("su 0 am start -W -n $name/$ALERT_DIALOG")
        val started = Regex("""Activity: ([^/\s]+)/(\S+)""").find(out)?.let { m ->
            val cls = m.groupValues[2]
            Activity(m.groupValues[1], if (cls.startsWith(".")) m.groupValues[1] + cls else cls)
        }
        if (started?.className == GateJudge.BLOCKED_APP) shell("input keyevent KEYCODE_BACK")
        if (started == null) return CheckResult(check, Verdict.FAIL, "start refused: ${out.lines().filter { it.isNotBlank() }.joinToString(" | ")}")
        return GateJudge.inFront(check, started, setOf(Activity(name, ALERT_DIALOG))).let { it.copy(detail = "${it.detail} lockTask=${lockTask()}") }
    }

    // ---- helpers ----

    private fun tapAndExpect(check: String, label: String, expected: Set<Activity>): CheckResult {
        if (lockTask() != "LOCKED") return CheckResult(check, Verdict.FAIL, "lock task is ${lockTask()}, not LOCKED; top=${top()}")
        SystemClock.sleep(SETTLE_MS) // Let the screen transition finish, or the tap lands mid-animation.
        val node = waitNode(label, 10_000) { it == label }
            ?: return CheckResult(check, Verdict.FAIL, "no '$label' on screen; top=${top()}")
        val before = top()
        tap(node)
        return observe(check, before, expected).let { it.copy(detail = "${it.detail} lockTask=${lockTask()}") }
    }

    /**
     * After a tap: waits for one of [expected] (or lock task's blocked screen), then samples the
     * foreground for [STAY_MS] and reads the dial keys on screen. [GateJudge.routeReached] decides.
     */
    private fun observe(check: String, before: Activity?, expected: Set<Activity>): CheckResult {
        var seen: Activity? = null
        waitFor(8_000) {
            seen = top()
            seen in expected || seen?.className == GateJudge.BLOCKED_APP
        }
        val samples = mutableListOf(seen)
        var labels = emptyList<String>()
        if (seen in expected) {
            labels = visibleLabels()
            repeat(STAY_SAMPLES) { SystemClock.sleep(STAY_MS / STAY_SAMPLES); samples += top() }
        }
        // Close lock task's blocked screen, or it stays behind as a locked task of its own.
        if (samples.any { it?.className == GateJudge.BLOCKED_APP }) shell("input keyevent KEYCODE_BACK")
        return GateJudge.routeReached(check, before, samples, expected, labels)
    }

    /** The activities `ACTION_DIAL` resolves to in the system dialer (Plainphone's Phone intent). */
    private fun dialPad(): Set<Activity> {
        val dialer = context.getSystemService(TelecomManager::class.java)?.systemDialerPackage ?: return emptySet()
        return resolved(Intent(Intent.ACTION_DIAL).setPackage(dialer))
    }

    /** The activities the emergency-dialer intent resolves to (the keyguard's and More's target). */
    private fun emergencyDialPad(): Set<Activity> = resolved(Intent(EMERGENCY_DIAL))

    /** Each resolved activity by its name and, for an alias, its target: `dumpsys` shows the target. */
    private fun resolved(intent: Intent): Set<Activity> =
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_SYSTEM_ONLY).flatMap { info ->
            val a = info.activityInfo
            listOfNotNull(Activity(a.packageName, a.name), a.targetActivity?.let { Activity(a.packageName, it) })
        }.toSet()

    private fun visibleLabels(): List<String> {
        val labels = mutableListOf<String>()
        findNode { labels += it; false }
        return labels
    }

    /** Presses Home and waits for Plainphone. Returns a failure detail, or null. */
    private fun home(): String? {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("input keyevent KEYCODE_HOME")
        return if (waitFor(10_000) { top()?.packageName == PLAINPHONE }) null else "Home is not Plainphone: top=${top()}"
    }

    /** Ends the test call from the in-call screen (`KEYCODE_ENDCALL` does not end an emergency call). */
    private fun hangUp(): String {
        repeat(3) {
            if (callState() == "0") return "0"
            shell("input keyevent KEYCODE_WAKEUP")
            if (top()?.className?.endsWith("InCallActivity") != true) shell("su 0 am start -n $IN_CALL")
            waitNode("End call", 5_000) { it == "End call" }?.let(::tap)
            waitFor(5_000) { callState() == "0" }
        }
        return callState()
    }

    private fun callState(): String = Regex("""mCallState=(\d)""").find(shell("dumpsys telephony.registry"))?.groupValues?.get(1) ?: "?"

    private fun top(): Activity? = Dumpsys.topResumed(shell("dumpsys activity activities"))

    private fun lockTask(): String = Dumpsys.lockTaskState(shell("dumpsys activity activities")) ?: "?"

    private fun tap(node: AccessibilityNodeInfo) {
        val bounds = Rect().also(node::getBoundsInScreen)
        shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
    }

    private fun waitNode(label: String, timeoutMs: Long, match: (String) -> Boolean = { it == label }): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        waitFor(timeoutMs) { findNode(match = match)?.also { found = it } != null }
        return found
    }

    /** First node, in any window (or only [inPackage]'s), whose text or description matches. */
    private fun findNode(inPackage: String? = null, match: (String) -> Boolean): AccessibilityNodeInfo? {
        val roots = uiAutomation.windows.mapNotNull { it.root } + listOfNotNull(uiAutomation.rootInActiveWindow)
        for (root in roots) {
            val queue = ArrayDeque(listOf(root))
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                val labels = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                val packageOk = inPackage == null || node.packageName?.toString() == inPackage
                if (packageOk && node.isVisibleToUser && labels.any(match)) return node
                for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
            }
        }
        return null
    }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            if (runCatching(condition).getOrDefault(false)) return true
            if (SystemClock.uptimeMillis() > end) return false
            SystemClock.sleep(250)
        }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(command)).use { it.readBytes().decodeToString() }

    private companion object {
        const val PLAINPHONE = "org.plainphone"
        const val SETTLE_MS = 1_500L
        const val STAY_MS = 2_000L
        const val STAY_SAMPLES = 4
        const val NOT_BUILT_KEY = "org.plainphone.gate.notBuilt"
        const val EMERGENCY_DIAL = "com.android.phone.EmergencyDialer.DIAL"
        const val MESSAGES = "com.google.android.apps.messaging"
        const val IN_CALL = "com.google.android.dialer/com.android.dialer.incall.activity.ui.InCallActivity"
        const val PHONE = "Phone"
        const val MORE = "More"
        const val EMERGENCY_CALL = "Emergency call"
        const val EMERGENCY_CB = "android.provider.action.SMS_EMERGENCY_CB_RECEIVED"
        const val ALERT_DIALOG = "com.android.cellbroadcastreceiver.CellBroadcastAlertDialog"
    }
}
