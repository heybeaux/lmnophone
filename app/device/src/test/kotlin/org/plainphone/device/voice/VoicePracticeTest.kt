// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
import org.plainphone.device.voice.VoiceEffect.*
import org.plainphone.device.voice.VoiceEvent.*
import org.plainphone.device.voice.VoiceState.*
import org.plainphone.device.voice.VoiceEvent.MicUnavailable

class VoicePracticeTest {
    private val sam = VoicePeople.practice[0]
    private val jo = VoicePeople.practice[1]
    private val take = VoiceDraft("sam", 4_000)
    private val older = VoiceDraft("sam", 9_000)

    private fun reduce(s: VoiceState, e: VoiceEvent) = VoicePractice.reduce(s, e)

    @Test fun practicePeopleAreTheSyntheticFixtureOnly() {
        assertEquals(listOf("Sam", "Jo", "Alex"), VoicePeople.practice.map { it.name })
    }

    @Test fun choosingAPersonSpeaksTheNameAndShowsAnExistingDraft() {
        assertEquals(Transition(Person(sam), listOf(SpeakName(sam))), reduce(ChoosePerson, Choose(sam, null)))
        assertEquals(Transition(Review(sam, older), listOf(SpeakName(sam))), reduce(ChoosePerson, Choose(sam, older)))
    }

    @Test fun recordShowsRecordingBeforeTheRecorderStarts() {
        val t = reduce(Person(sam), Record)
        assertEquals(Recording(sam, null), t.state)
        assertEquals(listOf(StartRecording("sam")), t.effects)
    }

    @Test fun stopKeepsTheTakeAndEndsInReview() {
        val saving = reduce(Recording(sam, null), Stop)
        assertEquals(Saving(sam, null), saving.state)
        assertEquals(listOf(FinishRecording("sam", keep = true)), saving.effects)
        assertEquals(Review(sam, take), reduce(saving.state, RecordingFinished(take)).state)
    }

    @Test fun maxLengthAndEveryInterruptionEndInReviewWithTheDraftKept() {
        val causes = listOf(MaxLengthReached to VoiceNotice.MaxLength) +
            Interruption.entries.map { Interrupted(it) to VoiceNotice.Interrupted }
        for ((event, notice) in causes) {
            val saving = reduce(Recording(sam, null), event)
            assertEquals(event.toString(), listOf(FinishRecording("sam", keep = true)), saving.effects)
            assertEquals(event.toString(), Review(sam, take, notice = notice),
                reduce(saving.state, RecordingFinished(take)).state)
        }
    }

    @Test fun nothingUsableGoesBackToPersonOrThePreviousDraft() {
        assertEquals(Person(sam, VoiceNotice.NothingRecorded),
            reduce(Saving(sam, null), RecordingFinished(null)).state)
        assertEquals(Review(sam, older, notice = VoiceNotice.NothingRecorded),
            reduce(Saving(sam, older), RecordingFinished(null)).state)
    }

    @Test fun backWhileRecordingStopsAndKeepsTheTake() {
        val t = reduce(Recording(sam, null), Back)
        assertEquals(listOf(FinishRecording("sam", keep = true)), t.effects)
        assertEquals(Review(sam, take), reduce(t.state, RecordingFinished(take)).state)
    }

    @Test fun micProblemWhileRecordingDiscardsTheTakeAndKeepsThePreviousDraft() {
        for (problem in MicProblem.entries) {
            val t = reduce(Recording(sam, older), MicUnavailable(problem))
            assertEquals(listOf(FinishRecording("sam", keep = false)), t.effects)
            assertEquals(VoiceState.MicUnavailable(sam, problem, older), reduce(t.state, RecordingFinished(null)).state)
        }
    }

    @Test fun micProblemBeforeRecordingIsAnExplicitState() {
        assertEquals(VoiceState.MicUnavailable(sam, MicProblem.PermissionRefused, null),
            reduce(Person(sam), MicUnavailable(MicProblem.PermissionRefused)).state)
        assertEquals(VoiceState.MicUnavailable(sam, MicProblem.PrivacyToggle, older),
            reduce(Review(sam, older), MicUnavailable(MicProblem.PrivacyToggle)).state)
    }

    @Test fun micProblemTryAgainAndBack() {
        val problem = VoiceState.MicUnavailable(sam, MicProblem.RecorderError, older)
        assertEquals(Transition(Recording(sam, older), listOf(StartRecording("sam"))), reduce(problem, Record))
        assertEquals(Review(sam, older), reduce(problem, Back).state)
        assertEquals(Person(sam), reduce(problem.copy(previous = null), Back).state)
    }

