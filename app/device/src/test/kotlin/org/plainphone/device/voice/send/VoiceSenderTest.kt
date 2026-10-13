// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice.send

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
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
 * PLA-499 scope 2, 3, 5 and 6: truthful states, persisted before the hand-off, never sent or re-sent on its own,
 * and the draft erased only after a confirmed Sent of that same draft.
 */
class VoiceSenderTest {
    private val root: File = Files.createTempDirectory("voice-send").toFile()
    private var now = 1_000_000L
    private val drafts = VoiceDraftStore(File(root, "drafts"), VoiceFormat.AacM4a) { now }
    private val store = VoiceSendStore(File(root, "send"))
    private val port = FakeMmsPort()
    private var ids = 0
    private val sam = VoicePerson("sam", "Sam", "555-0100")

    private fun sender(process: String = "p1") =
        VoiceSender(store, drafts, port, process, { now }) { "attempt${++ids}" }

    private fun record(bytes: ByteArray = VoiceFormat.AacM4a.sample(4_000)): ByteArray {
        drafts.prepareTake("sam").writeBytes(bytes)
        assertNotNull(drafts.keepTake("sam"))
        return bytes
    }

    @Test fun sendHandsOnePduToTheSystemAndSaysSendingUntilTheResult() {
        val audio = record()
        val s = sender()
        assertEquals(SendStatus.Sending, s.send(sam).status)
        assertEquals("the record says Sending before the hand-off", SendStatus.Sending, port.statusAtHandOff)
        val (person, attempt, pdu) = port.sent.single()
        assertEquals("sam", person)
        assertEquals("attempt1", attempt)
        val read = MmsPduReader.read(pdu)
        assertEquals(listOf("5550100/TYPE=PLMN"), read.to)
        assertArrayEquals(audio, read.parts.single().data)
        assertEquals(SendStatus.Sending, s.view("sam")!!.status)
    }

    @Test fun okWithAnOkConfirmationIsSentAndErasesOurDraft() {
        record()
        val s = sender()
        val updates = mutableListOf<String>()
        s.onUpdate = { updates += it }
        s.send(sam)
        assertTrue(s.onResult("sam", "attempt1", MmsSendResult.RESULT_OK, OK_CONF))
        assertEquals(SendView(SendStatus.Sent, seen = false, failures = 0), s.view("sam"))
        assertNull(drafts.draft("sam"))
        assertEquals(listOf("attempt1"), port.released)
        assertEquals(listOf("sam"), updates)
    }

    @Test fun okWithoutAConfirmationIsNotSentAndKeepsTheDraft() {
        record()
        val s = sender()
        s.send(sam)
        s.onResult("sam", "attempt1", MmsSendResult.RESULT_OK, null)
        assertEquals(SendStatus.NotSent(SendProblem.OtherError), s.view("sam")!!.status)
        assertNotNull(drafts.draft("sam"))
    }

    @Test fun aFailureKeepsTheDraftAndCountsTriesButNeverSendsAgainByItself() {
        record()
        val s = sender()
        s.send(sam)
        s.onResult("sam", "attempt1", 4, null)
        assertEquals(SendView(SendStatus.NotSent(SendProblem.OtherError), seen = false, failures = 1), s.view("sam"))
        assertNotNull(drafts.draft("sam"))
        repeat(3) { s.view("sam"); s.seen("sam") }
        assertEquals("no automatic resend", 1, port.sent.size)
        s.send(sam)
        s.onResult("sam", "attempt2", 8, null)
        assertEquals(SendView(SendStatus.NotSent(SendProblem.NoSignal), seen = false, failures = 2), s.view("sam"))
        assertEquals(2, port.sent.size)
    }

    @Test fun aStaleResultNeverOverwritesANewerSendOrErasesItsDraft() {
        record()
        val s = sender()
        s.send(sam)
        s.onResult("sam", "attempt1", 4, null)
        s.send(sam)
        assertFalse(s.onResult("sam", "attempt1", MmsSendResult.RESULT_OK, OK_CONF))
        assertEquals(SendStatus.Sending, s.view("sam")!!.status)
        assertNotNull(drafts.draft("sam"))
        assertTrue("the stale attempt's file is still cleaned up", "attempt1" in port.released)
        assertFalse(s.onResult("jo", "attempt2", MmsSendResult.RESULT_OK, OK_CONF))
        assertFalse(s.onResult("../sam", "attempt2", MmsSendResult.RESULT_OK, OK_CONF))
    }

    @Test fun aSentResultKeepsANewerRecording() {
        record()
        val s = sender()
        s.send(sam)
        now += 5_000
        val newer = record(VoiceFormat.AacM4a.sample(6_000))
        s.onResult("sam", "attempt1", MmsSendResult.RESULT_OK, OK_CONF)
        assertEquals(SendStatus.Sent, s.view("sam")!!.status)
        assertArrayEquals(newer, drafts.file("sam").readBytes())
    }

    @Test fun noPermissionIsNotSentAndNothingIsHandedOver() {
        record()
        port.permitted = false
        val s = sender()
        assertEquals(SendStatus.NotSent(SendProblem.NoPermission), s.send(sam).status)
        assertTrue(port.sent.isEmpty())
        assertEquals(SendStatus.NotSent(SendProblem.NoPermission), s.view("sam")!!.status)
    }

