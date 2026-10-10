// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.update

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

/**
 * Private staging for one downloaded update in [dir] (an app-private directory). The directory is
 * owner-only (0700). A download goes into a part file; [seal] makes it owner-read-only (0400) and
 * moves it into place. [staged] returns the sealed file only while it is a regular, non-linked,
 * 0400 file directly inside the 0700 directory, so a file that was replaced, linked or opened up is
 * not used. Holding one file at a time, a new [receive] drops the earlier one.
 */
class UpdateStaging(private val dir: File) {
    private val sealedFile = File(dir, "update.apk")
    private val part = File(dir, "update.apk.part")

    /** Clears staging and returns a new, empty, owner-only part file to write the download into. */
    fun receive(): File? = try {
        if (!clear() || !ownerOnlyDir()) null
        else part.also {
            Files.createFile(it.toPath(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        }
    } catch (_: Exception) {
        null
    }

    /** Makes [file] (the part file from [receive], already written and synced) the staged file. */
    fun seal(file: File): Boolean = try {
        if (file.canonicalFile != part.canonicalFile || !regular(part)) false
        else {
            Files.setPosixFilePermissions(part.toPath(), PosixFilePermissions.fromString("r--------"))
            Files.move(part.toPath(), sealedFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
            syncDir()
            staged() != null
        }
    } catch (_: Exception) {
        false
    }

    /** The sealed staged file, or null when there is none or it is no longer private and read-only. */
    fun staged(): File? = try {
        sealedFile.takeIf {
            regular(it) && perms(it) == READ_ONLY && perms(dir) == OWNER_ONLY &&
                !Files.isSymbolicLink(dir.toPath()) && it.canonicalFile.parentFile == dir.canonicalFile
        }
    } catch (_: Exception) {
        null
    }

    /** Removes every file in staging; false when one is left. */
    fun clear(): Boolean {
        dir.listFiles()?.forEach { if (!Files.deleteIfExists(it.toPath())) return false }
        return dir.listFiles().isNullOrEmpty()
    }

    private fun ownerOnlyDir(): Boolean {
        if (!dir.isDirectory) Files.createDirectories(dir.toPath())
        Files.setPosixFilePermissions(dir.toPath(), OWNER_ONLY)
        return perms(dir) == OWNER_ONLY && !Files.isSymbolicLink(dir.toPath())
    }
    private fun syncDir() {
        // Directory fsync where the platform allows it; the entry is re-checked by staged() anyway.
        try { FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) } } catch (_: Exception) {}
    }
    private fun regular(f: File) = Files.isRegularFile(f.toPath(), LinkOption.NOFOLLOW_LINKS)
    private fun perms(f: File): Set<PosixFilePermission> = Files.getPosixFilePermissions(f.toPath(), LinkOption.NOFOLLOW_LINKS)

    private companion object {
        val OWNER_ONLY: Set<PosixFilePermission> = PosixFilePermissions.fromString("rwx------")
        val READ_ONLY: Set<PosixFilePermission> = PosixFilePermissions.fromString("r--------")
    }
}
