// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice.replies

import java.io.File
import java.io.IOException

/**
 * Which voice replies a family person's listener has heard to the end, as `<personId>.played` in [dir]: one
 * opaque [ReplyKey] per line, never a number or a message. LMNOPhone isn't the messaging app and never writes
 * to the messaging store, so "heard" lives here. The host passes a folder under noBackupFilesDir
 * (credential-encrypted, never backed up). Removing the person or turning voice messages off erases it.
 */
class ReplyPlayedStore(private val dir: File) {
    fun played(personId: String): Set<String> {
        if (!ID.matches(personId)) return emptySet()
        return try {
            val file = File(dir, "$personId$SUFFIX")
            if (!file.isFile || file.length() > MAX_BYTES) return emptySet()
            val lines = file.readLines(Charsets.US_ASCII).filter { it.isNotEmpty() }
            if (lines.any { !ReplyKey.PATTERN.matches(it) }) emptySet() else lines.toSet()
        } catch (_: IOException) { emptySet() }
    }

    /** @throws IOException when it can't be saved. */
    fun markPlayed(personId: String, key: String) {
        require(ID.matches(personId)) { "not a person id" }
        require(ReplyKey.PATTERN.matches(key)) { "not a reply key" }
        val file = File(dir, "$personId$SUFFIX")
        val kept = (readOrdered(file).filterNot { it == key } + key).takeLast(MAX_KEYS)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("played folder unavailable")
        val tmp = File(dir, "$personId$SUFFIX$TEMP")
        tmp.writeText(kept.joinToString("\n", postfix = "\n"), Charsets.US_ASCII)
        if (!tmp.renameTo(file)) { tmp.delete(); throw IOException("could not save played") }
    }

    /** Removes everything kept for [personId]. Quiet when there is nothing. @throws IOException if any remains. */
    fun erase(personId: String) {
        if (!ID.matches(personId)) return
        listOf(File(dir, "$personId$SUFFIX"), File(dir, "$personId$SUFFIX$TEMP")).forEach { file ->
            if (file.exists() && !file.delete() && file.exists()) throw IOException("could not erase played")
        }
    }

    private fun readOrdered(file: File): List<String> = try {
        if (!file.isFile || file.length() > MAX_BYTES) emptyList()
        else file.readLines(Charsets.US_ASCII).filter { ReplyKey.PATTERN.matches(it) }.distinct()
    } catch (_: IOException) { emptyList() }

    companion object {
        const val DIR = "voice-replies-played"
        /** Far more than the [ReplySelector.LATEST] that can show, so a heard reply never comes back as new. */
        const val MAX_KEYS = 64
        private const val SUFFIX = ".played"
        private const val TEMP = ".tmp"
        private const val MAX_BYTES = 8_192L
        private val ID = Regex("[a-z0-9]{1,32}")
    }
}
