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
        VoicePracticeController(recorder, player, store, mic, speaker, gate, clock = { now }, people = { VoicePeople.practice }).also { it.open() }

    private fun VoicePracticeController.recordFor(ms: Long, end: VoiceEvent = Stop) {
        dispatch(Record); now += ms; dispatch(end)
    }

    @Test fun restoresOnlyLiveFamilyAndUsesFamilyDraftStore() {
        val practice = store()
        val family = store()
        val person = VoicePerson("familysam", "Sam", "Sam")
        var people = listOf(person)
        val c = VoicePracticeController(recorder, player, practice, mic, speaker, gate,
            clock = { now }, people = { people }, draftStore = { family })
        c.open(); c.dispatch(Choose(person)); c.recordFor(1500)
        assertNotNull(family.draft(person.id)); assertNull(practice.draft(person.id))
        val place = c.place(); c.open(place)
        assertEquals(person, (c.state as VoiceState.Review).person)
        people = emptyList(); c.open(place)
        assertEquals(VoiceState.ChoosePerson, c.state)
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
        val c = VoicePracticeController(recorder, player, store, mic, speaker, gate, clock = { now }, format = VoiceFormat.AmrNb, people = { VoicePeople.practice })
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
        val again = VoicePracticeController(recorder, player, store, mic, speaker, gate, clock = { now }, people = { VoicePeople.practice }).also { it.open() }
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

    // PLA-479: a configuration change (text size) recreates MainActivity. The practice comes back to the same
    // screen and person; a recording is stopped by onStop first and its take kept ("Recording stopped.").

    private fun session(store: VoiceDraftStore): VoicePracticeSession =
        VoicePracticeSession(VoicePracticeController(recorder, player, store, mic, speaker, gate, clock = { now }, people = { VoicePeople.practice }), {}, {})
            .also { it.activityStarted(); it.show() }

    /** MainActivity's order on a recreation: onStop, keep the place, the route goes away, a new host opens it. */
    private fun recreate(old: VoicePracticeSession, store: VoiceDraftStore): VoicePracticeController =
        recreateSession(old, store).controller

    private fun recreateSession(old: VoicePracticeSession, store: VoiceDraftStore): VoicePracticeSession {
        old.activityStopped()
        val place = old.place()
        old.hide()
        speaker.spoken.clear()
        val c = VoicePracticeController(recorder, player, store, mic, speaker, gate, clock = { now }, people = { VoicePeople.practice })
        return VoicePracticeSession(c, {}, {}).apply { activityStarted(); show(place) }
    }

    @Test fun personScreenSurvivesARecreation() {
        val store = store(); val s = session(store); val c = s.controller
        c.dispatch(Choose(sam))
        val after = recreate(s, store)
        assertEquals(VoiceState.Person(sam), after.state)
        assertEquals("a text-size change is not a new screen: nothing is said again", emptyList<String>(), speaker.spoken)
    }

    @Test fun reviewWithATakeSurvivesARecreation() {
        val store = store(); val s = session(store); val c = s.controller
        c.dispatch(Choose(sam)); c.recordFor(3_000)
        val kept = (c.state as VoiceState.Review).draft
        val after = recreate(s, store)
        val review = after.state as VoiceState.Review
        assertEquals(sam, review.person)
        assertEquals(kept, review.draft)
        assertFalse(review.playing)
        assertTrue(store.file("sam").length() > 0)
        assertEquals(emptyList<String>(), speaker.spoken)
        assertEquals(1, recorder.starts)
    }

    @Test fun recordingDuringARecreationStopsKeepsTheTakeAndSaysSo() {
        val store = store(); val s = session(store); val c = s.controller
        c.dispatch(Choose(sam)); c.dispatch(Record); now += 2_000
        assertTrue(recorder.running)
        val after = recreate(s, store)
        assertFalse("the microphone is released", recorder.running)
        assertEquals(1, recorder.stops)
        assertEquals("never starts recording again by itself", 1, recorder.starts)
        assertFalse(gate.held)
        val review = after.state as VoiceState.Review
        assertEquals(sam, review.person)
        assertEquals(VoiceNotice.Interrupted, review.notice)
        assertEquals(store.draft("sam"), review.draft)
        assertTrue(store.file("sam").length() > 0)
    }

    @Test fun tooShortARecordingDuringARecreationStaysOnThePersonWithItsNotice() {
        val store = store(); val s = session(store); val c = s.controller
        c.dispatch(Choose(sam)); c.dispatch(Record); now += 300
        val after = recreate(s, store)
        assertFalse(recorder.running)
        assertEquals(VoiceState.Person(sam, VoiceNotice.NothingRecorded), after.state)
        assertNull(store.draft("sam"))
    }

    @Test fun micOffSurvivesARecreationWithTheEarlierTake() {
        val store = store(); val s = session(store); val c = s.controller
        c.dispatch(Choose(sam)); c.recordFor(3_000)
        mic.problem = MicProblem.PrivacyToggle; c.dispatch(RecordAgain)
        val after = recreate(s, store)
        assertEquals(VoiceState.MicUnavailable(sam, MicProblem.PrivacyToggle, store.draft("sam")), after.state)
    }

    // PLA-484: the people list is a place too, so a text-size change there says nothing again.
    @Test fun peopleListSurvivesARecreationSilently() {
        val store = store(); val s = session(store)
        assertEquals(listOf("Intro null"), speaker.spoken)
        assertNotNull("the people list is kept like the other screens", s.place())
        val after = recreate(s, store)
        assertEquals(VoiceState.ChoosePerson, after.state)
        assertEquals("a text-size change is not a new screen: nothing is said again", emptyList<String>(), speaker.spoken)
        assertEquals(0, recorder.starts)
        assertFalse(gate.held)
    }

    @Test fun peopleListSurvivesRepeatedRecreationsSilently() {
        val store = store(); var s = session(store)
        repeat(3) {
            // Text size 1.0 -> 1.3 -> 2.0 -> 1.0, as on the spare.
            s = recreateSession(s, store)
            assertEquals(VoiceState.ChoosePerson, s.controller.state)
            assertEquals(emptyList<String>(), speaker.spoken)
        }
    }

    @Test fun aFreshOpenStillSaysTheIntroOnce() {
        val store = store(); val s = session(store)
        assertEquals(listOf("Intro null"), speaker.spoken)
        s.hide(); speaker.spoken.clear()
        s.show()
        assertEquals(VoiceState.ChoosePerson, s.controller.state)
        assertEquals("a new visit from More is a new screen", listOf("Intro null"), speaker.spoken)
    }

    @Test fun unknownPlacesOpenAsUsual() {
        val store = store()
        val c = VoicePracticeController(recorder, player, store, mic, speaker, gate, clock = { now }, people = { VoicePeople.practice })
        c.open(VoicePlace("nobody", VoicePlace.Screen.Review))
        assertEquals(VoiceState.ChoosePerson, c.state)
        assertEquals("not the screen it was on, so it is introduced", listOf("Intro null"), speaker.spoken)
    }

    @Test fun aReviewWhoseTakeIsGoneComesBackOnThePerson() {
        val store = store(); val s = session(store); val c = s.controller
        c.dispatch(Choose(sam)); c.recordFor(3_000)
        s.activityStopped(); val place = s.place(); s.hide()
        store.delete("sam")
        val after = VoicePracticeController(recorder, player, store, mic, speaker, gate, clock = { now }, people = { VoicePeople.practice })
        after.open(place)
        assertEquals(VoiceState.Person(sam), after.state)
    }

    @Test fun noPlaceIsKeptOnceTheRouteIsGone() {
        val store = store(); val s = session(store)
        s.controller.dispatch(Choose(sam)); s.hide()
        assertNull(s.place())
    }
}
