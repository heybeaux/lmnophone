// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.runtime

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
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
 * The [ClockStateStore] for [org.plainphone.policy.ProvenRunningClock]: one checksummed record in
 * device-protected storage, replaced by synced rename so a power cut leaves the old or the new
 * tuple. There is no fallback copy. A missing or damaged record reads null, so the clock repairs it
 * to a fresh zero epoch; an older copy could restore a retired epoch whose total would mature a
 * newer wait at once (PLA-248). A `clock.lkg` left by an earlier build is ignored.
 *
 * Every instance for one directory, including symlinked aliases, shares one lock keyed by the
 * canonical path, and each compare-and-set also holds an OS lock on `clock.lock` against another
 * process. The comparison, the write and its readback all run under both. A writer waits at most
 * [lockWaitMillis] and is then refused. False is "not acknowledged": the clock then grants no
 * reading, and the next checkpoint reads again.
 */
class FileClockStateStore(dir: File) : ClockStateStore {
    private val file = RecordFile(dir, "clock", SCHEMA, keepLastKnownGood = false)
    private val lockFile = File(dir, "clock.lock")
    private val lock = locks.computeIfAbsent(canonical(dir)) { ReentrantLock() }

    override fun read(): ClockRecord? = file.readPrimary()?.let(::decode)

    override fun compareAndSet(expected: ClockRecord?, replacement: ClockRecord): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(lockWaitMillis)
        try {
            if (!lock.tryLock(lockWaitMillis, TimeUnit.MILLISECONDS)) return false
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt(); return false
        }
        try {
            return FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
                val held = processLock(channel, deadline) ?: return false
                held.use { read() == expected && file.save(encode(replacement)) && read() == replacement }
            }
        } catch (_: IOException) {
            return false
        } finally {
            lock.unlock()
        }
    }

    /** Polls for the OS lock until [deadline]; null when another process still holds it. */
    private fun processLock(channel: FileChannel, deadline: Long): FileLock? {
        while (true) {
            channel.tryLock()?.let { return it }
            if (System.nanoTime() >= deadline) return null
            try { Thread.sleep(10) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); return null }
        }
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

    companion object {
        /** Longest wait for another writer's compare-and-set before this one is refused. */
        @Volatile internal var lockWaitMillis: Long = 10_000
        private const val SCHEMA = 1
        private val locks = ConcurrentHashMap<String, ReentrantLock>()
        /** Creates [dir] if needed so symlinks resolve; falls back to the normalized absolute path. */
        private fun canonical(dir: File): String = try {
            dir.mkdirs(); dir.canonicalPath
        } catch (_: IOException) {
            dir.absoluteFile.normalize().path
        }
    }
}
