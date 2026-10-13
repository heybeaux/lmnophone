// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSpeechCompletionTest {
    @Test fun completionAndTimeoutTogetherOpenOnlyOnce() {
        val completion = VoiceSpeechCompletion()
        var opened = 0
        val id = completion.begin { opened++ }
        completion.finish(id); completion.finish(id)
        assertEquals(1, opened)
    }
    @Test fun leavingCancelsPendingNavigation() {
        val completion = VoiceSpeechCompletion()
        var opened = 0
        val id = completion.begin { opened++ }
        completion.cancel(); completion.finish(id)
        assertEquals(0, opened)
    }
    @Test fun oldSpeechCannotCompleteANewerAction() {
        val completion = VoiceSpeechCompletion()
        val opened = mutableListOf<String>()
        val old = completion.begin { opened += "Sam" }
        val current = completion.begin { opened += "Jo" }
        completion.finish(old)
        assertEquals(emptyList<String>(), opened)
        completion.finish(current)
        assertEquals(listOf("Jo"), opened)
    }
}
