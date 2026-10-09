// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordCodecTest {
    private val payload = "com.example.maps\n".toByteArray()

    @Test fun roundTrips() {
        assertArrayEquals(payload, RecordCodec.decode(RecordCodec.encode(3, payload), schemaVersion = 3))
    }

    @Test fun emptyPayloadRoundTrips() {
        assertArrayEquals(ByteArray(0), RecordCodec.decode(RecordCodec.encode(1, ByteArray(0)), schemaVersion = 1))
    }

    @Test fun rejectsOtherSchema() {
        assertNull(RecordCodec.decode(RecordCodec.encode(2, payload), schemaVersion = 1))
    }

    @Test fun rejectsEveryTruncation() {
        val bytes = RecordCodec.encode(1, payload)
        for (n in 0 until bytes.size) assertNull("length $n", RecordCodec.decode(bytes.copyOf(n), 1))
    }

    @Test fun rejectsTrailingBytes() {
        assertNull(RecordCodec.decode(RecordCodec.encode(1, payload) + byteArrayOf(0), 1))
    }

    @Test fun rejectsEverySingleBitFlip() {
        val bytes = RecordCodec.encode(1, payload)
        for (i in bytes.indices) for (bit in 0 until 8) {
            val bad = bytes.copyOf()
            bad[i] = (bad[i].toInt() xor (1 shl bit)).toByte()
            assertNull("byte $i bit $bit", RecordCodec.decode(bad, 1))
        }
    }

    @Test fun rejectsALengthFieldThatLies() {
        val bytes = RecordCodec.encode(1, payload)
        bytes[11] = 0x7f
        assertNull(RecordCodec.decode(bytes, 1))
    }
}
