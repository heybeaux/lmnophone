// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.device.voice.VoiceEvent.*
import org.plainphone.device.voice.replies.ReplyClip
import org.plainphone.device.voice.replies.ReplyKey

/** PLA-527, UX V1, V2, V12, V12a: hearing a family person's voice reply. Listening never opens the microphone. */
class VoiceRepliesFlowTest {
    @get:Rule val tmp = TemporaryFolder()

    private class Recorder : VoiceRecorder {
        var starts = 0
        var running = false
        override fun silent() = false
        override fun start(file: File, format: VoiceFormat, maxMs: Long, listener: VoiceRecorder.Listener) {
            starts++; file.writeBytes(format.sample()); running = true; listener.onCaptureStarted()
        }
        override fun stop(): Boolean { running = false; return true }
    }
    private class Player : VoicePlayer {
        val replies = mutableListOf<Long>()
        var stops = 0
        var fail = false
        var done: (() -> Unit)? = null
        var error: (() -> Unit)? = null
        override fun play(file: File, onDone: () -> Unit, onError: () -> Unit) = error("not a draft")
        override fun playReply(clip: ReplyClip, onDone: () -> Unit, onError: () -> Unit) {
            replies += clip.partId
            if (fail) throw IllegalStateException("unreadable")
            done = onDone; error = onError
        }
        override fun stop() { stops++; done = null; error = null }
        fun finish() { val d = done; done = null; d?.invoke() }
    }
    private class Mic : MicAccess {
        var checks = 0
        override fun problem(): MicProblem? { checks++; return null }
    }
    private class Gate : VoiceAudioGate {
        var allow = true
        var held = false
        override fun acquire(): Boolean { if (allow) held = true; return allow }
        override fun release() { held = false }
    }
    /** Records each utterance; one [speakAll] call is one utterance. */
    private class Speaker : VoiceSpeaker {
        val said = mutableListOf<String>()
        var holdThen: (() -> Unit)? = null
        var hold = false
        override fun speak(prompt: VoicePrompt, person: VoicePerson?) { said += "$prompt ${person?.name}" }
        override fun speakAll(prompts: List<Pair<VoicePrompt, VoicePerson?>>) {
            said += prompts.joinToString(" + ") { "${it.first} ${it.second?.name}" }
        }
        override fun speakThen(prompt: VoicePrompt, person: VoicePerson?, then: () -> Unit) {
            speak(prompt, person); if (hold) holdThen = then else then()
        }
        override fun stop() { holdThen = null }
    }
    private class Replies(var clips: Map<String, List<ReplyClip>>) : VoiceReplies {
        val playedLog = mutableListOf<Long>()
        var allowed = true
        override fun unplayed() = clips
        override fun canPlay(personId: String) = allowed
        override fun played(personId: String, clip: ReplyClip) {
            playedLog += clip.partId
            clips = clips.mapValues { (_, v) -> v.filterNot { it.key == clip.key } }.filterValues { it.isNotEmpty() }
        }
    }

    private val sam = VoicePerson("sam", "Sam")
    private val jo = VoicePerson("jo", "Jo")
    private val alex = VoicePerson("alex", "Alex")
    private fun clip(id: Long, person: String = "sam") = ReplyClip(ReplyKey.of(id, id * 1000), person, id * 1000, id * 10, "audio/mp4")
    private val recorder = Recorder()
    private val player = Player()
    private val mic = Mic()
    private val gate = Gate()
    private val speaker = Speaker()
    private val replies = Replies(mapOf("sam" to listOf(clip(1), clip(2)), "alex" to listOf(clip(3, "alex"))))
    private var now = 1_000_000L
    private fun controller() = VoicePracticeController(recorder, player, VoiceDraftStore(tmp.newFolder()) { now }, mic, speaker,
        gate, clock = { now }, people = { listOf(sam, jo, alex) }, replies = replies).also { it.open() }

    @Test fun peopleListMarksNewMessagesAndSaysEachOnceInCardOrder() {
        val c = controller()
        assertTrue(c.hasNewMessage("sam"))
        assertFalse(c.hasNewMessage("jo"))
        assertTrue(c.hasNewMessage("alex"))
        assertEquals(listOf("Intro null + NewFrom Sam + NewFrom Alex"), speaker.said)
    }

    @Test fun withNothingNewThePeopleListSaysOnlyItsUsualWords() {
        replies.clips = emptyMap()
        controller()
        assertEquals(listOf("Intro null"), speaker.said)
    }

