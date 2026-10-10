// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.Mode
import org.plainphone.core.RunningTime
import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationDebit
import org.plainphone.policy.ElevationLedger
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.ElevationState
import org.plainphone.policy.FailureCounter
import org.plainphone.policy.FrictionSettings
import org.plainphone.policy.PersonCredential
import org.plainphone.policy.RecoveryProfile
import org.plainphone.policy.SecretVerifier
import org.plainphone.policy.WaitingState

class JointAuthorityStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dir: File by lazy { tmp.newFolder("authority") }
    private val self = TrustedPersonId("self")
    private var faults = StorageFaults { _, _ -> }
    private fun open() = JointAuthorityStore(dir) { point, d -> faults.at(point, d) }

    private fun credentials(revision: Long = 0, failures: Int = 0) = CredentialState(Mode.SELF_MANAGED,
        mapOf(self to PersonCredential(SecretVerifier(ByteArray(16), ByteArray(32) { 1 }, 600_000),
            SecretVerifier(ByteArray(16) { 2 }, ByteArray(32) { 3 }, 600_000), FailureCounter(failures))),
        revision = revision,
        recoveryProfile = RecoveryProfile(mapOf(self to "synthetic-account"), null, FrictionSettings(1_200_000, 1)))
    private val ledger = ElevationRecord(0, ElevationState(ElevationLedger(listOf(ElevationDebit("turn-1", RunningTime("old", 4))))))

    private fun initialized(): JointAuthorityStore = open().also {
        assertTrue(it.initialize(credentials(), WaitingState(storageGeneration = "live"), ledger))
    }
    private fun bump(store: JointAuthorityStore, failures: Int): Boolean {
        val current = store.credentials.read()!!
        return store.credentials.compareAndSet(current, credentials(current.revision + 1, failures))
    }
    private fun copyA() = File(dir, "authority-a.rec")
    private fun copyB() = File(dir, "authority-b.rec")

    @Test fun nothingInitializedReadsAsMissingNotVacant() {
        val store = open()
        assertNull(store.credentials.read())
        assertNull(store.waitingStore.read())
        assertNull(store.elevationStore.read())
    }

    @Test fun initializeOnlyOnceAndSurvivesReopen() {
        initialized()
        assertFalse(open().initialize(credentials(), WaitingState(storageGeneration = "other"), ElevationRecord()))
        val reopened = open()
        assertEquals("live", reopened.waitingStore.read()!!.storageGeneration)
        assertEquals(ledger.state.ledger.debits, reopened.elevationStore.read()!!.state.ledger.debits)
    }

    @Test fun compareAndSetComparesStoredContentNotObjectIdentity() {
        val store = initialized()
        val stale = store.credentials.read()!!
        assertTrue(bump(store, 1))
        assertFalse(store.credentials.compareAndSet(stale, credentials(1, 9)))
        assertEquals(1, open().credentials.read()!!.people.getValue(self).pinCounter.failures)
    }

    @Test fun credentialWritePreservesOtherSectionsByteForByte() {
        val store = initialized()
        val waiting = store.sectionBytesForTest(Section.WAITING)
        val elevation = store.sectionBytesForTest(Section.ELEVATION)
        assertTrue(bump(store, 2))
        assertArrayEquals(waiting, open().sectionBytesForTest(Section.WAITING))
        assertArrayEquals(elevation, open().sectionBytesForTest(Section.ELEVATION))
    }

    @Test fun oneDamagedCopyStillReadsTheAcknowledgedRecord() {
        val store = initialized(); assertTrue(bump(store, 3))
        copyA().writeBytes(copyA().readBytes().copyOf(20))
        assertEquals(3, open().credentials.read()!!.people.getValue(self).pinCounter.failures)
        copyB().delete()
        assertNull(open().credentials.read())
    }

    @Test fun staleSoleReplicaIsFencedEvenWithAValidChecksum() {
        val store = initialized(); assertTrue(bump(store, 1))
        val older = copyA().readBytes()
        assertTrue(bump(store, 2))
        copyA().writeBytes(older); copyB().delete()
        val reopened = open()
        assertNull(reopened.credentials.read())
        assertNull(reopened.waitingStore.read())
        assertNull(reopened.elevationStore.read())
        assertFalse(reopened.credentials.compareAndSet(credentials(1, 1), credentials(2, 5)))
    }

    @Test fun bothCopiesOlderThanTheFenceAreFenced() {
        val store = initialized(); assertTrue(bump(store, 1))
        val a = copyA().readBytes(); val b = copyB().readBytes()
        assertTrue(bump(store, 2))
        copyA().writeBytes(a); copyB().writeBytes(b)
        assertNull(open().credentials.read())
    }

    @Test fun withoutAnyFenceOnlyTwoAgreeingCopiesRead() {
        val store = initialized(); assertTrue(bump(store, 1))
        dir.listFiles()!!.filter { it.name.startsWith("fence") }.forEach { it.delete() }
        // No older record exists after an acknowledgement, so two agreeing copies are current.
        assertEquals(1L, open().credentials.read()!!.revision)
        copyB().delete()
        assertNull(open().credentials.read())
    }

    @Test fun crashAtEveryWritePointExposesOldOrNewNeverAMix() {
        for (point in WritePoint.entries) {
            tmp.root.listFiles()!!.forEach { it.deleteRecursively() }
            val store = initialized()
            faults = StorageFaults { at, _ -> if (at == point) throw SimulatedCrash() }
            try { bump(store, 4) } catch (_: SimulatedCrash) { }
            faults = StorageFaults { _, _ -> }
            val reopened = open()
            val read = checkNotNull(reopened.credentials.read()) { "after crash at $point" }
            assertTrue("$point", read.revision == 0L || read.revision == 1L)
            assertEquals(if (read.revision == 1L) 4 else 0, read.people.getValue(self).pinCounter.failures)
            assertEquals("live", reopened.waitingStore.read()!!.storageGeneration)
            // The next write after recovery succeeds and is durable.
            assertTrue("$point", bump(reopened, 6))
            assertEquals(6, open().credentials.read()!!.people.getValue(self).pinCounter.failures)
        }
    }

    /** A non-empty directory where a record file goes makes that file's write or rename fail. */
    private fun blockFile(d: File, name: String) { File(d, name).apply { deleteRecursively(); mkdirs() }; File(d, "$name/x").writeText("x") }

    /**
     * PLA-128 F2: each physical write fails (returns, not a crash) in turn. The result must equal what
     * reads back, and an acknowledged record must be fenced against the old copies.
     */
    @Test fun failedWriteAtEveryStepReportsExactlyTheVisibleRecord() {
        val steps = listOf(WritePoint.BEFORE_COPY_A to "authority-a.rec.tmp", WritePoint.AFTER_COPY_A to "fence-a.rec",
            WritePoint.AFTER_FENCE_A to "fence-b.rec", WritePoint.AFTER_FENCE to "authority-b.rec.tmp")
        for ((point, blocked) in steps) {
            tmp.root.listFiles()!!.forEach { it.deleteRecursively() }
            val store = initialized()
            val old = copyA().readBytes()
            faults = StorageFaults { at, d -> if (at == point) blockFile(d, blocked) }
            val result = bump(store, 7)
            faults = StorageFaults { _, _ -> }
            val visible = open().credentials.read()
            assertEquals("$point", if (result) 1L else 0L, visible?.revision)
            assertEquals("$point", visible?.revision, store.credentials.read()?.revision)
            if (result) {
                copyA().writeBytes(old); copyB().writeBytes(old)
                assertNull("$point old copies are fenced", open().credentials.read())
            } else {
                File(dir, blocked).deleteRecursively()
                assertTrue("$point next write after recovery", bump(open(), 8))
            }
        }
    }

    /**
     * PLA-143 R1 for the joint record: each directory sync of a write fails in turn. Syncs 1-2 follow
     * the two copy renames (the commit point), 3-4 the fences. A copy whose directory entry was not
     * synced never commits; every result equals the record that reads back.
     */
    @Test fun failedDirectorySyncAtEveryStepReportsExactlyTheVisibleRecord() {
        for (failing in 1..4) {
            tmp.root.listFiles()!!.forEach { it.deleteRecursively() }
            val store = initialized()
            var syncs = 0
            store.withDirectorySync { d -> if (++syncs == failing) throw java.io.IOException("sync $failing") else PlatformDirectorySync.sync(d) }
            val result = bump(store, 7)
            store.withDirectorySync(PlatformDirectorySync)
            val visible = open().credentials.read()
            assertEquals("sync $failing", if (result) 1L else 0L, visible?.revision)
            if (failing <= 2) assertFalse("sync $failing: an unsynced copy must not commit", result)
            assertTrue("sync $failing: the next write succeeds", bump(open(), 8))
        }
    }

    @Test fun readbackFailureReturnsFalseAndChangesNothing() {
        val store = initialized()
        faults = StorageFaults { at, d -> if (at == WritePoint.AFTER_COPY_A) File(d, "authority-a.rec").writeBytes(byteArrayOf(1, 2, 3)) }
        assertFalse(bump(store, 7))
        faults = StorageFaults { _, _ -> }
        assertEquals(0L, open().credentials.read()!!.revision)
        assertEquals(0L, store.credentials.read()!!.revision)
    }

    @Test fun reentrantWriteFromInsideATransactionIsRefused() {
        val store = initialized()
        val snapshot = store.credentials.read()!!
        var inner: Boolean? = null
        var innerRead: CredentialState? = snapshot
        faults = StorageFaults { at, _ ->
            if (at == WritePoint.AFTER_COPY_A && inner == null) {
                innerRead = store.credentials.read()
                inner = store.credentials.compareAndSet(snapshot, credentials(1, 9))
            }
        }
        assertTrue(bump(store, 1))
        assertEquals(false, inner)
        assertNull(innerRead)
        assertEquals(1, open().credentials.read()!!.people.getValue(self).pinCounter.failures)
    }

    @Test fun elevationCompareAndSetWithAndWithoutCredentialCheck() {
        val store = initialized()
        val before = store.elevationStore.read()!!
        assertTrue(store.elevationStore.compareAndSet(before, ElevationRecord(before.revision + 1, before.state), store.credentials.read()))
        assertFalse(store.elevationStore.compareAndSet(before, ElevationRecord(before.revision + 2, before.state), null))
        assertEquals(before.revision + 1, open().elevationStore.read()!!.revision)
    }

    @Test fun elevationCompareAndSetChecksTheCredentialRevisionInTheSameTransaction() {
        val store = initialized()
        val elevation = store.elevationStore.read()!!
        val stale = store.credentials.read()!!
        assertTrue(bump(store, 1))
        assertFalse(store.elevationStore.compareAndSet(elevation, ElevationRecord(1, elevation.state), stale))
        assertEquals(0L, open().elevationStore.read()!!.revision)
    }
}
