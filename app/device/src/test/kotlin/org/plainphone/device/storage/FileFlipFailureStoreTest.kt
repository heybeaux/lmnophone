// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.PackageName

/** Flip-failure evidence outlives the process (PLA-241 finding 4). */
class FileFlipFailureStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun aNewInstanceReadsWhatTheLastOneSaved() {
        val dir = tmp.newFolder("de")
        val names = setOf(PackageName("com.android.dialer"), PackageName("com.example.messages"))
        assertTrue(FileFlipFailureStore(dir).save(names))
        assertEquals(names, FileFlipFailureStore(dir).read())
    }

    @Test fun nothingSavedReadsEmpty() {
        assertEquals(emptySet<PackageName>(), FileFlipFailureStore(tmp.newFolder("de")).read())
    }

    @Test fun anEmptySaveClears() {
        val dir = tmp.newFolder("de")
        FileFlipFailureStore(dir).save(setOf(PackageName("com.android.dialer")))
        assertTrue(FileFlipFailureStore(dir).save(emptySet()))
        assertEquals(emptySet<PackageName>(), FileFlipFailureStore(dir).read())
    }
}
