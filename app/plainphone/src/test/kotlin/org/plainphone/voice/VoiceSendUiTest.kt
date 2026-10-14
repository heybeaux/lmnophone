// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.device.voice.VoiceDraft
import org.plainphone.device.voice.VoicePeople
import org.plainphone.device.voice.VoicePerson
import org.plainphone.device.voice.VoicePrompt
import org.plainphone.device.voice.VoiceState
import org.plainphone.device.voice.send.SendProblem
import org.plainphone.ui.R
import org.plainphone.ui.user.VoicePersonUi
import org.plainphone.ui.user.VoiceUiSendProblem
import org.plainphone.ui.user.VoiceUiState

/** PLA-499: send states to screens, and their words. Fictional people and numbers only. */
class VoiceSendUiTest {
    private val sam = VoicePerson("sam", "Sam")
    private val alex = VoicePerson("alex", "Alex")
    private val samUi = VoicePersonUi("sam", "Sam")
    private val draft = VoiceDraft("sam", 1L)
    private val people = listOf(sam, alex)
    private val sendable: (VoicePerson) -> Boolean = { it.id == "sam" }

    private fun map(s: VoiceState, canSend: (VoicePerson) -> Boolean = sendable, helper: String = "Jo") =
        VoiceUiMapper.map(s, people, canSend = canSend, helper = helper)

    @Test fun reviewOffersSendOnlyForSomeoneWhoCanBeSentTo() {
        assertEquals(VoiceUiState.Review(samUi, canSend = true, practice = false), map(VoiceState.Review(sam, draft)))
        val alexReview = map(VoiceState.Review(alex, VoiceDraft("alex", 1L))) as VoiceUiState.Review
        assertFalse(alexReview.canSend)
        assertTrue(alexReview.practice)
    }

    @Test fun withSendingOffEverythingStaysPractice() {
        val off: (VoicePerson) -> Boolean = { false }
        assertEquals(VoiceUiState.Review(samUi), map(VoiceState.Review(sam, draft), off))
        assertTrue(map(VoiceState.ChoosePerson, off).practice)
        assertTrue(map(VoiceState.Person(sam), off).practice)
        assertTrue(map(VoiceState.Recording(sam, null), off).practice)
    }

    @Test fun thePeopleListIsPracticeOnlyWhenNobodyCanBeSentTo() {
        assertFalse(map(VoiceState.ChoosePerson).practice)
        assertTrue(VoiceUiMapper.map(VoiceState.ChoosePerson, listOf(alex), canSend = sendable).practice)
        assertTrue(VoiceUiMapper.map(VoiceState.ChoosePerson).practice)
    }

    @Test fun thePeopleListComesFromTheHost() {
        assertEquals(listOf(samUi, VoicePersonUi("alex", "Alex")),
            (map(VoiceState.ChoosePerson) as VoiceUiState.ChoosePerson).people)
        assertEquals(emptyList<VoicePersonUi>(),
            (VoiceUiMapper.map(VoiceState.ChoosePerson) as VoiceUiState.ChoosePerson).people)
    }

    @Test fun sendingAndSentMapOneToOne() {
        assertEquals(VoiceUiState.Sending(samUi), map(VoiceState.Sending(sam, draft)))
        assertEquals(VoiceUiState.Sending(samUi), map(VoiceState.Sending(sam, draft, announced = true)))
        assertEquals(VoiceUiState.Sent(samUi), map(VoiceState.Sent(sam)))
    }

    @Test fun everyProblemHasAScreen() {
        val expected = mapOf(
            SendProblem.NoSignal to VoiceUiSendProblem.NoSignal,
            SendProblem.MmsOff to VoiceUiSendProblem.NotSent,
            SendProblem.TooBig to VoiceUiSendProblem.NotSent,
            SendProblem.NoPermission to VoiceUiSendProblem.NotSent,
            SendProblem.OtherError to VoiceUiSendProblem.NotSent,
            SendProblem.NotConfirmed to VoiceUiSendProblem.NotConfirmed,
        )
        assertEquals(SendProblem.entries.toSet(), expected.keys)
        for ((problem, ui) in expected) {
            assertEquals(problem.name, VoiceUiState.NotSent(samUi, ui), map(VoiceState.NotSent(sam, draft, problem, 1)))
        }
    }

    @Test fun theTrustedPersonIsNamedOnlyAfterTheSecondFailureAndOnlyWhenKnown() {
        assertEquals("Jo", (map(VoiceState.NotSent(sam, draft, SendProblem.OtherError, 2)) as VoiceUiState.NotSent).helper)
        assertNull((map(VoiceState.NotSent(sam, draft, SendProblem.OtherError, 1)) as VoiceUiState.NotSent).helper)
        assertNull((map(VoiceState.NotSent(sam, draft, SendProblem.OtherError, 2), helper = " ") as VoiceUiState.NotSent).helper)
        // No signal and not confirmed are not the transport failing: no second sentence.
        assertNull((map(VoiceState.NotSent(sam, draft, SendProblem.NoSignal, 3)) as VoiceUiState.NotSent).helper)
        assertNull((map(VoiceState.NotSent(sam, draft, SendProblem.NotConfirmed, 3)) as VoiceUiState.NotSent).helper)
    }

    @Test fun sendWordsComeFromTheUxDoc() {
        val expected = mapOf(
            VoicePrompt.ChooseSend to R.string.voice_say_choose,
            VoicePrompt.ReviewSend to R.string.voice_say_review_send,
            VoicePrompt.MaxLengthSend to R.string.voice_say_max_send,
            VoicePrompt.UnsentSend to R.string.voice_say_unsent_send,
            VoicePrompt.Sending to R.string.voice_say_sending,
            VoicePrompt.Sent to R.string.voice_say_sent,
            VoicePrompt.NotSent to R.string.voice_say_not_sent,
            VoicePrompt.NotSentAgain to R.string.voice_say_not_sent_again,
            VoicePrompt.NoSignal to R.string.voice_say_no_signal,
            VoicePrompt.NotConfirmed to R.string.voice_say_not_confirmed,
        )
        for ((prompt, res) in expected) assertEquals(prompt.name, res, VoiceWords.res(prompt, helperKnown = true))
        assertEquals(R.string.voice_say_not_sent, VoiceWords.res(VoicePrompt.NotSentAgain, helperKnown = false))
    }
}
