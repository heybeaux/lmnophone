// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import org.plainphone.device.voice.replies.ReplyClip
import org.plainphone.device.voice.send.SendProblem
import org.plainphone.device.voice.send.SendStatus
import org.plainphone.device.voice.send.SendView

/**
 * Voice messages. Slice 1 (PLA-457): a local practice. Record, stop, listen, record again, delete.
 * Slice 2a (PLA-499): Send on the Review screen. [VoiceEffect.SendDraft] is emitted only for an explicit
 * [VoiceEvent.Send] on Review or [VoiceEvent.TryAgain] on Not sent: never by itself, never again on its own.
 * The controller drops both unless real sending is on and the person has a number, so the practice still sends nothing.
 */
/** Never carries a phone number: the sender looks it up by [id] when Send is tapped ([VoiceNumbers]). */
data class VoicePerson(val id: String, val name: String, val spokenName: String = name) {
    override fun toString() = "VoicePerson(redacted)"
}



data class VoiceDraft(val personId: String, val savedAtMs: Long)

/**
 * Where the practice was, kept across a configuration change such as text size (PLA-479). Ids and enums
 * only: the take itself stays in [VoiceDraftStore]. Recording and Saving are never a place: onStop has
 * already stopped the recorder, so they show as the Review or Person screen they ended on. The people
 * list is [People], with no person (PLA-484).
 */
data class VoicePlace(val personId: String?, val screen: Screen, val notice: VoiceNotice? = null,
    val problem: MicProblem? = null) {
    enum class Screen { People, Person, Review, MicOff, Send }

    companion object {
        val People = VoicePlace(null, Screen.People)
    }
}

enum class Interruption { Call, AudioFocusLost, AppStopped, ScreenOff }
enum class MicProblem { PermissionRefused, PrivacyToggle, RecorderError }
enum class VoiceNotice { MaxLength, Interrupted, NothingRecorded, Deleted, CouldNotPlay, MicBusy, Unsent }

/** What the phone says aloud (UX doc V1-V13, practice wording). The host maps each to a string. */
enum class VoicePrompt { Intro, Name, Person, Review, Unsent, TooShort, MaxLength, Busy, Deleted, MicOff, MicNotAllowed,
    MicError, CouldNotPlay, OpeningMessages, MessagesUnavailable,
    /** With Send shown (PLA-499): the controller picks these instead of Intro, Review, MaxLength and Unsent. */
    ChooseSend, ReviewSend, MaxLengthSend, UnsentSend,
    Sending, Sent, NotSent, NotSentAgain, NoSignal, NotConfirmed,
    /** A family person's voice reply (PLA-527, UX V1, V2, V12a): "{person} sent you a message." on the people list. */
    NewFrom, PersonNew, Reply, ReplyNext, ReplyEnd }

/**
 * A family person's unplayed voice replies on their page (PLA-527, UX V12a), oldest first. [index] is the clip
 * playing or next; [completed] counts clips heard to the end in this visit. When every clip is heard the button
 * is Play again, which replays the last one. Never starts by itself.
 */
data class ReplyListen(val queue: List<ReplyClip>, val index: Int = 0, val playing: Boolean = false, val completed: Int = 0) {
    val finished get() = queue.isNotEmpty() && completed >= queue.size
}

sealed interface VoiceState {
    val person: VoicePerson? get() = null

    data object ChoosePerson : VoiceState
    /** [listen]: the person has voice replies to hear (PLA-527); null when there are none. */
    data class Person(override val person: VoicePerson, val notice: VoiceNotice? = null,
        val listen: ReplyListen? = null) : VoiceState
    /** Shown before the recorder starts and for as long as it runs. */
    data class Recording(override val person: VoicePerson, val previous: VoiceDraft?) : VoiceState
    /** The recorder has been told to stop; waits for [VoiceEvent.RecordingFinished]. */
    data class Saving(override val person: VoicePerson, val previous: VoiceDraft?,
        val notice: VoiceNotice? = null, val problem: MicProblem? = null) : VoiceState
    data class Review(override val person: VoicePerson, val draft: VoiceDraft,
        val playing: Boolean = false, val notice: VoiceNotice? = null) : VoiceState
    data class MicUnavailable(override val person: VoicePerson, val problem: MicProblem,
        val previous: VoiceDraft?) : VoiceState
    /** V5: handed to the phone's messaging, no answer yet. [announced]: "Sending to {person}" was said. */
    data class Sending(override val person: VoicePerson, val draft: VoiceDraft, val announced: Boolean = false) : VoiceState
    /** V6: the network accepted the message. Never "delivered" or "heard". The draft is gone. */
    data class Sent(override val person: VoicePerson) : VoiceState
    /** V7/V8, or not confirmed: the message is still on this phone. [failures] counts tries that didn't send. */
    data class NotSent(override val person: VoicePerson, val draft: VoiceDraft, val problem: SendProblem,
        val failures: Int) : VoiceState
}

