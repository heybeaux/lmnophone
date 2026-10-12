// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.device.voice.VoiceEvent.*

class VoicePracticeControllerTest {
    @get:Rule val tmp = TemporaryFolder()

    private class Recorder : VoiceRecorder {
        var starts = 0
        var stops = 0
        var failStart: Exception? = null
        var stopResult = true
        var running = false
        var listener: VoiceRecorder.Listener? = null
        var lastFormat: VoiceFormat? = null
        var lastMaxMs = 0L
        override fun start(file: File, format: VoiceFormat, maxMs: Long, listener: VoiceRecorder.Listener) {
            starts++
            failStart?.let { throw it }
            lastFormat = format; lastMaxMs = maxMs
            file.writeBytes(format.header + ByteArray(320))
            running = true; this.listener = listener
        }
        override fun stop(): Boolean { stops++; check(running); running = false; return stopResult }
    }
    private class Player : VoicePlayer {
        val played = mutableListOf<File>()
        var stops = 0
        var fail = false
        var done: (() -> Unit)? = null
        override fun play(file: File, onDone: () -> Unit, onError: () -> Unit) {
            played += file
            if (fail) throw IOException("cannot open") else done = onDone
        }
        override fun stop() { stops++ }
    }
    private class Mic(var problem: MicProblem? = null) : MicAccess {
        var checks = 0
        override fun problem(): MicProblem? { checks++; return problem }
    }
    private class Speaker : VoiceSpeaker {
        val spoken = mutableListOf<String>()
        override fun speakName(name: String) { spoken += name }
    }

    private var now = 1_000_000L
    private val recorder = Recorder()
    private val player = Player()
    private val mic = Mic()
    private val speaker = Speaker()
    private val sam = VoicePeople.practice[0]
    private fun store(dir: File = tmp.newFolder()) = VoiceDraftStore(dir) { now }
    private fun controller(store: VoiceDraftStore = store()) =
        VoicePracticeController(recorder, player, store, mic, speaker, clock = { now }).also { it.open() }

    private fun VoicePracticeController.recordFor(ms: Long, end: VoiceEvent = Stop) {
        dispatch(Record); now += ms; dispatch(end)
    }

    @Test fun recordsAmrNbMonoForAtMostSixtySecondsByDefault() {
        val c = controller(); c.dispatch(Choose(sam)); c.dispatch(Record)
        assertEquals(VoiceFormat.AmrNb, recorder.lastFormat)
        assertEquals(60_000L, recorder.lastMaxMs)
        assertEquals("audio/amr", VoiceFormat.AmrNb.mimeType)
        assertEquals(1, VoiceFormat.AmrNb.channels)
        assertTrue(c.state is VoiceState.Recording)
    }

    @Test fun stopKeepsOneDraftPerPersonAndSpeaksTheName() {
        val store = store(); val c = controller(store)
        c.dispatch(Choose(sam)); assertEquals(listOf("Sam"), speaker.spoken)
        c.recordFor(3_000)
        assertEquals(1, recorder.stops)
        val review = c.state as VoiceState.Review
        assertEquals("sam", review.draft.personId)
        assertTrue(store.file("sam").length() > 0)
        c.dispatch(RecordAgain); now += 2_000; c.dispatch(Stop)
        assertEquals(listOf("sam.amr"), store.dir.list()!!.sorted())
    }

    @Test fun reopeningAPersonShowsTheirKeptDraft() {
        val store = store(); val c = controller(store)
        c.dispatch(Choose(sam)); c.recordFor(3_000); c.dispatch(Back)
        c.dispatch(Choose(sam))
        assertTrue(c.state is VoiceState.Review)
        val again = VoicePracticeController(recorder, player, store, mic, speaker, clock = { now }).also { it.open() }
        again.dispatch(Choose(sam))
        assertTrue(again.state is VoiceState.Review)
    }

    @Test fun tooShortATakeIsNotKept() {
        val store = store(); val c = controller(store)
        c.dispatch(Choose(sam)); c.recordFor(200)
        assertEquals(VoiceState.Person(sam, VoiceNotice.NothingRecorded), c.state)
        assertFalse(store.file("sam").exists()); assertTrue(store.dir.list()!!.isEmpty())
    }

    @Test fun recorderWithNoValidDataIsNotKept() {
        recorder.stopResult = false
        val store = store(); val c = controller(store)
        c.dispatch(Choose(sam)); c.recordFor(5_000)
        assertEquals(VoiceState.Person(sam, VoiceNotice.NothingRecorded), c.state)
        assertTrue(store.dir.list()!!.isEmpty())
    }

    @Test fun startThrowingIsRecorderErrorAndTheHookFired() {
        for (failure in listOf(RuntimeException("start failed"), IllegalStateException("mic busy"), IOException("prepare"))) {
            recorder.failStart = failure
            val starts = recorder.starts
            val store = store(); val c = controller(store)
            c.dispatch(Choose(sam)); c.dispatch(Record)
            assertEquals(starts + 1, recorder.starts)
            assertEquals(VoiceState.MicUnavailable(sam, MicProblem.RecorderError, null), c.state)
            assertTrue(store.dir.list()!!.isEmpty())
            assertFalse(recorder.running)
        }
    }

