// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import java.io.File
import org.plainphone.device.voice.replies.ReplyClip
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
    /**
     * Plays a family person's voice reply straight from the messaging store (PLA-527); never copies it and never
     * opens the microphone. Throws if playback can't start, for example a part that isn't downloaded or readable.
     */
    fun playReply(clip: ReplyClip, onDone: () -> Unit, onError: () -> Unit): Unit = throw UnsupportedOperationException()
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
    /** Several prompts as one utterance (the people list's intro and its "{person} sent you a message."). */
    fun speakAll(prompts: List<Pair<VoicePrompt, VoicePerson?>>) = prompts.forEach { speak(it.first, it.second) }
    /** Says [prompt], then calls [then] once it has been said. [stop] may drop [then]. */
    fun speakThen(prompt: VoicePrompt, person: VoicePerson?, then: () -> Unit) { speak(prompt, person); then() }
    /** Silences speech at once, before the microphone opens or when the person taps. */
    fun stop()
}

/**
 * Family people's voice replies from the phone's MMS inbox (PLA-527), read-only. Only people on the current
 * list while voice messages are on. Implementations never throw, but the controller guards each call anyway.
 */
interface VoiceReplies {
    /** Person id to unplayed clips, oldest first. */
    fun unplayed(): Map<String, List<ReplyClip>>
    /** Asked again right before a clip plays: the person may have been removed meanwhile. */
    fun canPlay(personId: String): Boolean
    fun canPlay(personId: String, clip: ReplyClip): Boolean = clip.personId == personId && canPlay(personId)
    /** [clip] was heard to the end. */
    fun played(personId: String, clip: ReplyClip)
}

/**
 * A person's phone number, looked up by person id at the point of use (PLA-499). Null: no number, so no Send.
 * The family list (PLA-500) supplies it from VoiceRecipientSource; numbers never enter [VoicePerson] or UI state.
 */
fun interface VoiceNumbers { fun number(personId: String): String? }