    @Test fun aSecurityExceptionAtHandOffIsNoPermissionNeverACrash() {
        record()
        port.failure = SecurityException("no SEND_SMS")
        val s = sender()
        assertEquals(SendStatus.NotSent(SendProblem.NoPermission), s.send(sam).status)
        assertEquals(listOf("attempt1"), port.released)
        assertEquals(SendStatus.NotSent(SendProblem.NoPermission), s.view("sam")!!.status)
    }

    @Test fun anyOtherHandOffFailureIsOtherError() {
        record()
        port.failure = IllegalStateException("binder")
        assertEquals(SendStatus.NotSent(SendProblem.OtherError), sender().send(sam).status)
    }

    @Test fun mmsTurnedOffByTheCarrierIsNotSentBeforeAnyHandOff() {
        record()
        port.carrier = CarrierMms(enabled = false, maxMessageBytes = 1_048_576)
        assertEquals(SendStatus.NotSent(SendProblem.MmsOff), sender().send(sam).status)
        assertTrue(port.sent.isEmpty())
    }

    @Test fun noSignalAtTheTapIsNotSentButUnknownSignalStillTries() {
        record()
        port.signal = false
        assertEquals(SendStatus.NotSent(SendProblem.NoSignal), sender().send(sam).status)
        assertTrue(port.sent.isEmpty())
        port.signal = null
        assertEquals(SendStatus.Sending, sender().send(sam).status)
    }

    @Test fun aMessageOverTheCarrierLimitIsTooBigAndNeverTruncated() {
        val audio = record(VoiceFormat.AacM4a.sample(9_000))
        port.carrier = CarrierMms(enabled = true, maxMessageBytes = 9_000)
        assertEquals(SendStatus.NotSent(SendProblem.TooBig), sender().send(sam).status)
        assertTrue(port.sent.isEmpty())
        assertArrayEquals(audio, drafts.file("sam").readBytes())
        port.carrier = null
        assertEquals("unknown settings use the Android default limit", SendStatus.Sending, sender().send(sam).status)
    }

    @Test fun noNumberOrNoDraftIsOtherErrorAndNothingIsSent() {
        val s = sender()
        assertEquals(SendStatus.NotSent(SendProblem.OtherError), s.send(sam).status)
        record()
        assertEquals(SendStatus.NotSent(SendProblem.OtherError), s.send(VoicePerson("sam", "Sam")).status)
        assertEquals(SendStatus.NotSent(SendProblem.OtherError), s.send(VoicePerson("sam", "Sam", "not a number")).status)
        assertTrue(port.sent.isEmpty())
    }

    @Test fun afterARestartAnUnansweredSendIsNotConfirmedNeverSent() {
        record()
        sender("p1").send(sam)
        val again = sender("p2")
        assertEquals(SendStatus.NotSent(SendProblem.NotConfirmed), again.view("sam")!!.status)
        assertNotNull(drafts.draft("sam"))
        assertTrue("a late answer for that send still counts", again.onResult("sam", "attempt1", MmsSendResult.RESULT_OK, OK_CONF))
        assertEquals(SendStatus.Sent, again.view("sam")!!.status)
    }

    @Test fun aSendWithNoAnswerForTooLongIsNotConfirmed() {
        record()
        val s = sender()
        s.send(sam)
        now += VoiceSender.UNANSWERED_MS
        assertEquals(SendStatus.Sending, s.view("sam")!!.status)
        now += 1
        assertEquals(SendStatus.NotSent(SendProblem.NotConfirmed), s.view("sam")!!.status)
    }

    @Test fun seenAndForgetChangeOnlyTheRecord() {
        record()
        val s = sender()
        s.send(sam)
        s.onResult("sam", "attempt1", 4, null)
        s.seen("sam")
        assertTrue(s.view("sam")!!.seen)
        s.forget("sam")
        assertNull(s.view("sam"))
        assertNotNull(drafts.draft("sam"))
    }

    @Test fun theRecordNeverHoldsTheNumber() {
        record()
        sender().send(sam)
        val text = File(root, "send").listFiles()!!.joinToString("\n") { it.readText() }
        assertFalse(text, text.contains("5550100") || text.contains("555-0100"))
    }

    @Test fun aDamagedRecordReadsAsNoRecord() {
        File(root, "send").mkdirs()
        File(root, "send/sam.send").writeText("attempt=x\nstatus=bogus\n")
        assertNull(sender().view("sam"))
        File(root, "send/sam.send").writeBytes(ByteArray(10) { -1 })
        assertNull(sender().view("sam"))
    }

    private class FakeMmsPort : MmsPort {
        var permitted = true
        var signal: Boolean? = true
        var carrier: CarrierMms? = CarrierMms(enabled = true, maxMessageBytes = 1_048_576)
        var failure: RuntimeException? = null
        val sent = mutableListOf<Triple<String, String, ByteArray>>()
        val released = mutableListOf<String>()
        var statusAtHandOff: SendStatus? = null
        var peek: () -> SendStatus? = { null }
        override fun carrier() = carrier
        override fun hasSignal() = signal
        override fun permitted() = permitted
        override fun send(personId: String, attemptId: String, pdu: ByteArray) {
            statusAtHandOff = peek()
            failure?.let { throw it }
            sent += Triple(personId, attemptId, pdu)
        }
        override fun release(attemptId: String) { released += attemptId }
    }

    init { port.peek = { store.read("sam")?.status } }

    private companion object {
        val OK_CONF = byteArrayOf(0x8C.toByte(), 0x81.toByte(), 0x98.toByte(), 'T'.code.toByte(), 0,
            0x8D.toByte(), 0x92.toByte(), 0x92.toByte(), 0x80.toByte())
    }
}