    @Test fun playAndStopPlayback() {
        val playing = reduce(Review(sam, take), Play)
        assertEquals(Transition(Review(sam, take, playing = true), listOf(StartPlayback("sam"))), playing)
        assertEquals(Transition(Review(sam, take), listOf(StopPlayback)), reduce(playing.state, Stop))
        assertEquals(Review(sam, take), reduce(playing.state, PlaybackEnded).state)
        assertEquals(Review(sam, take, notice = VoiceNotice.CouldNotPlay), reduce(playing.state, PlaybackFailed).state)
    }

    @Test fun interruptionDuringPlaybackStopsItAndStaysInReview() {
        for (cause in Interruption.entries) {
            assertEquals(Transition(Review(sam, take), listOf(StopPlayback)),
                reduce(Review(sam, take, playing = true), Interrupted(cause)))
        }
    }

    @Test fun recordAgainKeepsTheOldDraftUntilTheNewTakeIsSaved() {
        val t = reduce(Review(sam, older, playing = true), RecordAgain)
        assertEquals(Transition(Recording(sam, older), listOf(StopPlayback, StartRecording("sam"))), t)
        val saving = reduce(t.state, Stop).state
        assertEquals(Review(sam, take), reduce(saving, RecordingFinished(take)).state)
    }

    @Test fun deleteRemovesTheFileAndReturnsToPerson() {
        assertEquals(Transition(Person(sam, VoiceNotice.Deleted), listOf(StopPlayback, DeleteDraft("sam"))),
            reduce(Review(sam, take, playing = true), Delete))
    }

    @Test fun backLeavesOneLevelAtATime() {
        assertEquals(Transition(ChoosePerson, listOf(StopPlayback)), reduce(Review(sam, take), Back))
        assertEquals(Transition(ChoosePerson), reduce(Person(sam), Back))
        assertEquals(Transition(ChoosePerson, listOf(Leave)), reduce(ChoosePerson, Back))
    }

    @Test fun recipientCannotChangeOnceChosen() {
        val chosen = listOf(Person(sam), Recording(sam, null), Saving(sam, null), Review(sam, take),
            VoiceState.MicUnavailable(sam, MicProblem.RecorderError, null))
        for (s in chosen) assertEquals(Transition(s), reduce(s, Choose(jo, null)))
    }

    @Test fun savingIgnoresEverythingButTheResult() {
        val s = Saving(sam, null)
        for (e in events(Random(1)).take(200)) if (e !is RecordingFinished) assertEquals(Transition(s), reduce(s, e))
    }

    /** Random walks: no send, the recorder only runs while Recording is shown, and every exit finishes it. */
    @Test fun invariantsHoldOnRandomWalks() {
        val random = Random(457)
        repeat(2_000) {
            var s: VoiceState = ChoosePerson
            var recorderRunning = false
            var person: VoicePerson? = null
            repeat(40) {
                val e = events(random).first()
                val t = reduce(s, e)
                for (effect in t.effects) when (effect) {
                    is StartRecording -> {
                        assertTrue("start only into Recording: $s $e", t.state is Recording)
                        assertFalse(recorderRunning); recorderRunning = true
                    }
                    is FinishRecording -> { assertTrue("finish only a running recorder: $s $e", recorderRunning); recorderRunning = false }
                    is StartPlayback -> assertTrue(t.state is Review && (t.state as Review).playing)
                    is SpeakName, StopPlayback, is DeleteDraft, Leave -> Unit
                }
                if (recorderRunning) assertTrue("recorder running only while Recording is shown: $s $e -> ${t.state}", t.state is Recording)
                val next = t.state.person
                if (s.person != null && next != null) assertEquals("recipient changed: $s $e", s.person, next)
                if (s !is ChoosePerson && next != null && person != null && s.person != null) assertEquals(person, next)
                person = next
                s = t.state
            }
        }
        assertEquals(6, VoiceState::class.java.declaredClasses.count { VoiceState::class.java.isAssignableFrom(it) })
        assertTrue(VoiceState::class.java.declaredClasses.none { it.simpleName.contains("Sent") })
        assertTrue(VoiceEffect::class.java.declaredClasses.none { it.simpleName.contains("Send") })
    }

    private fun events(random: Random) = generateSequence {
        when (random.nextInt(16)) {
            0 -> Choose(VoicePeople.practice.random(random), if (random.nextBoolean()) take else null)
            1 -> Record; 2 -> Stop; 3 -> Play; 4 -> PlaybackEnded; 5 -> PlaybackFailed
            6 -> RecordAgain; 7 -> Delete; 8 -> Back; 9 -> MaxLengthReached
            10 -> Interrupted(Interruption.entries.random(random))
            11 -> MicUnavailable(MicProblem.entries.random(random))
            12 -> RecordingFinished(if (random.nextBoolean()) take else null)
            13 -> Choose(sam, null)
            14 -> Stop
            else -> Record
        }
    }
}
