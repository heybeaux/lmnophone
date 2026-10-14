// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * PLA-465 review findings. The first three tests are the Verifier's scratch reproductions (they
 * failed on 185332d1); the capture-start one now reports the start the way AndroidVoiceRecorder does,
 * [AndroidVoiceRecorder.TONE_GAP_MS] after the tap. Host evidence, not measured device audio.
 */
class VoiceReviewRegressionTest {
    @get:Rule val tmp = TemporaryFolder()
    private var now = 0L
    private var speechRunning = false
    private var playing = false
    private var plays = 0
    private var takes = 0
    private var recorderStarts = 0
    private var listener: VoiceRecorder.Listener? = null
    private lateinit var output: File
    private val recorder = object : VoiceRecorder {
        override fun start(file: File, format: VoiceFormat, maxMs: Long, listener: VoiceRecorder.Listener) {
            output = file; recorderStarts++; takes++
            this@VoiceReviewRegressionTest.listener = listener
        }
        override fun silent() = false
        override fun stop(): Boolean {
            output.writeBytes(VoiceFormat.AacM4a.sample() + takes.toByte())
            return true
        }
    }
    private val player = object : VoicePlayer {
        override fun play(file: File, onDone: () -> Unit, onError: () -> Unit) { playing = true; plays++ }
        override fun stop() { playing = false }
    }
    private val speaker = object : VoiceSpeaker {
        override fun speak(prompt: VoicePrompt, person: VoicePerson?) { speechRunning = true }
        override fun stop() { speechRunning = false }
    }
    private val gate = object : VoiceAudioGate {
        var allow = true
        var held = false
        override fun acquire(): Boolean { if (allow) held = true; return allow }
        override fun release() { held = false }
    }

    /** Tap, setup and tone, then the microphone really starts. */
    private fun VoicePracticeController.startTake(event: VoiceEvent) {
        dispatch(event); now += AndroidVoiceRecorder.TONE_GAP_MS; listener!!.onCaptureStarted()
    }

    private fun setup(): Pair<VoicePracticeController, VoiceDraftStore> {
        val store = VoiceDraftStore(tmp.newFolder()) { now }
        val c = VoicePracticeController(recorder, player, store, MicAccess { null }, speaker, gate, { now }, people = { VoicePeople.practice })
        c.open(); c.dispatch(VoiceEvent.Choose(VoicePeople.practice.first()))
        c.startTake(VoiceEvent.Record); now += 3000; c.dispatch(VoiceEvent.Stop)
        return c to store
    }

    @Test fun interruptionClearsPlayingStateSoListenCanRestart() {
        for (cause in Interruption.entries) {
            val (c, _) = setup()
            c.dispatch(VoiceEvent.Play)
            assertTrue(gate.held)
            c.close(cause)
            assertFalse(playing); assertFalse(gate.held)
            assertFalse("$cause: after interruption the UI must offer Listen", (c.state as VoiceState.Review).playing)
            c.dispatch(VoiceEvent.Play)
            assertTrue(playing)
        }
    }

    @Test fun lessThanOneSecondOfAudioDoesNotReplacePreviousDraft() {
        val (c, store) = setup()
        val original = store.file("sam").readBytes()
        c.startTake(VoiceEvent.RecordAgain); now += 850
        c.dispatch(VoiceEvent.Stop)
        assertArrayEquals("850 ms of captured audio must preserve the earlier draft", original, store.file("sam").readBytes())
        assertEquals(VoiceNotice.NothingRecorded, (c.state as VoiceState.Review).notice)
    }

    @Test fun oneSecondOfCapturedAudioDoesReplaceThePreviousDraft() {
        val (c, store) = setup()
        val original = store.file("sam").readBytes()
        c.startTake(VoiceEvent.RecordAgain); now += 1_000
        c.dispatch(VoiceEvent.Stop)
        assertFalse(original.contentEquals(store.file("sam").readBytes()))
    }

    @Test fun stopBeforeTheMicrophoneStartsKeepsTheEarlierDraft() {
        val (c, store) = setup()
        val original = store.file("sam").readBytes()
        c.dispatch(VoiceEvent.RecordAgain); now += 5_000
        c.dispatch(VoiceEvent.Stop)
        assertArrayEquals(original, store.file("sam").readBytes())
    }

