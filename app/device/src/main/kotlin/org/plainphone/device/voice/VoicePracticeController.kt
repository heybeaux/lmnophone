// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import java.io.File

/** Mono recorder. The host adapter wraps MediaRecorder; callbacks arrive on the main thread. */
interface VoiceRecorder {
    interface Listener {
        fun onMaxLength()
        fun onError()
        /** Android silenced this recording, for example with the microphone privacy toggle. */
        fun onSilenced()
    }
    /** Starts recording into [file]. Throws if the microphone or file can't be opened. */
    fun start(file: File, format: VoiceFormat, maxMs: Long, listener: Listener)
    /** Stops and releases. True when the file was finalised with audio. Never throws. */
    fun stop(): Boolean
}

interface VoicePlayer {
    /** Throws if playback can't start. */
    fun play(file: File, onDone: () -> Unit, onError: () -> Unit)
    fun stop()
}

/** Null when the microphone may be used now; the host tries the Device Owner grant first. */
fun interface MicAccess { fun problem(): MicProblem? }

fun interface VoiceSpeaker { fun speakName(name: String) }

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
    private val clock: () -> Long,
    private val format: VoiceFormat = VoiceFormat.AmrNb,
    private val maxMs: Long = DEFAULT_MAX_MS,
) {
    var state: VoiceState = VoiceState.ChoosePerson
        private set
    var onChange: (VoiceState) -> Unit = {}
    var onLeave: () -> Unit = {}

    private var recording: Take? = null

    private inner class Take(val startedAt: Long) : VoiceRecorder.Listener {
        var started = false
        private fun live() = recording === this && state is VoiceState.Recording
        override fun onMaxLength() { if (live()) dispatch(VoiceEvent.MaxLengthReached) }
        override fun onError() { if (live()) dispatch(VoiceEvent.MicUnavailable(MicProblem.RecorderError)) }
        override fun onSilenced() { if (live()) dispatch(VoiceEvent.MicUnavailable(MicProblem.PrivacyToggle)) }
    }

    fun open() {
        close()
        store.prune()
        state = VoiceState.ChoosePerson
        onChange(state)
    }

    fun dispatch(event: VoiceEvent) {
        val e = if (event is VoiceEvent.Choose && state == VoiceState.ChoosePerson)
            event.copy(existing = safeDraft(event.person.id)) else event
        val t = VoicePractice.reduce(state, e)
        state = t.state
        onChange(state)
        for (effect in t.effects) run(effect)
    }

    /** Leaving the screen for any reason: stop recording (keeping the take) and playback. */
    fun close() {
        if (state is VoiceState.Recording) dispatch(VoiceEvent.Interrupted(Interruption.AppStopped))
        stopPlayer()
    }

    private fun run(effect: VoiceEffect) {
        when (effect) {
            is VoiceEffect.SpeakName -> try { speaker.speakName(effect.person.name) } catch (_: Exception) { }
            is VoiceEffect.StartRecording -> start(effect.personId)
            is VoiceEffect.FinishRecording -> dispatch(VoiceEvent.RecordingFinished(finish(effect)))
            is VoiceEffect.StartPlayback -> try {
                player.play(store.file(effect.personId),
                    onDone = { if ((state as? VoiceState.Review)?.playing == true) dispatch(VoiceEvent.PlaybackEnded) },
                    onError = { if ((state as? VoiceState.Review)?.playing == true) dispatch(VoiceEvent.PlaybackFailed) })
            } catch (_: Exception) { dispatch(VoiceEvent.PlaybackFailed) }
            VoiceEffect.StopPlayback -> stopPlayer()
            is VoiceEffect.DeleteDraft -> try { store.delete(effect.personId) } catch (_: Exception) { }
            VoiceEffect.Leave -> onLeave()
        }
    }

    private fun start(personId: String) {
        val take = Take(clock()).also { recording = it }
        mic.problem()?.let { dispatch(VoiceEvent.MicUnavailable(it)); return }
        try {
            val file = store.prepareTake(personId)
            recorder.start(file, format, maxMs, take)
            take.started = true
        } catch (_: Exception) {
            dispatch(VoiceEvent.MicUnavailable(MicProblem.RecorderError))
        }
    }

    private fun finish(effect: VoiceEffect.FinishRecording): VoiceDraft? {
        val take = recording
        recording = null
        val finalised = take?.started == true && try { recorder.stop() } catch (_: Exception) { false }
        val usable = effect.keep && finalised && take != null && clock() - take.startedAt >= MIN_USABLE_MS
        if (!usable) { safely { store.discardTake(effect.personId) }; return null }
        return try { store.keepTake(effect.personId) } catch (_: Exception) {
            safely { store.discardTake(effect.personId) }; null
        }
    }

    private fun stopPlayer() = safely { player.stop() }
    private fun safeDraft(personId: String) = try { store.draft(personId) } catch (_: Exception) { null }
    private inline fun safely(block: () -> Unit) { try { block() } catch (_: Exception) { } }

    companion object {
        const val DEFAULT_MAX_MS = 60_000L
        /** Shorter takes are treated as a slip of the finger: nothing recorded. */
        const val MIN_USABLE_MS = 500L
    }
}
