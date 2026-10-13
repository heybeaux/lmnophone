// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice.send

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PLA-499 scope 2: Sent only on RESULT_OK and a parsed m-send-conf with Response-Status Ok. Everything else is
 * Not sent with one of the reasons. Codes are SmsManager.MMS_ERROR_* (developer.android.com, API 36 android.jar).
 */
class MmsSendResultTest {
    private val ok = conf(0x80)

    @Test fun everyResultCodeMapsToOneTruthfulStatus() {
        val table = listOf(
            Triple(-1, ok, SendStatus.Sent),
            Triple(-1, conf(0xE0), SendStatus.NotSent(SendProblem.OtherError)),
            Triple(-1, conf(0xC1), SendStatus.NotSent(SendProblem.OtherError)),
            Triple(-1, null, SendStatus.NotSent(SendProblem.OtherError)),
            Triple(-1, ByteArray(3), SendStatus.NotSent(SendProblem.OtherError)),
            Triple(0, ok, SendStatus.NotSent(SendProblem.OtherError)),
            Triple(1, ok, SendStatus.NotSent(SendProblem.OtherError)),
            Triple(2, null, SendStatus.NotSent(SendProblem.OtherError)),
            Triple(3, null, SendStatus.NotSent(SendProblem.NoSignal)),
            Triple(4, null, SendStatus.NotSent(SendProblem.OtherError)),
            Triple(5, null, SendStatus.NotSent(SendProblem.OtherError)),
            Triple(6, null, SendStatus.NotSent(SendProblem.OtherError)),
            Triple(7, null, SendStatus.NotSent(SendProblem.OtherError)),
            Triple(8, null, SendStatus.NotSent(SendProblem.NoSignal)),
            Triple(9, null, SendStatus.NotSent(SendProblem.OtherError)),
            Triple(10, null, SendStatus.NotSent(SendProblem.OtherError)),
            Triple(11, null, SendStatus.NotSent(SendProblem.NoSignal)),
            Triple(12, null, SendStatus.NotSent(SendProblem.MmsOff)),
            Triple(12, ok, SendStatus.NotSent(SendProblem.MmsOff)),
            Triple(99, ok, SendStatus.NotSent(SendProblem.OtherError)),
            Triple(-2, ok, SendStatus.NotSent(SendProblem.OtherError)),
        )
        for ((code, data, expected) in table) assertEquals("code $code", expected, MmsSendResult.map(code, data))
    }

    private fun conf(status: Int) = byteArrayOf(0x8C.toByte(), 0x81.toByte(), 0x98.toByte(), 'T'.code.toByte(), 0,
        0x8D.toByte(), 0x92.toByte(), 0x92.toByte(), status.toByte())

    @Test fun anOkStatusInsideADamagedConfirmationIsNeverSent() {
        val malformed = listOf(
            ok + byteArrayOf(0x8B.toByte()),
            ok + byteArrayOf(0x8B.toByte(), 'T'.code.toByte()),
            ok + byteArrayOf(0x92.toByte(), 0xE0.toByte()),
            ok + byteArrayOf(0x8C.toByte(), 0x80.toByte()),
            byteArrayOf(0x8C.toByte(), 0x81.toByte(), 0x92.toByte(), 0x80.toByte()),
            byteArrayOf(0x8C.toByte(), 0x81.toByte(), 0x98.toByte(), 'T'.code.toByte(), 0, 0x92.toByte(), 0x80.toByte()),
        )
        for ((index, data) in malformed.withIndex()) {
            assertEquals("damaged confirmation $index", SendStatus.NotSent(SendProblem.OtherError),
                MmsSendResult.map(MmsSendResult.RESULT_OK, data))
        }
    }

    @Test fun aCompleteConfirmationMayHaveHeadersAfterTheStatus() {
        val data = ok + byteArrayOf(0x8B.toByte(), 'T'.code.toByte(), 0)
        assertEquals(SendStatus.Sent, MmsSendResult.map(MmsSendResult.RESULT_OK, data))
    }
}
