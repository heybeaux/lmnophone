// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityManager
import java.io.File

/**
 * MediaRecorder, mono. AAC-LC at 44.1 kHz / 24 kbit/s in `.m4a` by default; AMR-NB at 8 kHz /
 * 12.2 kbit/s in `.amr` as the fallback.
 *
 * Order (UX V3): the recorder is prepared, the start tone plays, and only then does the microphone
 * open, [TONE_GAP_MS] later. A failure at that point reports [VoiceRecorder.Listener.onStartFailed];
 * success reports [VoiceRecorder.Listener.onCaptureStarted], which is where a take's length counts from.
 * Max length is enforced twice: MediaRecorder.setMaxDuration and a main-thread timer one second later.
 * Silencing (the microphone privacy toggle) is caught two ways: this recorder's own
 * AudioRecordingConfiguration.isClientSilenced (API 29), and a peak level of exactly zero for the
 * first [SILENCE_WINDOW_MS], which is what the toggle produces (digital zero, not a quiet room).
 * Nothing is logged: no file names, people or audio.
 */
class AndroidVoiceRecorder(context: Context) : VoiceRecorder {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var recorder: MediaRecorder? = null
    private var running = false
    private var autoStopped = false
    private var callback: AudioManager.AudioRecordingCallback? = null
    private val pending = mutableListOf<Runnable>()
    /** Any non-zero peak since capture began. */
    private var heard = false

    override fun start(file: File, format: VoiceFormat, maxMs: Long, listener: VoiceRecorder.Listener) {
        check(recorder == null) { "already recording" }
        autoStopped = false; running = false; heard = false
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(app) else MediaRecorder()
        recorder = r
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            when (format) {
                VoiceFormat.AacM4a -> {
                    r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    r.setAudioSamplingRate(44_100)
                    r.setAudioEncodingBitRate(24_000)
                }
                VoiceFormat.AmrNb -> {
                    r.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                    r.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    r.setAudioSamplingRate(8_000)
                    r.setAudioEncodingBitRate(12_200)
                }
            }
            r.setAudioChannels(format.channels)
            r.setMaxDuration(maxMs.toInt())
            r.setOutputFile(file.path)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    autoStopped = true
                    post { listener.onMaxLength() }
                }
            }
            r.setOnErrorListener { _, _, _ -> post { listener.onError() } }
            r.prepare()
        } catch (e: Exception) {
            release(); file.delete(); throw e
        }
        tone(ToneGenerator.TONE_PROP_BEEP)
        schedule(Runnable {
            try { r.start(); running = true } catch (_: Exception) { listener.onStartFailed(); return@Runnable }
            listener.onCaptureStarted()
            watchSilence(r, listener)
            schedule(Runnable { listener.onMaxLength() }, maxMs + 1_000)
        }, TONE_GAP_MS)
    }

    private fun watchSilence(r: MediaRecorder, listener: VoiceRecorder.Listener) {
        callback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
                if (recorder != null && clientSilenced()) post { listener.onSilenced() }
            }
        }.also { audio.registerAudioRecordingCallback(it, main) }
        schedule(Runnable { if (clientSilenced()) listener.onSilenced() }, 300)
        // The first reading only resets the peak; any non-zero reading after it means real audio.
        try { r.maxAmplitude } catch (_: Exception) { }
        for (at in SILENCE_STEP_MS..SILENCE_WINDOW_MS step SILENCE_STEP_MS) schedule(Runnable {
            if (!heard) heard = try { r.maxAmplitude > 0 } catch (_: Exception) { true }
            if (!heard && at >= SILENCE_WINDOW_MS) listener.onSilenced()
        }, at)
    }

    override fun silent(): Boolean {
        val r = recorder ?: return false
        if (!running) return false
        if (clientSilenced()) return true
        // Peak since the last reading; an unreadable recorder (e.g. auto-stopped at max length) counts as heard.
        if (!heard) heard = try { r.maxAmplitude > 0 } catch (_: Exception) { true }
        return !heard
    }

    private fun clientSilenced() =
        try { recorder?.activeRecordingConfiguration?.isClientSilenced == true } catch (_: Exception) { false }

    override fun stop(): Boolean {
        val r = recorder ?: return false
        val wasRunning = running
        val ok = wasRunning && (autoStopped || try { r.stop(); true } catch (_: RuntimeException) { false })
        release()
        // No stop tone while a call rings or is up (UX V11).
        if (wasRunning && audio.mode == AudioManager.MODE_NORMAL) tone(ToneGenerator.TONE_PROP_BEEP2)
        return ok
    }

    private fun tone(type: Int) {
        try {
            val t = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            t.startTone(type, 150)
            main.postDelayed({ t.release() }, 400)
        } catch (_: Exception) { /* No tone is better than no recording. */ }
    }

    private fun release() {
        pending.forEach(main::removeCallbacks); pending.clear()
        callback?.let { try { audio.unregisterAudioRecordingCallback(it) } catch (_: Exception) { } }
        callback = null
        try { recorder?.release() } catch (_: Exception) { }
        recorder = null; running = false
    }

    private fun post(block: () -> Unit) = schedule(Runnable(block), 0)
    private fun schedule(r: Runnable, ms: Long) {
        val once = object : Runnable { override fun run() { pending.remove(this); if (recorder != null) r.run() } }
        pending += once
        main.postDelayed(once, ms)
    }

    companion object {
        const val TONE_GAP_MS = 250L
        const val SILENCE_STEP_MS = 250L
        const val SILENCE_WINDOW_MS = 1_250L
    }
}

