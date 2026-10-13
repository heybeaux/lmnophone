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
import android.os.Handler
import android.os.Looper
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
import org.plainphone.device.voice.VoiceNumbers
import org.plainphone.device.voice.VoicePeople
import org.plainphone.device.voice.VoicePerson
import org.plainphone.device.voice.VoicePlace
import org.plainphone.device.voice.VoicePracticeController
import org.plainphone.device.voice.VoicePracticeSession
import org.plainphone.device.voice.VoicePrompt
import org.plainphone.device.voice.VoiceState
import org.plainphone.device.voice.send.AndroidMmsPort
import org.plainphone.device.voice.send.SendProblem
import org.plainphone.device.voice.send.VoiceSendChannel
import org.plainphone.device.voice.send.VoiceSendEngine
import org.plainphone.device.voice.send.VoiceSendLimits
import org.plainphone.device.voice.send.VoiceSender
import org.plainphone.ui.R
import org.plainphone.ui.user.VoicePersonUi
import org.plainphone.ui.user.VoiceUiEvent
import org.plainphone.ui.user.VoiceUiMicProblem
import org.plainphone.ui.user.VoiceUiNotice
import org.plainphone.ui.user.VoiceUiSendProblem
import org.plainphone.ui.user.VoiceUiState

/**
 * Voice states to screens. Saving still shows Recording: the recorder is being stopped. A screen keeps the
 * practice band unless its person [canSend]; the people list keeps it while nobody on [people] can be sent to.
 * [helper] (the trusted person) is named only after a second real failure (UX V7 again). Numbers never reach a screen.
 */
object VoiceUiMapper {
    fun map(
        s: VoiceState,
        people: List<VoicePerson> = VoicePeople.practice,
        family: Boolean = false,
        canSend: (VoicePerson) -> Boolean = { false },
        helper: String = "",
    ): VoiceUiState = when (s) {
        VoiceState.ChoosePerson -> VoiceUiState.ChoosePerson(people.map { person(it, family) }, fromHome = family,
            practice = people.none(canSend))
        is VoiceState.Person -> VoiceUiState.Person(person(s.person, family), s.notice?.let(::notice), openMessages = family,
            practice = !canSend(s.person))
        is VoiceState.Recording -> VoiceUiState.Recording(person(s.person, family), !canSend(s.person))
        is VoiceState.Saving -> VoiceUiState.Recording(person(s.person, family), !canSend(s.person))
        is VoiceState.Review -> canSend(s.person).let { send ->
            VoiceUiState.Review(person(s.person, family), s.playing, s.notice?.let(::notice), openMessages = family,
                canSend = send, practice = !send)
        }
        is VoiceState.MicUnavailable -> VoiceUiState.MicOff(person(s.person, family), when (s.problem) {
            MicProblem.PermissionRefused -> VoiceUiMicProblem.NotAllowed
            MicProblem.PrivacyToggle -> VoiceUiMicProblem.TurnedOff
            MicProblem.RecorderError -> VoiceUiMicProblem.DidNotWork
        }, !canSend(s.person))
        is VoiceState.Sending -> VoiceUiState.Sending(person(s.person, family))
        is VoiceState.Sent -> VoiceUiState.Sent(person(s.person, family))
        is VoiceState.NotSent -> when (s.problem) {
            SendProblem.NoSignal -> VoiceUiState.NotSent(person(s.person, family), VoiceUiSendProblem.NoSignal)
            SendProblem.NotConfirmed -> VoiceUiState.NotSent(person(s.person, family), VoiceUiSendProblem.NotConfirmed)
            else -> VoiceUiState.NotSent(person(s.person, family), VoiceUiSendProblem.NotSent,
                helper.takeIf { s.failures >= 2 && it.isNotBlank() })
        }
    }

    private fun person(p: VoicePerson, family: Boolean) = VoicePersonUi(p.id, p.name, pictureInInk = family)

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
 * playback are refused while a call rings or is up, or when focus is refused.
 *
 * Real sending (PLA-499) goes through [VoiceSendRoute]: for the family list only where the build turns it on
 * ([VoiceSendBuild.familySend]; release never), and for the practice only with the test build's adb marker. Then a
 * person with a number gets Send; the send happens only on that tap or Try again, never by itself. Answers from the
 * phone show only while the screen is visible; otherwise on the next start.
 *
 * Practice drafts live in noBackupFilesDir/voice-practice, family drafts in noBackupFilesDir/voice-family-drafts
 * ([org.plainphone.device.voice.AndroidFamilyStore.drafts]): app-private, credential-encrypted, never backed up.
 * They are kept until deleted and pruned 7 days after saving ([VoiceDraftStore.RETENTION_MS]).
 */
class VoicePracticeHost(context: Context, admin: ComponentName, private val helper: () -> String) {
    private val app = context.applicationContext
    private val familyStore = org.plainphone.device.voice.AndroidFamilyStore.open(app)
    private var family = false
    private var people = VoicePeople.practice
    var openMessages: (String) -> Boolean = { false }
    private val audio = app.getSystemService(AudioManager::class.java)
    private val telephony = app.getSystemService(TelephonyManager::class.java)
    private val speaker = AndroidVoiceSpeaker(app, ::words)
    private val format = VoiceFormat.AacM4a
    private val practiceStore = VoiceDraftStore(File(app.noBackupFilesDir, "voice-practice"), format) { System.currentTimeMillis() }
    private val familyDrafts = VoiceDraftStore(org.plainphone.device.voice.AndroidFamilyStore.drafts(app), format) { System.currentTimeMillis() }
    val store: VoiceDraftStore get() = if (family) familyDrafts else practiceStore
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

