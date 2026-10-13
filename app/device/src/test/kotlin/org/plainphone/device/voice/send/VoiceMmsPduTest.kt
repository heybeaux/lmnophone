// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice.send

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLA-499 scope 1: one m-send-req with one audio/mp4 part, the To address and no text part. */
class VoiceMmsPduTest {
    private val audio = ByteArray(5_000) { (it * 31 + 7).toByte() }

    @Test fun theSendRequestDecodesBackToOneAudioPartAndTheAddress() {
        val pdu = VoiceMmsPdu.sendRequest("555-0100", audio, dateSeconds = 1_700_000_000L)
        val read = MmsPduReader.read(pdu)
        assertEquals(MmsPduReader.MESSAGE_TYPE_SEND_REQ, read.messageType)
        assertEquals(listOf("5550100/TYPE=PLMN"), read.to)
        assertEquals("application/vnd.wap.multipart.mixed", read.contentType)
        assertEquals(1, read.parts.size)
        val part = read.parts.single()
        assertEquals("audio/mp4", part.contentType)
        assertEquals("voice.m4a", part.name)
        assertEquals("voice.m4a", part.contentLocation)
        assertEquals("<voice.m4a>", part.contentId)
        assertArrayEquals(audio, part.data)
        assertTrue("no text or SMIL part", read.parts.none { it.contentType.startsWith("text/") || it.contentType.contains("smil") })
    }

    @Test fun anInternationalNumberKeepsItsPlusAndLosesItsFormatting() {
        val pdu = VoiceMmsPdu.sendRequest("+1 (555) 010-0123", audio, dateSeconds = 1L)
        assertEquals(listOf("+15550100123/TYPE=PLMN"), MmsPduReader.read(pdu).to)
    }

    @Test fun anythingThatIsNotAPhoneNumberIsRefused() {
        for (bad in listOf("", "   ", "abc", "555-01OO", "+", "12", "5550100;5550101", "sam@example.com", "1".repeat(21))) {
            assertNull(bad, VoiceNumber.normalize(bad))
            assertThrows(bad) { VoiceMmsPdu.sendRequest(bad, audio, 1L) }
        }
    }

    @Test fun anEmptyRecordingIsRefused() {
        assertThrows("empty") { VoiceMmsPdu.sendRequest("555-0100", ByteArray(0), 1L) }
    }

    @Test fun theHeaderOverheadStaysInsideTheBudgetForTheLongestNumber() {
        val pdu = VoiceMmsPdu.sendRequest("+" + "5".repeat(20), audio, 1L)
        assertTrue("overhead ${pdu.size - audio.size}", pdu.size - audio.size <= VoiceSendLimits.PDU_OVERHEAD_BYTES)
    }

    @Test fun aSendConfirmationWithStatusOkIsRead() {
        // The PLA-458 fake MMSC's answer: m-send-conf, transaction id, version, Response-Status Ok, Message-ID.
        val conf = byteArrayOf(0x8C.toByte(), 0x81.toByte(), 0x98.toByte()) + "T18a2b".toByteArray() + byteArrayOf(0,
            0x8D.toByte(), 0x92.toByte(), 0x92.toByte(), 0x80.toByte(), 0x8B.toByte()) + "fake-mmsc-1".toByteArray() + byteArrayOf(0)
        assertEquals(0x80, MmsPduReader.sendConfStatus(conf))
    }

    @Test fun otherStatusesAndOtherMessagesAreNotAnOkConfirmation() {
        val refused = byteArrayOf(0x8C.toByte(), 0x81.toByte(), 0x98.toByte(), 'T'.code.toByte(), 0,
            0x8D.toByte(), 0x92.toByte(), 0x92.toByte(), 0xE0.toByte())
        assertEquals(0xE0, MmsPduReader.sendConfStatus(refused))
        val noStatus = byteArrayOf(0x8C.toByte(), 0x81.toByte(), 0x98.toByte(), 'T'.code.toByte(), 0)
        assertNull(MmsPduReader.sendConfStatus(noStatus))
        assertNull("an m-send-req is not a confirmation",
            MmsPduReader.sendConfStatus(VoiceMmsPdu.sendRequest("555-0100", audio, 1L)))
        assertNull(MmsPduReader.sendConfStatus(null))
        assertNull(MmsPduReader.sendConfStatus(ByteArray(0)))
        assertNull("truncated", MmsPduReader.sendConfStatus(byteArrayOf(0x8C.toByte(), 0x81.toByte(), 0x98.toByte(), 'T'.code.toByte())))
        assertNull("garbage", MmsPduReader.sendConfStatus(ByteArray(64) { 0x1F }))
    }

    private fun assertThrows(label: String, block: () -> Unit) {
        try { block() } catch (_: IllegalArgumentException) { return }
        throw AssertionError("expected IllegalArgumentException: $label")
    }
}
