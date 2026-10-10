// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.emergencygate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GateJudgeTest {
    private val dialers = setOf("com.google.android.dialer", "com.android.phone")

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

    @Test fun `system dialer in front passes`() {
        val result = GateJudge.dialerInFront("home-phone", Activity("com.google.android.dialer", "x.Dialtacts"), dialers)
        assertEquals(Verdict.PASS, result.verdict)
        assertEquals("GATE home-phone PASS com.google.android.dialer/x.Dialtacts", result.line())
    }

    @Test fun `blocked app screen fails even though it is the android package`() {
        val result = GateJudge.dialerInFront("home-phone", Activity("android", GateJudge.BLOCKED_APP), dialers + "android")
        assertEquals(Verdict.FAIL, result.verdict)
        assertTrue(result.detail, result.detail.contains("blocked"))
    }

    @Test fun `Plainphone still in front fails`() {
        val result = GateJudge.dialerInFront("home-phone", Activity("org.plainphone", "org.plainphone.MainActivity"), dialers)
        assertEquals(Verdict.FAIL, result.verdict)
        assertTrue(result.detail, result.detail.contains("org.plainphone/org.plainphone.MainActivity"))
    }

    @Test fun `nothing in front fails`() {
        assertEquals(Verdict.FAIL, GateJudge.dialerInFront("boot-checking", null, dialers).verdict)
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

    @Test fun `overall verdict - any fail fails, then any inconclusive, and not-built items are ignored`() {
        fun r(v: Verdict) = CheckResult("c", v, "")
        assertEquals(Verdict.PASS, GateJudge.overall(listOf(r(Verdict.PASS), r(Verdict.NOT_BUILT))))
        assertEquals(Verdict.INCONCLUSIVE, GateJudge.overall(listOf(r(Verdict.PASS), r(Verdict.INCONCLUSIVE))))
        assertEquals(Verdict.FAIL, GateJudge.overall(listOf(r(Verdict.INCONCLUSIVE), r(Verdict.FAIL), r(Verdict.PASS))))
        assertEquals(Verdict.FAIL, GateJudge.overall(emptyList()))
    }

    @Test fun `gate lines parse back`() {
        val line = CheckResult("lock-screen", Verdict.FAIL, "foreground is a/b c").line()
        assertEquals(CheckResult("lock-screen", Verdict.FAIL, "foreground is a/b c"), CheckResult.parse(line))
        assertNull(CheckResult.parse("INSTRUMENTATION_STATUS: x"))
    }
}
