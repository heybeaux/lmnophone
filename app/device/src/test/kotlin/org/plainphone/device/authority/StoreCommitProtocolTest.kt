// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import java.nio.file.Files
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
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.FailureCounter
import org.plainphone.policy.FrictionSettings
import org.plainphone.policy.FrictionSettingsPatch
import org.plainphone.policy.LockoutNotice
import org.plainphone.policy.CredentialChannel
import org.plainphone.policy.PersonCredential
import org.plainphone.policy.RecoveryProfile
import org.plainphone.policy.SecretVerifier
import org.plainphone.policy.WaitingEvent
import org.plainphone.policy.WaitingIdentity
import org.plainphone.policy.WaitingOperation
import org.plainphone.policy.WaitingPhase
import org.plainphone.policy.WaitingState
import org.plainphone.policy.WaitingText

/** PLA-116 findings F1-F4: the return value always matches disk, records always read back, one lock per directory, canonical bytes. */
class StoreCommitProtocolTest {
    @get:Rule val tmp = TemporaryFolder()
    private val self = TrustedPersonId("self")
    private val other = TrustedPersonId("other")
    private var faults = StorageFaults { _, _ -> }

    private fun person(seed: Int, failures: Int = 0) = PersonCredential(SecretVerifier(ByteArray(16) { seed.toByte() }, ByteArray(32) { 1 }, 600_000),
        SecretVerifier(ByteArray(16) { 2 }, ByteArray(32) { 3 }, 600_000), FailureCounter(failures))
    private fun selfManaged(revision: Long = 0, failures: Int = 0) = CredentialState(Mode.SELF_MANAGED,
        mapOf(self to person(1, failures)), revision = revision,
        recoveryProfile = RecoveryProfile(mapOf(self to "synthetic-account"), null, FrictionSettings(1_200_000, 1)))
    private fun open(dir: File) = JointAuthorityStore(dir) { point, d -> faults.at(point, d) }
    private fun initialized(dir: File = tmp.newFolder()) = open(dir).also {
        assertTrue(it.initialize(selfManaged(), WaitingState(storageGeneration = "live"), ElevationRecord()))
    }
    private fun bump(store: JointAuthorityStore, failures: Int): Boolean {
        val current = store.credentials.read()!!
        return store.credentials.compareAndSet(current, selfManaged(current.revision + 1, failures))
    }

    // F1: whichever file fails, true means the new record is durable and false means the old one still is.
    @Test fun everyFailedFileWriteReportsWhatIsOnDisk() {
        for (name in listOf("authority-a", "authority-b", "fence-a", "fence-b")) {
            val dir = tmp.newFolder(); val store = initialized(dir)
            File(dir, "$name.rec.tmp").mkdirs()
            val result = bump(store, 4)
            File(dir, "$name.rec.tmp").deleteRecursively()
            val read = open(dir).credentials.read()!!
            assertEquals(name, if (result) 1L else 0L, read.revision)
            // The next write after the fault succeeds from whatever state was reported.
            assertTrue(name, bump(open(dir), 5))
            assertEquals(name, read.revision + 1, open(dir).credentials.read()!!.revision)
        }
    }

    @Test fun firstFenceFailureIsTheUncommittedCase() {
        val dir = tmp.newFolder(); val store = initialized(dir)
        File(dir, "fence-a.rec.tmp").mkdirs()
        assertFalse(bump(store, 4))
        assertEquals(0, open(dir).credentials.read()!!.people.getValue(self).pinCounter.failures)
    }

    @Test fun readbackFailureOfTheFirstFenceKeepsTheOldRecord() {
        val dir = tmp.newFolder(); val store = initialized(dir)
        faults = StorageFaults { at, d -> if (at == WritePoint.AFTER_FENCE_A) File(d, "fence-a.rec").writeBytes(ByteArray(5)) }
        assertFalse(bump(store, 4))
        faults = StorageFaults { _, _ -> }
        assertEquals(0L, open(dir).credentials.read()!!.revision)
    }

