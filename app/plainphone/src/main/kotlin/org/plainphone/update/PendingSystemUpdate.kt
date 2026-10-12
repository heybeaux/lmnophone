// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.update

import java.io.File
import java.security.MessageDigest
import org.plainphone.ui.home.HomeNotice

/**
 * The time Android first reported a pending system update (`onSystemUpdatePending`), kept in [file] with
 * an opaque key for the build it was reported on ([buildKey]). Android's -1 ("no update pending") clears
 * it, and so does running a different build: the update has landed and the "before" warning is over. A
 * damaged record is removed. The record holds no fingerprint or serial.
 */
class PendingSystemUpdate(private val file: File, private val build: String) {
    /** Records [receivedTime], or clears with a negative one. Returns whether an update is pending. */
    fun onPending(receivedTime: Long): Boolean {
        if (receivedTime < 0) {
            file.delete()
            return false
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText("v1\n$receivedTime\n$build\n")
        if (!tmp.renameTo(file)) {
            tmp.delete()
            return false
        }
        return true
    }

    fun pending(): Boolean = receivedTime() != null

    /** The recorded time for this build, or null (clearing a stale or damaged record). */
    fun receivedTime(): Long? {
        if (!file.exists()) return null
        val lines = try { file.readLines() } catch (_: java.io.IOException) { emptyList() }
        val time = lines.getOrNull(1)?.toLongOrNull()
        if (lines.size == 3 && lines[0] == "v1" && time != null && time >= 0 && lines[2] == build) return time
        file.delete()
        return null
    }
}

/** An opaque per-build key: a short SHA-256 of the build fingerprint, never the fingerprint itself. */
fun buildKey(fingerprint: String): String =
    MessageDigest.getInstance("SHA-256").digest(fingerprint.toByteArray()).take(8)
        .joinToString("") { "%02x".format(it) }

/** Home's one notice line: an existing line wins; otherwise a pending update shows its calm line. */
fun withUpdateLine(base: HomeNotice?, updatePending: Boolean): HomeNotice? =
    base ?: if (updatePending) HomeNotice.SoftwareUpdateWaiting else null