    private val practiceNumbers: VoiceNumbers? = VoiceSendBuild.numbers(app)
    private val practiceSender: VoiceSender? = practiceNumbers?.let { VoiceSendEngine.get(app, VoiceSendChannel.Practice) }
    private val familySender: VoiceSender? =
        if (VoiceSendBuild.familySend()) VoiceSendEngine.get(app, VoiceSendChannel.Family) else null
    private val route = VoiceSendRoute({ family }, familySender, familyStore, practiceSender, practiceNumbers)
    private val mms = AndroidMmsPort(app)
    private val main = Handler(Looper.getMainLooper())

    private val controller: VoicePracticeController = VoicePracticeController(AndroidVoiceRecorder(app), AndroidVoicePlayer(), store,
        org.plainphone.device.voice.MicAccess {
            val access = AndroidMicAccess(app, admin)
            if (family) { if (access.granted()) null else MicProblem.PermissionRefused } else access.problem()
        }, speaker, gate, SystemClock::elapsedRealtime, format, people = { people }, draftStore = { store },
        sender = route.port, recordCapMs = { VoiceSendLimits.maxRecordMs(mms.carrier(), format) }, numbers = route.numbers)
    private val session = VoicePracticeSession(controller, ::arm, ::disarm)

    var ui by mutableStateOf<VoiceUiState>(VoiceUiMapper.map(VoiceState.ChoosePerson, people, family, controller::canSend))
        private set
    var onLeave: () -> Unit = {}

    /** UX V6: after 10 seconds with no tap, Sent goes back to the people. Not a send: that is already done. */
    private val sentReturn = Runnable {
        if (session.armed && controller.state is VoiceState.Sent) controller.dispatch(VoiceEvent.Done)
    }

    /**
     * An answer from the phone: shown now only while the screen is visible and showing that sender's people,
     * else on the next start.
     */
    private fun sendUpdate(forFamily: Boolean): (String) -> Unit =
        { id -> main.post { if (session.armed && family == forFamily) controller.sendChanged(id) } }
    private val familyUpdate = sendUpdate(true)
    private val practiceUpdate = sendUpdate(false)

