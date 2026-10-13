// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.voice

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.device.voice.*
import org.plainphone.ui.trusted.*

class FamilyEditorTest {
    @get:Rule val temp = TemporaryFolder()
    private var authorized = true
    private val erased = mutableListOf<String>()
    private val heard = mutableListOf<String>()
    private val store by lazy { FamilyStore(File(temp.root, "family"), { erased += it }) }
    private fun editor(grant: Boolean = true) = FamilyEditor(store, AdminContext("Alex", "Sam"),
        { authorized }, { grant }, { heard += it }, {}, {}).also { it.open() }
    private fun add(e: FamilyEditor) {
        e.event(FamilyEvent.Enable); e.event(FamilyEvent.Add)
        e.event(FamilyEvent.Name("Sam")); e.event(FamilyEvent.Continue)
        e.event(FamilyEvent.Number("555-0100")); e.event(FamilyEvent.Continue)
        e.event(FamilyEvent.Continue); e.event(FamilyEvent.Continue)
    }
    @Test fun addIsImmediateAndNumberIsReadBackBeforeSave() {
        val e = editor(); e.event(FamilyEvent.Enable); e.event(FamilyEvent.Add)
        e.event(FamilyEvent.Name("Sam")); e.event(FamilyEvent.Continue)
        assertFalse(e.state.canContinue)
        e.event(FamilyEvent.Number("555-0100")); e.event(FamilyEvent.Continue); e.event(FamilyEvent.Continue)
        assertEquals(FamilyStage.Confirm, e.state.stage)
        assertEquals("555-0100", e.state.number)
        assertTrue(store.read().people.isEmpty())
        e.event(FamilyEvent.Continue)
        assertEquals("Sam", store.read().people.single().name)
    }
    @Test fun staleEventsCannotWriteAfterPinAuthorityEnds() {
        val e = editor(); e.event(FamilyEvent.Enable); e.event(FamilyEvent.Add)
        e.event(FamilyEvent.Name("Sam")); e.event(FamilyEvent.Continue)
        e.event(FamilyEvent.Number("555-0100")); e.event(FamilyEvent.Continue); e.event(FamilyEvent.Continue)
        authorized = false; e.event(FamilyEvent.Continue)
        assertTrue(store.read().people.isEmpty())
    }
    @Test fun failedMicrophoneGrantDoesNotEnable() {
        val e = editor(false); e.event(FamilyEvent.Enable)
        assertFalse(store.read().enabled); assertTrue(e.state.failed)
        assertTrue(e.state.micFailed)
    }
    @Test fun microphoneFailureClearsAfterSuccessfulRetry() {
        var grant = false
        val e = FamilyEditor(store, AdminContext("Alex", "Sam"), { true }, { grant }, {}, {}, {})
        e.open(); e.event(FamilyEvent.Enable)
        assertTrue(e.state.micFailed)
        grant = true; e.event(FamilyEvent.Enable)
        assertTrue(store.read().enabled)
        assertFalse(e.state.failed); assertFalse(e.state.micFailed)
    }
    @Test fun throwingMicrophoneGrantUsesMicrophoneError() {
        val e = FamilyEditor(store, AdminContext("Alex", "Sam"), { true }, { error("unavailable") }, {}, {}, {})
        e.open(); e.event(FamilyEvent.Enable)
        assertFalse(store.read().enabled); assertTrue(e.state.micFailed)
    }
    @Test fun removeRequiresConfirmationAndDeletesOnlyThatDraft() {
        val e = editor(); add(e)
        val id = store.read().people.single().id
        e.event(FamilyEvent.Edit(id)); e.event(FamilyEvent.AskRemove)
        assertEquals(1, store.read().people.size)
        e.event(FamilyEvent.Back); assertEquals(1, store.read().people.size)
        e.event(FamilyEvent.Edit(id)); e.event(FamilyEvent.AskRemove); e.event(FamilyEvent.Continue)
        assertTrue(store.read().people.isEmpty()); assertEquals(listOf(id), erased)
    }
    @Test fun spokenNameCanDifferAndHearDoesNotSave() {
        val e = editor(); e.event(FamilyEvent.Enable); e.event(FamilyEvent.Add)
        e.event(FamilyEvent.Name("Jo")); e.event(FamilyEvent.SpokenName("Jo")); e.event(FamilyEvent.Hear)
        assertEquals(listOf("Jo"), heard); assertTrue(store.read().people.isEmpty())
    }
}
