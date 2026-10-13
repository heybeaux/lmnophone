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

    @Test fun everyPromptHasWords() {
        val ids = VoicePrompt.entries.map { VoiceWords.res(it, helperKnown = true) }
        assertEquals(ids.size, ids.toSet().size)
    }
}
