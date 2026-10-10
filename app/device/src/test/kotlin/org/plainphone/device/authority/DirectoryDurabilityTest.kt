// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.Mode
import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.*

/**
 * PLA-188: nothing is acknowledged without a successful directory sync after the rename it depends on.
 * The first six tests are the Verifier's reproductions from the PLA-188 review, kept as written.
 */
class DirectoryDurabilityTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun credentials(revision: Long = 0) = CredentialState(Mode.SELF_MANAGED,
        mapOf(TrustedPersonId("Sam") to PersonCredential(
            SecretVerifier(ByteArray(16), ByteArray(32) { 1 }, 600_000),
            SecretVerifier(ByteArray(16) { 2 }, ByteArray(32) { 3 }, 600_000))),
        revision = revision,
        recoveryProfile = RecoveryProfile(mapOf(TrustedPersonId("Sam") to "synthetic-account"), null,
            FrictionSettings(1_200_000, 1)))
    private fun initialize(store: JointAuthorityStore) =
        store.initialize(credentials(), WaitingState(storageGeneration = "live"), ElevationRecord())

    @Test fun genesisMustNotAcknowledgeAnUnsyncedSecondCopy() {
        val dir = tmp.newFolder()
        var calls = 0
        val store = JointAuthorityStore(dir).withDirectorySync { d ->
            if (++calls >= 2) throw IOException("second copy directory sync unavailable")
            PlatformDirectorySync.sync(d)
        }
        val result = initialize(store)
        println("genesis: result=$result syncCalls=$calls visibleRevision=${JointAuthorityStore(dir).credentials.read()?.revision}")
        // Was assertEquals(2, calls) at b12a371; the durable rollback now adds its own syncs.
        assertTrue(calls >= 2)
        assertFalse("genesis acknowledged despite failed second copy sync", result)
    }

    @Test fun cancelRequestRetryMustNotAcknowledgeUnsyncedFiles() {
        val dir = tmp.newFolder()
        val store = JointAuthorityStore(dir)
        assertTrue(store.cancelIntents.initialize("request-Sam"))
        var calls = 0
        val failing = DirectorySync { calls++; throw IOException("directory sync unavailable") }
        store.withDirectorySync(failing)
        assertFalse(store.cancelIntents.request("request-Sam"))
        assertEquals(2, calls)
        val retry = JointAuthorityStore(dir).withDirectorySync(failing).cancelIntents.request("request-Sam")
        println("request retry: result=$retry totalSyncCalls=$calls visible=${store.cancelIntents.read("request-Sam")}")
        assertFalse("retry acknowledged with no successful sync", retry)
    }

    @Test fun clearInitializationRetryMustNotAcknowledgeUnsyncedFile() {
        val dir = tmp.newFolder()
        var calls = 0
        val failing = DirectorySync { calls++; throw IOException("directory sync unavailable") }
        val store = JointAuthorityStore(dir).withDirectorySync(failing)
        assertFalse(store.cancelIntents.initialize("request-Jo"))
        val retry = JointAuthorityStore(dir).withDirectorySync(failing).cancelIntents.initialize("request-Jo")
        println("clear retry: result=$retry totalSyncCalls=$calls visible=${store.cancelIntents.read("request-Jo")}")
        assertFalse("retry acknowledged with no successful sync", retry)
    }

    @Test fun failedSyncThenUnavailableRollbackMustNotAcknowledge() {
        val dir = tmp.newFolder()
        val store = JointAuthorityStore(dir)
        assertTrue(initialize(store))
        var calls = 0
        store.withDirectorySync { d ->
            if (++calls == 2) {
                // Both renames are visible. Simulate storage refusing subsequent replacement writes.
                assertTrue(File(d, "authority-a.rec.tmp").mkdir())
                assertTrue(File(d, "authority-b.rec.tmp").mkdir())
                throw IOException("second copy directory sync unavailable")
            }
            PlatformDirectorySync.sync(d)
        }
        val result = store.credentials.compareAndSet(store.credentials.read()!!, credentials(1))
        println("rollback unavailable: result=$result syncCalls=$calls visibleRevision=${JointAuthorityStore(dir).credentials.read()?.revision}")
        // Was assertEquals(2, calls) at b12a371; the rollback attempt now adds its own syncs.
        assertTrue(calls >= 2)
        assertFalse("failed rollback promoted the unsynced second copy to acknowledged", result)
    }

    @Test fun healthyGenesisAndCancelRetriesWork() {
        val store = JointAuthorityStore(tmp.newFolder())
        assertTrue(initialize(store))
        assertTrue(store.cancelIntents.initialize("healthy-Sam"))
        assertTrue(store.cancelIntents.initialize("healthy-Sam"))
        assertTrue(store.cancelIntents.request("healthy-Sam"))
        assertTrue(store.cancelIntents.request("healthy-Sam"))
        assertEquals(CancelIntent.REQUESTED, store.cancelIntents.read("healthy-Sam"))
    }

    @Test fun failedSecondCopySyncWithWorkingRollbackKeepsPreviousRecord() {
        val dir = tmp.newFolder()
        val store = JointAuthorityStore(dir)
        assertTrue(initialize(store))
        var calls = 0
        store.withDirectorySync { d ->
            if (++calls == 2) throw IOException("second copy directory sync unavailable")
            PlatformDirectorySync.sync(d)
        }
        assertFalse(store.credentials.compareAndSet(store.credentials.read()!!, credentials(1)))
        assertEquals(0L, JointAuthorityStore(dir).credentials.read()?.revision)
    }

    // ---- Author additions ----

    /** R3 follow-through: false is "not acknowledged" (PLA-160); fresh reads agree on one whole record. */
    @Test fun unrecoverableRollbackIsNotAcknowledgedAndFreshReadsAgree() {
        val dir = tmp.newFolder()
        val store = JointAuthorityStore(dir)
        assertTrue(initialize(store))
        var calls = 0
        store.withDirectorySync { d ->
            if (++calls == 2) {
                File(d, "authority-a.rec.tmp").mkdir(); File(d, "authority-b.rec.tmp").mkdir()
                throw IOException("second copy directory sync unavailable")
            }
            PlatformDirectorySync.sync(d)
        }
        assertFalse(store.credentials.compareAndSet(store.credentials.read()!!, credentials(1)))
        val a = JointAuthorityStore(dir).credentials.read()?.revision
        assertEquals(a, JointAuthorityStore(dir).credentials.read()?.revision)
        assertTrue("revision $a", a == 0L || a == 1L)
        // A compare-and-set built from a fresh read works once storage recovers.
        File(dir, "authority-a.rec.tmp").delete(); File(dir, "authority-b.rec.tmp").delete()
        store.withDirectorySync(PlatformDirectorySync)
        val fresh = store.credentials.read()!!
        assertTrue(store.credentials.compareAndSet(fresh, credentials(fresh.revision + 1)))
    }

    @Test fun cancelRetriesSucceedOnceDirectorySyncWorksAgain() {
        val dir = tmp.newFolder()
        val failing = DirectorySync { throw IOException("directory sync unavailable") }
        val store = JointAuthorityStore(dir).withDirectorySync(failing)
        assertFalse(store.cancelIntents.initialize("request-Jo"))
        assertFalse(store.cancelIntents.request("request-Jo"))
        var calls = 0
        store.withDirectorySync { d -> calls++; PlatformDirectorySync.sync(d) }
        assertTrue(store.cancelIntents.request("request-Jo"))
        assertTrue("the retry synced even though the files already held REQUESTED", calls >= 2)
        assertEquals(CancelIntent.REQUESTED, JointAuthorityStore(dir).cancelIntents.read("request-Jo"))
    }

    @Test fun failedGenesisLeavesNoRecordAndCanBeRetried() {
        val dir = tmp.newFolder()
        var calls = 0
        val store = JointAuthorityStore(dir).withDirectorySync { d ->
            if (++calls == 2) throw IOException("second copy directory sync unavailable")
            PlatformDirectorySync.sync(d)
        }
        assertFalse(initialize(store))
        assertNull(JointAuthorityStore(dir).credentials.read())
        assertTrue(initialize(store))
        assertEquals(0L, JointAuthorityStore(dir).credentials.read()?.revision)
    }
}
