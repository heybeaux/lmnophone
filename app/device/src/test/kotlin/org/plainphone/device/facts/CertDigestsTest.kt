// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.facts

import org.junit.Assert.assertEquals
import org.junit.Test

class CertDigestsTest {
    @Test fun hexEncodingMatchesExistingFormatForEveryByteIncludingSignedAndLeadingZeroBytes() {
        val bytes = (0..255).map { it.toByte() }.toByteArray()
        val existing = bytes.joinToString("") { "%02x".format(it) }
        assertEquals(existing, CertDigests.hex(bytes))
        assertEquals("", CertDigests.hex(byteArrayOf()))
        assertEquals("00ff018010", CertDigests.hex(byteArrayOf(0, -1, 1, -128, 16)))
    }

    @Test fun sha256StillMatchesKnownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            CertDigests.sha256(byteArrayOf()).value)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            CertDigests.sha256("abc".toByteArray()).value)
    }
}
