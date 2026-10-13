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
import org.plainphone.device.voice.VoiceAudioGate
import org.plainphone.device.voice.VoiceDraftStore
import org.plainphone.device.voice.VoiceEvent
import org.plainphone.device.voice.VoiceFormat
import org.plainphone.device.voice.VoiceNotice
import org.plainphone.device.voice.VoicePeople
import org.plainphone.device.voice.VoicePerson
import org.plainphone.device.voice.VoicePracticeController
import org.plainphone.device.voice.VoicePracticeSession
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
 * Hosts the practice for MainActivity. [open] when the screen appears, [close] when it goes away,
 * [onActivityStop] and [onActivityStart] with the activity. While the screen shows and the activity is
 * started it listens for screen-off and calls; while recording or playing it holds transient audio
 * focus. Each of those stops the recorder (keeping the take) or the playback (UX V11). Recording and
 * playback are refused while a call rings or is up, or when focus is refused. Never sends.
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
    private var focus: AudioFocusRequest? = null
    /** Last state from the call listener, which reports the current state as soon as it is armed. */
    private var callState = TelephonyManager.CALL_STATE_IDLE

    private val gate: VoiceAudioGate = object : VoiceAudioGate {
        override fun acquire(): Boolean {
            if (callState != TelephonyManager.CALL_STATE_IDLE || audio.mode != AudioManager.MODE_NORMAL) return false
            if (focus != null) return true
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener { change ->
                    if (change < 0) controller.close(Interruption.AudioFocusLost)
                }.build()
            // Refused during a call, for example (no delayed gain: this asks now or not at all).
            if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
            focus = request
            return true
        }
        override fun release() {
            focus?.let { audio.abandonAudioFocusRequest(it) }
            focus = null
        }
    }

    private val controller: VoicePracticeController = VoicePracticeController(AndroidVoiceRecorder(app), AndroidVoicePlayer(), store,
        AndroidMicAccess(app, admin), speaker, gate, SystemClock::elapsedRealtime, format)
    private val session = VoicePracticeSession(controller, ::arm, ::disarm)

    var ui by mutableStateOf<VoiceUiState>(VoiceUiMapper.map(VoiceState.ChoosePerson))
        private set
    var onLeave: () -> Unit = {}

    init {
        controller.onChange = { state -> ui = VoiceUiMapper.map(state) }
        controller.onLeave = { onLeave() }
    }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { controller.close(Interruption.ScreenOff) }
    }

    private val calls = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            callState = state
            if (state != TelephonyManager.CALL_STATE_IDLE) controller.close(Interruption.Call)
        }
    }

    private fun arm() {
        app.registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        try { telephony.registerTelephonyCallback(app.mainExecutor, calls) } catch (_: Exception) {
            // No phone-state access: audio mode and focus refusal still keep a call out.
        }
    }

    private fun disarm() {
        try { app.unregisterReceiver(screenOff) } catch (_: Exception) { }
        try { telephony.unregisterTelephonyCallback(calls) } catch (_: Exception) { }
        callState = TelephonyManager.CALL_STATE_IDLE
    }

    /** The practice route appeared. */
    fun open() = session.show()

    /** The practice route went away: stops recording (keeping it), playback and speech. */
    fun close() = session.hide()

    /** MainActivity.onStop: stops all audio and the listeners; the screen and its draft stay. */
    fun onActivityStop() = session.activityStopped()

    /** MainActivity.onStart: re-arms the listeners if the practice is still on screen. */
    fun onActivityStart() = session.activityStarted()

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
