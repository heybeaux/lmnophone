// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.storage

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * A checksummed record kept as `<name>.rec` plus a last-known-good `<name>.lkg` in [dir].
 *
 * Each save writes a temp file, syncs it and renames it over the target, so a power cut leaves
 * either the old or the new bytes. Before the primary is replaced, a primary that still decodes
 * becomes the last-known-good copy; a corrupt primary never overwrites a good backup. Records that
 * must never fall back to an older copy pass [keepLastKnownGood] = false.
 *
 * [beforeRename] is a test seam that runs on the synced temp file just before it is renamed.
 */
internal class RecordFile(
    private val dir: File,
    name: String,
    private val schemaVersion: Int,
    private val keepLastKnownGood: Boolean = true,
    private val beforeRename: (File) -> Unit = {},
) {
    private val primary = File(dir, "$name.rec")
    private val backup = File(dir, "$name.lkg")

    fun readPrimary(): ByteArray? = read(primary)
    fun readLastKnownGood(): ByteArray? = read(backup)

    /** True only when the new primary has been synced, renamed and read back unchanged. */
    fun save(payload: ByteArray): Boolean = try {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("no directory: $dir")
        if (keepLastKnownGood) primary.takeIf { it.isFile }?.readBytes()
            ?.takeIf { RecordCodec.decode(it, schemaVersion) != null }
            ?.let { replace(backup, it) }
        replace(primary, RecordCodec.encode(schemaVersion, payload))
        readPrimary()?.contentEquals(payload) == true
    } catch (e: IOException) {
        false
    }

    private fun read(file: File): ByteArray? = try {
        if (file.isFile) RecordCodec.decode(file.readBytes(), schemaVersion) else null
    } catch (e: IOException) {
        null
    }

    private fun replace(target: File, bytes: ByteArray) {
        val temp = File(dir, "${target.name}.tmp")
        FileOutputStream(temp).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        beforeRename(temp)
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        syncDirectory()
    }

    /** Makes the rename itself durable where the platform allows opening a directory. */
    private fun syncDirectory() {
        try {
            FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) }
        } catch (e: IOException) {
            // Not supported on every file system; the file contents are already synced.
        }
    }
}
