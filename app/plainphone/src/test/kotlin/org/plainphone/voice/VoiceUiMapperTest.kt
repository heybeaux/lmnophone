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
import org.plainphone.ui.user.VoiceUiListen

class VoiceUiMapperTest {
    private val sam = VoicePerson("sam", "Sam")
    private val samUi = VoicePersonUi("sam", "Sam")
    private val draft = VoiceDraft("sam", 1L)

    @Test fun choosePersonListsOnlyThePracticePeople() {
        assertEquals(VoiceUiState.ChoosePerson(VoicePeople.practice.map { VoicePersonUi(it.id, it.name) }),
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

/** PLA-527: the production mapper sets New message (V1) and Listen / Stop listening / Play again (V2, V12a). */
class VoiceReplyMappingTest {
    private val sam = VoicePerson("sam", "Sam")
    private val jo = VoicePerson("jo", "Jo")
    private fun clip(id: Long) = org.plainphone.device.voice.replies.ReplyClip(
        org.plainphone.device.voice.replies.ReplyKey.of(id, id), "sam", id, id, "audio/mp4")

    @Test fun peopleListCardShowsNewMessageOnlyForThoseWithOne() {
        val ui = VoiceUiMapper.map(VoiceState.ChoosePerson, listOf(sam, jo), family = true, newMessage = { it == "sam" })
        assertEquals(VoiceUiState.ChoosePerson(listOf(VoicePersonUi("sam", "Sam", pictureInInk = true, newMessage = true),
            VoicePersonUi("jo", "Jo", pictureInInk = true)), fromHome = true), ui)
    }

    @Test fun personScreenOffersListenThenStopThenPlayAgain() {
        val queue = listOf(clip(1), clip(2))
        fun map(listen: org.plainphone.device.voice.ReplyListen?) =
            (VoiceUiMapper.map(VoiceState.Person(sam, listen = listen), listOf(sam), family = true) as VoiceUiState.Person).listen
        assertEquals(null, map(null))
        assertEquals(null, map(org.plainphone.device.voice.ReplyListen(emptyList())))
        assertEquals(VoiceUiListen.Listen, map(org.plainphone.device.voice.ReplyListen(queue)))
        assertEquals(VoiceUiListen.Stop, map(org.plainphone.device.voice.ReplyListen(queue, index = 0, playing = true)))
        assertEquals(VoiceUiListen.Stop, map(org.plainphone.device.voice.ReplyListen(queue, index = 1, playing = true, completed = 1)))
        assertEquals(VoiceUiListen.PlayAgain, map(org.plainphone.device.voice.ReplyListen(queue, index = 1, completed = 2)))
    }

    @Test fun personScreenKeepsMessagesWithListen() {
        val ui = VoiceUiMapper.map(VoiceState.Person(sam, listen = org.plainphone.device.voice.ReplyListen(listOf(clip(1)))),
            listOf(sam), family = true) as VoiceUiState.Person
        assertEquals(true, ui.openMessages)
        assertEquals(VoicePersonUi("sam", "Sam", pictureInInk = true), ui.person)
    }

    @Test fun mapperStateNeverCarriesNumbersOrMessageIds() {
        val ui = VoiceUiMapper.map(VoiceState.Person(sam, listen = org.plainphone.device.voice.ReplyListen(listOf(clip(5550100)))),
            listOf(sam), family = true)
        assertEquals(false, ui.toString().contains("5550100"))
    }
}