    @Test fun openingThePersonOffersListenButNeverPlaysByItself() {
        val c = controller()
        speaker.said.clear()
        c.dispatch(Choose(sam))
        val s = c.state as VoiceState.Person
        assertEquals(listOf(clip(1), clip(2)), s.listen?.queue)
        assertFalse(s.listen!!.playing)
        assertEquals(listOf("PersonNew Sam"), speaker.said)
        assertTrue(player.replies.isEmpty())
        assertFalse(gate.held)
    }

    @Test fun personWithNothingNewGetsTheUsualScreenAndWords() {
        val c = controller()
        speaker.said.clear()
        c.dispatch(Choose(jo))
        assertNull((c.state as VoiceState.Person).listen)
        assertEquals(listOf("Person Jo"), speaker.said)
    }

    @Test fun listenPlaysOldestFirstThenOffersPlayAgain() {
        val c = controller()
        c.dispatch(Choose(sam)); speaker.said.clear()
        c.dispatch(Listen)
        assertEquals(listOf("Reply Sam"), speaker.said)
        assertEquals(listOf(10L), player.replies)
        assertTrue(gate.held)
        assertTrue((c.state as VoiceState.Person).listen!!.playing)
        player.finish()
        assertEquals(listOf(10L), replies.playedLog)
        assertEquals(listOf("Reply Sam", "ReplyNext Sam"), speaker.said)
        assertEquals(listOf(10L, 20L), player.replies)
        player.finish()
        assertEquals(listOf(10L, 20L), replies.playedLog)
        val listen = (c.state as VoiceState.Person).listen!!
        assertFalse(listen.playing)
        assertEquals(1, listen.index)
        assertEquals(listOf("Reply Sam", "ReplyNext Sam", "ReplyEnd Sam"), speaker.said)
        assertFalse(gate.held)
    }

    @Test fun playAgainReplaysTheLastOneHeard() {
        val c = controller()
        c.dispatch(Choose(sam)); c.dispatch(Listen); player.finish(); player.finish()
        speaker.said.clear(); player.replies.clear()
        c.dispatch(Listen)
        assertEquals(listOf(20L), player.replies)
        assertEquals(listOf("Reply Sam"), speaker.said)
        player.finish()
        assertEquals(listOf("Reply Sam", "ReplyEnd Sam"), speaker.said)
        assertEquals(listOf(20L), player.replies)
    }

    @Test fun stopListeningStopsAtOnceAndMarksNothingUnheard() {
        val c = controller()
        c.dispatch(Choose(sam)); c.dispatch(Listen)
        val before = player.stops
        c.dispatch(Stop)
        assertEquals(before + 1, player.stops)
        val listen = (c.state as VoiceState.Person).listen!!
        assertFalse(listen.playing)
        assertTrue(replies.playedLog.isEmpty())
        assertFalse(gate.held)
        player.replies.clear()
        c.dispatch(Listen)
        assertEquals(listOf(10L), player.replies)
        player.finish()
        assertEquals(listOf(10L, 20L), player.replies)
    }

    @Test fun leavingStopsPlaybackAndThePeopleListIsReadAgain() {
        val c = controller()
        c.dispatch(Choose(sam)); c.dispatch(Listen); player.finish()
        c.dispatch(Back)
        assertEquals(VoiceState.ChoosePerson, c.state)
        assertTrue(player.stops >= 1)
        assertNull(player.done)
        assertFalse(gate.held)
        assertTrue(c.hasNewMessage("sam"))
        player.finish()
        assertEquals(listOf(10L), replies.playedLog)
    }

    @Test fun everyInterruptionStopsPlaybackQuietly() {
        for (cause in Interruption.entries) {
            val c = controller()
            c.dispatch(Choose(sam)); c.dispatch(Listen); speaker.said.clear()
            c.close(cause)
            val listen = (c.state as VoiceState.Person).listen!!
            assertFalse("$cause", listen.playing)
            assertNull("$cause", player.done)
            assertFalse("$cause", gate.held)
            assertTrue("$cause", speaker.said.isEmpty())
        }
        assertTrue(replies.playedLog.isEmpty())
    }

    @Test fun speechInterruptedBeforeTheClipStartsNeverPlaysIt() {
        speaker.hold = true
        val c = controller()
        c.dispatch(Choose(sam)); c.dispatch(Listen)
        val pending = speaker.holdThen!!
        c.dispatch(Back)
        pending()
        assertTrue(player.replies.isEmpty())
    }

    @Test fun listeningNeverOpensTheMicrophone() {
        val c = controller()
        c.dispatch(Choose(sam)); c.dispatch(Listen); player.finish(); player.finish(); c.dispatch(Listen); c.dispatch(Stop)
        assertEquals(0, recorder.starts)
        assertEquals(0, mic.checks)
    }

