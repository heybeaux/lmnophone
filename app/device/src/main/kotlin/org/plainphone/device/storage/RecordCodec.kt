// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.storage

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * One stored record: magic, schema version, payload length, payload, then a SHA-256 of everything
 * before it. A record that is truncated, padded, altered or from another schema decodes to null.
 */
internal object RecordCodec {
    private val MAGIC = byteArrayOf('P'.code.toByte(), 'P'.code.toByte(), 'R'.code.toByte(), '1'.code.toByte())
    const val HEADER_SIZE = 12
    private const val CHECKSUM_SIZE = 32

    fun encode(schemaVersion: Int, payload: ByteArray): ByteArray {
        val body = ByteBuffer.allocate(HEADER_SIZE + payload.size)
            .put(MAGIC).putInt(schemaVersion).putInt(payload.size).put(payload).array()
        return body + sha256(body)
    }

    fun decode(bytes: ByteArray, schemaVersion: Int): ByteArray? {
        if (bytes.size < HEADER_SIZE + CHECKSUM_SIZE) return null
        val buf = ByteBuffer.wrap(bytes)
        val magic = ByteArray(MAGIC.size).also { buf.get(it) }
        if (!magic.contentEquals(MAGIC)) return null
        val schema = buf.int
        val length = buf.int
        if (length < 0 || bytes.size.toLong() != HEADER_SIZE.toLong() + length + CHECKSUM_SIZE) return null
        val bodySize = HEADER_SIZE + length
        val expected = sha256(bytes.copyOfRange(0, bodySize))
        if (!MessageDigest.isEqual(expected, bytes.copyOfRange(bodySize, bytes.size))) return null
        if (schema != schemaVersion) return null
        return bytes.copyOfRange(HEADER_SIZE, bodySize)
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
