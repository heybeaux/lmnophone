// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import android.app.ActivityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * device-lockdown, "Lock task must actually be running" (PLA-307 finding 2, PLA-322 findings 1 and 3):
 * after an unlocked pass that reports ready, a phone that is not in lock task (an unapproved app won
 * the race with Home's process death) brings Home forward, and Home enters lock task through its
 * KEYGUARD gate. Only LOCKED is containment. PINNED is not ready and needs attention, and starting
 * Home is not assumed to end a foreign pin. A calling screen that recovery displaces comes back
 * inside lock task once LOCKED reads back.
 */
class LockTaskRecoveryTest {
    private var state: () -> LockTaskState = { LockTaskState.NONE }
    private var inCall: () -> Boolean = { false }
    private var clock = 1_000L
    private var forwards = 0
    private val recovery = LockTaskRecovery({ state() }, { inCall() }, { forwards++ }, { clock })

    @Test fun aReadyUnlockedPassOutsideLockTaskBringsHomeForward() {
        assertEquals(RecoveryAction.HOME_FORWARD, recovery.afterPass(locked = false, ready = true))
        assertEquals(1, forwards)
    }

    @Test fun aRunningLockTaskNeedsNothing() {
        state = { LockTaskState.LOCKED }
        assertEquals(RecoveryAction.NOTHING, recovery.afterPass(locked = false, ready = true))
        assertEquals(0, forwards)
    }

    @Test fun anUnreadableLockTaskStateCountsAsNotContained() {
        state = { LockTaskState.UNREADABLE }
        assertEquals(RecoveryAction.HOME_FORWARD, recovery.afterPass(locked = false, ready = true))
        assertEquals(1, forwards)
    }

    @Test fun aThrowingStateReadCountsAsNotContained() {
        state = { throw SecurityException("read failed") }
        assertEquals(RecoveryAction.HOME_FORWARD, recovery.afterPass(locked = false, ready = true))
        assertEquals(1, forwards)
    }

    /** PLA-322 finding 3: screen pinning is not containment, and starting Home does not end it. */
    @Test fun pinnedIsNotReadyNeedsAttentionAndDoesNotBringHomeForward() {
        state = { LockTaskState.PINNED }
        assertEquals(RecoveryAction.PINNED_ATTENTION, recovery.afterPass(locked = false, ready = true))
        assertEquals(RecoveryAction.PINNED_ATTENTION, recovery.afterPass(locked = false, ready = false))
        assertEquals(0, forwards)
    }

    @Test fun whenThePinEndsHomeAsksForOneFreshPass() {
        state = { LockTaskState.PINNED }
        recovery.afterPass(locked = false, ready = true)
        assertFalse(recovery.onHomeLockTaskState(LockTaskState.PINNED).reapply)
        assertTrue(recovery.onHomeLockTaskState(LockTaskState.LOCKED).reapply)
        assertFalse(recovery.onHomeLockTaskState(LockTaskState.LOCKED).reapply)
    }

    @Test fun aLockedPassNeverOpensHome() {
        assertEquals(RecoveryAction.NOTHING, recovery.afterPass(locked = true, ready = true))
        assertEquals(0, forwards)
    }

    @Test fun aPassThatIsNotReadyLeavesHomeOnTheAttentionScreenWhereItIs() {
        assertEquals(RecoveryAction.NOTHING, recovery.afterPass(locked = false, ready = false))
        assertEquals(0, forwards)
    }

    @Test fun aFailedStartIsReportedAndNeverThrows() {
        val failing = LockTaskRecovery({ LockTaskState.NONE }, { false }, { throw IllegalStateException("start refused") }, { 0L })
        failing.opened(CallingScreen.PHONE)
        assertEquals(RecoveryAction.NOTHING, failing.afterPass(locked = false, ready = true))
        assertNull("nothing was displaced, so nothing comes back", failing.onHomeLockTaskState(LockTaskState.LOCKED).restore)
    }

    /** PLA-322 finding 1: the Phone LMNOPhone opened comes back inside lock task, once, after LOCKED. */
    @Test fun aDisplacedPhoneComesBackOnceLockedReadsBack() {
        recovery.opened(CallingScreen.PHONE)
        assertEquals(RecoveryAction.HOME_FORWARD, recovery.afterPass(locked = false, ready = true))
        assertNull("not before LOCKED", recovery.onHomeLockTaskState(LockTaskState.NONE).restore)
        assertNull("PINNED is not LOCKED", recovery.onHomeLockTaskState(LockTaskState.PINNED).restore)
        assertEquals(CallingScreen.PHONE, recovery.onHomeLockTaskState(LockTaskState.LOCKED).restore)
        assertNull("only once", recovery.onHomeLockTaskState(LockTaskState.LOCKED).restore)
    }

    @Test fun aDisplacedEmergencyKeypadComesBackOnceLockedReadsBack() {
        recovery.opened(CallingScreen.EMERGENCY_KEYPAD)
        recovery.afterPass(locked = false, ready = true)
        assertEquals(CallingScreen.EMERGENCY_KEYPAD, recovery.onHomeLockTaskState(LockTaskState.LOCKED).restore)
    }

    /** In-call UI stand-in: Telecom reports a call; no call is placed, ended or touched. */
    @Test fun anOngoingCallBringsTheInCallScreenBackAndWinsOverAnOpenedScreen() {
        inCall = { true }
        recovery.opened(CallingScreen.PHONE)
        recovery.afterPass(locked = false, ready = true)
        assertEquals(CallingScreen.IN_CALL, recovery.onHomeLockTaskState(LockTaskState.LOCKED).restore)
    }

    @Test fun anUnreadableCallStateStillRecoversAndFallsBackToTheOpenedScreen() {
        inCall = { throw SecurityException("no READ_PHONE_STATE") }
        recovery.opened(CallingScreen.EMERGENCY_KEYPAD)
        assertEquals(RecoveryAction.HOME_FORWARD, recovery.afterPass(locked = false, ready = true))
        assertEquals(CallingScreen.EMERGENCY_KEYPAD, recovery.onHomeLockTaskState(LockTaskState.LOCKED).restore)
    }

    @Test fun aScreenTheUserAlreadyLeftForHomeIsNotReopened() {
        recovery.opened(CallingScreen.PHONE)
        recovery.homeResumed()
        recovery.afterPass(locked = false, ready = true)
        assertNull(recovery.onHomeLockTaskState(LockTaskState.LOCKED).restore)
    }

    @Test fun nothingIsReopenedWhenRecoveryDidNotRun() {
        state = { LockTaskState.LOCKED }
        recovery.opened(CallingScreen.PHONE)
        recovery.afterPass(locked = false, ready = true)
        assertNull(recovery.onHomeLockTaskState(LockTaskState.LOCKED).restore)
    }

    @Test fun aRestoreThatWaitedTooLongIsDropped() {
        recovery.opened(CallingScreen.PHONE)
        recovery.afterPass(locked = false, ready = true)
        clock += LockTaskRecovery.RESTORE_WINDOW_MS + 1
        assertNull(recovery.onHomeLockTaskState(LockTaskState.LOCKED).restore)
    }

    @Test fun anUnidentifiedForeignScreenIsNotGuessed() {
        // No call and nothing LMNOPhone opened: Home comes forward and nothing is relaunched.
        recovery.afterPass(locked = false, ready = true)
        assertNull(recovery.onHomeLockTaskState(LockTaskState.LOCKED).restore)
    }
}

/** PLA-322 finding 3: the three Android states, read at the adapter boundary. Only LOCKED is contained. */
class LockTaskStateTest {
    @Test fun mapsAllThreeAndroidStates() {
        assertEquals(LockTaskState.NONE, LockTaskState.read { ActivityManager.LOCK_TASK_MODE_NONE })
        assertEquals(LockTaskState.LOCKED, LockTaskState.read { ActivityManager.LOCK_TASK_MODE_LOCKED })
        assertEquals(LockTaskState.PINNED, LockTaskState.read { ActivityManager.LOCK_TASK_MODE_PINNED })
    }

    @Test fun aThrowOrAnUnknownValueIsUnreadable() {
        assertEquals(LockTaskState.UNREADABLE, LockTaskState.read { throw SecurityException("no") })
        assertEquals(LockTaskState.UNREADABLE, LockTaskState.read { 7 })
    }

    @Test fun onlyLockedIsContained() {
        assertEquals(listOf(LockTaskState.LOCKED), LockTaskState.entries.filter { it.contained })
    }
}