/** Plays a draft as speech media. */
class AndroidVoicePlayer : VoicePlayer {
    private var player: MediaPlayer? = null

    override fun play(file: File, onDone: () -> Unit, onError: () -> Unit) {
        stop()
        val p = MediaPlayer()
        player = p
        try {
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            p.setDataSource(file.path)
            p.setOnCompletionListener { stop(); onDone() }
            p.setOnErrorListener { _, _, _ -> stop(); onError(); true }
            p.prepare()
            p.start()
        } catch (e: Exception) { stop(); throw e }
    }

    override fun stop() {
        val p = player ?: return
        player = null
        try { p.stop() } catch (_: Exception) { }
        try { p.release() } catch (_: Exception) { }
    }
}

/**
 * RECORD_AUDIO, granted by the Device Owner to its own package (API 31+; Lead, PLA-457). The
 * runtime prompt is never shown: it is blocked in lock task, and the person can't read it. The
 * readback decides; if the grant doesn't hold, the answer is PermissionRefused (UX V10b).
 */
class AndroidMicAccess(context: Context, private val admin: ComponentName) : MicAccess {
    private val app = context.applicationContext
    fun granted() = app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** True when the permission now reads back granted, after one Device Owner grant if needed. */
    fun selfGrant(): Boolean {
        if (granted()) return true
        try {
            val dpm = app.getSystemService(DevicePolicyManager::class.java)
            if (dpm.isDeviceOwnerApp(app.packageName)) {
                dpm.setPermissionGrantState(admin, app.packageName, Manifest.permission.RECORD_AUDIO,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            }
        } catch (_: Exception) { /* Readback decides. */ }
        return granted()
    }

    override fun problem(): MicProblem? = if (selfGrant()) null else MicProblem.PermissionRefused
}

/**
 * Speaks prompts with the phone's text-to-speech through media sound. Quiet when TalkBack is on (it
 * reads the screen) and during a call. [text] maps a prompt to its exact words.
 */
class AndroidVoiceSpeaker(context: Context, private val text: (VoicePrompt, VoicePerson?) -> String) : VoiceSpeaker {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private var ready = false
    private var queued: String? = null
    private var tts: TextToSpeech? = null

    override fun speak(prompt: VoicePrompt, person: VoicePerson?) = speak(prompt, person, requested = false)

    /** The picture's "hear the name": under TalkBack it is the only way to hear it in the phone's voice. */
    override fun speakOnRequest(prompt: VoicePrompt, person: VoicePerson?) = speak(prompt, person, requested = true)

    private fun speak(prompt: VoicePrompt, person: VoicePerson?, requested: Boolean) {
        val touch = app.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
        if (!shouldSpeak(touch, audio.mode, requested)) return
        val words = text(prompt, person)
        if (tts == null) tts = TextToSpeech(app) { status ->
            ready = status == TextToSpeech.SUCCESS
            tts?.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            queued?.takeIf { ready }?.let { say(it) }
            queued = null
        }
        if (ready) say(words) else queued = words
    }

    private fun say(words: String) {
        tts?.speak(words, TextToSpeech.QUEUE_FLUSH, null, "voice-prompt")
    }

    override fun stop() { queued = null; try { tts?.stop() } catch (_: Exception) { } }
    fun shutdown() { try { tts?.shutdown() } catch (_: Exception) { }; tts = null; ready = false; queued = null }

    companion object {
        /** Never during a call. Under TalkBack (it reads the screen) only what the person asked for. */
        fun shouldSpeak(touchExploration: Boolean, audioMode: Int, requested: Boolean) =
            audioMode == AudioManager.MODE_NORMAL && (requested || !touchExploration)
    }
}
