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
enum class VoiceNotice { MaxLength, Interrupted, NothingRecorded, Deleted, CouldNotPlay }

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
    /** Result of [VoiceEffect.FinishRecording]: the kept draft, or null when nothing usable was saved. */
    data class RecordingFinished(val draft: VoiceDraft?) : VoiceEvent
}

sealed interface VoiceEffect {
    data class SpeakName(val person: VoicePerson) : VoiceEffect
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
            is VoiceEvent.Choose -> Transition(
                e.existing?.let { VoiceState.Review(e.person, it) } ?: VoiceState.Person(e.person),
                listOf(VoiceEffect.SpeakName(e.person)))
            VoiceEvent.Back -> Transition(s, listOf(VoiceEffect.Leave))
            else -> Transition(s)
        }
        is VoiceState.Person -> when (e) {
            VoiceEvent.Record -> record(s.person, null)
            VoiceEvent.Back -> Transition(VoiceState.ChoosePerson)
            is VoiceEvent.MicUnavailable -> Transition(VoiceState.MicUnavailable(s.person, e.problem, null))
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
                else -> Transition(s)
            }
        }
        is VoiceState.Saving -> when (e) {
            is VoiceEvent.RecordingFinished -> {
                val draft = e.draft ?: s.previous
                Transition(when {
                    s.problem != null -> VoiceState.MicUnavailable(s.person, s.problem, s.previous)
                    draft == null -> VoiceState.Person(s.person, VoiceNotice.NothingRecorded)
                    e.draft == null -> VoiceState.Review(s.person, draft, notice = VoiceNotice.NothingRecorded)
                    else -> VoiceState.Review(s.person, draft, notice = s.notice)
                })
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
                VoiceEvent.PlaybackFailed -> Transition(s.copy(playing = false, notice = VoiceNotice.CouldNotPlay))
                VoiceEvent.RecordAgain, VoiceEvent.Record -> record(s.person, s.draft, stop)
                VoiceEvent.Delete -> Transition(VoiceState.Person(s.person, VoiceNotice.Deleted),
                    listOf(VoiceEffect.StopPlayback, VoiceEffect.DeleteDraft(s.person.id)))
                VoiceEvent.Back -> Transition(VoiceState.ChoosePerson, listOf(VoiceEffect.StopPlayback))
                is VoiceEvent.MicUnavailable -> Transition(VoiceState.MicUnavailable(s.person, e.problem, s.draft), stop)
                else -> Transition(s)
            }
        }
        is VoiceState.MicUnavailable -> when (e) {
            VoiceEvent.Record, VoiceEvent.RecordAgain -> record(s.person, s.previous)
            VoiceEvent.Back -> Transition(s.previous?.let { VoiceState.Review(s.person, it) } ?: VoiceState.Person(s.person))
            else -> Transition(s)
        }
    }

    private fun record(person: VoicePerson, previous: VoiceDraft?, before: List<VoiceEffect> = emptyList()) =
        Transition(VoiceState.Recording(person, previous), before + VoiceEffect.StartRecording(person.id))
}
