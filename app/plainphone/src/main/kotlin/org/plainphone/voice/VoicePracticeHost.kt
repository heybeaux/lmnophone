// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.voice

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import org.plainphone.device.voice.AndroidMicAccess
import org.plainphone.device.voice.AndroidVoicePlayer
import org.plainphone.device.voice.AndroidVoiceRecorder
import org.plainphone.device.voice.AndroidVoiceSpeaker
import org.plainphone.device.voice.Interruption
import org.plainphone.device.voice.MicProblem
import org.plainphone.device.voice.VoiceDraftStore
import org.plainphone.device.voice.VoiceEvent
import org.plainphone.device.voice.VoiceFormat
import org.plainphone.device.voice.VoiceNotice
import org.plainphone.device.voice.VoicePeople
import org.plainphone.device.voice.VoicePerson
import org.plainphone.device.voice.VoicePracticeController
import org.plainphone.device.voice.VoicePrompt
import org.plainphone.device.voice.VoiceState
import org.plainphone.ui.R
import org.plainphone.ui.user.VoicePersonUi
import org.plainphone.ui.user.VoiceUiEvent
import org.plainphone.ui.user.VoiceUiMicProblem
import org.plainphone.ui.user.VoiceUiNotice
import org.plainphone.ui.user.VoiceUiState

/** Practice states to screens. Saving still shows Recording: the recorder is being stopped. */
object VoiceUiMapper {
    fun map(s: VoiceState): VoiceUiState = when (s) {
        VoiceState.ChoosePerson -> VoiceUiState.ChoosePerson(VoicePeople.practice.map(::person))
        is VoiceState.Person -> VoiceUiState.Person(person(s.person), s.notice?.let(::notice))
        is VoiceState.Recording -> VoiceUiState.Recording(person(s.person))
        is VoiceState.Saving -> VoiceUiState.Recording(person(s.person))
        is VoiceState.Review -> VoiceUiState.Review(person(s.person), s.playing, s.notice?.let(::notice))
        is VoiceState.MicUnavailable -> VoiceUiState.MicOff(person(s.person), when (s.problem) {
            MicProblem.PermissionRefused -> VoiceUiMicProblem.NotAllowed
            MicProblem.PrivacyToggle -> VoiceUiMicProblem.TurnedOff
            MicProblem.RecorderError -> VoiceUiMicProblem.DidNotWork
        })
    }

    private fun person(p: VoicePerson) = VoicePersonUi(p.id, p.name)

    private fun notice(n: VoiceNotice) = when (n) {
        VoiceNotice.MaxLength -> VoiceUiNotice.MaxLength
        VoiceNotice.Interrupted -> VoiceUiNotice.Interrupted
        VoiceNotice.NothingRecorded -> VoiceUiNotice.NothingRecorded
        VoiceNotice.Deleted -> VoiceUiNotice.Deleted
        VoiceNotice.CouldNotPlay -> VoiceUiNotice.CouldNotPlay
        VoiceNotice.MicBusy -> VoiceUiNotice.Busy
        VoiceNotice.Unsent -> VoiceUiNotice.Unsent
    }
}

/**
 * Hosts the practice for MainActivity. [open] when the screen appears, [close] when it goes away or
 * the activity stops. While open it listens for screen-off and calls; while recording it holds
 * transient audio focus. Each of those stops the recorder and keeps the take (UX V11). Never sends.
 *
 * Drafts live in noBackupFilesDir/voice-practice: app-private, credential-encrypted, never backed up.
 * They are kept until deleted and pruned 7 days after saving ([VoiceDraftStore.RETENTION_MS]).
 */
class VoicePracticeHost(context: Context, admin: ComponentName, private val helper: () -> String) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val telephony = app.getSystemService(TelephonyManager::class.java)
    private val speaker = AndroidVoiceSpeaker(app, ::words)
    private val format = VoiceFormat.AacM4a
    val store = VoiceDraftStore(File(app.noBackupFilesDir, "voice-practice"), format) { System.currentTimeMillis() }
    private val controller = VoicePracticeController(AndroidVoiceRecorder(app), AndroidVoicePlayer(), store,
        AndroidMicAccess(app, admin), speaker, SystemClock::elapsedRealtime, format)

    var ui by mutableStateOf<VoiceUiState>(VoiceUiMapper.map(VoiceState.ChoosePerson))
        private set
    var onLeave: () -> Unit = {}
    private var opened = false
    private var focus: AudioFocusRequest? = null

    init {
        controller.onChange = { state ->
            ui = VoiceUiMapper.map(state)
            if (state is VoiceState.Recording) holdFocus() else dropFocus()
        }
        controller.onLeave = { onLeave() }
    }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { controller.close(Interruption.ScreenOff) }
    }

    private val calls = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            if (state != TelephonyManager.CALL_STATE_IDLE) controller.close(Interruption.Call)
        }
    }

    fun open() {
        if (opened) return
        opened = true
        app.registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        try { telephony.registerTelephonyCallback(app.mainExecutor, calls) } catch (_: Exception) {
            // No phone-state access: the call's audio-focus loss still stops the recorder.
        }
        controller.open()
    }

    /** Stops recording (keeping it), playback and speech. Safe to call more than once. */
    fun close(cause: Interruption = Interruption.AppStopped) {
        controller.close(cause)
        dropFocus()
        if (!opened) return
        opened = false
        try { app.unregisterReceiver(screenOff) } catch (_: Exception) { }
        try { telephony.unregisterTelephonyCallback(calls) } catch (_: Exception) { }
    }

    fun onEvent(e: VoiceUiEvent) {
        when (e) {
            is VoiceUiEvent.Choose -> VoicePeople.practice.firstOrNull { it.id == e.id }
                ?.let { controller.dispatch(VoiceEvent.Choose(it)) }
            VoiceUiEvent.Record -> controller.dispatch(VoiceEvent.Record)
            VoiceUiEvent.Stop -> controller.dispatch(VoiceEvent.Stop)
            VoiceUiEvent.Play -> controller.dispatch(VoiceEvent.Play)
            VoiceUiEvent.RecordAgain -> controller.dispatch(VoiceEvent.RecordAgain)
            VoiceUiEvent.Delete -> controller.dispatch(VoiceEvent.Delete)
            VoiceUiEvent.Back -> controller.dispatch(VoiceEvent.Back)
            VoiceUiEvent.HearName -> controller.sayName()
        }
    }

    private fun holdFocus() {
        if (focus != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { change ->
                if (change < 0) controller.close(Interruption.AudioFocusLost)
            }.build()
        focus = request
        audio.requestAudioFocus(request)
    }

    private fun dropFocus() {
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }

    private fun words(prompt: VoicePrompt, person: VoicePerson?): String {
        val name = person?.name.orEmpty()
        val id = when (prompt) {
            VoicePrompt.Intro -> R.string.voice_say_intro
            VoicePrompt.Name -> R.string.voice_say_name
            VoicePrompt.Person -> R.string.voice_say_person
            VoicePrompt.Review -> R.string.voice_say_review
            VoicePrompt.Unsent -> R.string.voice_say_unsent
            VoicePrompt.TooShort -> R.string.voice_say_too_short
            VoicePrompt.MaxLength -> R.string.voice_say_max
            VoicePrompt.Busy -> R.string.voice_say_busy
            VoicePrompt.Deleted -> R.string.voice_say_deleted
            VoicePrompt.MicOff -> R.string.voice_say_mic_off
            VoicePrompt.MicNotAllowed -> R.string.voice_say_mic_not_allowed
        }
        return app.getString(id, name, helper())
    }

    fun shutdown() { close(); speaker.shutdown() }
}
