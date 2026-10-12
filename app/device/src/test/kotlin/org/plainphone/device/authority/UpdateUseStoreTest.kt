// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.BootTime
import org.plainphone.core.Mode
import org.plainphone.core.RunningTime
import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationDebit
import org.plainphone.policy.ElevationGrant
import org.plainphone.policy.ElevationLedger
import org.plainphone.policy.ElevationPersonBinding
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.ElevationState
import org.plainphone.policy.ElevationTarget
import org.plainphone.policy.FailureCounter
import org.plainphone.policy.PersonContact
import org.plainphone.policy.PersonCredential
import org.plainphone.policy.SecretVerifier
import org.plainphone.policy.StagedUpdateBinding
import org.plainphone.policy.UpdatePhase
import org.plainphone.policy.UpdateReceipt
import org.plainphone.policy.UpdateRecord
import org.plainphone.policy.UpdateSelfCheck
import org.plainphone.policy.UpdateSubmission
import org.plainphone.policy.WaitingState

/** The Update store port against the real file store: joint write, result CAS, receipt outbox, migration. */
class UpdateUseStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dir: File by lazy { tmp.newFolder("authority") }
    private val alice = TrustedPersonId("alice")
    private val bob = TrustedPersonId("bob")
    private val aliceContact = PersonContact("synthetic-alice", "synthetic-number-a")
    private var faults = StorageFaults { _, _ -> }
    private fun open() = JointAuthorityStore(dir) { point, d -> faults.at(point, d) }

    private fun credential(generation: Long, contact: PersonContact?) = PersonCredential(
        SecretVerifier(ByteArray(16), ByteArray(32) { 1 }, 600_000), SecretVerifier(ByteArray(16) { 2 }, ByteArray(32) { 3 }, 600_000),
        FailureCounter(0), FailureCounter(0), generation, contact)
    private fun credentials(revision: Long = 0, bobContact: PersonContact? = null) = CredentialState(Mode.TRUSTED,
        mapOf(alice to credential(3, aliceContact), bob to credential(5, bobContact)), revision)

    private val staged = StagedUpdateBinding("ab".repeat(32), 7)
    private val sha = staged.sha256
    private val start = BootTime("boot", 1_000)
    private val deadline = BootTime("boot", 601_000)
    private val ledger = ElevationLedger(listOf(ElevationDebit("old", RunningTime("e", 1)), ElevationDebit("grant-1", RunningTime("e", 2))))
    private val granted = ElevationRecord(4, ElevationState(ledger,
        grant = ElevationGrant.restore("grant-1", ElevationTarget.Update(staged), start, deadline, false)!!),
        person = ElevationPersonBinding(alice, 3, 0))
    /** Exactly what the controller writes: same ledger, no grant, no person, one revision higher. */
    private val usedUp = ElevationRecord(5, ElevationState(ledger))
    private val idle = ElevationRecord(4, ElevationState(ledger))
    private fun receipt(id: String = "grant-1", actor: TrustedPersonId = alice, generation: Long = 3) =
        UpdateReceipt(id, actor, generation, mapOf(alice to 3L, bob to 5L))
    private fun submission(receipt: UpdateReceipt = receipt(), deadline: BootTime? = this.deadline, sessionId: Int = 41,
        sha256: String = sha, version: Long = 7) = UpdateSubmission(sessionId, sha256, version, receipt, deadline)

    private fun initialized(elevation: ElevationRecord = granted, bobContact: PersonContact? = null): JointAuthorityStore =
        open().also { assertTrue(it.initialize(credentials(bobContact = bobContact), WaitingState(storageGeneration = "g"), elevation)) }
    private fun allSections(store: JointAuthorityStore) = Section.entries.map { store.sectionBytesForTest(it)!!.toList() }

    private fun commitSelf(store: JointAuthorityStore, submission: UpdateSubmission = submission(),
        expected: ElevationRecord = granted, replacement: ElevationRecord = usedUp, check: () -> Boolean = { true }): Boolean =
        store.update.commitUpdateUse(expected, replacement, store.credentials.read()!!, store.update.readUpdate()!!, submission, check)

    @Test fun enrollmentCreatesTheOnlyEmptyRecordAndMissingReadsNull() {
        assertNull(open().update.readUpdate())
        val store = initialized()
        val record = open().update.readUpdate()!!
        assertEquals(0, record.revision)
        assertNull(record.submission)
        assertEquals(emptyList<StoredUpdateReceipt>(), store.updateReceipts())
    }

    @Test fun selfManagedUseWritesUsedUpElevationAndSubmittedWithFrozenReceipt() {
        val store = initialized(bobContact = PersonContact("synthetic-bob", "synthetic-number-b"))
        var checks = 0
        val before = store.update.readUpdate()!!
        assertTrue(commitSelf(store) {
            checks++
            // Re-entrant reads see the exact pair under comparison; mutations are refused.
            assertEquals(granted.revision, store.elevationStore.read()!!.revision)
            assertEquals(0, store.update.readUpdate()!!.revision)
            val current = store.credentials.read()!!
            assertFalse(store.credentials.compareAndSet(current, credentials(1)))
            assertFalse(store.update.compareAndSetUpdate(before, UpdateRecord(1)))
            true
        })
        assertEquals(1, checks)
        val reopened = open()
        val record = reopened.update.readUpdate()!!
        assertEquals(1, record.revision)
        val saved = record.submission!!
        assertEquals(UpdatePhase.SUBMITTED, saved.phase)
        assertEquals(UpdateSelfCheck.PENDING, saved.selfCheck)
        assertEquals(41, saved.sessionId)
        assertEquals(deadline, saved.elevationDeadline)
        assertEquals("grant-1", saved.receipt.id)
        assertEquals(mapOf(alice to 3L, bob to 5L), saved.receipt.recipientGenerations)
        assertArrayEquals(AuthorityCodec.encodeElevation(usedUp), reopened.sectionBytesForTest(Section.ELEVATION))
        val outbox = reopened.updateReceipts()!!.single()
        assertEquals("grant-1", outbox.receipt.id)
        assertEquals(mapOf(alice to ("synthetic-alice" to "synthetic-number-a"), bob to ("synthetic-bob" to "synthetic-number-b")),
            outbox.recipientContacts.mapValues { it.value?.let { c -> c.name to c.number } })
        assertEquals(emptySet<TrustedPersonId>(), outbox.acknowledged)
    }

    @Test fun falseCheckOrStaleRecordChangesNothing() {
        val store = initialized()
        val before = allSections(store)
        assertFalse(commitSelf(store) { false })
        assertEquals(before, allSections(open()))
        // The check runs once and may not be skipped by a second call.
        var checks = 0
        assertTrue(commitSelf(open()) { checks++; true })
        assertEquals(1, checks)
        val after = allSections(open())
        // Replaying the same use against the original expectations is refused (duplicate submission).
        assertFalse(open().update.commitUpdateUse(granted, usedUp, credentials(), UpdateRecord(), submission()) { true })
        assertEquals(after, allSections(open()))
    }

    @Test fun credentialOrElevationChangeBeforeOrDuringTheCheckRefuses() {
        val store = initialized()
        val stale = store.credentials.read()!!
        assertTrue(store.credentials.compareAndSet(stale, credentials(1)))
        val before = allSections(store)
        assertFalse(store.update.commitUpdateUse(granted, usedUp, stale, store.update.readUpdate()!!, submission()) { true })
        assertEquals(before, allSections(open()))
        // Another thread's write waits for the whole transaction; it can't land between the two compares.
        val other = initializedIn(tmp.newFolder("other"))
        val changed = CountDownLatch(1)
        assertTrue(other.update.commitUpdateUse(granted, usedUp, other.credentials.read()!!, other.update.readUpdate()!!, submission()) {
            Thread {
                val now = other.credentials.read()!!
                if (other.credentials.compareAndSet(now, credentials(now.revision + 1))) changed.countDown()
            }.start()
            !changed.await(300, TimeUnit.MILLISECONDS) && other.credentials.read()!!.revision == 0L
        })
        assertTrue(changed.await(5, TimeUnit.SECONDS))
        assertEquals(1, other.update.readUpdate()!!.revision)
        assertEquals(1, other.credentials.read()!!.revision)
    }

    private fun initializedIn(folder: File) = JointAuthorityStore(folder).also {
        assertTrue(it.initialize(credentials(), WaitingState(storageGeneration = "g"), granted))
    }

    @Test fun onlyTheExactUsedUpPairIsAccepted() {
        val store = initialized()
        val before = allSections(store)
        val bad = listOf(
            // Elevation shapes: a refunded debit, a kept grant, a skipped revision, a kept holder.
            { commitSelf(store, replacement = ElevationRecord(5, ElevationState(ElevationLedger(ledger.debits.take(1))))) },
            { commitSelf(store, replacement = granted) },
            { commitSelf(store, replacement = ElevationRecord(6, ElevationState(ledger))) },
            // Submission facts must match the grant, its holder and its deadline.
            { commitSelf(store, submission(sha256 = "cd".repeat(32))) },
            { commitSelf(store, submission(version = 8)) },
            { commitSelf(store, submission(deadline = BootTime("boot", 900_000))) },
            { commitSelf(store, submission(deadline = null)) },
            { commitSelf(store, submission(receipt("other-id"))) },
            { commitSelf(store, submission(UpdateReceipt("grant-1", bob, 5, mapOf(alice to 3L, bob to 5L)))) },
            { commitSelf(store, submission(UpdateReceipt("grant-1", alice, 3, mapOf(alice to 3L)))) },
            { commitSelf(store, submission(UpdateReceipt("grant-1", alice, 3, mapOf(alice to 3L, bob to 4L)))) },
            { commitSelf(store, submission().copy(phase = UpdatePhase.FAILED)) },
        )
        bad.forEachIndexed { i, attempt -> assertFalse("case $i", attempt()); assertEquals("case $i", before, allSections(open())) }
    }

    @Test fun outstandingSubmissionOrPendingSelfCheckRefusesAndALaterSubmissionKeepsTheOutbox() {
        val store = initialized()
        assertTrue(commitSelf(store))
        val submitted = store.update.readUpdate()!!
        // A second use while SUBMITTED is outstanding.
        assertTrue(store.elevationStore.compareAndSet(usedUp, granted2(usedUp), null))
        assertFalse(commitSelf(store, submission(receipt("grant-2"), deadline2, sessionId = 42, version = 8), granted2(usedUp), used2()))
        // INSTALLED with a pending self-check still refuses; a passed one allows the next submission.
        val installed = UpdateRecord(2, submitted.submission!!.copy(phase = UpdatePhase.INSTALLED))
        assertTrue(store.update.compareAndSetUpdate(submitted, installed))
        assertFalse(commitSelf(store, submission(receipt("grant-2"), deadline2, sessionId = 42, version = 8), granted2(usedUp), used2()))
        assertTrue(store.update.compareAndSetUpdate(installed, UpdateRecord(3, installed.submission!!.copy(selfCheck = UpdateSelfCheck.PASSED))))
        assertTrue(commitSelf(store, submission(receipt("grant-2"), deadline2, sessionId = 42, version = 8), granted2(usedUp), used2()))
        val record = open().update.readUpdate()!!
        assertEquals(4, record.revision)
        assertEquals(42, record.submission!!.sessionId)
        // The first receipt was never delivered, so it is still in the outbox beside the new one.
        assertEquals(listOf("grant-1", "grant-2"), open().updateReceipts()!!.map { it.receipt.id })
    }

    private val deadline2 = BootTime("boot", 700_000)
    private fun granted2(base: ElevationRecord): ElevationRecord {
        val debits = ElevationLedger(base.state.ledger.debits + ElevationDebit("grant-2", RunningTime("e", 3)))
        return ElevationRecord(base.revision + 1, ElevationState(debits, grant = ElevationGrant.restore("grant-2",
            ElevationTarget.Update(StagedUpdateBinding(sha, 8)), BootTime("boot", 100_000), deadline2, false)!!),
            person = ElevationPersonBinding(alice, 3, 0))
    }
    private fun used2() = granted2(usedUp).let { ElevationRecord(it.revision + 1, ElevationState(it.state.ledger)) }

    @Test fun supervisedUseKeepsTheElevationRecordExactly() {
        val store = initialized(idle)
        val supervised = UpdateSubmission(41, sha, 7, UpdateReceipt("request-1", bob, 5, mapOf(alice to 3L, bob to 5L)))
        val elevation = store.sectionBytesForTest(Section.ELEVATION)
        // A changed elevation or an invented deadline is refused.
        assertFalse(store.update.commitUpdateUse(idle, ElevationRecord(5, ElevationState(ledger)), credentials(), UpdateRecord(), supervised) { true })
        assertFalse(store.update.commitUpdateUse(idle, idle, credentials(), UpdateRecord(), supervised.copy(elevationDeadline = deadline)) { true })
        assertTrue(store.update.commitUpdateUse(idle, idle, credentials(), UpdateRecord(), supervised) { true })
        assertArrayEquals(elevation, open().sectionBytesForTest(Section.ELEVATION))
        assertEquals("request-1", open().update.readUpdate()!!.submission!!.receipt.id)
    }

    @Test fun crashAtEveryWritePointExposesTheOriginalOrTheCompletePair() {
        for (point in WritePoint.entries) {
            val folder = tmp.newFolder("crash-$point")
            assertTrue(JointAuthorityStore(folder).initialize(credentials(), WaitingState(storageGeneration = "g"), granted))
            val crashing = JointAuthorityStore(folder) { at, _ -> if (at == point) throw SimulatedCrash() }
            val original = allSections(crashing)
            try {
                crashing.update.commitUpdateUse(granted, usedUp, credentials(), UpdateRecord(), submission()) { true }
            } catch (_: SimulatedCrash) { }
            val after = JointAuthorityStore(folder)
            val update = after.update.readUpdate()!!
            val elevation = after.elevationStore.read()!!
            if (update.submission == null) {
                assertEquals("$point", original, allSections(after))
            } else {
                assertEquals("$point", 1, update.revision)
                assertArrayEquals("$point", AuthorityCodec.encodeElevation(usedUp), AuthorityCodec.encodeElevation(elevation))
                assertEquals("$point", "grant-1", after.updateReceipts()!!.single().receipt.id)
            }
            // Before copy B lands the old pair reads; after it, the complete pair.
            assertEquals("$point", point >= WritePoint.AFTER_COPY_B, update.submission != null)
        }
    }

    @Test fun resultCasChangesOnlyTheResultAndKeepsTheReceipt() {
        val store = initialized()
        assertTrue(commitSelf(store))
        val submitted = store.update.readUpdate()!!
        val s = submitted.submission!!
        val outbox = store.sectionBytesForTest(Section.UPDATE)!!.let { AuthorityCodec.decodeUpdate(it)!!.outbox }
        val others = listOf(Section.CREDENTIALS, Section.WAITING, Section.ELEVATION, Section.RECEIPTS, Section.APPROVALS)
            .map { store.sectionBytesForTest(it)!!.toList() }
        val refused = listOf(
            UpdateRecord(2, s.copy(sessionId = 7, phase = UpdatePhase.FAILED)),
            UpdateRecord(2, s.copy(receipt = receipt("grant-9"), phase = UpdatePhase.FAILED)),
            UpdateRecord(2, s.copy(elevationDeadline = null, phase = UpdatePhase.FAILED)),
            UpdateRecord(3, s.copy(phase = UpdatePhase.FAILED)),
            UpdateRecord(2, s),
            UpdateRecord(2, null),
        )
        refused.forEachIndexed { i, r -> assertFalse("case $i", store.update.compareAndSetUpdate(submitted, r)) }
        val failed = UpdateRecord(2, s.copy(phase = UpdatePhase.FAILED))
        assertTrue(store.update.compareAndSetUpdate(submitted, failed))
        // FAILED is not terminal: a late install still moves it to INSTALLED.
        val installed = UpdateRecord(3, s.copy(phase = UpdatePhase.INSTALLED))
        assertFalse(store.update.compareAndSetUpdate(submitted, installed))
        assertTrue(store.update.compareAndSetUpdate(failed, installed))
        assertFalse(store.update.compareAndSetUpdate(installed, UpdateRecord(4, s.copy(phase = UpdatePhase.FAILED))))
        val passed = UpdateRecord(4, s.copy(phase = UpdatePhase.INSTALLED, selfCheck = UpdateSelfCheck.FAILED))
        assertTrue(store.update.compareAndSetUpdate(installed, passed))
        assertFalse(store.update.compareAndSetUpdate(passed, UpdateRecord(5, s.copy(phase = UpdatePhase.INSTALLED, selfCheck = UpdateSelfCheck.PASSED))))
        val reopened = open()
        assertEquals(UpdateSelfCheck.FAILED, reopened.update.readUpdate()!!.submission!!.selfCheck)
        assertEquals("grant-1", reopened.update.readUpdate()!!.submission!!.receipt.id)
        assertEquals(outbox.map { it.receipt.id }, reopened.updateReceipts()!!.map { it.receipt.id })
        assertEquals(others, listOf(Section.CREDENTIALS, Section.WAITING, Section.ELEVATION, Section.RECEIPTS, Section.APPROVALS)
            .map { reopened.sectionBytesForTest(it)!!.toList() })
    }

    @Test fun damagedReceiptOrSectionReadsNull() {
        // Actor missing from its recipients, and actor generation differing from its recipient entry.
        assertNull(AuthorityCodec.decodeUpdate(UpdateCodecFixtures.section(actor = "alice", actorGeneration = 3, recipients = listOf("bob" to 5L))))
        assertNull(AuthorityCodec.decodeUpdate(UpdateCodecFixtures.section(actor = "alice", actorGeneration = 2, recipients = listOf("alice" to 3L))))
        assertNotNull(AuthorityCodec.decodeUpdate(UpdateCodecFixtures.section(actor = "alice", actorGeneration = 3, recipients = listOf("alice" to 3L))))
        val bytes = UpdateCodecFixtures.section(actor = "alice", actorGeneration = 3, recipients = listOf("alice" to 3L))
        assertNull(AuthorityCodec.decodeUpdate(bytes + 0))
        assertNull(AuthorityCodec.decodeUpdate(bytes.copyOf(bytes.size - 1)))
    }

    @Test fun receiptsDeliverFromFrozenContactsWithStableIdsAndDurableAcknowledgements() {
        val store = initialized(bobContact = PersonContact("synthetic-bob", "synthetic-number-b"))
        assertTrue(commitSelf(store))
        // Bob's contact changes after the submission; delivery still uses the frozen one.
        val now = store.credentials.read()!!
        assertTrue(store.credentials.compareAndSet(now, credentials(1, PersonContact("synthetic-bob-2", "synthetic-number-c"))))
        val sent = mutableListOf<Triple<String, String, String>>()
        var failBob = true
        val sender = UpdateNoticeSender { dedup, _, contact, _ ->
            sent += Triple(dedup, contact.name, contact.number)
            !(contact.number == "synthetic-number-b" && failBob)
        }
        assertFalse(UpdateReceiptDelivery(open(), sender).deliverPending())
        assertEquals(setOf(alice), open().updateReceipts()!!.single().acknowledged)
        failBob = false
        assertTrue(UpdateReceiptDelivery(open(), sender).deliverPending())
        assertEquals(emptyList<StoredUpdateReceipt>(), open().updateReceipts())
        // Frozen (name, number) pairs: Bob's later "synthetic-bob-2" contact is never used.
        assertEquals(listOf(Triple("grant-1:alice", "synthetic-alice", "synthetic-number-a"),
            Triple("grant-1:bob", "synthetic-bob", "synthetic-number-b"),
            Triple("grant-1:bob", "synthetic-bob", "synthetic-number-b")), sent)
        // Nothing left to send; the submission record keeps its receipt.
        assertTrue(UpdateReceiptDelivery(open(), sender).deliverPending())
        assertEquals(3, sent.size)
        assertEquals("grant-1", open().update.readUpdate()!!.submission!!.receipt.id)
    }

    @Test fun aRecipientWithoutAnAddressIsNeverLookedUpLater() {
        val store = initialized(bobContact = null)
        assertTrue(commitSelf(store))
        val now = store.credentials.read()!!
        assertTrue(store.credentials.compareAndSet(now, credentials(1, PersonContact("synthetic-bob", "synthetic-number-b"))))
        val sent = mutableListOf<String>()
        assertTrue(UpdateReceiptDelivery(open(), UpdateNoticeSender { dedup, _, _, _ -> sent += dedup; true }).deliverPending())
        assertEquals(listOf("grant-1:alice"), sent)
        assertEquals(emptyList<StoredUpdateReceipt>(), open().updateReceipts())
    }

    @Test fun crashBetweenSendAndAcknowledgementResendsTheSameId() {
        val store = initialized()
        assertTrue(commitSelf(store))
        val sent = mutableListOf<String>()
        faults = StorageFaults { point, _ -> if (point == WritePoint.BEFORE_COPY_A) throw SimulatedCrash() }
        try {
            UpdateReceiptDelivery(open(), UpdateNoticeSender { dedup, _, _, _ -> sent += dedup; true }).deliverPending()
        } catch (_: SimulatedCrash) { }
        faults = StorageFaults { _, _ -> }
        assertTrue(UpdateReceiptDelivery(open(), UpdateNoticeSender { dedup, _, _, _ -> sent += dedup; true }).deliverPending())
        assertEquals(listOf("grant-1:alice", "grant-1:alice"), sent)
    }

    @Test fun aLockHeldOutsideTheCoordinatorCannotDeadlockTheFinalCheck() {
        val store = initialized()
        val monitor = Object()
        val inside = CountDownLatch(1)
        val writerWaiting = CountDownLatch(1)
        val waitBefore = JointAuthorityStore.coordinatorWaitMillis
        JointAuthorityStore.coordinatorWaitMillis = 300
        try {
            // Thread B takes the outside lock, then wants the coordinator (as PinVault.verify would).
            val b = Thread {
                synchronized(monitor) {
                    inside.await(5, TimeUnit.SECONDS); writerWaiting.countDown()
                    val now = store.credentials.read()
                    if (now != null) store.credentials.compareAndSet(now, credentials(1))
                }
            }
            b.start()
            var result: Boolean? = null
            val a = Thread {
                // Thread A holds the coordinator and wants the outside lock inside finalCheck (as PinVault.consumeFor would).
                result = commitSelf(store) { inside.countDown(); writerWaiting.await(5, TimeUnit.SECONDS); synchronized(monitor) { true } }
            }
            a.start()
            a.join(10_000); b.join(10_000)
            assertFalse("no deadlock", a.isAlive || b.isAlive)
            assertEquals(true, result)
            assertEquals(1, open().update.readUpdate()!!.revision)
        } finally {
            JointAuthorityStore.coordinatorWaitMillis = waitBefore
        }
    }

    /** PLA-328 reviewer probe, kept unchanged: ordinary final-check reads survive nested effects that refuse them. */
    @Test fun reviewOrdinaryViewSurvivesNestedEffectRefusal() {
        val store = initialized()
        val expected = store.credentials.read()!!
        assertTrue(store.serialized(false) {
            commitSelf(store) {
                assertEquals(expected.revision, store.credentials.read()!!.revision)
                store.androidEffect {
                    org.junit.Assert.assertNull(store.credentials.read())
                    store.androidEffect { org.junit.Assert.assertNull(store.credentials.read()) }
                    org.junit.Assert.assertNull(store.credentials.read())
                }
                assertEquals(expected.revision, store.credentials.read()!!.revision)
                true
            }
        })
    }

    @Test fun reviewAndroidEffectCannotReadTheTransactionView() {
        val store = initialized()
        var seen: org.plainphone.policy.CredentialState? = null
        assertTrue(store.serialized(false) {
            commitSelf(store) {
                store.androidEffect { seen = store.credentials.read() }
                true
            }
        })
        org.junit.Assert.assertNull("Android effect must not bypass refusal through the transaction view", seen)
    }

    @Test fun schema4RecordMigratesWithAnEmptyUpdateRecordAndKeepsEverySection() {
        val sections = listOf(AuthorityCodec.encodeCredentials(credentials())!!, AuthorityCodec.encodeWaiting(WaitingState(storageGeneration = "g"))!!,
            AuthorityCodec.encodeElevation(granted)!!, AuthorityCodec.encodeReceipts(emptyList())!!,
            AuthorityCodec.encodeApprovals(org.plainphone.policy.AppApprovalState())!!)
        for (point in listOf<WritePoint?>(null) + WritePoint.entries) {
            val folder = tmp.newFolder("schema4-$point")
            val record = LegacyAuthority.encode(9, sections)
            for (name in listOf("authority-a", "authority-b")) assertTrue(AtomicRecord(folder, name, 4).write(record) {})
            for (name in listOf("fence-a", "fence-b")) assertTrue(AtomicRecord(folder, name, 4).write(java.nio.ByteBuffer.allocate(8).putLong(9).array()) {})
            assertNull(JointAuthorityStore(folder).update.readUpdate())
            if (point != null) {
                try { JointAuthorityStore(folder) { at, _ -> if (at == point) throw SimulatedCrash() }.migrate() } catch (_: SimulatedCrash) { }
            }
            val store = JointAuthorityStore(folder)
            assertTrue("$point", store.migrate())
            val update = store.update.readUpdate()!!
            assertEquals(0, update.revision); assertNull(update.submission)
            assertEquals(emptyList<StoredUpdateReceipt>(), store.updateReceipts())
            sections.forEachIndexed { i, bytes -> assertArrayEquals("$point $i", bytes, store.sectionBytesForTest(Section.entries[i])) }
        }
    }

    @Test fun noStorageReadOrRepairAfterTheFinalCheck() {
        // R2 (PLA-207): every load, decode and repair happens before finalCheck. Copies made
        // unreadable during the check are therefore never read; only the durable write follows.
        val store = initialized()
        val copies = dir.listFiles()!!.filter { it.name.startsWith("authority-a") || it.name.startsWith("authority-b") }
        assertTrue(copies.isNotEmpty())
        assertTrue(commitSelf(store) {
            copies.forEach { it.writeBytes(byteArrayOf(9, 9, 9)) }
            true
        })
        assertEquals(UpdatePhase.SUBMITTED, open().update.readUpdate()!!.submission!!.phase)
        assertArrayEquals(AuthorityCodec.encodeElevation(usedUp), open().sectionBytesForTest(Section.ELEVATION))
    }

    @Test fun aFailedFinalCheckWritesNothing() {
        val store = initialized()
        val before = allSections(store)
        assertFalse(commitSelf(store) { false })
        assertEquals(before, allSections(open()))
    }
}

/** Raw UPDATE-section bytes, for receipts the policy's own constructor would refuse. */
internal object UpdateCodecFixtures {
    fun section(actor: String, actorGeneration: Long, recipients: List<Pair<String, Long>>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(out).use { d ->
            d.writeLong(1); d.writeBoolean(true)
            d.writeInt(41); d.writeUTF("ab".repeat(32)); d.writeLong(7)
            d.writeUTF("grant-1"); d.writeUTF(actor); d.writeLong(actorGeneration)
            d.writeInt(recipients.size); recipients.forEach { (id, g) -> d.writeUTF(id); d.writeLong(g) }
            d.writeBoolean(false); d.writeUTF("SUBMITTED"); d.writeUTF("PENDING")
            d.writeInt(0)
        }
        return out.toByteArray()
    }

}
