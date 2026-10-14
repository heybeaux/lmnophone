// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.voice

import org.junit.Assert.assertEquals
import org.junit.Test
import org.plainphone.device.voice.VoicePrompt
import org.plainphone.ui.R

/** Spoken words per prompt (docs/ux/flows-voice-messages.md, PLA-472 practice rows). */
class VoiceWordsTest {
    @Test fun helpWordsNameTheTrustedPersonOnlyWhenKnown() {
        assertEquals(R.string.voice_say_mic_off, VoiceWords.res(VoicePrompt.MicOff, helperKnown = true))
        assertEquals(R.string.voice_say_mic_off_short, VoiceWords.res(VoicePrompt.MicOff, helperKnown = false))
        assertEquals(R.string.voice_say_mic_not_allowed, VoiceWords.res(VoicePrompt.MicNotAllowed, helperKnown = true))
        assertEquals(R.string.voice_say_mic_not_allowed_short, VoiceWords.res(VoicePrompt.MicNotAllowed, helperKnown = false))
    }

    @Test fun micErrorAndPlayFailureHaveTheirOwnWords() {
        for (known in listOf(true, false)) {
            assertEquals(R.string.voice_say_mic_error, VoiceWords.res(VoicePrompt.MicError, known))
            assertEquals(R.string.voice_say_play, VoiceWords.res(VoicePrompt.CouldNotPlay, known))
        }
    }

    @Test fun familyPromptsExplainMessagesAndTheFailedHandoff() {
        assertEquals(R.string.family_say_person, VoiceWords.res(VoicePrompt.Person, true, family = true))
        assertEquals(R.string.voice_say_person, VoiceWords.res(VoicePrompt.Person, true, family = false))
        assertEquals(R.string.family_say_opening_messages, VoiceWords.res(VoicePrompt.OpeningMessages, true))
        assertEquals(R.string.family_say_messages_unavailable, VoiceWords.res(VoicePrompt.MessagesUnavailable, true))
    }

    @Test fun replyPromptsHaveTheirOwnWords() {
        assertEquals(R.string.family_say_new_from, VoiceWords.res(VoicePrompt.NewFrom, true, family = true))
        assertEquals(R.string.family_say_person_new, VoiceWords.res(VoicePrompt.PersonNew, true, family = true))
        assertEquals(R.string.family_say_reply, VoiceWords.res(VoicePrompt.Reply, true, family = true))
        assertEquals(R.string.family_say_reply_next, VoiceWords.res(VoicePrompt.ReplyNext, true, family = true))
        assertEquals(R.string.family_say_reply_end, VoiceWords.res(VoicePrompt.ReplyEnd, true, family = true))
    }

    @Test fun everyPromptHasWords() {
        val ids = VoicePrompt.entries.map { VoiceWords.res(it, helperKnown = true) }
        assertEquals(ids.size, ids.toSet().size)
    }
}
