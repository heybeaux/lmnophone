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
        /** What [silent] reports; only asked while the recorder runs. */
        var silentSoFar = false
        var silentChecks = 0
        override fun silent(): Boolean { silentChecks++; check(running); return silentSoFar }
        override fun start(file: File, format: VoiceFormat, maxMs: Long, listener: VoiceRecorder.Listener) {
            starts++
            failStart?.let { throw it }
            lastFormat = format; lastMaxMs = maxMs
            file.writeBytes(format.sample())
            running = true; this.listener = listener
            listener.onCaptureStarted()
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
    private class Gate : VoiceAudioGate {
        var allow = true
        var held = false
        var asks = 0
        override fun acquire(): Boolean { asks++; if (allow) held = true; return allow }
        override fun release() { held = false }
    }
    private class Speaker : VoiceSpeaker {
        val spoken = mutableListOf<String>()
        var stops = 0
        val requested = mutableListOf<String>()
        override fun speak(prompt: VoicePrompt, person: VoicePerson?) { spoken += "$prompt ${person?.name}" }
        override fun speakOnRequest(prompt: VoicePrompt, person: VoicePerson?) { requested += "$prompt ${person?.name}" }
        override fun stop() { stops++ }
    }

    private var now = 1_000_000L
    private val recorder = Recorder()
    private val player = Player()
    private val mic = Mic()
    private val speaker = Speaker()
    private val gate = Gate()
    private val sam = VoicePeople.practice[0]
    private fun store(dir: File = tmp.newFolder()) = VoiceDraftStore(dir) { now }
    private fun controller(store: VoiceDraftStore = store()) =
        VoicePracticeController(recorder, player, store, mic, speaker, gate, clock = { now }).also { it.open() }

    private fun VoicePracticeController.recordFor(ms: Long, end: VoiceEvent = Stop) {
        dispatch(Record); now += ms; dispatch(end)
    }

    @Test fun recordsAacM4aMonoForAtMostTwoMinutesByDefault() {
        val c = controller(); c.dispatch(Choose(sam)); c.dispatch(Record)
        assertEquals(VoiceFormat.AacM4a, recorder.lastFormat)
        assertEquals(120_000L, recorder.lastMaxMs)
        assertEquals("audio/mp4", VoiceFormat.AacM4a.mimeType)
        assertEquals(1, VoiceFormat.AacM4a.channels)
        assertEquals("audio/amr", VoiceFormat.AmrNb.mimeType)
        assertEquals(1, VoiceFormat.AmrNb.channels)
        assertTrue(c.state is VoiceState.Recording)
    }

    @Test fun openingSaysItIsPracticeAndRecordSilencesSpeechFirst() {
        val c = controller()
        assertEquals(listOf("Intro null"), speaker.spoken)
        c.dispatch(Choose(sam)); val stops = speaker.stops
        c.dispatch(Record)
        assertTrue(speaker.stops > stops)
        assertEquals(listOf("Intro null", "Person Sam"), speaker.spoken)
    }

    @Test fun amrFallbackIsConfigurable() {
        val store = VoiceDraftStore(tmp.newFolder(), VoiceFormat.AmrNb) { now }
        val c = VoicePracticeController(recorder, player, store, mic, speaker, gate, clock = { now }, format = VoiceFormat.AmrNb)
            .also { it.open() }
        c.dispatch(Choose(sam)); c.recordFor(2_000)
        assertEquals(VoiceFormat.AmrNb, recorder.lastFormat)
        assertEquals(listOf("sam.amr"), store.dir.list()!!.toList())
    }

    @Test fun stopKeepsOneDraftPerPersonAndSpeaksTheName() {
        val store = store(); val c = controller(store)
        c.dispatch(Choose(sam)); assertEquals("Person Sam", speaker.spoken.last())
        c.recordFor(3_000)
        assertEquals(1, recorder.stops)
        val review = c.state as VoiceState.Review
        assertEquals("sam", review.draft.personId)
        assertTrue(store.file("sam").length() > 0)
        c.dispatch(RecordAgain); now += 2_000; c.dispatch(Stop)
        assertEquals(listOf("sam.m4a"), store.dir.list()!!.sorted())
    }

    @Test fun reopeningAPersonShowsTheirKeptDraft() {
        val store = store(); val c = controller(store)
        c.dispatch(Choose(sam)); c.recordFor(3_000); c.dispatch(Back)
        c.dispatch(Choose(sam))
        assertTrue(c.state is VoiceState.Review)
        val again = VoicePracticeController(recorder, player, store, mic, speaker, gate, clock = { now }).also { it.open() }
        again.dispatch(Choose(sam))
        assertTrue(again.state is VoiceState.Review)
    }

    @Test fun tooShortATakeIsNotKept() {
        val store = store(); val c = controller(store)
        c.dispatch(Choose(sam)); c.recordFor(999)
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

    @Test fun startThrowingIsBusyAndTheHookFired() {
        for (failure in listOf(RuntimeException("start failed"), IllegalStateException("mic busy"), IOException("prepare"))) {
            recorder.failStart = failure
            val starts = recorder.starts
            val store = store(); val c = controller(store)
            c.dispatch(Choose(sam)); c.dispatch(Record)
            assertEquals(starts + 1, recorder.starts)
            assertEquals(VoiceState.Person(sam, VoiceNotice.MicBusy), c.state)
            assertEquals("Busy Sam", speaker.spoken.last())
            assertTrue(store.dir.list()!!.isEmpty())
            assertFalse(recorder.running)
        }
    }

    @Test fun microphoneFailingJustAfterStartIsBusyAndKeepsTheDraft() {
        val store = store(); val c = controller(store)
        c.dispatch(Choose(sam)); c.recordFor(3_000)
        val kept = store.file("sam").readBytes()
        c.dispatch(RecordAgain); recorder.listener!!.onStartFailed()
        assertEquals(VoiceNotice.MicBusy, (c.state as VoiceState.Review).notice)
        assertFalse(recorder.running); assertArrayEquals(kept, store.file("sam").readBytes())
        assertFalse(store.takeFile("sam").exists())
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

    /**
     * PLA-475 finding 2: Stop at 1.1 s, after the 1 s minimum but before the 1.25 s zero-peak window and
     * before Android reports the silencing. The recorder is asked before the take is kept, so the silent
     * replacement is never kept or shown on Review, and the earlier draft stays byte for byte.
     */
    @Test fun silentTakeStoppedBeforeTheSilenceWindowIsNotKept() {
        for (end in listOf<VoiceEvent>(Stop, Back, Interrupted(Interruption.ScreenOff), MaxLengthReached)) {
            val store = store(); val c = controller(store)
            c.dispatch(Choose(sam)); c.recordFor(3_000)
            val kept = store.file("sam").readBytes(); val draft = (c.state as VoiceState.Review).draft
            c.dispatch(RecordAgain); recorder.silentSoFar = true; now += 1_100; c.dispatch(end)
            assertEquals("$end", VoiceState.MicUnavailable(sam, MicProblem.PrivacyToggle, draft), c.state)
            assertArrayEquals("$end", kept, store.file("sam").readBytes())
            assertFalse("$end", store.takeFile("sam").exists())
            assertFalse("$end", recorder.running)
            assertEquals("MicOff Sam", speaker.spoken.last())
            recorder.silentSoFar = false; now += 10_000
        }
    }

    @Test fun silentFirstTakeIsNotKeptEither() {
        val store = store(); val c = controller(store)
        c.dispatch(Choose(sam)); c.dispatch(Record); recorder.silentSoFar = true; now += 1_100; c.dispatch(Stop)
        assertEquals(VoiceState.MicUnavailable(sam, MicProblem.PrivacyToggle, null), c.state)
        assertTrue(store.dir.list()!!.isEmpty())
    }

    @Test fun audibleTakeIsAskedAboutAndKept() {
        val store = store(); val c = controller(store)
        c.dispatch(Choose(sam)); c.recordFor(1_100)
        assertEquals(1, recorder.silentChecks)
        assertTrue(c.state is VoiceState.Review)
    }

    /** PLA-475 finding 1: the picture's "hear the name" is the person's own request, so it is spoken even with TalkBack. */
    @Test fun hearTheNameIsAnExplicitRequest() {
        val c = controller(); c.dispatch(Choose(sam))
        val automatic = speaker.spoken.toList()
        c.sayName()
        assertEquals(listOf("Name Sam"), speaker.requested)
        assertEquals(automatic, speaker.spoken)
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
        val c = controller(); c.dispatch(Choose(sam)); c.dispatch(Record); now += 120_000
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
