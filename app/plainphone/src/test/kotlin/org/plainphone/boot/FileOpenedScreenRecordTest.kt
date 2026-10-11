// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** PLA-324: the opened calling screen outlives the process, but never a reboot or a bad file. */
class FileOpenedScreenRecordTest {
    private val file = File(Files.createTempDirectory("opened").toFile(), "opened-screen")
    private var clock = 5_000L

    @Test fun aSavedScreenLoadsInTheNextProcess() {
        FileOpenedScreenRecord(file) { clock }.save(CallingScreen.EMERGENCY_KEYPAD)
        clock += 1_000
        assertEquals(CallingScreen.EMERGENCY_KEYPAD, FileOpenedScreenRecord(file) { clock }.load())
    }

    @Test fun clearingRemovesIt() {
        val record = FileOpenedScreenRecord(file) { clock }
        record.save(CallingScreen.PHONE)
        record.save(null)
        assertNull(record.load())
    }

    @Test fun aRecordFromBeforeARebootIsIgnored() {
        FileOpenedScreenRecord(file) { clock }.save(CallingScreen.PHONE)
        assertNull("uptime went backwards, so the phone restarted", FileOpenedScreenRecord(file) { 100L }.load())
    }

    @Test fun aMissingOrGarbledFileIsNothingOpened() {
        assertNull(FileOpenedScreenRecord(file) { clock }.load())
        file.writeText("SETTINGS 12")
        assertNull(FileOpenedScreenRecord(file) { clock }.load())
        file.writeText("PHONE")
        assertNull(FileOpenedScreenRecord(file) { clock }.load())
    }
}
