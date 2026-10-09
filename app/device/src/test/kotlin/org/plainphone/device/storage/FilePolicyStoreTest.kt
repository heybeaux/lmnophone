// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.PackageName
import org.plainphone.policy.PolicyHealth
import org.plainphone.policy.StoredPolicy

class FilePolicyStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val dir: File by lazy { tmp.newFolder("policy") }
    private fun store() = FilePolicyStore(dir)
    private fun primary() = File(dir, "policy.rec")
    private fun backup() = File(dir, "policy.lkg")
    private fun policy(vararg names: String) =
        StoredPolicy(FilePolicyStore.SCHEMA_VERSION, names.map(::PackageName).toSet(), PolicyHealth.VALID)
    private fun names(p: StoredPolicy) = p.approved.map { it.value }.toSet()

    @Test fun nothingSavedIsBothInvalid() {
        val read = store().read()
        assertEquals(PolicyHealth.BOTH_INVALID, read.health)
        assertTrue(read.approved.isEmpty())
    }

    @Test fun savedPolicyReadsBackValid() {
        assertTrue(store().write(policy("com.example.maps", "com.example.bank")))
        val read = FilePolicyStore(dir).read()
        assertEquals(PolicyHealth.VALID, read.health)
        assertEquals(setOf("com.example.maps", "com.example.bank"), names(read))
        assertEquals(FilePolicyStore.SCHEMA_VERSION, read.schemaVersion)
    }

    @Test fun emptyApprovedSetIsStillValid() {
        assertTrue(store().write(policy()))
        val read = store().read()
        assertEquals(PolicyHealth.VALID, read.health)
        assertTrue(read.approved.isEmpty())
    }

    @Test fun truncatedPolicyFallsBackToThePreviousSave() {
        val s = store()
        assertTrue(s.write(policy("com.example.old")))
        assertTrue(s.write(policy("com.example.new")))
        primary().writeBytes(primary().readBytes().copyOf(7))
        val read = s.read()
        assertEquals(PolicyHealth.LAST_KNOWN_GOOD, read.health)
        assertEquals(setOf("com.example.old"), names(read))
    }

    @Test fun bothCopiesTruncatedIsBothInvalid() {
        val s = store()
        assertTrue(s.write(policy("com.example.old")))
        assertTrue(s.write(policy("com.example.new")))
        primary().writeBytes(ByteArray(0))
        backup().writeBytes(backup().readBytes().copyOf(3))
        val read = s.read()
        assertEquals(PolicyHealth.BOTH_INVALID, read.health)
        assertTrue(read.approved.isEmpty())
    }

    @Test fun oneFlippedBitFailsTheChecksum() {
        val s = store()
        assertTrue(s.write(policy("com.example.old")))
        assertTrue(s.write(policy("com.example.new")))
        val bytes = primary().readBytes()
        bytes[RecordCodec.HEADER_SIZE] = (bytes[RecordCodec.HEADER_SIZE].toInt() xor 0x01).toByte()
        primary().writeBytes(bytes)
        assertEquals(PolicyHealth.LAST_KNOWN_GOOD, s.read().health)
    }

    @Test fun unknownSchemaIsRejectedEvenWithAGoodChecksum() {
        val s = store()
        assertTrue(s.write(policy("com.example.old")))
        assertTrue(s.write(policy("com.example.new")))
        primary().writeBytes(RecordCodec.encode(FilePolicyStore.SCHEMA_VERSION + 1, "com.example.future".toByteArray()))
        val read = s.read()
        assertEquals(PolicyHealth.LAST_KNOWN_GOOD, read.health)
        assertEquals(setOf("com.example.old"), names(read))
    }

    @Test fun badPackageNameWithAGoodChecksumIsRejected() {
        val s = store()
        assertTrue(s.write(policy("com.example.old")))
        assertTrue(s.write(policy("com.example.new")))
        primary().writeBytes(RecordCodec.encode(FilePolicyStore.SCHEMA_VERSION, "not a package\n".toByteArray()))
        assertEquals(PolicyHealth.LAST_KNOWN_GOOD, s.read().health)
    }

    @Test fun leftoverTempFileFromACrashIsIgnoredAndReplaced() {
        val s = store()
        assertTrue(s.write(policy("com.example.old")))
        File(dir, "policy.rec.tmp").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(setOf("com.example.old"), names(s.read()))
        assertTrue(s.write(policy("com.example.new")))
        assertEquals(setOf("com.example.new"), names(s.read()))
    }

    @Test fun savingOverACorruptPolicyKeepsTheGoodBackup() {
        val s = store()
        assertTrue(s.write(policy("com.example.old")))
        assertTrue(s.write(policy("com.example.mid")))
        primary().writeBytes(byteArrayOf(9, 9, 9))
        assertTrue(s.write(policy("com.example.new")))
        primary().writeBytes(ByteArray(0))
        val read = s.read()
        assertEquals(PolicyHealth.LAST_KNOWN_GOOD, read.health)
        assertEquals(setOf("com.example.old"), names(read))
    }

    @Test fun refusesASchemaItCannotReadBack() {
        val other = StoredPolicy(FilePolicyStore.SCHEMA_VERSION + 1, emptySet(), PolicyHealth.VALID)
        assertFalse(store().write(other))
        assertFalse(primary().exists())
    }

    @Test fun reportsFailureWhenTheDirectoryCannotBeWritten() {
        val notADir = tmp.newFile("blocked")
        assertFalse(FilePolicyStore(notADir).write(policy("com.example.maps")))
    }
}
