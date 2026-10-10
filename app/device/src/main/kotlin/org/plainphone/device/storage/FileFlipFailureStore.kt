// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.storage

import java.io.File
import org.plainphone.core.PackageName
import org.plainphone.device.boot.FlipFailureStore

/**
 * [FlipFailureStore] in a checksummed record; [dir] must be device-protected so the locked-boot pass
 * can read and write it. An unreadable record reads as empty: the run's own failures still count.
 */
class FileFlipFailureStore(dir: File) : FlipFailureStore {
    private val file = RecordFile(dir, "flip-failures", SCHEMA)

    override fun read(): Set<PackageName> =
        (file.readPrimary() ?: file.readLastKnownGood())?.decodeToString()
            ?.lineSequence()?.filter { it.isNotBlank() }?.mapNotNull { runCatching { PackageName(it) }.getOrNull() }?.toSet()
            .orEmpty()

    override fun save(names: Set<PackageName>): Boolean =
        file.save(names.map { it.value }.sorted().joinToString("\n").encodeToByteArray())

    private companion object {
        const val SCHEMA = 1
    }
}
