// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import org.junit.Assert.assertTrue

/**
 * PLA-159 A-F1: the store's directory loses permissions (default: search) at [point] of the next
 * write that reaches it, so the writer cannot learn whether its rename committed. [run] restores
 * the original permissions before returning, so the test can then read what actually landed.
 */
internal class SearchDenial(private val point: WritePoint, private val permission: String = "r--------") : StorageFaults {
    private var fired = false

    override fun at(point: WritePoint, dir: File) {
        if (point != this.point || fired) return
        fired = true
        Files.setPosixFilePermissions(dir.toPath(), PosixFilePermissions.fromString(permission))
    }

    /** Installs this denial with [install], runs [block], then uninstalls it and restores [dir]. */
    fun <T> run(dir: File, install: (StorageFaults) -> Unit, block: () -> T): T {
        val original = Files.getPosixFilePermissions(dir.toPath())
        install(this)
        val result = try { block() } finally {
            install(StorageFaults { _, _ -> })
            Files.setPosixFilePermissions(dir.toPath(), original)
        }
        assertTrue("$permission at $point never fired", fired)
        return result
    }
}
