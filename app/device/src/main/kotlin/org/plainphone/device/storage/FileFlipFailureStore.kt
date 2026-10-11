// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.storage

import java.io.File
import org.plainphone.core.PackageName
import org.plainphone.device.boot.FlipFailureStore
import org.plainphone.device.boot.FlipFailures

/**
 * [FlipFailureStore] in a checksummed record; [dir] must be device-protected so the locked-boot pass
 * can read and write it.
 *
 * Only a missing directory or a directory with no record reads as [FlipFailures.NONE]. A primary that
 * exists but does not decode, a backup without a primary, an unlistable directory or an unparseable
 * line reads as unknown (PLA-264 finding 3). The last-known-good copy is never read: it may be older
 * than a damaged primary, and an older empty record must not certify that a failure was resolved.
 */
class FileFlipFailureStore(private val dir: File) : FlipFailureStore {
    private val file = RecordFile(dir, NAME, SCHEMA)

    override fun read(): FlipFailures {
        val entries = dir.list()
            ?: return if (!dir.exists() && dir.absoluteFile.parentFile?.list() != null) FlipFailures.NONE else FlipFailures.UNKNOWN
        if ("$NAME.rec" !in entries) return if ("$NAME.lkg" in entries) FlipFailures.UNKNOWN else FlipFailures.NONE
        val lines = file.readPrimary()?.decodeToString()?.lineSequence()?.filter { it.isNotBlank() }?.toList()
            ?: return FlipFailures.UNKNOWN
        val names = lines.filter { it != UNKNOWN_LINE }.map { runCatching { PackageName(it) }.getOrNull() }
        return FlipFailures(names.filterNotNull().toSet(), unknown = UNKNOWN_LINE in lines || null in names)
    }

    override fun save(state: FlipFailures): Boolean {
        val lines = listOfNotNull(UNKNOWN_LINE.takeIf { state.unknown }) + state.names.map { it.value }.sorted()
        return file.save(lines.joinToString("\n").encodeToByteArray())
    }

    private companion object {
        const val NAME = "flip-failures"
        const val SCHEMA = 1
        /** Not a package name, so a schema-1 record from before PLA-269 still reads as it did. */
        const val UNKNOWN_LINE = "!unknown"
    }
}