    init {
        controller.onChange = { state ->
            ui = VoiceUiMapper.map(state, people, family, controller::canSend, helper())
            main.removeCallbacks(sentReturn)
            if (state is VoiceState.Sent) main.postDelayed(sentReturn, SENT_RETURN_MS)
        }
        controller.onLeave = { onLeave() }
        familySender?.onUpdate = familyUpdate
        practiceSender?.onUpdate = practiceUpdate
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

    /** Where the screen was before MainActivity was recreated; used by the next [open] only. */
    private var restoring: VoicePlace? = null

    /** The practice route appeared, back at the kept place after a configuration change. */
    fun open() {
        val list = try { familyStore.read() } catch (_: Exception) { org.plainphone.device.voice.FamilyList() }
        family = list.visible
        people = if (family) list.people.map { VoicePerson(it.id, it.name, it.spokenName) } else VoicePeople.practice
        session.show(restoring.also { restoring = null })
    }

    /** The practice route went away: stops recording (keeping it), playback and speech. */
    fun close() { restoring = null; session.hide() }

    /** MainActivity.onStop during a configuration change: where the screen is (PLA-479). */
    fun place(): VoicePlace? = session.place()

    /** MainActivity.onCreate after a configuration change: the next [open] comes back to [place]. */
    fun restoreOnOpen(place: VoicePlace?) { restoring = place }

    /** MainActivity.onStop: stops all audio and the listeners; the screen and its draft stay. */
    fun onActivityStop() = session.activityStopped()

    /**
     * MainActivity.onStart: re-arms the listeners if the practice is still on screen, and shows any answer
     * that came while it was away. Reads only: never sends.
     */
    fun onActivityStart() {
        session.activityStarted()
        if (session.armed) controller.state.person?.let { controller.sendChanged(it.id) }
        if (session.armed && controller.state is VoiceState.Sent) {
            main.removeCallbacks(sentReturn)
            main.postDelayed(sentReturn, SENT_RETURN_MS)
        }
    }

    fun onEvent(e: VoiceUiEvent) {
        when (e) {
            is VoiceUiEvent.Choose -> people.firstOrNull { it.id == e.id }
                ?.let { controller.dispatch(VoiceEvent.Choose(it)) }
            VoiceUiEvent.Record -> controller.dispatch(VoiceEvent.Record)
            VoiceUiEvent.Stop -> controller.dispatch(VoiceEvent.Stop)
            VoiceUiEvent.Play -> controller.dispatch(VoiceEvent.Play)
            VoiceUiEvent.RecordAgain -> controller.dispatch(VoiceEvent.RecordAgain)
            VoiceUiEvent.Delete -> controller.dispatch(VoiceEvent.Delete)
            VoiceUiEvent.Back -> controller.dispatch(VoiceEvent.Back)
            VoiceUiEvent.HearName -> controller.sayName()
            VoiceUiEvent.OpenMessages -> if (family && (controller.state is VoiceState.Person || controller.state is VoiceState.Review)) {
                val person = controller.state.person ?: return
                controller.close(Interruption.AppStopped)
                speaker.speakBeforeAction(VoicePrompt.OpeningMessages, person) {
                    // The receiving callback resolves this opaque id again; numbers never enter UI state.
                    if (!openMessages(person.id)) {
                        ui = when (val current = ui) {
                            is VoiceUiState.Person -> current.copy(messagesUnavailable = true)
                            is VoiceUiState.Review -> current.copy(messagesUnavailable = true)
                            else -> current
                        }
                        runCatching { speaker.speak(VoicePrompt.MessagesUnavailable, person) }
                    }
                }
            }
            VoiceUiEvent.Send -> controller.dispatch(VoiceEvent.Send)
            VoiceUiEvent.TryAgain -> controller.dispatch(VoiceEvent.TryAgain)
            VoiceUiEvent.Done -> controller.dispatch(VoiceEvent.Done)
        }
    }

    private fun words(prompt: VoicePrompt, person: VoicePerson?): String {
        val helper = helper()
        return app.getString(VoiceWords.res(prompt, helper.isNotBlank(), family), person?.spokenName.orEmpty(), helper)
    }

    fun shutdown() {
        close()
        main.removeCallbacks(sentReturn)
        familySender?.let { if (it.onUpdate === familyUpdate) it.onUpdate = {} }
        practiceSender?.let { if (it.onUpdate === practiceUpdate) it.onUpdate = {} }
        speaker.shutdown()
    }

    private companion object {
        const val SENT_RETURN_MS = 10_000L
    }
}

/** Spoken words per prompt (docs/ux/flows-voice-messages.md, PLA-472). Help words name the trusted person when known. */
internal object VoiceWords {
    fun res(prompt: VoicePrompt, helperKnown: Boolean, family: Boolean = false): Int = when (prompt) {
        VoicePrompt.Intro -> R.string.voice_say_intro
        VoicePrompt.Name -> R.string.voice_say_name
        VoicePrompt.Person -> if (family) R.string.family_say_person else R.string.voice_say_person
        VoicePrompt.Review -> R.string.voice_say_review
        VoicePrompt.Unsent -> R.string.voice_say_unsent
        VoicePrompt.TooShort -> R.string.voice_say_too_short
        VoicePrompt.MaxLength -> R.string.voice_say_max
        VoicePrompt.Busy -> R.string.voice_say_busy
        VoicePrompt.Deleted -> R.string.voice_say_deleted
        VoicePrompt.MicOff -> if (helperKnown) R.string.voice_say_mic_off else R.string.voice_say_mic_off_short
        VoicePrompt.MicNotAllowed -> if (helperKnown) R.string.voice_say_mic_not_allowed else R.string.voice_say_mic_not_allowed_short
        VoicePrompt.MicError -> R.string.voice_say_mic_error
        VoicePrompt.CouldNotPlay -> R.string.voice_say_play
        VoicePrompt.OpeningMessages -> R.string.family_say_opening_messages
        VoicePrompt.MessagesUnavailable -> R.string.family_say_messages_unavailable
        VoicePrompt.ChooseSend -> R.string.voice_say_choose
        VoicePrompt.ReviewSend -> R.string.voice_say_review_send
        VoicePrompt.MaxLengthSend -> R.string.voice_say_max_send
        VoicePrompt.UnsentSend -> R.string.voice_say_unsent_send
        VoicePrompt.Sending -> R.string.voice_say_sending
        VoicePrompt.Sent -> R.string.voice_say_sent
        VoicePrompt.NotSent -> R.string.voice_say_not_sent
        VoicePrompt.NotSentAgain -> if (helperKnown) R.string.voice_say_not_sent_again else R.string.voice_say_not_sent
        VoicePrompt.NoSignal -> R.string.voice_say_no_signal
        VoicePrompt.NotConfirmed -> R.string.voice_say_not_confirmed
    }
}
