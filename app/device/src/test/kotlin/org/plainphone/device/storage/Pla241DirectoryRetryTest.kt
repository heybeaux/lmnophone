// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.storage

import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Pla241DirectoryRetryTest {
    @get:Rule val tmp = TemporaryFolder()
    @Test fun retryMustNotForgetUnsyncedParent() {
        val parent = tmp.newFolder("parent")
        val dir = File(parent,"new-record-directory")
        val record = RecordFile(dir,"review",1,false)
        assertTrue(parent.setReadable(false,false))
        try {
            try {
                FileChannel.open(parent.toPath(),StandardOpenOption.READ).use { it.force(true) }
                fail("control: parent sync must fail")
            } catch (_: IOException) { }
            assertFalse(record.save(byteArrayOf(1)))
            assertTrue(dir.isDirectory)
            assertFalse("retry cannot acknowledge while new directory's parent still cannot sync", record.save(byteArrayOf(2)))
        } finally { parent.setReadable(true,true) }
    }
}
