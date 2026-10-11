// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** PLA-329 A1: best-effort storage must not abort startup or either calling-screen action. */
class CallingScreenRecordFailureTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun aDirectoryAtTheRecordPathDoesNotAbortConstructionAndMeansNothingOpened() {
        val recovery = recovery(FileOpenedScreenRecord(files.newFolder("opened-screen")) { 1_000L })

        assertEquals(RecoveryAction.HOME_FORWARD, recovery.afterPass(locked = false, ready = true))
        assertNull(recovery.onHomeLockTaskState(LockTaskState.LOCKED).restore)
    }

    @Test fun aMissingParentDoesNotAbortPhoneAndKeepsItsInProcessRecord() {
        assertFailedWriteKeepsCallingScreen(CallingScreen.PHONE)
    }

    @Test fun aMissingParentDoesNotAbortEmergencyAndKeepsItsInProcessRecord() {
        assertFailedWriteKeepsCallingScreen(CallingScreen.EMERGENCY_KEYPAD)
    }

    @Test fun anIoFailureWhileClearingStillClearsTheInProcessRecord() {
        val record = object : OpenedScreenRecord {
            override fun load() = CallingScreen.PHONE
            override fun save(screen: CallingScreen?): Unit = throw IOException("write failed")
        }
        val recovery = recovery(record)

        recovery.homeResumed()

        recovery.afterPass(locked = false, ready = true)
        assertNull(recovery.onHomeLockTaskState(LockTaskState.LOCKED).restore)
    }

    private fun assertFailedWriteKeepsCallingScreen(screen: CallingScreen) {
        val file = File(files.root, "missing/${screen.name}")
        assertFalse(file.parentFile.exists())
        val recovery = recovery(FileOpenedScreenRecord(file) { 1_000L })
        val launches = mutableListOf<CallingScreen>()

        // Match MainActivity's record-then-launch order with a host launch stand-in; place no call.
        recovery.opened(screen)
        launches += screen

        assertEquals(listOf(screen), launches)
        assertFalse("persistence failed", file.exists())
        assertEquals(RecoveryAction.HOME_FORWARD, recovery.afterPass(locked = false, ready = true))
        assertEquals(screen, recovery.onHomeLockTaskState(LockTaskState.LOCKED).restore)
    }

    private fun recovery(record: OpenedScreenRecord) = LockTaskRecovery(
        { LockTaskState.NONE }, { false }, {}, { 1_000L }, record,
    )
}