    @Test fun recordWhileListeningStopsPlaybackThenRecordsToTheSamePerson() {
        val c = controller()
        c.dispatch(Choose(sam)); c.dispatch(Listen)
        c.dispatch(Record)
        assertNull(player.done)
        assertEquals(sam, (c.state as VoiceState.Recording).person)
        assertEquals(1, recorder.starts)
    }

    @Test fun anUnreadableClipStopsQuietlyAndStaysNew() {
        player.fail = true
        val c = controller()
        c.dispatch(Choose(sam)); speaker.said.clear()
        c.dispatch(Listen)
        val listen = (c.state as VoiceState.Person).listen!!
        assertFalse(listen.playing)
        assertTrue(replies.playedLog.isEmpty())
        assertFalse(speaker.said.any { it.startsWith("CouldNotPlay") })
        assertFalse(gate.held)
    }

    @Test fun playerErrorMidClipStopsQuietly() {
        val c = controller()
        c.dispatch(Choose(sam)); c.dispatch(Listen); speaker.said.clear()
        player.error!!.invoke()
        assertFalse((c.state as VoiceState.Person).listen!!.playing)
        assertTrue(speaker.said.isEmpty())
        assertTrue(replies.playedLog.isEmpty())
    }

    @Test fun aCallOrRefusedFocusMeansNoPlayback() {
        gate.allow = false
        val c = controller()
        c.dispatch(Choose(sam)); c.dispatch(Listen)
        assertTrue(player.replies.isEmpty())
        assertFalse((c.state as VoiceState.Person).listen!!.playing)
    }

    @Test fun someoneNoLongerOnTheListIsNeverPlayed() {
        val c = controller()
        c.dispatch(Choose(sam))
        replies.allowed = false
        c.dispatch(Listen)
        assertTrue(player.replies.isEmpty())
        assertNull((c.state as VoiceState.Person).listen)
    }

    @Test fun withoutRepliesNothingChanges() {
        val c = VoicePracticeController(recorder, player, VoiceDraftStore(tmp.newFolder()) { now }, mic, speaker, gate,
            clock = { now }, people = { listOf(sam) }).also { it.open() }
        assertFalse(c.hasNewMessage("sam"))
        c.dispatch(Choose(sam))
        assertNull((c.state as VoiceState.Person).listen)
        c.dispatch(Listen)
        assertTrue(player.replies.isEmpty())
    }

    @Test fun anUnsentMessageStillShowsFirstAndTheReplyWaits() {
        val c = controller()
        c.dispatch(Choose(sam)); c.dispatch(Record); now += 2_000; c.dispatch(Stop)
        c.dispatch(Back)
        c.dispatch(Choose(sam))
        assertTrue(c.state is VoiceState.Review)
        assertTrue(c.hasNewMessage("sam"))
    }

    @Test fun refreshPicksUpANewReplyOnThePeopleList() {
        replies.clips = emptyMap()
        val c = controller()
        var changes = 0
        c.onChange = { changes++ }
        replies.clips = mapOf("jo" to listOf(clip(5, "jo")))
        c.refreshReplies()
        assertTrue(c.hasNewMessage("jo"))
        assertEquals(1, changes)
    }

    @Test fun repliesFailingToReadShowNothingNew() {
        val broken = object : VoiceReplies {
            override fun unplayed(): Map<String, List<ReplyClip>> = throw SecurityException("no READ_SMS")
            override fun canPlay(personId: String) = throw SecurityException("no READ_SMS")
            override fun played(personId: String, clip: ReplyClip) = throw SecurityException("no READ_SMS")
        }
        val c = VoicePracticeController(recorder, player, VoiceDraftStore(tmp.newFolder()) { now }, mic, speaker, gate,
            clock = { now }, people = { listOf(sam) }, replies = broken).also { it.open() }
        assertFalse(c.hasNewMessage("sam"))
        c.dispatch(Choose(sam))
        assertNull((c.state as VoiceState.Person).listen)
    }

    @Test fun reducerNeverStartsPlaybackWithoutListen() {
        val person = VoiceState.Person(sam, listen = ReplyListen(listOf(clip(1))))
        for (e in listOf(Play, PlaybackEnded, PlaybackFailed, Stop, Interrupted(Interruption.Call), Done, Send, TryAgain)) {
            assertFalse("$e", VoicePractice.reduce(person, e).effects.any { it is VoiceEffect.PlayReply })
        }
    }
}
