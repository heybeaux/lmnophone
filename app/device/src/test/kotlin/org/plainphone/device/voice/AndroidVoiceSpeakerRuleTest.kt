// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the phone's own voice speaks (PLA-475 finding 1). */
class AndroidVoiceSpeakerRuleTest {
    @Test fun automaticPromptsStayQuietUnderTalkBack() {
        assertTrue(AndroidVoiceSpeaker.shouldSpeak(touchExploration = false, audioMode = AudioManager.MODE_NORMAL, requested = false))
        assertFalse(AndroidVoiceSpeaker.shouldSpeak(touchExploration = true, audioMode = AudioManager.MODE_NORMAL, requested = false))
    }

    @Test fun aRequestedNameIsSpokenUnderTalkBack() {
        assertTrue(AndroidVoiceSpeaker.shouldSpeak(touchExploration = true, audioMode = AudioManager.MODE_NORMAL, requested = true))
    }

    @Test fun nothingIsSpokenDuringACall() {
        for (requested in listOf(true, false)) for (mode in listOf(AudioManager.MODE_IN_CALL, AudioManager.MODE_RINGTONE,
            AudioManager.MODE_IN_COMMUNICATION)) {
            assertFalse(AndroidVoiceSpeaker.shouldSpeak(touchExploration = false, audioMode = mode, requested = requested))
        }
    }
}