sealed interface VoiceEvent {
    /** [existing] is the person's kept draft; the controller fills it in. */
    data class Choose(val person: VoicePerson, val existing: VoiceDraft? = null, val send: SendView? = null,
        /** The person's unplayed voice replies, oldest first; the controller fills them in (PLA-527). */
        val replies: List<ReplyClip> = emptyList()) : VoiceEvent
    data object Record : VoiceEvent
    data object Stop : VoiceEvent
    data object Play : VoiceEvent
    /** Listen / Stop listening / Play again on a person's page (PLA-527, UX V12a). The only way a reply plays. */
    data object Listen : VoiceEvent
    data object PlaybackEnded : VoiceEvent
    data object PlaybackFailed : VoiceEvent
    data object ReplyUnavailable : VoiceEvent
    data object RecordAgain : VoiceEvent
    data object Delete : VoiceEvent
    data object Back : VoiceEvent
    data object MaxLengthReached : VoiceEvent
    data class Interrupted(val cause: Interruption) : VoiceEvent
    data class MicUnavailable(val problem: MicProblem) : VoiceEvent
    /** The recorder refused to start (for example another app holds the microphone). */
    data object CouldNotStart : VoiceEvent
    /**
     * Result of [VoiceEffect.FinishRecording]: the kept draft, or null when nothing usable was saved.
     * [silenced]: the take was silent (privacy toggle) and was not kept, even if Android hadn't said so yet.
     */
    data class RecordingFinished(val draft: VoiceDraft?, val silenced: Boolean = false) : VoiceEvent
    /** The Send button (V4/V9). The only first send. */
    data object Send : VoiceEvent
    /** Try again (V7/V8). The only resend. */
    data object TryAgain : VoiceEvent
    /** Done on Sent (V6), or its 10-second return to the people. */
    data object Done : VoiceEvent
    /** Where [personId]'s send is now, from the sender. Never sends. */
    data class SendUpdated(val personId: String, val view: SendView) : VoiceEvent
}

sealed interface VoiceEffect {
    data class Speak(val prompt: VoicePrompt, val person: VoicePerson) : VoiceEffect
    data class StartRecording(val personId: String) : VoiceEffect
    /** Stop the recorder. [keep] false discards the take; an earlier draft is always kept. */
    data class FinishRecording(val personId: String, val keep: Boolean) : VoiceEffect
    data class StartPlayback(val personId: String) : VoiceEffect
    data object StopPlayback : VoiceEffect
    data class DeleteDraft(val personId: String) : VoiceEffect
    /** Leave voice messages (back to More). */
    data object Leave : VoiceEffect
    /** Hand the person's draft to the phone's messaging, once. */
    data class SendDraft(val person: VoicePerson) : VoiceEffect
    /** The send's outcome was shown. */
    data class SendSeen(val personId: String) : VoiceEffect
    /** Say [prompt], then play [clip] from the messaging store (PLA-527). Only after [VoiceEvent.Listen]. */
    data class PlayReply(val person: VoicePerson, val clip: ReplyClip, val prompt: VoicePrompt) : VoiceEffect
    /** [clip] was heard to the end: no longer new. */
    data class ReplyPlayed(val personId: String, val clip: ReplyClip) : VoiceEffect
}

data class Transition(val state: VoiceState, val effects: List<VoiceEffect> = emptyList())

