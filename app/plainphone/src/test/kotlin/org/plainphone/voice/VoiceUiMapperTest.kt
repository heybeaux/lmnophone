// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.voice

import org.junit.Assert.assertEquals
import org.junit.Test
import org.plainphone.device.voice.MicProblem
import org.plainphone.device.voice.VoiceDraft
import org.plainphone.device.voice.VoiceNotice
import org.plainphone.device.voice.VoicePeople
import org.plainphone.device.voice.VoicePerson
import org.plainphone.device.voice.VoiceState
import org.plainphone.ui.user.VoicePersonUi
import org.plainphone.ui.user.VoiceUiMicProblem
import org.plainphone.ui.user.VoiceUiNotice
import org.plainphone.ui.user.VoiceUiState

class VoiceUiMapperTest {
    private val sam = VoicePerson("sam", "Sam")
    private val samUi = VoicePersonUi("sam", "Sam")
    private val draft = VoiceDraft("sam", 1L)

    @Test fun choosePersonListsOnlyThePracticePeople() {
        assertEquals(VoiceUiState.ChoosePerson(emptyList()),
            VoiceUiMapper.map(VoiceState.ChoosePerson))
    }

    @Test fun savingStillShowsRecordingSoNothingLooksStopped() {
        assertEquals(VoiceUiState.Recording(samUi), VoiceUiMapper.map(VoiceState.Saving(sam, null)))
        assertEquals(VoiceUiState.Recording(samUi), VoiceUiMapper.map(VoiceState.Recording(sam, draft)))
    }

    @Test fun everyNoticeHasAScreenNotice() {
        val expected = mapOf(
            VoiceNotice.MaxLength to VoiceUiNotice.MaxLength,
            VoiceNotice.Interrupted to VoiceUiNotice.Interrupted,
            VoiceNotice.NothingRecorded to VoiceUiNotice.NothingRecorded,
            VoiceNotice.Deleted to VoiceUiNotice.Deleted,
            VoiceNotice.CouldNotPlay to VoiceUiNotice.CouldNotPlay,
            VoiceNotice.MicBusy to VoiceUiNotice.Busy,
            VoiceNotice.Unsent to VoiceUiNotice.Unsent,
        )
        assertEquals(VoiceNotice.entries.toSet(), expected.keys)
        for ((notice, ui) in expected) {
            assertEquals(VoiceUiState.Review(samUi, playing = true, notice = ui),
                VoiceUiMapper.map(VoiceState.Review(sam, draft, playing = true, notice = notice)))
            assertEquals(VoiceUiState.Person(samUi, ui), VoiceUiMapper.map(VoiceState.Person(sam, notice)))
        }
    }

    @Test fun micProblemsMapOneToOne() {
        assertEquals(VoiceUiState.MicOff(samUi, VoiceUiMicProblem.NotAllowed),
            VoiceUiMapper.map(VoiceState.MicUnavailable(sam, MicProblem.PermissionRefused, null)))
        assertEquals(VoiceUiState.MicOff(samUi, VoiceUiMicProblem.TurnedOff),
            VoiceUiMapper.map(VoiceState.MicUnavailable(sam, MicProblem.PrivacyToggle, draft)))
        assertEquals(VoiceUiState.MicOff(samUi, VoiceUiMicProblem.DidNotWork),
            VoiceUiMapper.map(VoiceState.MicUnavailable(sam, MicProblem.RecorderError, null)))
    }
}
