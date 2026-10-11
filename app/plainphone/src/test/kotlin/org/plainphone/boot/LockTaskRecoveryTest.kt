// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * device-lockdown, "Lock task must actually be running" (PLA-307 finding 2): after an unlocked pass
 * that reports ready, a phone that is not in lock task (an unapproved app won the race with Home's
 * process death) brings Home forward, and Home enters lock task through its KEYGUARD gate.
 */
class LockTaskRecoveryTest {
    private var running: () -> Boolean = { false }
    private var forwards = 0
    private val recovery = LockTaskRecovery({ running() }, { forwards++ })

    @Test fun aReadyUnlockedPassOutsideLockTaskBringsHomeForward() {
        assertTrue(recovery.afterPass(locked = false, ready = true))
        assertEquals(1, forwards)
    }

    @Test fun aRunningLockTaskNeedsNothing() {
        running = { true }
        assertFalse(recovery.afterPass(locked = false, ready = true))
        assertEquals(0, forwards)
    }

    @Test fun anUnreadableLockTaskStateCountsAsNotRunning() {
        running = { throw SecurityException("read failed") }
        assertTrue(recovery.afterPass(locked = false, ready = true))
        assertEquals(1, forwards)
    }

    @Test fun aLockedPassNeverOpensHome() {
        assertFalse(recovery.afterPass(locked = true, ready = true))
        assertEquals(0, forwards)
    }

    @Test fun aPassThatIsNotReadyLeavesHomeOnTheAttentionScreenWhereItIs() {
        assertFalse(recovery.afterPass(locked = false, ready = false))
        assertEquals(0, forwards)
    }

    @Test fun aFailedStartIsReportedAndNeverThrows() {
        val failing = LockTaskRecovery({ false }, { throw IllegalStateException("start refused") })
        assertFalse(failing.afterPass(locked = false, ready = true))
    }
}
