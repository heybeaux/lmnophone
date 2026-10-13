// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import java.io.File
import org.plainphone.device.voice.send.SendProblem
import org.plainphone.device.voice.send.SendStatus
import org.plainphone.device.voice.send.SendView
import org.plainphone.device.voice.send.VoiceNumber

/** Mono recorder. The host adapter wraps MediaRecorder; callbacks arrive on the main thread. */
interface VoiceRecorder {
    interface Listener {
        fun onMaxLength()
        fun onError()
        /** Android silenced this recording, for example with the microphone privacy toggle. */
        fun onSilenced()
        /** The recorder accepted [start] but could not open the microphone (for example it is busy). */
        fun onStartFailed()
        /** The microphone is capturing now: after setup and the start tone. Duration counts from here. */
        fun onCaptureStarted()
    }
    /** Starts recording into [file]. Throws if the microphone or file can't be opened. */
    fun start(file: File, format: VoiceFormat, maxMs: Long, listener: Listener)
    /**
     * Asked just before [stop] when the take would be kept: true when Android silenced this recording or
     * it has captured nothing but digital zero so far (the privacy toggle), even inside the silence window.
     * Never throws.
     */
    fun silent(): Boolean
    /** Stops and releases. True when the file was finalised with audio. Never throws. */
    fun stop(): Boolean
}

interface VoicePlayer {
    /** Throws if playback can't start. */
    fun play(file: File, onDone: () -> Unit, onError: () -> Unit)
    fun stop()
}

/**
 * Audio focus and the call state, owned by the host. Recording and playback both ask first, and the
 * host's focus-loss and call listeners interrupt either one ([VoicePracticeController.close]).
 */
interface VoiceAudioGate {
    /** True when this app may use audio now: no call is ringing or up, and audio focus was granted. */
    fun acquire(): Boolean
    /** Gives audio focus back. Safe to call when it is not held. */
    fun release()
}

/** Null when the microphone may be used now; the host tries the Device Owner grant first. */
fun interface MicAccess { fun problem(): MicProblem? }

/** Text-to-speech. [person] is null only for prompts about the whole practice. */
interface VoiceSpeaker {
    fun speak(prompt: VoicePrompt, person: VoicePerson?)
    /** The person asked for these words (the picture's "hear the name"): said even when TalkBack is on. */
    fun speakOnRequest(prompt: VoicePrompt, person: VoicePerson?) = speak(prompt, person)
    /** Silences speech at once, before the microphone opens or when the person taps. */
    fun stop()
}

/** The real sender (PLA-499, [org.plainphone.device.voice.send.VoiceSender]). Absent when real sending is off. */
interface VoiceSendPort {
    /** Hands the person's draft over once and says where that send is. Called only for an explicit Send or Try again. */
    fun send(person: VoicePerson): SendView
    fun view(personId: String): SendView?
    fun seen(personId: String)
}

/**
 * Runs [VoicePractice] and its effects. Not thread-safe: call from the main thread only.
 * The recorder runs only while [VoiceState.Recording] is the state, and every way out of it stops it.
 */
