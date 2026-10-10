// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.Mode
import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.FailureCounter
import org.plainphone.policy.PersonCredential
import org.plainphone.policy.SecretVerifier
import org.plainphone.policy.WaitingState

/**
 * PLA-160 (PLA-159 A-F1). True means acknowledged: the new record reads back. False means not
 * acknowledged: the store may hold the previous record or the new one, so a caller reads again
 * before acting. A writer that loses access to its own directory cannot tell which, and says false.
 */
class FalseNotAcknowledgedTest {
    @get:Rule val tmp = TemporaryFolder()
    private val self = TrustedPersonId("self")
    private val files = listOf("authority-a", "authority-b", "fence-a", "fence-b")
    private var faults = StorageFaults { _, _ -> }

    private fun credentials(revision: Long = 0) = CredentialState(Mode.SELF_MANAGED,
        mapOf(self to PersonCredential(SecretVerifier(ByteArray(16), ByteArray(32) { 1 }, 600_000),
            SecretVerifier(ByteArray(16) { 2 }, ByteArray(32) { 3 }, 600_000), FailureCounter())), revision = revision)
    private fun open(dir: File) = JointAuthorityStore(dir) { point, d -> faults.at(point, d) }
    private fun initialize(store: JointAuthorityStore) = store.initialize(credentials(), WaitingState(storageGeneration = "live"), ElevationRecord())
    private fun initialized(dir: File = tmp.newFolder()) = open(dir).also { assertTrue(initialize(it)) }
    private fun bump(store: JointAuthorityStore): Boolean {
        val current = store.credentials.read()!!
        return store.credentials.compareAndSet(current, credentials(current.revision + 1))
    }
    private fun <T> denied(dir: File, point: WritePoint, permission: String, block: () -> T): T =
        SearchDenial(point, permission).run(dir, { faults = it }, block)

    /** Two fresh stores and repeated reads agree; returns the revision they read, or null. */
    private fun agreed(dir: File): Long? {
        val a = JointAuthorityStore(dir); val b = JointAuthorityStore(dir)
        val first = a.credentials.read()?.revision
        assertEquals(first, b.credentials.read()?.revision)
        assertEquals(first, a.credentials.read()?.revision)
        return first
    }

    /**
     * After an unacknowledged write: every subset of surviving files reads [restored] or nothing,
     * never anything else, and a compare-and-set built from a fresh read succeeds. A caller that
     * kept the expected value it sent loses whenever the new record landed.
     */
    private fun assertRecoverable(dir: File, sent: CredentialState, restored: Long, label: String) {
        val saved = files.associateWith { File(dir, "$it.rec").takeIf(File::exists)?.readBytes() }
        for (mask in 0 until 16) {
            files.forEachIndexed { i, name -> if (mask and (1 shl i) != 0) File(dir, "$name.rec").delete() }
            val read = agreed(dir)
            assertTrue("$label lost mask $mask read $read", read == null || read == restored)
            saved.forEach { (name, bytes) -> bytes?.let { File(dir, "$name.rec").writeBytes(it) } }
        }
        val store = open(dir)
        val stale = store.credentials.compareAndSet(sent, credentials(sent.revision + 1))
        assertEquals("$label stale expectation", restored == sent.revision, stale)
        val fresh = store.credentials.read()!!
        assertTrue(label, store.credentials.compareAndSet(fresh, credentials(fresh.revision + 1)))
        assertEquals(label, fresh.revision + 1, agreed(dir))
    }

    // The Verifier's six cases: no search permission after the second copy, so the new record has
    // committed but the writer cannot read it. False, then fresh stores agree on revision 1 or 2.
    @Test fun searchDenialAfterTheCommitPointReturnsFalseAndFreshReadsDecide() {
        for (permission in listOf("r--------", "rw-------")) {
            for (point in listOf(WritePoint.AFTER_COPY_B, WritePoint.AFTER_FENCE_A, WritePoint.AFTER_FENCE)) {
                val label = "$permission $point"
                val dir = tmp.newFolder(); val store = initialized(dir)
                assertTrue(bump(store))
                val sent = store.credentials.read()!!
                val result = denied(dir, point, permission) {
                    bump(store).also { assertNull(label, open(dir).credentials.read()) }
                }
                assertFalse(label, result)
                val restored = agreed(dir)!!
                assertTrue("$label read $restored", restored == 1L || restored == 2L)
                assertRecoverable(dir, sent, restored, label)
            }
        }
    }

