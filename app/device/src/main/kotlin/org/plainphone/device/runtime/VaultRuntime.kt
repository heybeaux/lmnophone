// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.runtime

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.plainphone.core.BootTime
import org.plainphone.core.RunningTime
import org.plainphone.device.storage.RecordFile
import org.plainphone.policy.ClockRecord
import org.plainphone.policy.ClockStateStore
import org.plainphone.policy.KeyDerivation
import org.plainphone.policy.SecretEntropy

/**
 * PBKDF2-HMAC-SHA256 with a 256-bit output, the derivation lanes.md assigns to lane D. The caller's
 * secret is copied into the key spec and that copy is cleared; the caller's array is never changed.
 */
object Pbkdf2KeyDerivation : KeyDerivation {
    override fun derive(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret, salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

/** Cryptographically secure random bytes for salts and recovery codes. */
object SecureEntropy : SecretEntropy {
    private val random = SecureRandom()
    override fun bytes(count: Int): ByteArray = ByteArray(count).also { random.nextBytes(it) }
}

/**
 * The [ClockStateStore] for [org.plainphone.policy.ProvenRunningClock]: a checksummed record with a
 * last-known-good copy in device-protected storage. Falling back to the older copy can only make the
 * proven time shorter, never longer, so it never opens a wait early. False is "not acknowledged":
 * the clock then grants no reading, and the next checkpoint reads again.
 */
class FileClockStateStore(dir: File) : ClockStateStore {
    private val file = RecordFile(dir, "clock", SCHEMA)

    @Synchronized
    override fun read(): ClockRecord? = (file.readPrimary()?.let(::decode) ?: file.readLastKnownGood()?.let(::decode))

    @Synchronized
    override fun compareAndSet(expected: ClockRecord?, replacement: ClockRecord): Boolean {
        if (read() != expected) return false
        return file.save(encode(replacement))
    }

    private fun encode(r: ClockRecord): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use {
            it.writeUTF(r.proven.epoch); it.writeLong(r.proven.totalMillis)
            it.writeUTF(r.checkpoint.bootId); it.writeLong(r.checkpoint.elapsedMillis)
        }
    }.toByteArray()

    private fun decode(payload: ByteArray): ClockRecord? = try {
        val input = DataInputStream(ByteArrayInputStream(payload))
        val record = ClockRecord(RunningTime(input.readUTF(), input.readLong()), BootTime(input.readUTF(), input.readLong()))
        if (input.available() != 0) null else record
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object { const val SCHEMA = 1 }
}