class VoicePracticeController(
    private val recorder: VoiceRecorder,
    private val player: VoicePlayer,
    private val store: VoiceDraftStore,
    private val mic: MicAccess,
    private val speaker: VoiceSpeaker,
    private val gate: VoiceAudioGate,
    private val clock: () -> Long,
    private val format: VoiceFormat = VoiceFormat.AacM4a,
    private val maxMs: Long = DEFAULT_MAX_MS,
    /** Null: real sending is off. Send and Try again are then ignored, and nothing is ever handed over. */
    private val sender: VoiceSendPort? = null,
    private val people: List<VoicePerson> = VoicePeople.practice,
    /** The longest message the carrier takes ([org.plainphone.device.voice.send.VoiceSendLimits.maxRecordMs]). */
    private val recordCapMs: () -> Long = { maxMs },
) {
    var state: VoiceState = VoiceState.ChoosePerson
        private set
    var onChange: (VoiceState) -> Unit = {}
    var onLeave: () -> Unit = {}

    private var recording: Take? = null

    private inner class Take : VoiceRecorder.Listener {
        var started = false
        /** When the microphone really started capturing; null until then. */
        var capturedAt: Long? = null
        private fun live() = recording === this && state is VoiceState.Recording
        override fun onCaptureStarted() { if (live() && capturedAt == null) capturedAt = clock() }
        override fun onMaxLength() { if (live()) dispatch(VoiceEvent.MaxLengthReached) }
        override fun onError() { if (live()) dispatch(VoiceEvent.MicUnavailable(MicProblem.RecorderError)) }
        override fun onSilenced() { if (live()) dispatch(VoiceEvent.MicUnavailable(MicProblem.PrivacyToggle)) }
        override fun onStartFailed() { if (live()) dispatch(VoiceEvent.CouldNotStart) }
    }

    /**
     * Shows the people list, or [place] when the screen is coming back after a configuration change.
     * A restored screen says nothing again and never starts the recorder or playback.
     */
    fun open(place: VoicePlace? = null) {
        close()
        safely { store.prune() }
        val restored = place?.let(::restore)
        state = restored ?: VoiceState.ChoosePerson
        onChange(state)
        // A new visit, or a place that is gone (an unknown person): the people list is introduced.
        if (restored == null) say(if (practice(null)) VoicePrompt.Intro else VoicePrompt.ChooseSend, null)
    }

    /** Where the practice is now, to come back to after a configuration change. */
    fun place(): VoicePlace = when (val s = state) {
        VoiceState.ChoosePerson -> VoicePlace.People
        is VoiceState.Person -> VoicePlace(s.person.id, VoicePlace.Screen.Person, s.notice)
        is VoiceState.Review -> VoicePlace(s.person.id, VoicePlace.Screen.Review, s.notice)
        is VoiceState.MicUnavailable -> VoicePlace(s.person.id, VoicePlace.Screen.MicOff, problem = s.problem)
        // Only if asked before onStop stopped the recorder: whatever take is kept, it was interrupted.
        is VoiceState.Recording -> VoicePlace(s.person.id, VoicePlace.Screen.Review, VoiceNotice.Interrupted)
        is VoiceState.Saving -> VoicePlace(s.person.id, VoicePlace.Screen.Review, s.notice ?: VoiceNotice.Interrupted)
        is VoiceState.Sending -> VoicePlace(s.person.id, VoicePlace.Screen.Send)
        is VoiceState.Sent -> VoicePlace(s.person.id, VoicePlace.Screen.Send)
        is VoiceState.NotSent -> VoicePlace(s.person.id, VoicePlace.Screen.Send)
    }

    /** The take is read again from the store: a pruned or deleted take shows the person's screen instead. */
    private fun restore(place: VoicePlace): VoiceState? {
        if (place.screen == VoicePlace.Screen.People) return VoiceState.ChoosePerson
        val person = people.firstOrNull { it.id == place.personId } ?: return null
        val draft = safeDraft(person.id)
        return when (place.screen) {
            VoicePlace.Screen.MicOff -> VoiceState.MicUnavailable(person, place.problem ?: MicProblem.RecorderError, draft)
            VoicePlace.Screen.Review -> draft?.let { VoiceState.Review(person, it, notice = place.notice) }
                ?: VoiceState.Person(person)
            VoicePlace.Screen.Person -> draft?.let { VoiceState.Review(person, it) } ?: VoiceState.Person(person, place.notice)
            VoicePlace.Screen.People -> VoiceState.ChoosePerson
            // Read again from the sender, the outcome shown as it was: never a new send.
            VoicePlace.Screen.Send -> VoicePractice.reduce(VoiceState.ChoosePerson, VoiceEvent.Choose(person, draft,
                if (canSend(person)) safeView(person.id)?.copy(seen = false) else null)).state
        }
    }

    /** Send is offered only when real sending is on and [person] has a number that can be sent to. */
    fun canSend(person: VoicePerson): Boolean = sender != null && person.phoneNumber?.let(VoiceNumber::normalize) != null

    /** True while no one on the list can be sent to: every screen keeps the practice band and words. */
    fun practice(person: VoicePerson? = state.person): Boolean = person?.let { !canSend(it) } ?: people.none(::canSend)

    /** The sender has news for [personId] (an answer, or a send no longer waited for). Never sends. */
    fun sendChanged(personId: String) {
        val person = state.person?.takeIf { it.id == personId && canSend(it) } ?: return
        safeView(person.id)?.let { dispatch(VoiceEvent.SendUpdated(person.id, it)) }
    }

    fun dispatch(event: VoiceEvent) {
        if ((event == VoiceEvent.Send || event == VoiceEvent.TryAgain) && state.person?.let(::canSend) != true) return
        val e = if (event is VoiceEvent.Choose && state == VoiceState.ChoosePerson)
            event.copy(existing = safeDraft(event.person.id),
                send = if (canSend(event.person)) safeView(event.person.id) else null) else event
        val before = state
        val t = VoicePractice.reduce(state, e)
        state = t.state
        // A prompt belongs to the screen it was said on: any tap or change (Listen, Back, Delete...) ends it.
        if (t.state != before || t.effects.isNotEmpty()) safely { speaker.stop() }
        if (!usesAudio(state)) safely { gate.release() }
        onChange(state)
        for (effect in t.effects) run(effect)
    }

    /**
     * Leaving the screen or an interruption (call, screen off, focus loss): stop recording (keeping the
     * take), playback and speech, and give audio focus back.
     */
    fun close(cause: Interruption = Interruption.AppStopped) {
        if (usesAudio(state)) dispatch(VoiceEvent.Interrupted(cause))
        stopPlayer()
        safely { speaker.stop() }
        safely { gate.release() }
    }

    private fun usesAudio(s: VoiceState) = s is VoiceState.Recording || (s as? VoiceState.Review)?.playing == true

    /** "Hear name" on a person's screen. */
    fun sayName() { state.person?.let { safely { speaker.speakOnRequest(VoicePrompt.Name, it) } } }

    private fun say(prompt: VoicePrompt, person: VoicePerson?) = safely { speaker.speak(prompt, person) }

    private fun run(effect: VoiceEffect) {
        when (effect) {
            is VoiceEffect.Speak -> say(words(effect.prompt, effect.person), effect.person)
            is VoiceEffect.StartRecording -> start(effect.personId)
            is VoiceEffect.FinishRecording -> {
                // A silent take (PLA-475): asked before stopping, so a Stop inside the silence window can't keep it.
                val silenced = effect.keep && recording?.started == true && try { recorder.silent() } catch (_: Exception) { false }
                dispatch(VoiceEvent.RecordingFinished(finish(effect.copy(keep = effect.keep && !silenced)), silenced))
            }
            is VoiceEffect.StartPlayback -> if (!acquire()) dispatch(VoiceEvent.PlaybackFailed) else try {
                player.play(store.file(effect.personId),
                    onDone = { if ((state as? VoiceState.Review)?.playing == true) dispatch(VoiceEvent.PlaybackEnded) },
                    onError = { if ((state as? VoiceState.Review)?.playing == true) dispatch(VoiceEvent.PlaybackFailed) })
            } catch (_: Exception) { dispatch(VoiceEvent.PlaybackFailed) }
            VoiceEffect.StopPlayback -> stopPlayer()
            is VoiceEffect.DeleteDraft -> try { store.delete(effect.personId) } catch (_: Exception) { }
            VoiceEffect.Leave -> onLeave()
            is VoiceEffect.SendDraft -> {
                val view = sender?.let { s -> try { s.send(effect.person) } catch (_: Exception) { null } }
                    ?: SendView(SendStatus.NotSent(SendProblem.OtherError), seen = false, failures = 1)
                dispatch(VoiceEvent.SendUpdated(effect.person.id, view))
            }
            is VoiceEffect.SendSeen -> safely { sender?.seen(effect.personId) }
        }
    }

    /** With Send on screen, the words offer it (UX V4, V9). */
    private fun words(prompt: VoicePrompt, person: VoicePerson): VoicePrompt = if (!canSend(person)) prompt else when (prompt) {
        VoicePrompt.Review -> VoicePrompt.ReviewSend
        VoicePrompt.MaxLength -> VoicePrompt.MaxLengthSend
        VoicePrompt.Unsent -> VoicePrompt.UnsentSend
        else -> prompt
    }

    /**
     * The carrier's cap applies only to someone who can be sent to. 0 means "no limit" to MediaRecorder, so a
     * cap too small for any message is never passed down: the send is refused as too big instead.
     */
    private fun maxMsFor(personId: String): Long {
        if (people.firstOrNull { it.id == personId }?.let(::canSend) != true) return maxMs
        val cap = try { recordCapMs() } catch (_: Exception) { 0L }
        return if (cap > 0) minOf(cap, maxMs) else maxMs
    }

    private fun start(personId: String) {
        safely { speaker.stop() }
        val take = Take().also { recording = it }
        // A call is ringing or up, or another app won't give up audio: don't open the microphone.
        if (!acquire()) { dispatch(VoiceEvent.CouldNotStart); return }
        mic.problem()?.let { dispatch(VoiceEvent.MicUnavailable(it)); return }
        val file = try { store.prepareTake(personId) } catch (_: Exception) {
            dispatch(VoiceEvent.MicUnavailable(MicProblem.RecorderError)); return
        }
        try {
            recorder.start(file, format, maxMsFor(personId), take)
            take.started = true
        } catch (_: Exception) {
            dispatch(VoiceEvent.CouldNotStart)
        }
    }

    private fun finish(effect: VoiceEffect.FinishRecording): VoiceDraft? {
        val take = recording
        recording = null
        val finalised = take?.started == true && try { recorder.stop() } catch (_: Exception) { false }
        // Measured from real capture, not from the tap: setup and the start tone don't count.
        val capturedAt = take?.capturedAt
        val usable = effect.keep && finalised && capturedAt != null && clock() - capturedAt >= MIN_USABLE_MS
        if (!usable) { safely { store.discardTake(effect.personId) }; return null }
        return try { store.keepTake(effect.personId) } catch (_: Exception) {
            safely { store.discardTake(effect.personId) }; null
        }
    }

    private fun acquire() = try { gate.acquire() } catch (_: Exception) { false }
    private fun stopPlayer() = safely { player.stop() }
    private fun safeDraft(personId: String) = try { store.draft(personId) } catch (_: Exception) { null }
    private fun safeView(personId: String) = try { sender?.view(personId) } catch (_: Exception) { null }
    private inline fun safely(block: () -> Unit) { try { block() } catch (_: Exception) { } }

    companion object {
        /** Longest practice message (Lead, PLA-457): 2 minutes. */
        const val DEFAULT_MAX_MS = 120_000L
        /** Shorter captured audio is dropped as a slip of the finger (UX V3 "Too short"). */
        const val MIN_USABLE_MS = 1_000L
    }
}