/** The real sender (PLA-499, [org.plainphone.device.voice.send.VoiceSender]). Absent when real sending is off. */
interface VoiceSendPort {
    /**
     * Hands the person's draft over once, to [number], and says where that send is. Called only for an explicit
     * Send or Try again, with the number looked up at that moment. Never log [number].
     */
    fun send(person: VoicePerson, number: String): SendView
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
    store: VoiceDraftStore,
    private val mic: MicAccess,
    private val speaker: VoiceSpeaker,
    private val gate: VoiceAudioGate,
    private val clock: () -> Long,
    private val format: VoiceFormat = VoiceFormat.AacM4a,
    private val maxMs: Long = DEFAULT_MAX_MS,
    /** Read again on every use: the family list (PLA-500) can change while the host lives. */
    private val people: () -> List<VoicePerson> = { VoicePeople.practice },
    private val draftStore: () -> VoiceDraftStore = { store },
    /** Null: real sending is off. Send and Try again are then ignored, and nothing is ever handed over. */
    private val sender: VoiceSendPort? = null,
    /** The longest message the carrier takes ([org.plainphone.device.voice.send.VoiceSendLimits.maxRecordMs]). */
    private val recordCapMs: () -> Long = { maxMs },
    /** Looked up again for every check and every send; never kept. */
    private val numbers: VoiceNumbers = VoiceNumbers { null },
    /** Null: no voice replies (the practice). Read again on open, on [refreshReplies] and on the people list. */
    private val replies: VoiceReplies? = null,
) {
    private val store: VoiceDraftStore get() = draftStore()
    var state: VoiceState = VoiceState.ChoosePerson
        private set
    var onChange: (VoiceState) -> Unit = {}
    var onLeave: () -> Unit = {}

    private var recording: Take? = null
    /** People with a new voice reply, read on the people list. Ids only. */
    private var newFrom: Set<String> = emptySet()
    /** Bumped whenever playback stops or another reply starts: a late speech or player callback then does nothing. */
    private var replyToken = 0
    private val unavailableReplies = mutableSetOf<String>()

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
        unavailableReplies.clear()
        safely { store.prune() }
        val restored = place?.let(::restore)
        state = restored ?: VoiceState.ChoosePerson
        cacheReplies()
        onChange(state)
        // A new visit, or a place that is gone (an unknown person): the people list is introduced, then once
        // for each person with a new voice reply, in card order (UX V1).
        if (restored == null) {
            val intro = (if (practice(null)) VoicePrompt.Intro else VoicePrompt.ChooseSend) to null
            val news = people().filter { hasNewMessage(it.id) }.map { VoicePrompt.NewFrom to it }
            if (news.isEmpty()) say(intro.first, null) else safely { speaker.speakAll(listOf(intro) + news) }
        }
    }

    /** The people list shows "New message" on [personId]'s card (UX V1). */
    fun hasNewMessage(personId: String): Boolean = personId in newFrom

    /**
     * Reads the voice replies again (the app came back, or a message arrived). Updates the people list's marks
     * and an idle person page; never plays or speaks.
     */
    fun refreshReplies() {
        unavailableReplies.clear()
        cacheReplies()
        val s = state
        if (s is VoiceState.Person) {
            val listen = s.listen
            val clip = listen?.queue?.getOrNull(listen.index)
            if (listen != null && !mayPlayPerson(s.person.id)) {
                clearReplies(s)
                return
            }
            if (listen?.playing != true) {
                val queue = replyQueue(s.person.id)
                state = when {
                    queue.isNotEmpty() -> s.copy(listen = ReplyListen(queue))
                    listen != null && (!listen.finished || clip == null || !mayPlay(s.person.id, clip)) -> s.copy(listen = null)
                    else -> s
                }
            } else if (clip != null && !mayPlay(s.person.id, clip)) {
                unavailableReplies += clip.key
                cacheReplies()
                dispatch(VoiceEvent.ReplyUnavailable)
                return
            }
        }
        onChange(state)
    }

    private fun unplayed(): Map<String, List<ReplyClip>> = try {
        replies?.unplayed().orEmpty().mapValues { (_, clips) -> clips.filterNot { it.key in unavailableReplies } }
    } catch (_: Exception) { emptyMap() }
    private fun replyQueue(personId: String): List<ReplyClip> = unplayed()[personId].orEmpty()
    private fun cacheReplies() { newFrom = unplayed().filterValues { it.isNotEmpty() }.keys.toSet() }

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
        val person = people().firstOrNull { it.id == place.personId } ?: return null
        val draft = safeDraft(person.id)
        return when (place.screen) {
            VoicePlace.Screen.MicOff -> VoiceState.MicUnavailable(person, place.problem ?: MicProblem.RecorderError, draft)
            VoicePlace.Screen.Review -> draft?.let { VoiceState.Review(person, it, notice = place.notice) }
                ?: VoiceState.Person(person)
            VoicePlace.Screen.Person -> draft?.let { VoiceState.Review(person, it) }
                ?: VoiceState.Person(person, place.notice, replyQueue(person.id).takeIf { it.isNotEmpty() }?.let(::ReplyListen))
            VoicePlace.Screen.People -> VoiceState.ChoosePerson
            // Read again from the sender, the outcome shown as it was: never a new send.
            VoicePlace.Screen.Send -> VoicePractice.reduce(VoiceState.ChoosePerson, VoiceEvent.Choose(person, draft,
                if (canSend(person)) safeView(person.id)?.copy(seen = false) else null)).state
        }
    }

    /** Send is offered only when real sending is on and [person] has a number that can be sent to. */
    fun canSend(person: VoicePerson): Boolean = sender != null && sendableNumber(person.id) != null

    /** A failing lookup (an unreadable family list) counts as no number: no Send, no crash. */
    private fun sendableNumber(personId: String): String? =
        try { numbers.number(personId)?.takeIf { VoiceNumber.normalize(it) != null } } catch (_: Exception) { null }

    /** True while no one on the list can be sent to: every screen keeps the practice band and words. */
    fun practice(person: VoicePerson? = state.person): Boolean = person?.let { !canSend(it) } ?: people().none(::canSend)

    /** The sender has news for [personId] (an answer, or a send no longer waited for). Never sends. */
    fun sendChanged(personId: String) {
        val person = state.person?.takeIf { it.id == personId && canSend(it) } ?: return
        safeView(person.id)?.let { dispatch(VoiceEvent.SendUpdated(person.id, it)) }
    }

    fun dispatch(event: VoiceEvent) {
        if ((event == VoiceEvent.Send || event == VoiceEvent.TryAgain) && state.person?.let(::canSend) != true) return
        if (event == VoiceEvent.Listen && !mayListen()) return
        val e = if (event is VoiceEvent.Choose && state == VoiceState.ChoosePerson)
            event.copy(existing = safeDraft(event.person.id),
                send = if (canSend(event.person)) safeView(event.person.id) else null,
                replies = replyQueue(event.person.id)) else event
        val before = state
        val t = VoicePractice.reduce(state, e)
        state = withReplies(before, t.state)
        if (state == VoiceState.ChoosePerson && before != VoiceState.ChoosePerson) cacheReplies()
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

    private fun usesAudio(s: VoiceState) = s is VoiceState.Recording || (s as? VoiceState.Review)?.playing == true ||
        (s as? VoiceState.Person)?.listen?.playing == true

    /**
     * Listen asks again whether the person is still on the list (UX V12a): if not, the button goes and nothing
     * plays. Stop listening always goes through.
     */
    private fun mayListen(): Boolean {
        val s = state as? VoiceState.Person ?: return true
        val listen = s.listen ?: return true
        if (listen.playing) return true
        val clip = listen.queue.getOrNull(listen.index)
        if (clip != null && mayPlay(s.person.id, clip)) return true
        clearReplies(s)
        return false
    }

    private fun mayPlay(personId: String, clip: ReplyClip): Boolean = try {
        replies?.canPlay(personId, clip) == true
    } catch (_: Exception) { false }

    private fun mayPlayPerson(personId: String): Boolean = try {
        replies?.canPlay(personId) == true
    } catch (_: Exception) { false }

    private fun clearReplies(person: VoiceState.Person) {
        stopPlayer()
        safely { speaker.stop() }
        safely { gate.release() }
        state = person.copy(listen = null)
        cacheReplies()
        onChange(state)
    }

    /** Coming back to a person's page from elsewhere (Review after Delete, a too-short take...) offers their replies. */
    private fun withReplies(before: VoiceState, after: VoiceState): VoiceState =
        if (after is VoiceState.Person && after.listen == null && before !is VoiceState.Person && before != VoiceState.ChoosePerson)
            replyQueue(after.person.id).takeIf { it.isNotEmpty() }?.let { after.copy(listen = ReplyListen(it)) } ?: after
        else after

    /** "Hear name" on a person's screen. Not while a reply plays: it would talk over it. */
    fun sayName() {
        if ((state as? VoiceState.Person)?.listen?.playing == true) return
        state.person?.let { safely { speaker.speakOnRequest(VoicePrompt.Name, it) } }
    }

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
                val number = sendableNumber(effect.person.id)
                val view = number?.let { n -> sender?.let { s -> try { s.send(effect.person, n) } catch (_: Exception) { null } } }
                    ?: SendView(SendStatus.NotSent(SendProblem.OtherError), seen = false, failures = 1)
                dispatch(VoiceEvent.SendUpdated(effect.person.id, view))
            }
            is VoiceEffect.SendSeen -> safely { sender?.seen(effect.personId) }
            is VoiceEffect.PlayReply -> playReply(effect)
            is VoiceEffect.ReplyPlayed -> safely { replies?.played(effect.personId, effect.clip) }
        }
    }

    /**
     * Says who the reply is from, then plays it. Audio focus first, so nothing is said or played during a call.
     * An unreadable reply is skipped quietly, without marking it heard. Membership is checked before each clip
     * and again after speech. Callbacks from an earlier clip, or after leaving, do nothing.
     */
    private fun playReply(effect: VoiceEffect.PlayReply) {
        val token = ++replyToken
        fun live() = token == replyToken && (state as? VoiceState.Person)?.let { s ->
            val listen = s.listen
            s.person.id == effect.person.id && listen != null && listen.playing &&
                listen.queue.getOrNull(listen.index)?.key == effect.clip.key
        } == true
        fun unavailable() {
            if (live()) {
                unavailableReplies += effect.clip.key
                cacheReplies()
                dispatch(VoiceEvent.ReplyUnavailable)
            }
        }
        fun authorized(): Boolean {
            if (!mayPlayPerson(effect.person.id)) {
                (state as? VoiceState.Person)?.let(::clearReplies)
                return false
            }
            if (!mayPlay(effect.person.id, effect.clip)) { unavailable(); return false }
            return true
        }
        if (!authorized()) return
        if (!acquire()) { if (live()) dispatch(VoiceEvent.PlaybackFailed); return }
        val start = {
            if (live() && authorized()) try {
                player.playReply(effect.clip,
                    onDone = { if (live() && authorized()) dispatch(VoiceEvent.PlaybackEnded) }, onError = ::unavailable)
            } catch (_: Exception) { unavailable() }
        }
        try { speaker.speakThen(effect.prompt, effect.person, start) } catch (_: Exception) { start() }
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
        if (people().firstOrNull { it.id == personId }?.let(::canSend) != true) return maxMs
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
    private fun stopPlayer() { replyToken++; safely { player.stop() } }
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
