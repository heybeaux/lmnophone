// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.update

import org.plainphone.device.authority.PlatformDirectorySync
import java.io.File
import java.io.FileOutputStream

/**
 * Durable memory of the session IDs this app passed to `commit()`, one empty marker file per ID in
 * [dir] (app-private, no backup). It outlives the adapter instance and the process: `isCommitted()`
 * still reads false for about 16 ms after `commit()` returns (X6, API 35), and abandoning a session
 * in that window cancels its install, so every instance and every later process must see the commit.
 * A marker is written and synced BEFORE `commit()` is called; [remember] failing means no commit.
 */
class CommitMemory(private val dir: File) {
    /** Writes and syncs the marker for [sessionId]; false when it may not be durable. */
    fun remember(sessionId: Int): Boolean = try {
        if (!dir.isDirectory && !dir.mkdirs()) false
        else {
            FileOutputStream(File(dir, "$sessionId")).use { it.fd.sync() }
            PlatformDirectorySync.sync(dir)
            File(dir, "$sessionId").isFile
        }
    } catch (_: Exception) {
        false
    }

    /** Every remembered ID; null when the directory cannot be read, which never means "none". */
    fun ids(): Set<Int>? = try {
        if (!dir.isDirectory) dir.mkdirs()
        dir.listFiles()?.mapNotNull { it.name.toIntOrNull() }?.toSet()
    } catch (_: Exception) {
        null
    }

    /** Drops the markers in [ids]; false when one is left. */
    fun forget(ids: Collection<Int>): Boolean = ids.all { id -> File(dir, "$id").let { !it.exists() || it.delete() } }
}
