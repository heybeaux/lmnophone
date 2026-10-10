// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.Mode
import org.plainphone.core.PackageName
import org.plainphone.core.TrustedPersonId
import org.plainphone.core.BootTime
import org.plainphone.core.ClockReading
import org.plainphone.core.RunningClock
import org.plainphone.core.RunningTime
import org.plainphone.policy.CredentialState
import org.plainphone.policy.CredentialStore
import org.plainphone.policy.FloorIdentity
import org.plainphone.policy.FloorRole
import org.plainphone.policy.IdentitySource
import org.plainphone.policy.InstalledPackage
import org.plainphone.policy.KeyDerivation
import org.plainphone.policy.PinResult
import org.plainphone.policy.PinVault
import org.plainphone.policy.SecretEntropy
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.FrictionSettings
import org.plainphone.policy.LostWaitingRecord
import org.plainphone.policy.PersonAuthorityConsumer
import org.plainphone.policy.PersonContact
import org.plainphone.policy.PersonCredential
import org.plainphone.policy.PolicyInput
import org.plainphone.policy.PolicyPlan
import org.plainphone.policy.ReconcileFailure
import org.plainphone.policy.ReconcileOutcome
import org.plainphone.policy.ReconciliationEffects
import org.plainphone.policy.RecoveredState
import org.plainphone.policy.RecoveryProfile
import org.plainphone.policy.RecoveryReceipt
import org.plainphone.policy.WaitingReconciler
import org.plainphone.policy.WaitingState

/**
 * Lost waiting record reconciliation on the real joint store with the real policy reconciler.
 * In this store credentials and the journal share one record, so a confirmed loss with readable
 * credentials is a record whose waiting section no longer decodes; losing the whole record loses the
 * credentials too, and the phone stays held. JVM file-system evidence only.
 */
class ReconciliationStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val anna = TrustedPersonId("anna")
    private val ben = TrustedPersonId("ben")
    private val friction = FrictionSettings(1_200_000, 1)
    private val input = PolicyInput("model", "build", PackageName("org.plainphone"), PackageName("system.messages"),
        listOf(InstalledPackage(PackageName("system.dialer"), true, 1)),
        listOf(FloorIdentity(PackageName("system.dialer"), FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)))

    private val pin get() = CharArray(6) { '1' }
    private var sequence = 0
    private val clock = RunningClock { ClockReading(RunningTime("epoch", 0), BootTime("boot", 0)) }
    private fun vault(store: CredentialStore) = PinVault(store, KeyDerivation { chars, salt, _ ->
        java.security.MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
    }, SecretEntropy { n -> ByteArray(n) { (++sequence).toByte() } }, clock)
    /** Real PIN verifiers, so a Repair grant can come from the vault. */
    private val prepared = vault(object : CredentialStore {
        override fun read() = null
        override fun compareAndSet(expected: CredentialState, replacement: CredentialState) = false
    }).let { v -> listOf(v.prepareCredential(pin).credential, v.prepareCredential(pin).credential) }
    private fun person(seed: Int, contact: PersonContact?) = prepared[seed - 1].copy(generation = seed.toLong(), contact = contact)
    /** Ben is a legacy holder with no contact on record: his address stays explicitly unavailable. */
    private fun credentials(revision: Long = 0) = CredentialState(Mode.TRUSTED,
        mapOf(anna to person(1, PersonContact("synthetic-anna", "synthetic-number-anna")), ben to person(2, null)), revision,
        recoveryProfile = RecoveryProfile(mapOf(anna to "account-anna", ben to "account-ben"), "account-backup", friction))

    private class Effects : ReconciliationEffects {
        var reset: Set<String>? = null
        var resetRead: Set<String>? = null
        var failing: String? = null
        override fun revokeTemporaryAccess() = failing != "revoke"
        override fun isDeviceOwner(): Boolean? = true
        override fun applyPolicy(plan: PolicyPlan) = failing != "policy"
        override fun applyResetProtection(userIds: Set<String>): Boolean { reset = userIds; return failing != "reset" }
        override fun applyFriction(settings: FrictionSettings) = true
        override fun readBackPolicy(plan: PolicyPlan) = true
        override fun readBackResetProtection(userIds: Set<String>): Boolean { resetRead = userIds; return true }
        override fun readBackFriction(settings: FrictionSettings) = true
    }

    private var faults = StorageFaults { _, _ -> }
    private fun open(dir: File) = JointAuthorityStore(dir) { point, d -> faults.at(point, d) }
    private fun store(): Pair<File, JointAuthorityStore> {
        val dir = tmp.newFolder()
        val store = open(dir)
        assertTrue(store.initialize(credentials(), WaitingState(storageGeneration = "live"), ElevationRecord()))
        return dir to store
    }
    private fun reconciler(store: JointAuthorityStore, effects: Effects = Effects(),
        authority: PersonAuthorityConsumer = PersonAuthorityConsumer { _, _, _ -> true }) =
        WaitingReconciler(store.reconciliation, { store.credentials.read() }, { input }, effects, authority)
    /** The waiting section stops decoding while the rest of the record stays readable. */
    private fun loseWaiting(store: JointAuthorityStore) = assertTrue(store.replaceSectionForTest(Section.WAITING, byteArrayOf(7, 7, 7)))

    @Test fun aReadableJournalIsNotLost() {
        val (_, store) = store()
        assertNull(store.reconciliation.lostRecord())
        assertEquals(ReconcileOutcome.IGNORED, reconciler(store).reconcile().outcome)
    }

    @Test fun aWhollyUnreadableRecordIsNeverConfirmedLossAndStaysHeld() {
        val (dir, store) = store()
        File(dir, "authority-a.rec").writeBytes(byteArrayOf(1)); File(dir, "authority-b.rec").writeBytes(byteArrayOf(1))
        val reopened = open(dir)
        assertNull(reopened.reconciliation.lostRecord())
        val result = reconciler(reopened).reconcile()
        assertEquals(ReconcileOutcome.NEEDS_ATTENTION, result.outcome)
        assertEquals(ReconcileFailure.LOSS_RECORD, result.failure)
        assertNull(store.credentials.read())
    }

    @Test fun anUndecodableWaitingSectionGetsOneStableReservedEpisode() {
        val (dir, store) = store()
        loseWaiting(store)
        assertNull(store.waitingStore.read())
        assertNotNull(store.credentials.read())
        val first = store.reconciliation.lostRecord()!!
        assertTrue(first.id.startsWith("loss-"))
        assertEquals(first.id, open(dir).reconciliation.lostRecord()!!.id)
        assertTrue(File(dir, "reserved").listFiles()!!.any { it.name.startsWith("loss-") })
    }

    @Test fun recoveryUsesActiveHoldersPlusBackupAndFreezesAddresses() {
        val (dir, store) = store()
        loseWaiting(store)
        val lost = store.reconciliation.lostRecord()!!
        val effects = Effects()
        assertEquals(ReconcileOutcome.RECOVERED, reconciler(store, effects).reconcile().outcome)
        assertEquals(setOf("account-anna", "account-ben", "account-backup"), effects.reset)
        assertEquals(effects.reset, effects.resetRead)
        val waiting = open(dir).waitingStore.read()!!
        assertEquals(lost.id, waiting.storageGeneration)
        assertEquals(0L, waiting.revision)
        assertNull(waiting.operation)
        val receipt = waiting.recoveryReceipts.single()
        assertEquals("recovered:${lost.id.length}:${lost.id}", receipt.key)
        assertEquals(mapOf(anna to 1L, ben to 2L), receipt.recipientGenerations)
        assertEquals(RecoveredState.ACTIVE, receipt.state)
        val audit = open(dir).recoveryAddresses()!!.single()
        assertEquals(receipt.key, audit.key)
        assertEquals("synthetic-number-anna", audit.recipientContacts[anna]!!.number)
        assertTrue(audit.recipientContacts.containsKey(ben)); assertNull(audit.recipientContacts[ben])
        // Restart after success sees the valid state and does not enqueue again.
        assertNull(open(dir).reconciliation.lostRecord())
        assertEquals(ReconcileOutcome.IGNORED, reconciler(open(dir)).reconcile().outcome)
        assertEquals(1, open(dir).recoveryAddresses()!!.size)
    }

    @Test fun acknowledgingTheOutboxItemKeepsTheAddressAudit() {
        val (dir, store) = store()
        loseWaiting(store)
        assertEquals(ReconcileOutcome.RECOVERED, reconciler(store).reconcile().outcome)
        val waiting = store.waitingStore.read()!!
        assertTrue(store.waitingStore.compareAndSet(waiting, WaitingState(1, null, emptyList(), waiting.storageGeneration)))
        assertTrue(open(dir).waitingStore.read()!!.recoveryReceipts.isEmpty())
        assertEquals(1, open(dir).recoveryAddresses()!!.size)
    }

    @Test fun completionIsRefusedUnlessEverythingStillMatches() {
        val (_, store) = store()
        loseWaiting(store)
        val lost = store.reconciliation.lostRecord()!!
        val current = store.credentials.read()!!
        fun receipt(id: String = lost.id, generations: Map<TrustedPersonId, Long> = mapOf(anna to 1L, ben to 2L)) =
            RecoveryReceipt("recovered:${id.length}:$id", Mode.TRUSTED, generations, friction)
        fun state(generation: String = lost.id, revision: Long = 0, receipts: List<RecoveryReceipt> = listOf(receipt())) =
            WaitingState(revision, null, emptyList(), generation, receipts)
        val r = store.reconciliation
        assertFalse("another episode", r.completeReconciliation(LostWaitingRecord("loss-other"), current, state()))
        assertFalse("new generation", r.completeReconciliation(lost, current, state(generation = "live")))
        assertFalse("revision", r.completeReconciliation(lost, current, state(revision = 1)))
        assertFalse("no receipt", r.completeReconciliation(lost, current, state(receipts = emptyList())))
        assertFalse("receipt key", r.completeReconciliation(lost, current, state(receipts = listOf(receipt(id = "loss-other")))))
        assertFalse("recipients", r.completeReconciliation(lost, current, state(receipts = listOf(receipt(generations = mapOf(anna to 1L))))))
        assertFalse("stale credentials", r.completeReconciliation(lost, credentials(revision = 9), state()))
        assertTrue(store.credentials.compareAndSet(current, credentials(revision = 1)))
        assertFalse("credentials changed", r.completeReconciliation(lost, current, state()))
        assertEquals(lost.id, store.reconciliation.lostRecord()!!.id)
        assertNull(store.waitingStore.read())
    }

    @Test fun ordinaryWritesNeverOverwriteALostJournal() {
        val (_, store) = store()
        loseWaiting(store)
        val lost = store.reconciliation.lostRecord()!!
        assertFalse(store.waitingStore.compareAndSet(WaitingState(storageGeneration = "live"), WaitingState(1, storageGeneration = "live")))
        assertEquals(lost.id, store.reconciliation.lostRecord()!!.id)
    }

    @Test fun crashAtEveryCompletionWritePointIsStillLostOrFullyRecovered() {
        for (point in WritePoint.entries) {
            val (dir, store) = store()
            loseWaiting(store)
            val lost = store.reconciliation.lostRecord()!!
            faults = StorageFaults { at, _ -> if (at == point) throw SimulatedCrash() }
            runCatching { reconciler(store).reconcile() }
            faults = StorageFaults { _, _ -> }
            val reopened = open(dir)
            val waiting = reopened.waitingStore.read()
            if (waiting == null) {
                assertEquals("$point", lost.id, reopened.reconciliation.lostRecord()!!.id)
                assertEquals("$point", ReconcileOutcome.RECOVERED, reconciler(reopened).reconcile().outcome)
            } else {
                assertEquals("$point", lost.id, waiting.storageGeneration)
                assertEquals("$point", 1, waiting.recoveryReceipts.size)
                assertEquals("$point", waiting.recoveryReceipts.single().key, reopened.recoveryAddresses()!!.single().key)
            }
        }
    }

    @Test fun preLossCopiesAreFencedEvenWithAHigherJournalRevision() {
        val (dir, store) = store()
        repeat(3) { val w = store.waitingStore.read()!!; assertTrue(store.waitingStore.compareAndSet(w, WaitingState(w.revision + 1, storageGeneration = "live"))) }
        val preLoss = listOf("authority-a.rec", "authority-b.rec").associateWith { File(dir, it).readBytes() }
        loseWaiting(store)
        assertEquals(ReconcileOutcome.RECOVERED, reconciler(store).reconcile().outcome)
        preLoss.forEach { (name, bytes) -> File(dir, name).writeBytes(bytes) }
        assertNull(open(dir).waitingStore.read())
    }

    @Test fun pinRepairRunsTheSameReconciliationForACurrentHolder() {
        val (_, store) = store()
        loseWaiting(store)
        val vault = vault(store.credentials)
        val reconciler = reconciler(store, authority = vault)
        assertNull(reconciler.prepareRepair(TrustedPersonId("stranger")))
        val request = reconciler.prepareRepair(anna)!!
        // A grant for another action is refused; the exact Repair action works once.
        val wrong = (vault.authenticate(anna, pin, org.plainphone.policy.PrivilegedAction("other", "x")) as PinResult.Authorized).authority
        assertEquals(ReconcileOutcome.REFUSED, reconciler.repair(request, wrong).outcome)
        val grant = (vault.authenticate(anna, pin, request.action) as PinResult.Authorized).authority
        assertEquals(ReconcileOutcome.RECOVERED, reconciler.repair(request, grant).outcome)
    }

    @Test fun aFailedEffectLeavesTheSameEpisodeHeld() {
        val (_, store) = store()
        loseWaiting(store)
        val lost = store.reconciliation.lostRecord()!!
        val effects = Effects().apply { failing = "reset" }
        assertEquals(ReconcileFailure.RESET_APPLY, reconciler(store, effects).reconcile().failure)
        assertEquals(lost.id, store.reconciliation.lostRecord()!!.id)
        assertNull(store.waitingStore.read())
    }

    @Test fun schemaFiveRecordsMigrateWithAnEmptyRecoverySection() {
        val (_, current) = store()
        val sections = Section.entries.filter { it != Section.RECOVERY }.map { current.sectionBytesForTest(it)!! }
        val dir = tmp.newFolder()
        val bytes = LegacyAuthority.encode(4, sections)
        val fence = java.nio.ByteBuffer.allocate(8).putLong(4).array()
        listOf("authority-a" to bytes, "authority-b" to bytes, "fence-a" to fence, "fence-b" to fence)
            .forEach { (name, payload) -> assertTrue(AtomicRecord(dir, name, 5).write(payload) {}) }
        assertNull(JointAuthorityStore(dir).credentials.read())
        assertTrue(JointAuthorityStore(dir).migrate())
        val migrated = JointAuthorityStore(dir)
        assertEquals(credentials().revision, migrated.credentials.read()!!.revision)
        assertEquals("live", migrated.waitingStore.read()!!.storageGeneration)
        assertTrue(migrated.recoveryAddresses()!!.isEmpty())
        assertNull(migrated.reconciliation.lostRecord())
    }
}
