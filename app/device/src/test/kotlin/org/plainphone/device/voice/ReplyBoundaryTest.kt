// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.device.voice.replies.ReplyClip
import org.plainphone.device.voice.replies.ReplyKey

class ReplyBoundaryTest {
    @get:Rule val tmp = TemporaryFolder()
    private val person = VoicePerson("sam", "Sam")
    private val queue = (1L..2L).map { ReplyClip(ReplyKey.of(it, it), "sam", it, it, "audio/mp4") }
    private var allowed = true
    private var holdSpeech = false
    private var pendingSpeech: (() -> Unit)? = null
    private var finish: (() -> Unit)? = null
    private var failFirst = false
    private val attempted = mutableListOf<Long>()
    private val heard = mutableListOf<Long>()
    private val replies = object : VoiceReplies {
        override fun unplayed() = if (allowed) mapOf("sam" to queue) else emptyMap()
        override fun canPlay(personId: String) = allowed
        override fun played(personId: String, clip: ReplyClip) { if (allowed) heard += clip.partId }
    }
    private fun controller(): VoicePracticeController {
        val recorder = object : VoiceRecorder {
            override fun silent() = false
            override fun start(file: File, format: VoiceFormat, maxMs: Long, listener: VoiceRecorder.Listener) = error("microphone must stay closed")
            override fun stop() = true
        }
        val player = object : VoicePlayer {
            override fun play(file: File, onDone: () -> Unit, onError: () -> Unit) = error("not a draft")
            override fun playReply(clip: ReplyClip, onDone: () -> Unit, onError: () -> Unit) {
                attempted += clip.partId
                if (failFirst && clip.partId == 1L) throw IllegalStateException("unreadable")
                finish = onDone
            }
            override fun stop() { finish = null }
        }
        val speaker = object : VoiceSpeaker {
            override fun speak(prompt: VoicePrompt, person: VoicePerson?) { }
            override fun speakThen(prompt: VoicePrompt, person: VoicePerson?, then: () -> Unit) {
                if (holdSpeech) pendingSpeech = then else then()
            }
            override fun stop() { pendingSpeech = null }
        }
        val gate = object : VoiceAudioGate {
            override fun acquire() = true
            override fun release() { }
        }
        return VoicePracticeController(recorder, player, VoiceDraftStore(tmp.newFolder()) { 100L },
            MicAccess { null }, speaker, gate, clock = { 100L }, people = { listOf(person) }, replies = replies).also {
            it.open()
            it.dispatch(VoiceEvent.Choose(person))
        }
    }

    @Test fun unreadableFirstClipDoesNotPreventTheNextReadableClip() {
        failFirst = true
        val controller = controller()
        controller.dispatch(VoiceEvent.Listen)
        assertEquals(listOf(1L, 2L), attempted)
        assertTrue(heard.isEmpty())
    }

    @Test fun removalWhileTheSpokenIntroIsPendingPreventsPlayback() {
        holdSpeech = true
        val controller = controller()
        controller.dispatch(VoiceEvent.Listen)
        val pending = pendingSpeech!!
        allowed = false
        pending()
        assertTrue(attempted.isEmpty())
    }

    @Test fun removalBetweenClipsPreventsTheNextClip() {
        val controller = controller()
        controller.dispatch(VoiceEvent.Listen)
        allowed = false
        finish!!()
        assertEquals(listOf(1L), attempted)
    }

    @Test fun refreshAfterRemovalHidesTheCachedListenQueue() {
        val controller = controller()
        allowed = false
        controller.refreshReplies()
        assertNull((controller.state as VoiceState.Person).listen)
        assertFalse(controller.hasNewMessage("sam"))
    }
}
