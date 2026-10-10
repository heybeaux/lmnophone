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
 * ([CheckResult.line]); `tools/emergency-gate.sh` sets up each state (PIN, reboot, test number) and
 * collects them. Verdicts come from [GateJudge].
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
            listOf(CheckResult(check.ifEmpty { "unknown" }, Verdict.FAIL, "gate error: ${e.javaClass.simpleName}: ${e.message}"))
        }
        val report = results.joinToString("\n") { it.line() } + "\n"
        finish(AndroidActivity.RESULT_OK, Bundle().apply { putString(REPORT_KEY_STREAMRESULT, report) })
    }

    private fun run(check: String): List<CheckResult> = when (check) {
        "home-phone" -> listOf(phoneFromHome(check))
        "app-home-phone" -> listOf(phoneFromApp(check))
        "more-emergency" -> listOf(moreEmergency(check))
        "boot-checking" -> listOf(phoneAfterBoot(check))
        "lock-screen" -> listOf(lockScreenEmergency(check, expectLocked = false))
        "locked-boot" -> listOf(lockScreenEmergency(check, expectLocked = true))
        "sos" -> listOf(sos(check))
        "telecom-emergency-call" -> listOf(emergencyCall(arguments.getString("number") ?: "7357"))
        "alert" -> listOf(alertDisplayReady(), alertLaunch(), GateJudge.alertShown(injectionSupported = false, shown = null)
            .let { it.copy(detail = "${it.detail} (emulator console has no cbs; modem has no +CBM injection)") })
        else -> listOf(CheckResult(check.ifEmpty { "unknown" }, Verdict.FAIL, "unknown check"))
    }

    // ---- routes ----

    private fun phoneFromHome(check: String): CheckResult {
        home()?.let { return CheckResult(check, Verdict.FAIL, it) }
        return tapAndExpect(check, PHONE, systemDialer())
    }

    /** Messages is on every plan's lock-task list; it stands in for an approved app until approvals are wired. */
    private fun phoneFromApp(check: String): CheckResult {
        home()?.let { return CheckResult(check, Verdict.FAIL, it) }
        val launch = context.packageManager.getLaunchIntentForPackage(MESSAGES)?.component
            ?: return CheckResult(check, Verdict.FAIL, "approved app $MESSAGES has no launcher activity")
        shell("am start -n ${launch.flattenToShortString()}")
        if (!waitFor(10_000) { top()?.packageName == MESSAGES }) {
            return CheckResult(check, Verdict.FAIL, "approved app $MESSAGES did not open: top=${top()}")
        }
        return phoneFromHome(check).let { it.copy(detail = "from $MESSAGES: ${it.detail}") }
    }

    private fun moreEmergency(check: String): CheckResult {
        home()?.let { return CheckResult(check, Verdict.FAIL, it) }
        val more = waitNode(MORE, 5_000) ?: return CheckResult(check, Verdict.NOT_BUILT, "Home has no More")
        tap(more)
        SystemClock.sleep(2_000)
        val entry = findNode(PLAINPHONE) { it.contains("Emergency", ignoreCase = true) }
            ?: return CheckResult(check, Verdict.NOT_BUILT, "More shows no emergency entry; top=${top()}")
        tap(entry)
        return expect(check, systemDialer())
    }

    /** Straight after boot: Plainphone's first screen, usually the checking screen, then Phone. */
    private fun phoneAfterBoot(check: String): CheckResult {
        if (!waitFor(30_000) { top()?.packageName == PLAINPHONE }) {
            return CheckResult(check, Verdict.FAIL, "Plainphone is not in front after boot: top=${top()}")
        }
        SystemClock.sleep(SETTLE_MS)
        val screen = when {
            findNode(PLAINPHONE) { it == "Messages" } != null -> "home"
            findNode(PLAINPHONE) { it.contains("is checking the phone") } != null -> "checking"
            else -> "unknown"
        }
        val uptime = SystemClock.elapsedRealtime() / 1000
        return tapAndExpect(check, PHONE, systemDialer()).let { it.copy(detail = "screen=$screen uptime=${uptime}s ${it.detail}") }
    }

    /** The keyguard's Emergency call. The host has set a PIN; [expectLocked] means before the first unlock. */
    private fun lockScreenEmergency(check: String, expectLocked: Boolean): CheckResult {
        val users = context.getSystemService(UserManager::class.java)
        if (expectLocked && users.isUserUnlocked) return CheckResult(check, Verdict.FAIL, "user is already unlocked")
        if (!expectLocked) {
            shell("input keyevent KEYCODE_SLEEP")
            SystemClock.sleep(1_500)
        }
        shell("input keyevent KEYCODE_WAKEUP")
        SystemClock.sleep(1_000)
        shell("wm dismiss-keyguard")
        val button = waitNode(EMERGENCY_CALL, 10_000) { it == EMERGENCY_CALL || it == "Emergency" }
            ?: return CheckResult(check, Verdict.FAIL, "no Emergency call on the lock screen; top=${top()}")
        tap(button)
        return expect(check, emergencyDialers()).let {
            it.copy(detail = "${it.detail} unlocked=${users.isUserUnlocked} lockTask=${lockTask()}")
        }
    }

    /**
     * Power pressed five times. Safe only in Touch & hold mode: the gate never touches the hold area,
     * and fails if any call starts.
     */
    private fun sos(check: String): CheckResult {
        home()?.let { return CheckResult(check, Verdict.FAIL, it) }
        // Spaced presses: back-to-back keyevents are rejected as too fast. executeShellCommand does not
        // parse shell syntax, so the loop goes to sh on stdin.
        shellScript("for i in 1 2 3 4 5; do input keyevent 26; sleep 0.1; done")
        SystemClock.sleep(2_000)
        shell("input keyevent KEYCODE_WAKEUP")
        val result = expect(check, emergencyDialers(), 8_000)
        val holdMode = findNode { it.contains("Touch & hold", ignoreCase = true) } != null
        val callState = callState()
        // Close SOS without touching it (its Cancel is a slider), and the blocked camera from presses 1-2.
        shell("am force-stop $SAFETYHUB")
        home()
        return when {
            callState != "0" -> CheckResult(check, Verdict.FAIL, "a call started (mCallState=$callState): ${result.detail}")
            result.verdict == Verdict.PASS && !holdMode ->
                CheckResult(check, Verdict.FAIL, "SOS is not in Touch & hold mode: ${result.detail}")
            else -> result.copy(detail = "${result.detail} touchAndHold=$holdMode mCallState=$callState lockTask=${lockTask()}")
        }
    }

    /** Dials a test-mode emergency number (added by the host) from the system dialer, then hangs up. */
    private fun emergencyCall(number: String): CheckResult {
        val check = "telecom-emergency-call"
        val reached = phoneFromHome(check)
        if (reached.verdict != Verdict.PASS) return reached.copy(detail = "dialer not reached: ${reached.detail}")
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
        hangUp()
        return result.copy(detail = "${result.detail} service=$service operator=$operator")
    }

    // ---- alert display ----

    private fun alertPackage(): String? = context.packageManager.queryBroadcastReceivers(
        Intent(EMERGENCY_CB), PackageManager.MATCH_SYSTEM_ONLY or PackageManager.MATCH_UNINSTALLED_PACKAGES,
    ).map { it.activityInfo.packageName }.firstOrNull { it.contains("cellbroadcastreceiver") }

    private fun alertDisplayReady(): CheckResult {
        val name = alertPackage() ?: return GateJudge.alertDisplayReady(
            AlertDisplay("cellbroadcastreceiver", found = false, enabled = false, hidden = false, suspended = false, allowedInLockTask = false),
        )
        val user = Regex("""User 0:[^\n]*""").find(shell("dumpsys package $name"))?.value.orEmpty()
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
            hidden = user.contains("hidden=true"), suspended = user.contains("suspended=true"),
            allowedInLockTask = !lockTaskOn || name in lockTaskList,
        )
        return GateJudge.alertDisplayReady(display).let { it.copy(detail = "${it.detail} componentState=$componentState lockTask=${lockTask()}") }
    }

    /**
     * Starts the alert screen directly (root): the closest the emulator gets to a received alert. With no
     * alert in the intent the screen closes itself at once, so the verdict is the activity that
     * `am start -W` reports starting: the alert screen, or lock task's blocked screen.
     */
    private fun alertLaunch(): CheckResult {
        val check = "alert-display-launch"
        val name = alertPackage() ?: return CheckResult(check, Verdict.FAIL, "no alert-display package")
        home()?.let { return CheckResult(check, Verdict.FAIL, it) }
        val out = shell("su 0 am start -W -n $name/$ALERT_DIALOG")
        val started = Regex("""Activity: ([^/\s]+)/(\S+)""").find(out)?.let { m ->
            val cls = m.groupValues[2]
            Activity(m.groupValues[1], if (cls.startsWith(".")) m.groupValues[1] + cls else cls)
        }
        if (started?.className == GateJudge.BLOCKED_APP) shell("input keyevent KEYCODE_BACK")
        if (started == null) return CheckResult(check, Verdict.FAIL, "start refused: ${out.lines().filter { it.isNotBlank() }.joinToString(" | ")}")
        return GateJudge.dialerInFront(check, started, setOf(name)).let { it.copy(detail = "${it.detail} lockTask=${lockTask()}") }
    }

    // ---- helpers ----

    private fun tapAndExpect(check: String, label: String, accepted: Set<String>): CheckResult {
        if (lockTask() != "LOCKED") return CheckResult(check, Verdict.FAIL, "lock task is ${lockTask()}, not LOCKED; top=${top()}")
        SystemClock.sleep(SETTLE_MS) // Let the screen transition finish, or the tap lands mid-animation.
        val node = waitNode(label, 10_000) { it == label }
            ?: return CheckResult(check, Verdict.FAIL, "no '$label' on screen; top=${top()}")
        tap(node)
        return expect(check, accepted).let { it.copy(detail = "${it.detail} lockTask=${lockTask()}") }
    }

    /** Waits for an accepted package (or lock task's blocked screen) on top, then judges. */
    private fun expect(check: String, accepted: Set<String>, timeoutMs: Long = 8_000): CheckResult {
        var seen: Activity? = null
        waitFor(timeoutMs) {
            seen = top()
            seen?.packageName in accepted || seen?.className == GateJudge.BLOCKED_APP
        }
        return GateJudge.dialerInFront(check, seen, accepted)
    }

    /** Presses Home and waits for Plainphone. Returns a failure detail, or null. */
    private fun home(): String? {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("input keyevent KEYCODE_HOME")
        return if (waitFor(10_000) { top()?.packageName == PLAINPHONE }) null else "Home is not Plainphone: top=${top()}"
    }

    /** Ends the test call from the in-call screen (`KEYCODE_ENDCALL` does not end an emergency call). */
    private fun hangUp() {
        repeat(3) {
            if (callState() == "0") return
            shell("input keyevent KEYCODE_WAKEUP")
            if (top()?.className?.endsWith("InCallActivity") != true) shell("su 0 am start -n $IN_CALL")
            waitNode("End call", 5_000) { it == "End call" }?.let(::tap)
            waitFor(5_000) { callState() == "0" }
        }
    }

    private fun callState(): String? = Regex("""mCallState=(\d)""").find(shell("dumpsys telephony.registry"))?.groupValues?.get(1)

    private fun systemDialer(): Set<String> =
        setOfNotNull(context.getSystemService(TelecomManager::class.java)?.systemDialerPackage)

    /** The emergency dialers the keyguard and SOS may open: the system dialer, Phone's EmergencyDialer, the EMERGENCY role holder. */
    private fun emergencyDialers(): Set<String> = systemDialer() + "com.android.phone" +
        shell("cmd role get-role-holders android.app.role.EMERGENCY").split(';', '\n').map { it.trim() }.filter { it.isNotEmpty() }

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

    private fun shellScript(script: String) {
        val (output, input) = uiAutomation.executeShellCommandRw("sh")
        ParcelFileDescriptor.AutoCloseOutputStream(input).use { it.write("$script\nexit\n".toByteArray()) }
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(command)).use { it.readBytes().decodeToString() }

    private companion object {
        const val PLAINPHONE = "org.plainphone"
        const val SETTLE_MS = 1_500L
        const val MESSAGES = "com.google.android.apps.messaging"
        const val SAFETYHUB = "com.google.android.apps.safetyhub"
        const val IN_CALL = "com.google.android.dialer/com.android.dialer.incall.activity.ui.InCallActivity"
        const val PHONE = "Phone"
        const val MORE = "More"
        const val EMERGENCY_CALL = "Emergency call"
        const val EMERGENCY_CB = "android.provider.action.SMS_EMERGENCY_CB_RECEIVED"
        const val ALERT_DIALOG = "com.android.cellbroadcastreceiver.CellBroadcastAlertDialog"
    }
}
