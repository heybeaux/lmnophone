// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.PackageName
import org.plainphone.policy.*

class PreviewStarterPolicyStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val a = PackageName("test.camera")
    private val b = PackageName("test.calculator")
    private fun invalid(store: PolicyStore) {
        val read = store.read()
        assertEquals(PolicyHealth.BOTH_INVALID, read.health)
        assertTrue(read.approved.isEmpty())
    }
    @Test fun missingAndDirectoryRefuse() {
        val dir = tmp.newFolder()
        invalid(PreviewStarterPolicyStore(dir))
        File(dir, "starter-list.txt").mkdir()
        invalid(PreviewStarterPolicyStore(dir))
    }
    @Test fun emptyTruncatedMalformedWhitespaceAndDuplicatesRefuse() {
        val dir = tmp.newFolder()
        for (text in listOf("", "test.camera", "bad/name\n", "test.cam era\n", " test.camera\n",
                "test.camera\r\n", "test.camera\ntest.camera\n", " \n")) {
            File(dir, "starter-list.txt").writeText(text)
            invalid(PreviewStarterPolicyStore(dir))
        }
    }
    @Test fun readFailuresAndLockedStorageRefuseAndFaultHooksFire() {
        val dir = tmp.newFolder()
        File(dir, "starter-list.txt").writeText("test.camera\n")
        for (failure in listOf(IOException("injected"), SecurityException("injected"))) {
            var hits = 0
            invalid(PreviewStarterPolicyStore(dir, { true }) { hits++; throw failure })
            assertEquals(1, hits)
        }
        var hits = 0
        invalid(PreviewStarterPolicyStore(dir, { hits++; false }))
        assertEquals(1, hits)
    }
    @Test fun exactSetAndExplicitEmptyListAreValid() {
        val dir = tmp.newFolder(); val file = File(dir, "starter-list.txt")
        file.writeText("test.camera\n\ntest.calculator\n")
        assertEquals(StoredPolicy(1, setOf(a, b), PolicyHealth.VALID), PreviewStarterPolicyStore(dir).read())
        file.writeText("\n")
        assertEquals(StoredPolicy(1, emptySet(), PolicyHealth.VALID), PreviewStarterPolicyStore(dir).read())
    }
    @Test fun writeIsReadOnlyAndCreatesNothing() {
        val dir = tmp.newFolder(); val file = File(dir, "starter-list.txt")
        val text = "test.camera\n"; file.writeText(text)
        assertFalse(PreviewStarterPolicyStore(dir).write(StoredPolicy(1, setOf(b), PolicyHealth.VALID)))
        assertEquals(text, file.readText())
        assertEquals(listOf("starter-list.txt"), dir.list()!!.toList())
        val absent = File(tmp.root, "absent")
        assertFalse(PreviewStarterPolicyStore(absent).write(StoredPolicy(1, setOf(b), PolicyHealth.VALID)))
        assertFalse(absent.exists())
    }
    @Test fun realWriterSaveAddRemoveRoundTrip() {
        val dir = tmp.newFolder(); val writer = PreviewAppListStore(dir); val reader = PreviewStarterPolicyStore(dir)
        writer.saveStarter(listOf(a.value, a.value))
        assertEquals(StoredPolicy(1, setOf(a), PolicyHealth.VALID), reader.read())
        assertTrue(writer.add(b.value, listOf(a.value)) { true })
        assertEquals(StoredPolicy(1, setOf(a, b), PolicyHealth.VALID), reader.read())
        assertTrue(writer.remove(a.value, emptySet()))
        assertTrue(writer.remove(b.value, emptySet()))
        assertEquals(StoredPolicy(1, emptySet(), PolicyHealth.VALID), reader.read())
    }
}