object VoicePractice {
    fun reduce(s: VoiceState, e: VoiceEvent): Transition = when (s) {
        VoiceState.ChoosePerson -> when (e) {
            is VoiceEvent.Choose -> chosen(e.person, e.existing, e.send) ?: e.existing?.let {
                Transition(VoiceState.Review(e.person, it, notice = VoiceNotice.Unsent), speak(VoicePrompt.Unsent, e.person))
            } ?: if (e.replies.isNotEmpty()) Transition(VoiceState.Person(e.person, listen = ReplyListen(e.replies)),
                speak(VoicePrompt.PersonNew, e.person))
            else Transition(VoiceState.Person(e.person), speak(VoicePrompt.Person, e.person))
            VoiceEvent.Back -> Transition(s, listOf(VoiceEffect.Leave))
            else -> Transition(s)
        }
        is VoiceState.Person -> person(s, e)
        is VoiceState.Recording -> {
            fun finish(notice: VoiceNotice?) = Transition(VoiceState.Saving(s.person, s.previous, notice),
                listOf(VoiceEffect.FinishRecording(s.person.id, keep = true)))
            when (e) {
                VoiceEvent.Stop, VoiceEvent.Back -> finish(null)
                VoiceEvent.MaxLengthReached -> finish(VoiceNotice.MaxLength)
                is VoiceEvent.Interrupted -> finish(VoiceNotice.Interrupted)
                is VoiceEvent.MicUnavailable -> Transition(VoiceState.Saving(s.person, s.previous, problem = e.problem),
                    listOf(VoiceEffect.FinishRecording(s.person.id, keep = false)))
                VoiceEvent.CouldNotStart -> Transition(
                    s.previous?.let { VoiceState.Review(s.person, it, notice = VoiceNotice.MicBusy) }
                        ?: VoiceState.Person(s.person, VoiceNotice.MicBusy),
                    listOf(VoiceEffect.FinishRecording(s.person.id, keep = false), VoiceEffect.Speak(VoicePrompt.Busy, s.person)))
                else -> Transition(s)
            }
        }
        is VoiceState.Saving -> when (e) {
            is VoiceEvent.RecordingFinished -> {
                val draft = e.draft ?: s.previous
                val p = s.person
                when {
                    s.problem != null -> micUnavailable(p, s.problem, s.previous)
                    e.silenced -> micUnavailable(p, MicProblem.PrivacyToggle, s.previous)
                    draft == null -> Transition(VoiceState.Person(p, VoiceNotice.NothingRecorded), speak(VoicePrompt.TooShort, p))
                    e.draft == null -> Transition(VoiceState.Review(p, draft, notice = VoiceNotice.NothingRecorded),
                        speak(VoicePrompt.TooShort, p))
                    // After an interruption the person may not be looking or listening: stay quiet (UX V11).
                    s.notice == VoiceNotice.Interrupted -> Transition(VoiceState.Review(p, draft, notice = s.notice))
                    s.notice == VoiceNotice.MaxLength -> Transition(VoiceState.Review(p, draft, notice = s.notice),
                        speak(VoicePrompt.MaxLength, p))
                    else -> Transition(VoiceState.Review(p, draft, notice = s.notice), speak(VoicePrompt.Review, p))
                }
            }
            else -> Transition(s)
        }
        is VoiceState.Review -> {
            val stop = if (s.playing) listOf(VoiceEffect.StopPlayback) else emptyList()
            when (e) {
                VoiceEvent.Play -> if (s.playing) Transition(s) else Transition(s.copy(playing = true, notice = null),
                    listOf(VoiceEffect.StartPlayback(s.person.id)))
                VoiceEvent.Stop -> if (s.playing) Transition(s.copy(playing = false), stop) else Transition(s)
                is VoiceEvent.Interrupted -> Transition(s.copy(playing = false), stop)
                VoiceEvent.PlaybackEnded -> Transition(s.copy(playing = false))
                VoiceEvent.PlaybackFailed -> Transition(s.copy(playing = false, notice = VoiceNotice.CouldNotPlay),
                    speak(VoicePrompt.CouldNotPlay, s.person))
                VoiceEvent.RecordAgain, VoiceEvent.Record -> record(s.person, s.draft, stop)
                VoiceEvent.Delete -> Transition(VoiceState.Person(s.person, VoiceNotice.Deleted),
                    listOf(VoiceEffect.StopPlayback, VoiceEffect.DeleteDraft(s.person.id),
                        VoiceEffect.Speak(VoicePrompt.Deleted, s.person)))
                VoiceEvent.Back -> Transition(VoiceState.ChoosePerson, listOf(VoiceEffect.StopPlayback))
                is VoiceEvent.MicUnavailable -> micUnavailable(s.person, e.problem, s.draft, stop)
                VoiceEvent.Send -> Transition(VoiceState.Sending(s.person, s.draft), stop + VoiceEffect.SendDraft(s.person))
                else -> Transition(s)
            }
        }
        is VoiceState.Sending -> when {
            // Leaving doesn't stop the send; the answer shows next time (UX V5).
            e == VoiceEvent.Back -> Transition(VoiceState.ChoosePerson)
            e is VoiceEvent.SendUpdated && e.personId == s.person.id -> when (val status = e.view.status) {
                SendStatus.Sending -> if (s.announced) Transition(s)
                    else Transition(s.copy(announced = true), speak(VoicePrompt.Sending, s.person))
                SendStatus.Sent -> sent(s.person)
                is SendStatus.NotSent -> notSent(s.person, s.draft, status.problem, e.view.failures)
            }
            else -> Transition(s)
        }
        is VoiceState.Sent -> when (e) {
            VoiceEvent.Done, VoiceEvent.Back -> Transition(VoiceState.ChoosePerson)
            else -> Transition(s)
        }
        is VoiceState.NotSent -> when (e) {
            VoiceEvent.TryAgain -> Transition(VoiceState.Sending(s.person, s.draft), listOf(VoiceEffect.SendDraft(s.person)))
            VoiceEvent.Play -> Transition(VoiceState.Review(s.person, s.draft, playing = true),
                listOf(VoiceEffect.StartPlayback(s.person.id)))
            VoiceEvent.Back -> Transition(VoiceState.ChoosePerson)
            is VoiceEvent.SendUpdated -> if (e.personId != s.person.id) Transition(s) else when (val status = e.view.status) {
                SendStatus.Sending -> Transition(s)
                SendStatus.Sent -> sent(s.person)
                is SendStatus.NotSent -> if (status.problem == s.problem && e.view.failures == s.failures) Transition(s)
                    else notSent(s.person, s.draft, status.problem, e.view.failures)
            }
            else -> Transition(s)
        }
        is VoiceState.MicUnavailable -> when (e) {
            VoiceEvent.Record, VoiceEvent.RecordAgain -> record(s.person, s.previous)
            VoiceEvent.Back -> Transition(s.previous?.let { VoiceState.Review(s.person, it) } ?: VoiceState.Person(s.person))
            else -> Transition(s)
        }
    }

