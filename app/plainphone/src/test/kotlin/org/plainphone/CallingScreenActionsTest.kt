// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.boot.*
import org.plainphone.shell.ShellEffect

class CallingScreenActionsTest {
    @get:Rule val files = TemporaryFolder()
    private val events = mutableListOf<String>()
    private fun actions(record: (CallingScreen) -> Unit = { events += "record:$it" }) = CallingScreenActions(
        record, { events += "phone" }, { events += "emergency-keypad" }, { events += "in-call" },
        { action, uri -> events += "$action $uri"; true },
    )

    @Test fun moreEmergencyRecordsDistinctScreenBeforeOpeningExactly911WithoutCalling() {
        assertTrue(actions().perform(ShellEffect.OpenEmergencyDialer))
        assertEquals(listOf("record:EMERGENCY_911", "android.intent.action.DIAL tel:911"), events)
    }

    @Test fun recoveredMoreEmergencyUsesTheSameDialIntentAndRecord() {
        actions().reopen(CallingScreen.EMERGENCY_911)
        assertEquals(listOf("record:EMERGENCY_911", "android.intent.action.DIAL tel:911"), events)
    }

    @Test fun otherCallingRoutesKeepTheirExistingActions() {
        val actions = actions()
        assertTrue(actions.perform(ShellEffect.OpenDialer))
        actions.reopen(CallingScreen.PHONE)
        actions.reopen(CallingScreen.EMERGENCY_KEYPAD)
        actions.reopen(CallingScreen.IN_CALL)
        assertEquals(listOf("phone", "phone", "emergency-keypad", "in-call"), events)
    }

    @Test fun unrelatedShellEffectsAreLeftToTheShell() {
        val actions = actions()
        for (effect in listOf(ShellEffect.OpenMessages, ShellEffect.OpenApp("test.app"), ShellEffect.Finish)) {
            assertFalse(actions.perform(effect))
        }
        assertTrue(events.isEmpty())
    }

    @Test fun failedStorageStillOpens911AndPreservesInProcessRecovery() {
        val record = FileOpenedScreenRecord(File(files.root, "missing/opened-screen")) { 1000L }
        val recovery = LockTaskRecovery({ LockTaskState.NONE }, { false }, {}, { 1000L }, record)
        val actions = actions(recovery::opened)
        assertTrue(actions.perform(ShellEffect.OpenEmergencyDialer))
        assertEquals(listOf("android.intent.action.DIAL tel:911"), events)
        recovery.afterPass(locked = false, ready = true)
        actions.reopen(checkNotNull(recovery.onHomeLockTaskState(LockTaskState.LOCKED).restore))
        assertEquals(listOf("android.intent.action.DIAL tel:911", "android.intent.action.DIAL tel:911"), events)
    }

    @Test fun nextProcessRecoversMoreEmergencyFromTheRealFileStore() {
        val file = File(files.root, "opened-screen")
        val first = LockTaskRecovery({ LockTaskState.NONE }, { false }, {}, { 1000L },
            FileOpenedScreenRecord(file) { 1000L })
        actions(first::opened).perform(ShellEffect.OpenEmergencyDialer)
        assertEquals("EMERGENCY_911 1000", file.readText())
        assertEquals(CallingScreen.EMERGENCY_911, FileOpenedScreenRecord(file) { 1100L }.load())
        val next = LockTaskRecovery({ LockTaskState.NONE }, { false }, {}, { 1100L },
            FileOpenedScreenRecord(file) { 1100L })
        next.afterPass(locked = false, ready = true)
        events.clear()
        actions().reopen(checkNotNull(next.onHomeLockTaskState(LockTaskState.LOCKED).restore))
        assertEquals(listOf("record:EMERGENCY_911", "android.intent.action.DIAL tel:911"), events)
    }
}