    // Every permission at every point: true always reads the new record; false reads the previous
    // or the new one, and the next fresh write works. Read-only (r-x) keeps search and resolves.
    @Test fun directoryPermissionAtEveryPointTrueReadsNewFalseReadsEither() {
        for (permission in listOf("r-x------", "r--------", "rw-------")) {
            for (point in WritePoint.entries) {
                val label = "$permission $point"
                val dir = tmp.newFolder(); val store = initialized(dir)
                assertTrue(bump(store))
                val sent = store.credentials.read()!!
                val result = denied(dir, point, permission) { bump(store) }
                val restored = agreed(dir)!!
                if (result) assertEquals(label, 2L, restored) else assertTrue("$label read $restored", restored == 1L || restored == 2L)
                assertRecoverable(dir, sent, restored, label)
            }
        }
    }

    // First record, case 1: a blocked second-copy temp file returns false and leaves no readable genesis.
    @Test fun firstRecordBlockedSecondCopyReturnsFalseWithNoReadableGenesis() {
        val dir = tmp.newFolder(); val block = File(dir, "authority-b.rec.tmp")
        assertTrue(block.mkdir())
        val store = open(dir)
        assertFalse(initialize(store))
        assertNull(agreed(dir))
        assertTrue(block.delete())
        assertTrue(initialize(store))
        assertEquals(0L, agreed(dir))
    }

    // First record, case 2: a blocked fence temp file still returns true with a readable genesis;
    // migrate() stays false until the fence can be written, then repairs it.
    @Test fun firstRecordBlockedFenceReturnsTrueAndMigrateRepairsIt() {
        for (name in listOf("fence-a", "fence-b")) {
            val dir = tmp.newFolder(); val block = File(dir, "$name.rec.tmp")
            assertTrue(block.mkdir())
            val store = open(dir)
            assertTrue(name, initialize(store))
            assertEquals(name, 0L, agreed(dir))
            assertFalse(name, store.migrate())
            assertTrue(block.delete())
            assertTrue(name, store.migrate())
            assertEquals(name, 0L, agreed(dir))
        }
    }

    // First record, case 3: search denial after the second genesis copy returns false, yet genesis
    // reads once access returns. Initialize then refuses over it, and migrate() repairs it.
    @Test fun firstRecordSearchDenialAfterSecondCopyReturnsFalseButGenesisReadsAndMigrateRepairsIt() {
        val dir = tmp.newFolder(); val store = open(dir)
        assertFalse(denied(dir, WritePoint.AFTER_COPY_B, "r--------") { initialize(store) })
        assertEquals(0L, agreed(dir))
        assertFalse(initialize(open(dir)))
        assertTrue(open(dir).migrate())
        assertTrue(bump(open(dir)))
        assertEquals(1L, agreed(dir))
    }

    // migrate() is a readiness gate: false under denial at any point means retry, never "unchanged".
    @Test fun migrateFalseUnderSearchDenialMeansRetry() {
        for (point in WritePoint.entries) {
            val dir = tmp.newFolder(); val store = initialized(dir)
            assertTrue(bump(store))
            // An unfinished migration: copies current, fences behind. migrate() rewrites them.
            File(dir, "fence-a.rec").delete(); File(dir, "fence-b.rec").delete()
            assertFalse("$point", denied(dir, point, "r--------") { open(dir).migrate() })
            assertTrue("$point retry", open(dir).migrate())
            assertEquals("$point", 1L, agreed(dir))
        }
    }
}
