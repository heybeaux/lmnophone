// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

/**
 * Voice messages, slice 1 (PLA-457): a local practice. Record, stop, listen, record again, delete.
 * Nothing is ever sent: there is no sent state and no send effect. Transport is a later slice.
 */
data class VoicePerson(val id: String, val name: String)

/** Synthetic people for the practice. Never real names, photos or numbers. */
object VoicePeople {
    val practice = listOf(VoicePerson("sam", "Sam"), VoicePerson("jo", "Jo"), VoicePerson("alex", "Alex"))
}

data class VoiceDraft(val personId: String, val savedAtMs: Long)

enum class Interruption { Call, AudioFocusLost, AppStopped, ScreenOff }
enum class MicProblem { PermissionRefused, PrivacyToggle, RecorderError }
enum class VoiceNotice { MaxLength, Interrupted, NothingRecorded, Deleted, CouldNotPlay, MicBusy, Unsent }

/** What the phone says aloud (UX doc V1-V13, practice wording). The host maps each to a string. */
enum class VoicePrompt { Intro, Name, Person, Review, Unsent, TooShort, MaxLength, Busy, Deleted, MicOff, MicNotAllowed,
    MicError, CouldNotPlay }

sealed interface VoiceState {
    val person: VoicePerson? get() = null

    data object ChoosePerson : VoiceState
    data class Person(override val person: VoicePerson, val notice: VoiceNotice? = null) : VoiceState
    /** Shown before the recorder starts and for as long as it runs. */
    data class Recording(override val person: VoicePerson, val previous: VoiceDraft?) : VoiceState
    /** The recorder has been told to stop; waits for [VoiceEvent.RecordingFinished]. */
    data class Saving(override val person: VoicePerson, val previous: VoiceDraft?,
        val notice: VoiceNotice? = null, val problem: MicProblem? = null) : VoiceState
    data class Review(override val person: VoicePerson, val draft: VoiceDraft,
        val playing: Boolean = false, val notice: VoiceNotice? = null) : VoiceState
    data class MicUnavailable(override val person: VoicePerson, val problem: MicProblem,
        val previous: VoiceDraft?) : VoiceState
}

sealed interface VoiceEvent {
    /** [existing] is the person's kept draft; the controller fills it in. */
    data class Choose(val person: VoicePerson, val existing: VoiceDraft? = null) : VoiceEvent
    data object Record : VoiceEvent
    data object Stop : VoiceEvent
    data object Play : VoiceEvent
    data object PlaybackEnded : VoiceEvent
    data object PlaybackFailed : VoiceEvent
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
}

data class Transition(val state: VoiceState, val effects: List<VoiceEffect> = emptyList())

object VoicePractice {
    fun reduce(s: VoiceState, e: VoiceEvent): Transition = when (s) {
        VoiceState.ChoosePerson -> when (e) {
            is VoiceEvent.Choose -> e.existing?.let {
                Transition(VoiceState.Review(e.person, it, notice = VoiceNotice.Unsent), speak(VoicePrompt.Unsent, e.person))
            } ?: Transition(VoiceState.Person(e.person), speak(VoicePrompt.Person, e.person))
            VoiceEvent.Back -> Transition(s, listOf(VoiceEffect.Leave))
            else -> Transition(s)
        }
        is VoiceState.Person -> when (e) {
            VoiceEvent.Record -> record(s.person, null)
            VoiceEvent.Back -> Transition(VoiceState.ChoosePerson)
            is VoiceEvent.MicUnavailable -> micUnavailable(s.person, e.problem, null)
            else -> Transition(s)
        }
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
                else -> Transition(s)
            }
        }
        is VoiceState.MicUnavailable -> when (e) {
            VoiceEvent.Record, VoiceEvent.RecordAgain -> record(s.person, s.previous)
            VoiceEvent.Back -> Transition(s.previous?.let { VoiceState.Review(s.person, it) } ?: VoiceState.Person(s.person))
            else -> Transition(s)
        }
    }

    private fun speak(prompt: VoicePrompt, person: VoicePerson) = listOf(VoiceEffect.Speak(prompt, person))

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
