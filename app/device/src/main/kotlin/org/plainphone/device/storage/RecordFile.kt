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
 * Every rename and every directory created here is followed by a sync of its directory, and every
 * save also syncs [dir]'s parent: an existing [dir] may come from an earlier save whose parent sync
 * failed, so existence is no proof that its entry is durable. If a sync fails, the save is not
 * acknowledged. The stores keep [dir] one level below an app directory, so the parent is the whole chain. False means "not acknowledged", not "nothing changed":
 * the new bytes may already be visible and may or may not survive a power cut.
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

    /** True only when the new primary has been synced, renamed, its directory synced, and read back unchanged. */
    fun save(payload: ByteArray): Boolean = try {
        createDirectory()
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
        sync(dir)
    }

    /** Creates any missing directories, syncs the parent of each new one, and always syncs [dir]'s parent. */
    private fun createDirectory() {
        val created = generateSequence(dir.absoluteFile) { it.parentFile }.takeWhile { !it.exists() }.toList()
        if (!dir.mkdirs() && !dir.isDirectory) throw IOException("no directory: $dir")
        (created.reversed().map { it.parentFile } + dir.absoluteFile.parentFile).distinct().forEach { sync(it) }
    }

    /** Makes a rename or a new entry in [directory] durable. Throws when the sync cannot be done. */
    private fun sync(directory: File) {
        FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) }
    }
}
