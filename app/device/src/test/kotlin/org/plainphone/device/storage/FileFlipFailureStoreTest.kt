// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.PackageName
import org.plainphone.device.boot.FlipFailures

/**
 * Flip-failure evidence outlives the process (PLA-241 finding 4). Only "never saved" and a readable
 * last save count as known; anything that exists but cannot be read is unknown, which keeps attention
 * (PLA-264 finding 3). An older copy never stands in for a newer one.
 */
class FileFlipFailureStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dialer = PackageName("com.android.dialer")

    @Test fun aNewInstanceReadsWhatTheLastOneSaved() {
        val dir = tmp.newFolder("de")
        val names = setOf(dialer, PackageName("com.example.messages"))
        assertTrue(FileFlipFailureStore(dir).save(FlipFailures(names)))
        assertEquals(FlipFailures(names), FileFlipFailureStore(dir).read())
    }

    @Test fun nothingSavedReadsNone() {
        assertEquals(FlipFailures.NONE, FileFlipFailureStore(tmp.newFolder("de")).read())
        assertEquals(FlipFailures.NONE, FileFlipFailureStore(File(tmp.root, "not-yet")).read())
    }

    @Test fun aDeliberateEmptySaveClears() {
        val dir = tmp.newFolder("de")
        FileFlipFailureStore(dir).save(FlipFailures(setOf(dialer)))
        assertTrue(FileFlipFailureStore(dir).save(FlipFailures.NONE))
        assertEquals(FlipFailures.NONE, FileFlipFailureStore(dir).read())
    }

    @Test fun aCorruptPrimaryWithAnOlderEmptyBackupIsUnknown() {
        val dir = tmp.newFolder("de")
        val store = FileFlipFailureStore(dir)
        assertTrue(store.save(FlipFailures.NONE))
        assertTrue(store.save(FlipFailures(setOf(dialer))))
        File(dir, "flip-failures.rec").writeText("corrupt")
        val read = FileFlipFailureStore(dir).read()
        assertTrue(read.unknown)
        assertTrue(read.needsAttention)
    }

    @Test fun aCorruptSoleRecordIsUnknown() {
        val dir = tmp.newFolder("de")
        assertTrue(FileFlipFailureStore(dir).save(FlipFailures(setOf(dialer))))
        File(dir, "flip-failures.rec").writeText("corrupt")
        assertTrue(FileFlipFailureStore(dir).read().unknown)
    }

    @Test fun bothCopiesUnreadableIsUnknown() {
        val dir = tmp.newFolder("de")
        val store = FileFlipFailureStore(dir)
        store.save(FlipFailures.NONE)
        store.save(FlipFailures(setOf(dialer)))
        File(dir, "flip-failures.rec").writeText("corrupt")
        File(dir, "flip-failures.lkg").writeText("corrupt")
        assertTrue(FileFlipFailureStore(dir).read().unknown)
    }

    @Test fun aMissingPrimaryBesideABackupIsUnknown() {
        val dir = tmp.newFolder("de")
        val store = FileFlipFailureStore(dir)
        store.save(FlipFailures.NONE)
        store.save(FlipFailures(setOf(dialer)))
        assertTrue(File(dir, "flip-failures.rec").delete())
        assertTrue(FileFlipFailureStore(dir).read().unknown)
    }

    @Test fun anUnlistableDirectoryIsUnknown() {
        val dir = tmp.newFolder("de")
        FileFlipFailureStore(dir).save(FlipFailures(setOf(dialer)))
        assertTrue(dir.setReadable(false) && dir.setExecutable(false))
        try {
            assertTrue(FileFlipFailureStore(dir).read().unknown)
        } finally {
            dir.setReadable(true); dir.setExecutable(true)
        }
    }

    @Test fun theUnknownStateRoundTripsWithItsNames() {
        val dir = tmp.newFolder("de")
        val state = FlipFailures(setOf(dialer), unknown = true)
        assertTrue(FileFlipFailureStore(dir).save(state))
        assertEquals(state, FileFlipFailureStore(dir).read())
    }
}
