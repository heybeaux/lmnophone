// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class UpdateStagingTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dir by lazy { File(tmp.root, "update-staging") }
    private val staging by lazy { UpdateStaging(dir) }

    private fun stage(text: String): File {
        val part = staging.receive()!!
        part.writeText(text)
        assertTrue(staging.seal(part))
        return staging.staged()!!
    }

    @Test fun sealedFileIsOwnerReadOnlyInAnOwnerOnlyDirectory() {
        val f = stage("apk")
        assertEquals(dir.canonicalFile, f.parentFile.canonicalFile)
        assertEquals("r--------", PosixFilePermissions.toString(Files.getPosixFilePermissions(f.toPath())))
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.toPath())))
        assertFalse(f.canWrite())
    }

    @Test fun nothingIsStagedUntilSealed() {
        val part = staging.receive()!!
        part.writeText("half")
        assertNull(staging.staged())
    }

    @Test fun receiveDropsTheEarlierStagedFile() {
        stage("old")
        staging.receive()!!
        assertNull(staging.staged())
    }

    @Test fun stagedFileWithWiderPermissionsOrAWiderDirectoryIsRefused() {
        stage("apk").setWritable(true, true)
        assertNull(staging.staged())
        stage("apk").setReadable(true, false)
        assertNull(staging.staged())
        stage("apk")
        Files.setPosixFilePermissions(dir.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"))
        assertNull(staging.staged())
    }

    @Test fun symlinkedStagedFileIsRefused() {
        val f = stage("apk")
        val elsewhere = tmp.newFile("elsewhere.apk").also { it.writeText("other") }
        f.setWritable(true, true)
        assertTrue(f.delete())
        Files.createSymbolicLink(f.toPath(), elsewhere.toPath())
        assertNull(staging.staged())
    }

    @Test fun clearRemovesEverything() {
        stage("apk")
        staging.receive()!!.writeText("part")
        assertTrue(staging.clear())
        assertNull(staging.staged())
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test fun sealRefusesAFileFromElsewhere() {
        val other = tmp.newFile("x.part").also { it.writeText("apk") }
        assertFalse(staging.seal(other))
        assertNull(staging.staged())
    }
}
