// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice.send

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.device.voice.VoiceDraftStore
import org.plainphone.device.voice.VoiceFormat
import org.plainphone.device.voice.VoicePerson
import org.plainphone.device.voice.sample

/**
 * PLA-499 with the PLA-500 family seam: each draft context sends from, and after a confirmed Sent erases from,
 * its own drafts only, even when a practice person and a family person share an id, and even when the answer
 * arrives after a restart.
 */
class VoiceSendChannelTest {
    @Test fun aResultWithNoChannelIsPracticeBecauseOlderSendsCarryNone() {
        assertEquals(VoiceSendChannel.Practice, VoiceSendChannel.fromExtra(null))
    }

    @Test fun knownKeysRoundTripAndUnknownKeysAreRefused() {
        VoiceSendChannel.entries.forEach { assertEquals(it, VoiceSendChannel.fromExtra(it.key)) }
        assertNull(VoiceSendChannel.fromExtra("Family"))
        assertNull(VoiceSendChannel.fromExtra(""))
        assertNull(VoiceSendChannel.fromExtra("../voice-practice"))
    }

    @Test fun foldersMatchTheDraftOwnersAndNeverOverlap() {
        assertEquals("voice-practice", VoiceSendChannel.Practice.draftDir)
        assertEquals("voice-send", VoiceSendChannel.Practice.recordDir)
        assertEquals("voice-family-drafts", VoiceSendChannel.Family.draftDir)
        val all = VoiceSendChannel.entries.flatMap { listOf(it.draftDir, it.recordDir) }
        assertEquals(all.size, all.toSet().size)
        assertFalse(all.contains(VoiceSendFiles.OUT_DIR))
        assertFalse(all.contains("voice-family"))
    }

    private val root: File = Files.createTempDirectory("voice-send-channel").toFile()
    private val now = 1_000_000L
    private val port = VoiceSenderTest.FakeMmsPort()
    private var ids = 0

    private fun drafts(channel: VoiceSendChannel) =
        VoiceDraftStore(File(root, channel.draftDir), VoiceFormat.AacM4a) { now }

    private fun sender(channel: VoiceSendChannel) =
        VoiceSender(VoiceSendStore(File(root, channel.recordDir)), drafts(channel), port, "p1", { now }) {
            "attempt${++ids}"
        }

    private fun record(channel: VoiceSendChannel, bytes: ByteArray) {
        val store = drafts(channel)
        store.prepareTake("sam").writeBytes(bytes)
        assertNotNull(store.keepTake("sam"))
    }

    @Test fun aFamilySendWithACollidingIdNeverReadsOrErasesThePracticeDraft() {
        val practiceAudio = VoiceFormat.AacM4a.sample(3_000)
        val familyAudio = VoiceFormat.AacM4a.sample(5_000)
        record(VoiceSendChannel.Practice, practiceAudio)
        record(VoiceSendChannel.Family, familyAudio)
        val family = sender(VoiceSendChannel.Family)
        val practice = sender(VoiceSendChannel.Practice)

        assertEquals(SendStatus.Sending, family.send(VoicePerson("sam", "Sam"), "555-0101").status)
        val (_, attempt, pdu) = port.sent.single()
        assertTrue(MmsPduReader.read(pdu).parts.single().data.contentEquals(familyAudio))

        assertFalse("practice has no such send", practice.onResult("sam", attempt, MmsSendResult.RESULT_OK, VoiceSenderTest.OK_CONF))
        assertNull(practice.view("sam"))
        assertTrue(family.onResult("sam", attempt, MmsSendResult.RESULT_OK, VoiceSenderTest.OK_CONF))
        assertEquals(SendStatus.Sent, family.view("sam")!!.status)
        assertNull("family draft erased after Sent", drafts(VoiceSendChannel.Family).draft("sam"))
        assertNotNull("practice draft kept", drafts(VoiceSendChannel.Practice).draft("sam"))
        assertTrue(drafts(VoiceSendChannel.Practice).file("sam").readBytes().contentEquals(practiceAudio))
    }

    @Test fun aLateFamilyResultAfterRestartReachesOnlyTheFamilyRecord() {
        record(VoiceSendChannel.Family, VoiceFormat.AacM4a.sample(4_000))
        record(VoiceSendChannel.Practice, VoiceFormat.AacM4a.sample(2_000))
        sender(VoiceSendChannel.Family).send(VoicePerson("sam", "Sam"), "555-0101")
        val attempt = port.sent.single().second

        val restarted = VoiceSender(VoiceSendStore(File(root, VoiceSendChannel.Family.recordDir)),
            drafts(VoiceSendChannel.Family), port, "p2", { now }) { "attempt${++ids}" }
        assertEquals(SendStatus.NotSent(SendProblem.NotConfirmed), restarted.view("sam")!!.status)
        assertTrue(restarted.onResult("sam", attempt, MmsSendResult.RESULT_OK, VoiceSenderTest.OK_CONF))
        assertEquals(SendStatus.Sent, restarted.view("sam")!!.status)
        assertNotNull(drafts(VoiceSendChannel.Practice).draft("sam"))
        assertNull(sender(VoiceSendChannel.Practice).view("sam"))
    }
}