    private fun speak(prompt: VoicePrompt, person: VoicePerson) = listOf(VoiceEffect.Speak(prompt, person))

    /** A person's page. Leaving, recording or any interruption stops a reply at once; it stays new (UX V12a). */
    private fun person(s: VoiceState.Person, e: VoiceEvent): Transition {
        val listen = s.listen
        val playing = listen?.playing == true
        val stop = if (playing) listOf(VoiceEffect.StopPlayback) else emptyList()
        val quiet = s.copy(listen = listen?.copy(playing = false))
        return when (e) {
            VoiceEvent.Record -> record(s.person, null, stop)
            VoiceEvent.Back -> Transition(VoiceState.ChoosePerson, stop)
            is VoiceEvent.MicUnavailable -> micUnavailable(s.person, e.problem, null, stop)
            VoiceEvent.Listen -> when {
                listen == null || listen.queue.isEmpty() -> Transition(s)
                playing -> Transition(quiet, stop)
                else -> {
                    // Play again replays the last one heard; otherwise the first not yet heard.
                    val index = if (listen.finished) listen.queue.lastIndex else listen.index.coerceIn(0, listen.queue.lastIndex)
                    Transition(s.copy(listen = listen.copy(index = index, playing = true)),
                        listOf(VoiceEffect.PlayReply(s.person, listen.queue[index], VoicePrompt.Reply)))
                }
            }
            VoiceEvent.Stop, is VoiceEvent.Interrupted -> if (playing) Transition(quiet, stop) else Transition(s)
            // Unreadable or gone: stop quietly. The person can still open Messages (UX V12).
            VoiceEvent.PlaybackFailed -> if (playing) Transition(quiet) else Transition(s)
            VoiceEvent.ReplyUnavailable -> if (!playing || listen == null) Transition(s) else {
                val remaining = listen.queue.filterIndexed { index, _ -> index != listen.index }
                if (!listen.finished && listen.index < remaining.size) Transition(
                    s.copy(listen = listen.copy(queue = remaining)),
                    listOf(VoiceEffect.StopPlayback, VoiceEffect.PlayReply(s.person, remaining[listen.index], VoicePrompt.ReplyNext)))
                else Transition(s.copy(listen = remaining.takeIf { it.isNotEmpty() }?.let {
                    listen.copy(queue = it, index = it.lastIndex, playing = false, completed = it.size)
                }), listOf(VoiceEffect.StopPlayback) + if (remaining.isNotEmpty()) speak(VoicePrompt.ReplyEnd, s.person) else emptyList())
            }
            VoiceEvent.PlaybackEnded -> if (!playing || listen == null) Transition(s) else {
                val heard = VoiceEffect.ReplyPlayed(s.person.id, listen.queue[listen.index])
                val next = listen.index + 1
                if (!listen.finished && next < listen.queue.size) Transition(
                    s.copy(listen = listen.copy(index = next, completed = maxOf(listen.completed, next))),
                    listOf(heard, VoiceEffect.PlayReply(s.person, listen.queue[next], VoicePrompt.ReplyNext)))
                else Transition(s.copy(listen = listen.copy(playing = false, completed = listen.queue.size)),
                    listOf(heard, VoiceEffect.Speak(VoicePrompt.ReplyEnd, s.person)))
            }
            else -> Transition(s)
        }
    }