    @Test fun listenStopsTheSpokenReviewPrompt() {
        val (c, _) = setup()
        assertTrue(speechRunning)
        c.dispatch(VoiceEvent.Play)
        assertTrue(playing)
        assertFalse("Listen must stop speech instead of mixing it with the recording", speechRunning)
    }

    @Test fun backAndOtherScreenChangesStopThePrompt() {
        val (c, _) = setup()
        assertTrue(speechRunning)
        c.dispatch(VoiceEvent.Back)
        assertEquals(VoiceState.ChoosePerson, c.state)
        assertFalse(speechRunning)
        c.dispatch(VoiceEvent.Choose(VoicePeople.practice[1])); assertTrue(speechRunning)
        c.dispatch(VoiceEvent.Back); assertFalse(speechRunning)
    }

    @Test fun refusedFocusOrACallNeverOpensTheMicrophoneAndKeepsTheDraft() {
        val (c, store) = setup()
        val original = store.file("sam").readBytes(); val starts = recorderStarts
        gate.allow = false
        c.dispatch(VoiceEvent.RecordAgain)
        assertEquals(starts, recorderStarts)
        assertEquals(VoiceNotice.MicBusy, (c.state as VoiceState.Review).notice)
        assertArrayEquals(original, store.file("sam").readBytes())
        assertFalse(store.takeFile("sam").exists())
    }

    @Test fun refusedFocusOrACallNeverStartsPlayback() {
        val (c, _) = setup()
        gate.allow = false
        c.dispatch(VoiceEvent.Play)
        assertEquals(0, plays)
        val review = c.state as VoiceState.Review
        assertFalse(review.playing); assertEquals(VoiceNotice.CouldNotPlay, review.notice)
    }

    @Test fun focusIsHeldOnlyWhileRecordingOrPlaying() {
        val (c, _) = setup()
        assertFalse(gate.held)
        c.dispatch(VoiceEvent.Play); assertTrue(gate.held)
        c.dispatch(VoiceEvent.PlaybackEnded); assertFalse(gate.held)
        c.startTake(VoiceEvent.RecordAgain); assertTrue(gate.held)
        now += 2_000; c.dispatch(VoiceEvent.Stop); assertFalse(gate.held)
    }

    @Test fun activityRestartRearmsTheGuardsWithoutLosingTheScreenOrDraft() {
        val store = VoiceDraftStore(tmp.newFolder()) { now }
        val c = VoicePracticeController(recorder, player, store, MicAccess { null }, speaker, gate, { now }, people = { VoicePeople.practice })
        var arms = 0; var disarms = 0
        val session = VoicePracticeSession(c, { arms++ }, { disarms++ })
        session.show(); assertEquals(1, arms); assertTrue(session.armed)
        c.dispatch(VoiceEvent.Choose(VoicePeople.practice.first()))
        c.startTake(VoiceEvent.Record); now += 3_000

        session.activityStopped()
        assertEquals(1, disarms); assertFalse(session.armed); assertFalse(gate.held)
        val kept = c.state as VoiceState.Review
        assertEquals(VoiceNotice.Interrupted, kept.notice)

        session.activityStarted()
        assertEquals(2, arms); assertTrue(session.armed)
        assertEquals("the same screen and draft, not a reopened practice", kept, c.state)

        // Re-armed: a call while listening stops playback again.
        c.dispatch(VoiceEvent.Play); assertTrue(playing)
        c.close(Interruption.Call)
        assertFalse(playing); assertFalse((c.state as VoiceState.Review).playing)

        session.hide(); assertFalse(session.armed); assertEquals(2, disarms)
        session.activityStopped(); session.activityStarted()
        assertEquals("a hidden practice is not re-armed", 2, arms)
    }

    @Test fun showingWhileTheActivityIsStoppedArmsOnlyOnStart() {
        val c = VoicePracticeController(recorder, player, VoiceDraftStore(tmp.newFolder()) { now },
            MicAccess { null }, speaker, gate, { now }, people = { VoicePeople.practice })
        var arms = 0
        val session = VoicePracticeSession(c, { arms++ }, { })
        session.activityStopped(); session.show()
        assertEquals(0, arms); assertTrue(session.showing)
        session.activityStarted(); assertEquals(1, arms)
        session.show(); assertEquals(1, arms)
    }
}