    @Test fun crashAtEveryPointThenEveryLaterWriteStaysConsistent() {
        for (point in WritePoint.entries) {
            val dir = tmp.newFolder(); val store = initialized(dir)
            faults = StorageFaults { at, _ -> if (at == point) throw SimulatedCrash() }
            try { bump(store, 4) } catch (_: SimulatedCrash) { }
            faults = StorageFaults { _, _ -> }
            val first = open(dir).credentials.read()!!.revision
            assertTrue("$point", first == 0L || first == 1L)
            // Two further commits, then lose either copy: the acknowledged record survives in the other.
            assertTrue("$point", bump(open(dir), 5)); assertTrue("$point", bump(open(dir), 6))
            for (copy in listOf("authority-a", "authority-b")) {
                val saved = File(dir, "$copy.rec").readBytes()
                File(dir, "$copy.rec").delete()
                assertEquals("$point $copy", first + 2, open(dir).credentials.read()?.revision)
                File(dir, "$copy.rec").writeBytes(saved)
            }
            assertEquals("$point", first + 2, open(dir).credentials.read()!!.revision)
        }
    }

    // F2: a record that would not read back is refused before anything is written.
    @Test fun waitingRecordOverTheDecoderLimitIsRefusedAndThePriorRecordKept() {
        val dir = tmp.newFolder(); val store = initialized(dir)
        val patch = FrictionSettingsPatch.restore(self, 0, 0, FrictionSettings(1_200_000, 1), FrictionSettings(600_000, 1))!!
        val op = WaitingOperation(WaitingIdentity("wait-1", "synthetic-cancel"), self, 0, 0, Mode.SELF_MANAGED,
            RunningTime("epoch", 0), WaitingPhase.PENDING, mapOf(self to 0L), recipientContacts = mapOf(self to null), frictionPatch = patch)
        val texts = List(4096) { WaitingText("t$it", WaitingEvent.STARTED, op, mapOf(self to 0L)) }
        val full = WaitingState(1, op, texts, "live")
        assertTrue(store.waitingStore.compareAndSet(store.waitingStore.read()!!, full))
        val over = WaitingState(2, op, texts + WaitingText("t4096", WaitingEvent.STARTED, op, mapOf(self to 0L)), "live")
        assertNull(AuthorityCodec.encodeWaiting(over))
        assertFalse(store.waitingStore.compareAndSet(store.waitingStore.read()!!, over))
        assertEquals(4096, open(dir).waitingStore.read()!!.texts.size)
    }

    // F3: a symlinked alias of the same directory shares the one coordinator.
    @Test fun aliasedDirectoryCannotInterleaveAWrite() {
        val dir = tmp.newFolder(); val store = initialized(dir)
        val alias = File(tmp.root, "alias-${dir.name}")
        Files.createSymbolicLink(alias.toPath(), dir.toPath())
        val viaAlias = JointAuthorityStore(alias)
        val snapshot = viaAlias.credentials.read()!!
        var nested: Boolean? = null
        faults = StorageFaults { at, _ -> if (at == WritePoint.AFTER_COPY_A && nested == null) nested = viaAlias.credentials.compareAndSet(snapshot, selfManaged(1, 9)) }
        assertTrue(bump(store, 1))
        assertEquals(false, nested)
        assertEquals(1, open(dir).credentials.read()!!.people.getValue(self).pinCounter.failures)
    }

    // F4: equal content compares equal whatever the insertion order of maps and sets.
    @Test fun insertionOrderOfUnorderedFieldsNeverCausesAFalseConflict() {
        fun trusted(order: List<TrustedPersonId>, revision: Long = 0) = CredentialState(Mode.TRUSTED,
            order.associateWith { if (it == self) person(1) else person(2) }, revision = revision,
            pendingNotices = order.map { LockoutNotice(it, CredentialChannel.PIN, 0) }.toSet(),
            recoveryProfile = RecoveryProfile(order.associateWith { "synthetic-${it.value}" }, null, FrictionSettings(1_200_000, 1)))
        assertArrayEquals(AuthorityCodec.encodeCredentials(trusted(listOf(self, other))),
            AuthorityCodec.encodeCredentials(trusted(listOf(other, self))))
        val store = open(tmp.newFolder())
        assertTrue(store.initialize(trusted(listOf(self, other)), WaitingState(storageGeneration = "live"), ElevationRecord()))
        assertTrue(store.credentials.compareAndSet(trusted(listOf(other, self)), trusted(listOf(other, self), 1)))
    }
}
