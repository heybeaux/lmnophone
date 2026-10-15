// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.session

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.policy.*

class PreviewSessionJournalTest {
    private class Journal : JournalStore {
        var value: SessionJournal = SessionJournal.Damaged
        var writes = 0
        override fun read() = value
        override fun compareAndSet(expected: SessionJournal, replacement: SessionJournal): Boolean {
            writes++; if (value != expected) return false; value = replacement; return true
        }
    }
    @Test fun enrollmentSwitchesToCombinedJournalAndNeverFallsBackOnDamage() {
        var enrolled = false
        val legacy = Journal(); val combined = Journal()
        val journal = PreviewSessionJournal(legacy, combined) { enrolled }
        assertTrue(journal.compareAndSet(SessionJournal.Damaged, SessionJournal.Empty))
        assertEquals(1, legacy.writes)
        enrolled = true
        assertEquals(SessionJournal.Damaged, journal.read())
        assertTrue(journal.compareAndSet(SessionJournal.Damaged, SessionJournal.Empty))
        assertEquals(1, legacy.writes); assertEquals(1, combined.writes)
        combined.value = SessionJournal.Damaged
        assertEquals(SessionJournal.Damaged, journal.read())
    }
}
