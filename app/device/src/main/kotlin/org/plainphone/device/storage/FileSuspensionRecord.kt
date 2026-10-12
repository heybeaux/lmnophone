// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.storage

import java.io.File
import java.io.IOException
import org.plainphone.core.PackageName
import org.plainphone.device.boot.SuspensionRecord

/**
 * [SuspensionRecord] in a checksummed record; [dir] must be device-protected. Only a missing directory
 * or a directory with no record reads as empty. A record that exists but does not decode, or holds a
 * line that is not a package name, throws: an older or empty copy must never decide what is lifted, so
 * the last-known-good copy is not kept.
 */
class FileSuspensionRecord(private val dir: File) : SuspensionRecord {
    private val file = RecordFile(dir, NAME, SCHEMA, keepLastKnownGood = false)

    override fun read(): Set<PackageName> {
        val entries = dir.list() ?: if (!dir.exists()) return emptySet() else throw IOException("cannot list $dir")
        if ("$NAME.rec" !in entries) return emptySet()
        val bytes = file.readPrimary() ?: throw IOException("suspension record does not decode")
        return bytes.decodeToString().lineSequence().filter { it.isNotBlank() }
            .mapTo(mutableSetOf()) { line -> runCatching { PackageName(line) }.getOrElse { throw IOException("bad line") } }
    }

    override fun save(names: Set<PackageName>): Boolean =
        file.save(names.map { it.value }.sorted().joinToString("\n").encodeToByteArray())

    private companion object {
        const val NAME = "fallback-suspended"
        const val SCHEMA = 1
    }
}
