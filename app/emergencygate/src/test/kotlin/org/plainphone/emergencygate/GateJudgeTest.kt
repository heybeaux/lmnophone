// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.emergencygate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GateJudgeTest {
    // ---- dumpsys parsing ----

    @Test fun `top resumed activity expands a short class name`() {
        val dump = """
            |  Display #0
            |    topResumedActivity=ActivityRecord{a6f869c u0 com.google.android.dialer/.extensions.GoogleDialtactsActivity t11}
            |  ResumedActivity: ActivityRecord{a6f869c u0 other/.Ignored t11}
        """.trimMargin()
        assertEquals(
            Activity("com.google.android.dialer", "com.google.android.dialer.extensions.GoogleDialtactsActivity"),
            Dumpsys.topResumed(dump),
        )
    }

    @Test fun `top resumed activity keeps a full class name`() {
        val dump = "    topResumedActivity=ActivityRecord{1 u0 android/com.android.internal.app.BlockedAppActivity t9}"
        assertEquals(Activity("android", "com.android.internal.app.BlockedAppActivity"), Dumpsys.topResumed(dump))
    }

    @Test fun `no top resumed activity reads as null`() {
        assertNull(Dumpsys.topResumed("  mFocusedApp=null\n"))
    }

    @Test fun `lock task state is read`() {
        assertEquals("LOCKED", Dumpsys.lockTaskState("  mLockTaskModeState=LOCKED\n  mLockTaskModeTasks=[]"))
        assertNull(Dumpsys.lockTaskState("nothing here"))
    }

    @Test fun `telecom calls carry state and the emergency flag from their own block`() {
        val dump = """
            |  mCallsManager:
            |    [Call id=TC@1, state=ACTIVE, tpac=ComponentInfo{com.android.phone/x}, handle=tel:**57, prop=[ ecbm]], voip=false
            |    [Call id=TC@2, state=DISCONNECTED, tpac=ComponentInfo{com.android.phone/x}, handle=tel:**00], voip=false
            |  Call TC@1: {
            |      isEmergency: true
            |  }
            |  Call TC@2: {
            |      isEmergency: false
            |  }
        """.trimMargin()
        assertEquals(
            listOf(TelecomCall("TC@1", "ACTIVE", emergency = true), TelecomCall("TC@2", "DISCONNECTED", emergency = false)),
            Dumpsys.calls(dump),
        )
    }

    @Test fun `a call listed twice is reported once`() {
        val line = "    [Call id=TC@1, state=DIALING, tpac=x], voip=false\n"
        assertEquals(1, Dumpsys.calls(line + line + "Call TC@1: {\n isEmergency: true\n}").size)
    }

    // ---- judging ----

    private val dialPad = Activity("com.google.android.dialer", "com.android.dialer.main.impl.MainActivity")
    private val home = Activity("org.plainphone", "org.plainphone.MainActivity")
    private val digits = (0..9).map { it.toString() }

    private fun reached(before: Activity?, samples: List<Activity?>, labels: List<String> = digits) =
        GateJudge.routeReached("home-phone", before, samples, setOf(dialPad), labels)

    @Test fun `the expected dial pad, usable and still in front, passes`() {
        val result = reached(home, listOf(dialPad, dialPad, dialPad))
        assertEquals(result.detail, Verdict.PASS, result.verdict)
        assertEquals("GATE home-phone PASS ${dialPad}", result.line())
    }

    @Test fun `a wrong class in an accepted package fails`() {
        val settings = Activity("com.android.phone", "com.android.phone.Settings")
        val result = GateJudge.routeReached("locked-boot", home, listOf(settings), setOf(Activity("com.android.phone", "com.android.phone.EmergencyDialer")), digits)
        assertEquals(Verdict.FAIL, result.verdict)
        assertTrue(result.detail, result.detail.contains("com.android.phone/com.android.phone.Settings"))
    }

    @Test fun `an unrelated screen of the emergency role holder fails`() {
        val other = Activity("com.google.android.apps.safetyhub", "com.google.android.apps.safetyhub.NotADialer")
        val expected = setOf(Activity("com.google.android.apps.safetyhub", "com.google.android.apps.safetyhub.emergencydialer.ui.DialPadActivity"))
        assertEquals(Verdict.FAIL, GateJudge.routeReached("lock-screen", home, listOf(other), expected, digits).verdict)
    }

    @Test fun `a dial pad that does not stay in front fails`() {
        val result = reached(home, listOf(dialPad, home))
        assertEquals(Verdict.FAIL, result.verdict)
        assertTrue(result.detail, result.detail.contains("did not stay"))
    }

    // PLA-296 R1: a missing sample is a failing sample, wherever it falls.
    @Test fun `a missing middle sample fails`() {
        val result = reached(home, listOf(dialPad, null, dialPad))
        assertEquals(result.detail, Verdict.FAIL, result.verdict)
        assertTrue(result.detail, result.detail.contains("then nothing"))
    }

    @Test fun `a missing final sample fails`() {
        val result = reached(home, listOf(dialPad, dialPad, null))
        assertEquals(result.detail, Verdict.FAIL, result.verdict)
    }

    @Test fun `a missing sample followed by a blocked or wrong screen fails`() {
        val blocked = Activity("android", GateJudge.BLOCKED_APP)
        val wrong = Activity("com.google.android.dialer", "com.android.dialer.SomethingElse")
        assertEquals(Verdict.FAIL, reached(home, listOf(dialPad, null, blocked)).verdict)
        assertEquals(Verdict.FAIL, reached(home, listOf(dialPad, null, wrong)).verdict)
    }

    @Test fun `a tap that changed nothing fails even with the dial pad in front`() {
        val result = reached(dialPad, listOf(dialPad, dialPad))
        assertEquals(Verdict.FAIL, result.verdict)
        assertTrue(result.detail, result.detail.contains("before the tap"))
    }

    @Test fun `a dial pad without its keys fails`() {
        val result = reached(home, listOf(dialPad, dialPad), labels = listOf("1", "2", "Search"))
        assertEquals(Verdict.FAIL, result.verdict)
        assertTrue(result.detail, result.detail.contains("dial pad"))
    }

    @Test fun `blocked app screen fails even when listed as expected`() {
        val blocked = Activity("android", GateJudge.BLOCKED_APP)
        val result = GateJudge.routeReached("home-phone", home, listOf(blocked), setOf(dialPad, blocked), digits)
        assertEquals(Verdict.FAIL, result.verdict)
        assertTrue(result.detail, result.detail.contains("blocked"))
    }

    @Test fun `Plainphone still in front fails`() {
        val result = reached(home, listOf(home))
        assertEquals(Verdict.FAIL, result.verdict)
        assertTrue(result.detail, result.detail.contains("org.plainphone/org.plainphone.MainActivity"))
    }

    @Test fun `nothing in front, no samples or no expected component fails`() {
        assertEquals(Verdict.FAIL, reached(home, listOf(null)).verdict)
        assertEquals(Verdict.FAIL, reached(home, emptyList()).verdict)
        assertEquals(Verdict.FAIL, GateJudge.routeReached("home-phone", home, listOf(dialPad), emptySet(), digits).verdict)
    }

    @Test fun `dial pad keys are read from labels such as 2 ABC`() {
        assertTrue(GateJudge.dialPadUsable(listOf("1", "2,ABC", "3 DEF", "4,GHI", "5,JKL", "6,MNO", "7,PQRS", "8,TUV", "9,WXYZ", "0 +")))
        assertTrue(!GateJudge.dialPadUsable(listOf("12", "345", "6789", "0")))
        assertTrue(!GateJudge.dialPadUsable(emptyList()))
    }

    @Test fun `the alert screen launch is judged by its exact component`() {
        val dialog = Activity("com.google.android.cellbroadcastreceiver", "com.android.cellbroadcastreceiver.CellBroadcastAlertDialog")
        assertEquals(Verdict.PASS, GateJudge.inFront("alert-display-launch", dialog, setOf(dialog)).verdict)
        val other = Activity("com.google.android.cellbroadcastreceiver", "com.android.cellbroadcastreceiver.CellBroadcastSettings")
        assertEquals(Verdict.FAIL, GateJudge.inFront("alert-display-launch", other, setOf(dialog)).verdict)
    }

    // ---- PLA-296 R4: the alert launch result, by exit status, output and the read-back fault ----

    private val alertPkg = "com.google.android.cellbroadcastreceiver"
    private val dialog = Activity(alertPkg, "com.android.cellbroadcastreceiver.CellBroadcastAlertDialog")
    private val visible = AlertDisplay(alertPkg, found = true, enabled = true, hidden = false, suspended = false, allowedInLockTask = true)
    private val hidden = visible.copy(hidden = true)

    /** Recorded on the API 35 emulator (PLA-245 ctlNoAlert) and reproduced with `su 0 sh` (exit 1). */
    private val error3 = """
        |Starting: Intent { cmp=$alertPkg/com.android.cellbroadcastreceiver.CellBroadcastAlertDialog }
        |Error type 3
        |Error: Activity class {$alertPkg/com.android.cellbroadcastreceiver.CellBroadcastAlertDialog} does not exist.
    """.trimMargin()

    private fun launch(display: AlertDisplay, exit: Int?, out: String) = GateJudge.alertLaunched(display, exit, out, setOf(dialog))

    @Test fun `the alert screen starting passes`() {
        val out = "Starting: Intent { cmp=$alertPkg/com.android.cellbroadcastreceiver.CellBroadcastAlertDialog }\nStatus: ok\nActivity: $alertPkg/com.android.cellbroadcastreceiver.CellBroadcastAlertDialog\nComplete"
        assertEquals(Verdict.PASS, launch(visible, 0, out).verdict)
    }

    @Test fun `lock task's blocked screen is a behavioural failure`() {
        val out = "Status: ok\nActivity: android/com.android.internal.app.BlockedAppActivity\nComplete"
        val result = launch(visible, 0, out)
        assertEquals(Verdict.FAIL, result.verdict)
        assertTrue(result.detail, result.detail.startsWith("blocked by lock task:"))
    }

    @Test fun `an unavailable component with the hidden state read back fails as unavailable`() {
        val result = launch(hidden, 1, error3)
        assertEquals(result.detail, Verdict.FAIL, result.verdict)
        assertTrue(result.detail, result.detail.startsWith("unavailable:"))
    }

    @Test fun `an unavailable component with no fault read back is an error, not detection`() {
        assertEquals(Verdict.ERROR, launch(visible, 1, error3).verdict)
        assertEquals(Verdict.ERROR, launch(visible.copy(hidden = null), 1, error3).verdict)
    }

    @Test fun `empty output is an error`() {
        assertEquals(Verdict.ERROR, launch(hidden, 0, "").verdict)
        assertEquals(Verdict.ERROR, launch(hidden, 1, "").verdict)
        assertEquals(Verdict.ERROR, launch(hidden, null, "").verdict)
    }

    @Test fun `a shell or service failure is an error`() {
        assertEquals(Verdict.ERROR, launch(hidden, 255, "java.lang.SecurityException: shell failed").verdict)
        assertEquals(Verdict.ERROR, launch(hidden, 20, "cmd: Can't find service: activity").verdict)
        assertEquals(Verdict.ERROR, launch(hidden, 1, "$error3\njava.lang.SecurityException: Permission Denial").verdict)
    }

    @Test fun `no exit status or an exit status that contradicts the output is an error`() {
        assertEquals(Verdict.ERROR, launch(hidden, null, error3).verdict)
        assertEquals(Verdict.ERROR, launch(hidden, 0, error3).verdict)
        assertEquals(Verdict.ERROR, launch(visible, 1, "Activity: ${dialog}").verdict)
    }

    @Test fun `unrecognised output is an error`() {
        assertEquals(Verdict.ERROR, launch(hidden, 1, "Starting: Intent { }\nsomething else").verdict)
    }

    @Test fun `an emergency call that Telecom is placing passes`() {
        for (state in listOf("CONNECTING", "DIALING", "ACTIVE")) {
            val result = GateJudge.emergencyCallPlaced(listOf(TelecomCall("TC@1", state, emergency = true)))
            assertEquals(state, Verdict.PASS, result.verdict)
        }
    }

    @Test fun `no call, an ordinary call or a dead emergency call fails`() {
        assertEquals(Verdict.FAIL, GateJudge.emergencyCallPlaced(emptyList()).verdict)
        assertEquals(Verdict.FAIL, GateJudge.emergencyCallPlaced(listOf(TelecomCall("TC@1", "ACTIVE", emergency = false))).verdict)
        assertEquals(Verdict.FAIL, GateJudge.emergencyCallPlaced(listOf(TelecomCall("TC@1", "DISCONNECTED", emergency = true))).verdict)
    }

    @Test fun `an image that cannot inject an alert is inconclusive, never a pass`() {
        val result = GateJudge.alertShown(injectionSupported = false, shown = null)
        assertEquals(Verdict.INCONCLUSIVE, result.verdict)
    }

    @Test fun `an injected alert must be seen`() {
        assertEquals(Verdict.PASS, GateJudge.alertShown(injectionSupported = true, shown = true).verdict)
        assertEquals(Verdict.FAIL, GateJudge.alertShown(injectionSupported = true, shown = false).verdict)
        assertEquals(Verdict.FAIL, GateJudge.alertShown(injectionSupported = true, shown = null).verdict)
    }

    @Test fun `alert display is ready only when present, enabled, visible, unsuspended and allowed in lock task`() {
        val ok = AlertDisplay("cb", found = true, enabled = true, hidden = false, suspended = false, allowedInLockTask = true)
        assertEquals(Verdict.PASS, GateJudge.alertDisplayReady(ok).verdict)
        for (broken in listOf(
            ok.copy(found = false), ok.copy(enabled = false), ok.copy(hidden = true),
            ok.copy(suspended = true), ok.copy(allowedInLockTask = false),
        )) {
            assertEquals(broken.toString(), Verdict.FAIL, GateJudge.alertDisplayReady(broken).verdict)
        }
    }

    @Test fun `an unreadable hidden or suspended state is unknown, never ready`() {
        val ok = AlertDisplay("cb", found = true, enabled = true, hidden = false, suspended = false, allowedInLockTask = true)
        val hiddenUnknown = GateJudge.alertDisplayReady(ok.copy(hidden = null))
        assertEquals(Verdict.FAIL, hiddenUnknown.verdict)
        assertTrue(hiddenUnknown.detail, hiddenUnknown.detail.contains("hidden state unknown"))
        assertEquals(Verdict.FAIL, GateJudge.alertDisplayReady(ok.copy(suspended = null)).verdict)
    }

    private fun fixture(name: String) = javaClass.classLoader!!.getResource("packages/$name")!!.readText()

    @Test fun `API 35 package dump - visible, hidden and hidden plus suspended`() {
        assertEquals(PackageUserState(hidden = false, suspended = false), Dumpsys.userState(fixture("api35-visible.txt")))
        assertEquals(PackageUserState(hidden = true, suspended = false), Dumpsys.userState(fixture("api35-hidden.txt")))
        assertEquals(PackageUserState(hidden = true, suspended = true), Dumpsys.userState(fixture("api35-hidden-suspended.txt")))
    }

    @Test fun `API 36 package dumps keep or drop the fields as recorded`() {
        for (name in listOf("api36-visible.txt", "api36-hidden.txt")) {
            val state = Dumpsys.userState(fixture(name))
            val text = fixture(name)
            val first = text.lines().first { it.trimStart().startsWith("User 0: ") }
            assertEquals(name, if ("hidden=" in first) "hidden=true" in first else null, state.hidden)
        }
    }

    @Test fun `a dump without the user line reads as unknown`() {
        assertEquals(PackageUserState(hidden = null, suspended = null), Dumpsys.userState("Packages:\n  Package [x]\n    User 0:\n      [a,b]:\n"))
    }

    @Test fun `hidden is taken from package lists when the dump lacks it`() {
        assertEquals(true, Dumpsys.hiddenFromLists("cb", all = "package:a\npackage:cb\n", visible = "package:a\n"))
        assertEquals(false, Dumpsys.hiddenFromLists("cb", all = "package:cb\n", visible = "package:cb\n"))
        assertEquals(null, Dumpsys.hiddenFromLists("cb", all = "", visible = ""))
    }

    @Test fun `gate lines parse back`() {
        val line = CheckResult("lock-screen", Verdict.FAIL, "foreground is a/b c").line()
        assertEquals(CheckResult("lock-screen", Verdict.FAIL, "foreground is a/b c"), CheckResult.parse(line))
        assertNull(CheckResult.parse("INSTRUMENTATION_STATUS: x"))
    }
}
