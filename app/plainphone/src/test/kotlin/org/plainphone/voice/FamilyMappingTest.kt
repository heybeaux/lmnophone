// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.voice

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.shell.*
import org.plainphone.device.voice.*
import java.util.Locale

class FamilyMappingTest {
    private val mapper = ShellUiMapper(Locale.US, true)
    private val snapshot = PreviewPhone.empty()
    @Test fun productionHomeMapperShowsFamilyOnlyWhenEnabledAndPopulated() {
        assertFalse(mapper.home(snapshot).showFamily)
        assertFalse(mapper.home(snapshot.copy(familyEnabled = true)).showFamily)
        assertFalse(mapper.home(snapshot.copy(familyCount = 1)).showFamily)
        assertTrue(mapper.home(snapshot.copy(familyEnabled = true, familyCount = 1)).showFamily)
        assertFalse(mapper.more(snapshot.copy(familyEnabled = true, familyCount = 1)).voicePractice)
    }
    @Test fun mapperUsesProvidedFamilyInsteadOfSamples() {
        val person = VoicePerson("familysam", "Sam", "Sam")
        val state = VoiceUiMapper.map(VoiceState.ChoosePerson, listOf(person), family = true)
            as org.plainphone.ui.user.VoiceUiState.ChoosePerson
        assertEquals(listOf("familysam"), state.people.map { it.id })
        assertTrue(state.fromHome)
        val selected = VoiceUiMapper.map(VoiceState.Person(person), listOf(person), family = true)
            as org.plainphone.ui.user.VoiceUiState.Person
        assertTrue(selected.openMessages)
    }
}
