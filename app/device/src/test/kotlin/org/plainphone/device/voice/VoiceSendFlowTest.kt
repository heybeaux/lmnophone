// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.device.voice.VoiceEvent.*
import org.plainphone.device.voice.send.SendProblem
import org.plainphone.device.voice.send.SendStatus
import org.plainphone.device.voice.send.SendView

/**
 * PLA-499: Send on the Review screen. Sending happens only on an explicit Send or Try again; the screen shows
 * Sending until the answer, Sent only when the sender says Sent, and every other answer as Not sent.
 */
class VoiceSendFlowTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Fictional numbers only (555-01xx). */
    private val sam = VoicePerson("sam", "Sam")
    private val jo = VoicePerson("jo", "Jo")
    private val alex = VoicePerson("alex", "Alex")
    /** Looked up by person id at the point of use, like the family list's VoiceRecipientSource. Alex has none. */
    private val numbers = mutableMapOf("sam" to "555-0100", "jo" to "555-0101")
    private var numbersFail: Exception? = null
    private val draft = VoiceDraft("sam", 1L)

    private fun view(status: SendStatus, seen: Boolean = false, failures: Int = 0) = SendView(status, seen, failures)
    private fun notSent(p: SendProblem, seen: Boolean = false, failures: Int = 1) = view(SendStatus.NotSent(p), seen, failures)
    private fun reduce(s: VoiceState, e: VoiceEvent) = VoicePractice.reduce(s, e)
    private fun sends(t: Transition) = t.effects.filterIsInstance<VoiceEffect.SendDraft>()

    // ---- Reducer ----

    @Test fun sendOnReviewAsksToSendOnceAndSaysNothingYet() {
        val t = reduce(VoiceState.Review(sam, draft), Send)
        assertEquals(VoiceState.Sending(sam, draft), t.state)
        assertEquals(listOf(VoiceEffect.SendDraft(sam)), sends(t))
        assertTrue(t.effects.none { it is VoiceEffect.Speak })
    }

    @Test fun sendWhilePlayingStopsPlaybackFirst() {
        val t = reduce(VoiceState.Review(sam, draft, playing = true), Send)
        assertEquals(listOf(VoiceEffect.StopPlayback, VoiceEffect.SendDraft(sam)), t.effects)
    }

    @Test fun sendingIsSaidOnceWhenTheSenderHasTakenIt() {
        val first = reduce(VoiceState.Sending(sam, draft), SendUpdated("sam", view(SendStatus.Sending)))
        assertEquals(VoiceState.Sending(sam, draft, announced = true), first.state)
        assertEquals(listOf(VoiceEffect.Speak(VoicePrompt.Sending, sam)), first.effects)
        val again = reduce(first.state, SendUpdated("sam", view(SendStatus.Sending)))
        assertEquals(first.state, again.state)
        assertTrue(again.effects.isEmpty())
    }

    @Test fun sentOnlyWhenTheSenderSaysSent() {
        val t = reduce(VoiceState.Sending(sam, draft, announced = true), SendUpdated("sam", view(SendStatus.Sent)))
        assertEquals(VoiceState.Sent(sam), t.state)
        assertEquals(listOf(VoiceEffect.Speak(VoicePrompt.Sent, sam), VoiceEffect.SendSeen("sam")), t.effects)
    }

    @Test fun everyProblemIsNotSentAndKeepsTheMessage() {
        val prompts = mapOf(
            SendProblem.NoSignal to VoicePrompt.NoSignal,
            SendProblem.MmsOff to VoicePrompt.NotSent,
            SendProblem.TooBig to VoicePrompt.NotSent,
            SendProblem.NoPermission to VoicePrompt.NotSent,
            SendProblem.OtherError to VoicePrompt.NotSent,
            SendProblem.NotConfirmed to VoicePrompt.NotConfirmed,
        )
        assertEquals(SendProblem.entries.toSet(), prompts.keys)
        for ((problem, prompt) in prompts) {
            val t = reduce(VoiceState.Sending(sam, draft), SendUpdated("sam", notSent(problem)))
            assertEquals(problem.name, VoiceState.NotSent(sam, draft, problem, 1), t.state)
            assertEquals(problem.name, listOf(VoiceEffect.Speak(prompt, sam), VoiceEffect.SendSeen("sam")), t.effects)
            assertTrue(t.effects.none { it is VoiceEffect.DeleteDraft })
        }
    }

    @Test fun aSecondFailureAsksForTheTrustedPerson() {
        val t = reduce(VoiceState.Sending(sam, draft), SendUpdated("sam", notSent(SendProblem.OtherError, failures = 2)))
        assertEquals(VoiceEffect.Speak(VoicePrompt.NotSentAgain, sam), t.effects.first())
        // No signal and not confirmed keep their own words: neither is the transport failing.
        val noSignal = reduce(VoiceState.Sending(sam, draft), SendUpdated("sam", notSent(SendProblem.NoSignal, failures = 3)))
        assertEquals(VoiceEffect.Speak(VoicePrompt.NoSignal, sam), noSignal.effects.first())
    }

    @Test fun tryAgainIsTheOnlyWayToSendAgain() {
        val failed = VoiceState.NotSent(sam, draft, SendProblem.OtherError, 1)
        val t = reduce(failed, TryAgain)
        assertEquals(VoiceState.Sending(sam, draft), t.state)
        assertEquals(listOf(VoiceEffect.SendDraft(sam)), sends(t))
    }

    /** No automatic send: no state and no event other than Send on Review or Try again on Not sent ever sends. */
    @Test fun nothingElseEverSends() {
        val states = listOf(VoiceState.ChoosePerson, VoiceState.Person(sam), VoiceState.Recording(sam, draft),
            VoiceState.Saving(sam, draft), VoiceState.Review(sam, draft), VoiceState.Review(sam, draft, playing = true),
            VoiceState.MicUnavailable(sam, MicProblem.RecorderError, draft), VoiceState.Sending(sam, draft),
            VoiceState.Sending(sam, draft, announced = true), VoiceState.Sent(sam),
            *SendProblem.entries.map { VoiceState.NotSent(sam, draft, it, 2) }.toTypedArray())
        val updates = listOf(view(SendStatus.Sending), view(SendStatus.Sent), view(SendStatus.Sent, seen = true)) +
            SendProblem.entries.flatMap { listOf(notSent(it), notSent(it, seen = true)) }
        val events = listOf(Record, Stop, Play, PlaybackEnded, PlaybackFailed, RecordAgain, Delete, Back, Done,
            MaxLengthReached, CouldNotStart, RecordingFinished(draft), RecordingFinished(null),
            *Interruption.entries.map { Interrupted(it) }.toTypedArray(),
            *MicProblem.entries.map { VoiceEvent.MicUnavailable(it) }.toTypedArray(),
            *updates.map { SendUpdated("sam", it) }.toTypedArray(),
            *(updates.map { Choose(sam, draft, it) } + updates.map { Choose(sam, null, it) }).toTypedArray(),
            Send, TryAgain)
        for (s in states) for (e in events) {
            val allowed = (s is VoiceState.Review && e == Send) || (s is VoiceState.NotSent && e == TryAgain)
            if (!allowed) assertTrue("$s + $e", sends(reduce(s, e)).isEmpty())
        }
    }

    @Test fun leavingWhileSendingDoesNotStopTheSend() {
        val t = reduce(VoiceState.Sending(sam, draft, announced = true), Back)
        assertEquals(VoiceState.ChoosePerson, t.state)
        assertTrue(t.effects.isEmpty())
    }

    @Test fun anAnswerForSomeoneElseChangesNothing() {
        val s = VoiceState.Sending(sam, draft)
        assertEquals(Transition(s), reduce(s, SendUpdated("jo", view(SendStatus.Sent))))
    }

    @Test fun sentGoesBackToThePeopleOnDoneOrBack() {
        for (e in listOf(Done, Back)) assertEquals(VoiceState.ChoosePerson, reduce(VoiceState.Sent(sam), e).state)
    }

    @Test fun backFromNotSentKeepsTheMessage() {
        val t = reduce(VoiceState.NotSent(sam, draft, SendProblem.NoSignal, 1), Back)
        assertEquals(VoiceState.ChoosePerson, t.state)
        assertTrue(t.effects.none { it is VoiceEffect.DeleteDraft })
    }

    @Test fun aLateAnswerUpdatesTheNotConfirmedScreenWithoutSendingAgain() {
        val waiting = VoiceState.NotSent(sam, draft, SendProblem.NotConfirmed, 0)
        val sent = reduce(waiting, SendUpdated("sam", view(SendStatus.Sent)))
        assertEquals(VoiceState.Sent(sam), sent.state)
        assertTrue(sends(sent).isEmpty())
        val failed = reduce(waiting, SendUpdated("sam", notSent(SendProblem.NoSignal)))
        assertEquals(VoiceState.NotSent(sam, draft, SendProblem.NoSignal, 1), failed.state)
        assertTrue(sends(failed).isEmpty())
        assertEquals(Transition(waiting), reduce(waiting, SendUpdated("jo", view(SendStatus.Sent))))
        assertEquals(Transition(waiting), reduce(waiting, SendUpdated("sam", notSent(SendProblem.NotConfirmed, failures = 0))))
    }

    @Test fun anUnconfirmedAttemptStillShowsTryAgainWhenOpenedAgain() {
        val chosen = reduce(VoiceState.ChoosePerson, Choose(sam, draft,
            notSent(SendProblem.NotConfirmed, seen = true)))
        assertEquals(VoiceState.NotSent(sam, draft, SendProblem.NotConfirmed, 1), chosen.state)
        assertTrue(sends(chosen).isEmpty())
    }

    @Test fun listenOnNotSentPlaysTheMessage() {
        val t = reduce(VoiceState.NotSent(sam, draft, SendProblem.OtherError, 1), Play)
        assertEquals(VoiceState.Review(sam, draft, playing = true), t.state)
        assertEquals(listOf(VoiceEffect.StartPlayback("sam")), t.effects)
    }

    @Test fun choosingAPersonShowsWhereTheirSendIs() {
        val people = VoiceState.ChoosePerson
        assertEquals(VoiceState.Sending(sam, draft, announced = true),
            reduce(people, Choose(sam, draft, view(SendStatus.Sending))).state)
        assertEquals(VoiceState.Sent(sam), reduce(people, Choose(sam, null, view(SendStatus.Sent))).state)
        assertEquals(VoiceState.NotSent(sam, draft, SendProblem.NotConfirmed, 1),
            reduce(people, Choose(sam, draft, notSent(SendProblem.NotConfirmed))).state)
        // Already shown once: the message is an unsent message again (V9).
        assertEquals(VoiceState.Review(sam, draft, notice = VoiceNotice.Unsent),
            reduce(people, Choose(sam, draft, notSent(SendProblem.OtherError, seen = true))).state)
        // Sent and already seen, or a new recording after a send: the usual screens.
        assertEquals(VoiceState.Person(sam), reduce(people, Choose(sam, null, view(SendStatus.Sent, seen = true))).state)
        assertEquals(VoiceState.Review(sam, draft, notice = VoiceNotice.Unsent),
            reduce(people, Choose(sam, draft, view(SendStatus.Sent))).state)
        // A send whose message is gone can't be tried again: the person's screen.
        assertEquals(VoiceState.Person(sam), reduce(people, Choose(sam, null, notSent(SendProblem.OtherError))).state)
    }

    // ---- Controller ----

    private class Recorder : VoiceRecorder {
        var lastMaxMs = 0L
        var running = false
        override fun silent() = false
        override fun start(file: File, format: VoiceFormat, maxMs: Long, listener: VoiceRecorder.Listener) {
            lastMaxMs = maxMs; file.writeBytes(format.sample()); running = true; listener.onCaptureStarted()
        }
        override fun stop(): Boolean { running = false; return true }
    }
    private class Player : VoicePlayer {
        override fun play(file: File, onDone: () -> Unit, onError: () -> Unit) {}
        override fun stop() {}
    }
    private class Gate : VoiceAudioGate {
        override fun acquire() = true
        override fun release() {}
    }
    private class Speaker : VoiceSpeaker {
        val spoken = mutableListOf<String>()
        override fun speak(prompt: VoicePrompt, person: VoicePerson?) { spoken += "$prompt ${person?.name}" }
        override fun stop() {}
    }
    private class Sender : VoiceSendPort {
        val sent = mutableListOf<String>()
        val sentTo = mutableListOf<String>()
        val views = mutableMapOf<String, SendView>()
        val seen = mutableListOf<String>()
        var answer: SendView = SendView(SendStatus.Sending, false, 0)
        var fail: RuntimeException? = null
        override fun send(person: VoicePerson, number: String): SendView {
            sent += person.id
            sentTo += number
            fail?.let { throw it }
            views[person.id] = answer
            return answer
        }
        override fun view(personId: String) = views[personId]
        override fun seen(personId: String) { seen += personId; views[personId]?.let { views[personId] = it.copy(seen = true) } }
    }

    private var now = 1_000_000L
    private val recorder = Recorder()
    private val speaker = Speaker()
    private val sender = Sender()
    private val store by lazy { VoiceDraftStore(tmp.newFolder()) { now } }

    private fun controller(sender: VoiceSendPort? = this.sender, people: List<VoicePerson> = listOf(sam, jo, alex),
        cap: () -> Long = { 120_000L }) = VoicePracticeController(recorder, Player(), store, MicAccess { null }, speaker,
        Gate(), { now }, sender = sender, people = people, recordCapMs = cap,
        numbers = { id -> numbersFail?.let { throw it }; numbers[id] }).also { it.open() }

    /** VoiceSender on a confirmed Sent: the record says Sent and LMNOPhone's own draft is deleted. */
    private fun sentLikeVoiceSender(personId: String) {
        sender.views[personId] = SendView(SendStatus.Sent, false, 0)
        store.delete(personId)
    }

    private fun VoicePracticeController.recorded(person: VoicePerson) {
        dispatch(Choose(person)); dispatch(Record); now += 2_000; dispatch(Stop)
        assertTrue(state is VoiceState.Review)
    }

    @Test fun sendIsIgnoredWhenRealSendingIsOff() {
        val c = controller(sender = null)
        c.recorded(sam)
        val before = c.state
        c.dispatch(Send)
        assertSame(before, c.state)
        assertFalse(c.canSend(sam))
    }

    @Test fun sendIsIgnoredForSomeoneWithNoNumber() {
        val c = controller()
        c.recorded(alex)
        c.dispatch(Send)
        assertTrue(c.state is VoiceState.Review)
        assertTrue(sender.sent.isEmpty())
        assertFalse(c.canSend(alex))
        assertTrue(c.canSend(sam))
    }

    @Test fun sendHandsOverOnceAndShowsTheAnswerWhenItComes() {
        val c = controller()
        c.recorded(sam)
        speaker.spoken.clear()
        c.dispatch(Send)
        assertEquals(listOf("sam"), sender.sent)
        assertEquals(VoiceState.Sending(sam, VoiceDraft("sam", store.draft("sam")!!.savedAtMs), announced = true), c.state)
        assertEquals(listOf("Sending Sam"), speaker.spoken)
        c.dispatch(Send)
        assertEquals("a second tap while sending does nothing", listOf("sam"), sender.sent)
        sender.views["sam"] = SendView(SendStatus.Sent, false, 0)
        c.sendChanged("sam")
        assertEquals(VoiceState.Sent(sam), c.state)
        assertEquals(listOf("sam"), sender.seen)
        assertEquals(listOf("sam"), sender.sent)
    }

    @Test fun theNumberIsLookedUpWhenSendIsTappedNotWhenThePersonWasChosen() {
        val c = controller()
        c.recorded(sam)
        numbers["sam"] = "555-0102"
        c.dispatch(Send)
        assertEquals(listOf("555-0102"), sender.sentTo)
    }

    @Test fun aNumberRemovedAfterChoosingIsNeverSentTo() {
        val c = controller()
        c.recorded(sam)
        numbers.remove("sam")
        c.dispatch(Send)
        assertTrue(c.state is VoiceState.Review)
        assertTrue(sender.sent.isEmpty())
        assertFalse(c.canSend(sam))
    }

    @Test fun aNumberLookupThatFailsMeansNoSendAndNoCrash() {
        val c = controller()
        c.recorded(sam)
        numbersFail = java.io.IOException("family list unreadable")
        assertFalse(c.canSend(sam))
        c.dispatch(Send)
        assertTrue(c.state is VoiceState.Review)
        assertTrue(sender.sent.isEmpty())
    }

    @Test fun anAnswerForSomeoneNotOnScreenWaitsForNextTime() {
        val c = controller()
        c.recorded(sam); c.dispatch(Send); c.dispatch(Back)
        sentLikeVoiceSender("sam")
        c.sendChanged("sam")
        assertEquals(VoiceState.ChoosePerson, c.state)
        assertTrue(sender.seen.isEmpty())
        c.dispatch(Choose(sam))
        assertEquals(VoiceState.Sent(sam), c.state)
    }

    @Test fun aSenderThatThrowsIsNotSent() {
        sender.fail = IllegalStateException("boom")
        val c = controller()
        c.recorded(sam); c.dispatch(Send)
        val s = c.state as VoiceState.NotSent
        assertEquals(SendProblem.OtherError, s.problem)
    }

    @Test fun noSignalSkipsSendingAndSaysSo() {
        sender.answer = SendView(SendStatus.NotSent(SendProblem.NoSignal), false, 1)
        val c = controller()
        c.recorded(sam); speaker.spoken.clear(); c.dispatch(Send)
        assertEquals(SendProblem.NoSignal, (c.state as VoiceState.NotSent).problem)
        assertEquals(listOf("NoSignal Sam"), speaker.spoken)
        c.dispatch(TryAgain)
        assertEquals(listOf("sam", "sam"), sender.sent)
    }

    @Test fun reviewWordsOfferSendOnlyWhenSendIsShown() {
        val c = controller()
        c.recorded(sam)
        assertEquals("ReviewSend Sam", speaker.spoken.last())
        c.dispatch(Back); c.recorded(alex)
        assertEquals("Review Alex", speaker.spoken.last())
        c.dispatch(Back); c.dispatch(Choose(sam))
        assertEquals("UnsentSend Sam", speaker.spoken.last())
    }

    @Test fun theIntroSaysPracticeOnlyWhenNobodyCanBeSentTo() {
        controller()
        assertEquals("ChooseSend null", speaker.spoken.first())
        speaker.spoken.clear()
        controller(sender = null)
        assertEquals("Intro null", speaker.spoken.first())
        speaker.spoken.clear()
        controller(people = listOf(alex))
        assertEquals("Intro null", speaker.spoken.first())
    }

    @Test fun recordingStopsAtTheCarrierCapOnlyForSomeoneWhoCanBeSentTo() {
        controller(cap = { 30_000L }).apply { dispatch(Choose(sam)); dispatch(Record) }
        assertEquals(30_000L, recorder.lastMaxMs)
        controller(cap = { 30_000L }).apply { dispatch(Choose(alex)); dispatch(Record) }
        assertEquals(120_000L, recorder.lastMaxMs)
        controller(cap = { 500_000L }).apply { dispatch(Choose(jo)); dispatch(Record) }
        assertEquals(120_000L, recorder.lastMaxMs)
    }

    /** 0 to MediaRecorder means no limit; the send is refused as too big instead (never cut silently). */
    @Test fun aCapTooSmallForAnyMessageNeverReachesTheRecorderAsZero() {
        controller(cap = { 0L }).apply { dispatch(Choose(sam)); dispatch(Record) }
        assertEquals(120_000L, recorder.lastMaxMs)
        controller(cap = { throw IllegalStateException() }).apply { dispatch(Choose(sam)); dispatch(Record) }
        assertEquals(120_000L, recorder.lastMaxMs)
    }

    @Test fun aSendScreenComesBackAfterAConfigurationChange() {
        val c = controller()
        c.recorded(sam); c.dispatch(Send)
        val place = c.place()
        assertEquals(VoicePlace("sam", VoicePlace.Screen.Send), place)
        sentLikeVoiceSender("sam")
        val again = controller()
        speaker.spoken.clear()
        again.open(place)
        assertEquals(VoiceState.Sent(sam), again.state)
        assertTrue("a restored screen says nothing again", speaker.spoken.isEmpty())
        assertEquals(listOf("sam"), sender.sent)
    }

    @Test fun choosingSomeoneWhoCantBeSentToNeverAsksTheSender() {
        val c = controller()
        sender.views["alex"] = SendView(SendStatus.Sent, false, 0)
        c.dispatch(Choose(alex))
        assertEquals(VoiceState.Person(alex), c.state)
    }
}