    @Test fun storageThatCannotBeWrittenIsRecorderErrorWithoutStartingTheMic() {
        val notADir = tmp.newFile("voice-file")
        val c = controller(VoiceDraftStore(notADir) { now })
        c.dispatch(Choose(sam)); c.dispatch(Record)
        assertEquals(VoiceState.MicUnavailable(sam, MicProblem.RecorderError, null), c.state)
        assertEquals(0, recorder.starts)
    }

    @Test fun keepFailingAfterRecordingFallsBackToNothingRecorded() {
        val store = object : VoiceDraftStore(tmp.newFolder("voice2"), { now }) {
            var keeps = 0
            override fun keepTake(personId: String): VoiceDraft? { keeps++; throw IOException("disk full") }
        }
        val c = controller(store); c.dispatch(Choose(sam)); c.recordFor(3_000)
        assertEquals(1, store.keeps)
        assertEquals(VoiceState.Person(sam, VoiceNotice.NothingRecorded), c.state)
        assertFalse(store.takeFile("sam").exists())
    }

    @Test fun permissionAndPrivacyProblemsNeverStartTheRecorder() {
        for (problem in listOf(MicProblem.PermissionRefused, MicProblem.PrivacyToggle)) {
            mic.problem = problem
            val c = controller(); c.dispatch(Choose(sam)); c.dispatch(Record)
            assertEquals(VoiceState.MicUnavailable(sam, problem, null), c.state)
        }
        assertEquals(0, recorder.starts); assertTrue(mic.checks >= 2)
    }

    @Test fun silencedMicrophoneWhileRecordingDiscardsTheTake() {
        val store = store(); val c = controller(store)
        c.dispatch(Choose(sam)); c.dispatch(Record); now += 3_000
        recorder.listener!!.onSilenced()
        assertEquals(1, recorder.stops)
        assertEquals(VoiceState.MicUnavailable(sam, MicProblem.PrivacyToggle, null), c.state)
        assertTrue(store.dir.list()!!.isEmpty())
    }

    @Test fun recorderErrorWhileRecordingKeepsThePreviousDraft() {
        val store = store(); val c = controller(store)
        c.dispatch(Choose(sam)); c.recordFor(3_000)
        val kept = store.file("sam").readBytes()
        c.dispatch(RecordAgain); now += 1_000
        recorder.listener!!.onError()
        assertEquals(VoiceState.MicUnavailable::class, c.state::class)
        assertArrayEquals(kept, store.file("sam").readBytes())
        assertFalse(store.takeFile("sam").exists())
    }

    @Test fun maxLengthFromTheRecorderEndsInReview() {
        val c = controller(); c.dispatch(Choose(sam)); c.dispatch(Record); now += 60_000
        recorder.listener!!.onMaxLength()
        assertEquals(VoiceNotice.MaxLength, (c.state as VoiceState.Review).notice)
        assertEquals(1, recorder.stops)
    }

    @Test fun lateRecorderCallbacksAfterStopAreIgnored() {
        val c = controller(); c.dispatch(Choose(sam)); c.dispatch(Record); now += 2_000
        val stale = recorder.listener!!
        c.dispatch(Stop)
        val after = c.state
        stale.onMaxLength(); stale.onError(); stale.onSilenced()
        assertEquals(after, c.state); assertEquals(1, recorder.stops)
    }

    @Test fun everyInterruptionStopsTheRecorderAndKeepsTheDraft() {
        for (cause in Interruption.entries) {
            val stops = recorder.stops
            val c = controller(); c.dispatch(Choose(sam)); c.recordFor(2_000, Interrupted(cause))
            assertEquals(stops + 1, recorder.stops); assertFalse(recorder.running)
            assertEquals(VoiceNotice.Interrupted, (c.state as VoiceState.Review).notice)
        }
    }

    @Test fun playbackUsesTheDraftAndReportsEndAndFailure() {
        val store = store(); val c = controller(store)
        c.dispatch(Choose(sam)); c.recordFor(3_000); c.dispatch(Play)
        assertEquals(listOf(store.file("sam")), player.played)
        assertTrue((c.state as VoiceState.Review).playing)
        player.done!!()
        assertFalse((c.state as VoiceState.Review).playing)
        player.fail = true; c.dispatch(Play)
        assertEquals(VoiceNotice.CouldNotPlay, (c.state as VoiceState.Review).notice)
    }

    @Test fun deleteRemovesTheFile() {
        val store = store(); val c = controller(store)
        c.dispatch(Choose(sam)); c.recordFor(3_000); assertTrue(store.file("sam").exists())
        c.dispatch(Delete)
        assertFalse(store.file("sam").exists()); assertEquals(VoiceState.Person(sam, VoiceNotice.Deleted), c.state)
    }

    @Test fun leaveIsReportedOnceFromChoosePerson() {
        val c = controller(); var left = 0
        c.onLeave = { left++ }
        c.dispatch(Back); assertEquals(1, left)
    }

    @Test fun closeStopsEverythingAndNeverLeavesTheRecorderRunning() {
        val c = controller(); c.dispatch(Choose(sam)); c.dispatch(Record); now += 2_000
        c.close()
        assertFalse(recorder.running); assertTrue(player.stops > 0)
    }
}