    /**
     * Opening a person whose send is in progress, or whose outcome hasn't been shown yet. Null: the usual
     * screens (a seen outcome leaves an unsent message, V9). Never sends.
     */
    private fun chosen(person: VoicePerson, draft: VoiceDraft?, send: SendView?): Transition? {
        val status = send?.status ?: return null
        return when {
            status == SendStatus.Sending && draft != null ->
                Transition(VoiceState.Sending(person, draft, announced = true), speak(VoicePrompt.Sending, person))
            status == SendStatus.NotSent(SendProblem.NotConfirmed) && draft != null ->
                notSent(person, draft, SendProblem.NotConfirmed, send.failures)
            send.seen -> null
            status == SendStatus.Sent && draft == null -> sent(person)
            status is SendStatus.NotSent && draft != null -> notSent(person, draft, status.problem, send.failures)
            else -> null
        }
    }

    private fun sent(person: VoicePerson) =
        Transition(VoiceState.Sent(person), listOf(VoiceEffect.Speak(VoicePrompt.Sent, person), VoiceEffect.SendSeen(person.id)))

    private fun notSent(person: VoicePerson, draft: VoiceDraft, problem: SendProblem, failures: Int): Transition {
        val prompt = when (problem) {
            SendProblem.NoSignal -> VoicePrompt.NoSignal
            SendProblem.NotConfirmed -> VoicePrompt.NotConfirmed
            else -> if (failures >= 2) VoicePrompt.NotSentAgain else VoicePrompt.NotSent
        }
        return Transition(VoiceState.NotSent(person, draft, problem, failures),
            listOf(VoiceEffect.Speak(prompt, person), VoiceEffect.SendSeen(person.id)))
    }

    private fun micUnavailable(person: VoicePerson, problem: MicProblem, previous: VoiceDraft?,
        before: List<VoiceEffect> = emptyList()) = Transition(VoiceState.MicUnavailable(person, problem, previous),
        before + VoiceEffect.Speak(when (problem) {
            MicProblem.PermissionRefused -> VoicePrompt.MicNotAllowed
            MicProblem.PrivacyToggle -> VoicePrompt.MicOff
            MicProblem.RecorderError -> VoicePrompt.MicError
        }, person))

    private fun record(person: VoicePerson, previous: VoiceDraft?, before: List<VoiceEffect> = emptyList()) =
        Transition(VoiceState.Recording(person, previous), before + VoiceEffect.StartRecording(person.id))
}
