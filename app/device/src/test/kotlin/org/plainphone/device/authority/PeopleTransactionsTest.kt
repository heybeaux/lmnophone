// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import java.security.MessageDigest
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
import org.plainphone.core.ClockReading
import org.plainphone.core.Mode
import org.plainphone.core.RunningClock
import org.plainphone.core.RunningTime
import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.AppliedPeopleChange
import org.plainphone.policy.BatteryState
import org.plainphone.policy.CancelIntent
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationLedger
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.ElevationState
import org.plainphone.policy.EnrollmentConfirmations
import org.plainphone.policy.EnrollmentWitness
import org.plainphone.policy.FrictionSettings
import org.plainphone.policy.IncomingPersonEnrollment
import org.plainphone.policy.KeyDerivation
import org.plainphone.policy.PeopleChangeController
import org.plainphone.policy.PeopleChangeFailure
import org.plainphone.policy.PeopleChangeOutcome
import org.plainphone.policy.PeopleResetProtectionEffects
import org.plainphone.policy.PersonContact
import org.plainphone.policy.PinResult
import org.plainphone.policy.PinVault
import org.plainphone.policy.RecoveryProfile
import org.plainphone.policy.SecretEntropy
import org.plainphone.policy.WaitingChangeController
import org.plainphone.policy.WaitingChangeKind
import org.plainphone.policy.WaitingEvent
import org.plainphone.policy.WaitingHandOverSupport
import org.plainphone.policy.WaitingOutcome
import org.plainphone.policy.WaitingPhase
import org.plainphone.policy.WaitingSkipResult
import org.plainphone.policy.WaitingSkipSupport
import org.plainphone.policy.WaitingStart
import org.plainphone.policy.WaitingState

/**
 * Trusted-person waits over the real file-backed joint store with the real policy controllers.
 * Every check reopens the directory, so nothing process-local can mask a missing durable write.
 * These are JVM file-system tests: they say nothing about Android durability or which account a wipe accepts.
 */
class PeopleTransactionsTest {
    @get:Rule val tmp = TemporaryFolder()
    private val day = 86_400_000L
    private val battery = BatteryState(80, false)
    private val anna = TrustedPersonId("anna")
    private val ben = TrustedPersonId("ben")
    private val cleo = TrustedPersonId("cleo")
    private val all = EnrollmentConfirmations(true, true, true, true, true, true, true, true, true)

    /** Synthetic reset-protection effects; the list it holds is what a readback would see. */
    private class Effects : PeopleResetProtectionEffects {
        var applied: Set<String>? = null
        val calls = mutableListOf<String>()
        var failAt: String? = null
        var onCall: ((String) -> Unit)? = null
        override fun setResetProtection(userIds: Set<String>): Boolean = step("set") { applied = userIds }
        override fun broadcastResetProtectionChanged(): Boolean = step("broadcast") {}
        override fun readBackResetProtection(userIds: Set<String>): Boolean = step("readback") {} && applied == userIds
        private fun step(name: String, block: () -> Unit): Boolean {
            calls += name; onCall?.invoke(name)
            if (failAt == name) return false
            block(); return true
        }
    }

    private inner class Rig(people: Int = 1, val dir: File = tmp.newFolder()) {
        val pin get() = CharArray(6) { '1' }
        val newPin get() = CharArray(6) { '7' }
        var now: ClockReading? = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
        val clock = RunningClock { now }
        var faults = StorageFaults { _, _ -> }
        var sequence = 0
        val codes = mutableMapOf<TrustedPersonId, String>()
        val effects = Effects()
        lateinit var store: JointAuthorityStore
        lateinit var vault: PinVault
        lateinit var waiting: WaitingChangeController
        lateinit var people: PeopleChangeController

        init {
            wire()
            val holders = listOf(anna, ben).take(people).associateWith { id ->
                val prepared = vault.prepareCredential(pin)
                codes[id] = prepared.recoveryCode
                prepared.credential.copy(contact = PersonContact("synthetic-${id.value}", "synthetic-number-${id.value}"))
            }
            val credentials = CredentialState(Mode.TRUSTED, holders,
                recoveryProfile = RecoveryProfile(holders.keys.associateWith { "account-${it.value}" }, "account-backup",
                    FrictionSettings(1_200_000, 1)))
            assertTrue(store.initialize(credentials, WaitingState(storageGeneration = "live"), ElevationRecord(0, ElevationState(ElevationLedger()))))
        }

        /** Restart: new store, vault and controllers over the same directory. */
        fun wire() {
            store = JointAuthorityStore(dir) { point, d -> faults.at(point, d) }
            vault = PinVault(store.credentials, KeyDerivation { chars, salt, _ ->
                MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
            }, SecretEntropy { n -> ByteArray(n) { (++sequence).toByte() } }, clock)
            waiting = WaitingChangeController(store.waitingStore, { store.credentials.read() }, clock, vault,
                store.identities, store.cancelIntents,
                WaitingSkipSupport(vault, store.skipCommitter), WaitingHandOverSupport(vault, store.handOverCommitter))
            people = PeopleChangeController(store.waitingStore, store.credentials, store.peopleCommitter, effects, clock, vault)
        }
        fun disk() = JointAuthorityStore(dir)
        fun credentials() = disk().credentials.read()!!
        fun journal() = disk().waitingStore.read()!!
        fun auth(person: TrustedPersonId, action: org.plainphone.policy.PrivilegedAction) =
            (vault.authenticate(person, pin, action) as PinResult.Authorized).authority
        fun incoming(id: TrustedPersonId = cleo, witness: EnrollmentWitness = EnrollmentWitness.Helper): IncomingPersonEnrollment {
            val prepared = vault.prepareCredential(CharArray(6) { '5' })
            return checkNotNull(IncomingPersonEnrollment.confirm(id, PersonContact("synthetic-${id.value}", "synthetic-number-${id.value}"),
                "account-${id.value}", prepared, all, witness))
        }
        fun start(prepared: WaitingStart?, starter: TrustedPersonId = anna): String {
            val r = checkNotNull(prepared)
            assertEquals(WaitingOutcome.PENDING, waiting.startPeopleChange(r, auth(starter, r.action), true))
            return r.requestId
        }
        fun startAdd() = start(waiting.prepareAdd(anna, incoming()))
        fun mature(id: String) {
            now = ClockReading(RunningTime("epoch", day), BootTime("boot", day))
            assertEquals(WaitingOutcome.COMMITTING, waiting.tick(id, battery))
        }
        fun files() = dir.listFiles()!!.filter { it.isFile }.associate { it.name to it.readBytes() }
        fun restore(snapshot: Map<String, ByteArray>, names: Collection<String>) = names.forEach { File(dir, it).writeBytes(snapshot.getValue(it)) }
    }

    // ---- Codec ----

    @Test fun peoplePatchesRoundTripByteForByteWithNullLegacyContacts() {
        val rig = Rig(people = 2)
        val id = rig.start(rig.waiting.prepareHandOver(anna, rig.incoming(witness = EnrollmentWitness.CurrentPerson(ben))))
        val stored = rig.journal()
        val op = stored.operation!!
        assertEquals(WaitingChangeKind.HAND_OVER, op.kind)
        assertEquals(setOf(anna), op.peoplePatch!!.removedSlots)
        assertEquals(cleo, op.peoplePatch!!.incoming!!.person)
        assertEquals(EnrollmentWitness.CurrentPerson(ben), op.peoplePatch!!.incoming!!.witness)
        assertEquals("account-cleo", op.peoplePatch!!.incoming!!.resetProtectionUserId)
        val bytes = AuthorityCodec.encodeWaiting(stored)!!
        assertArrayEquals(bytes, AuthorityCodec.encodeWaiting(AuthorityCodec.decodeWaiting(bytes)!!))
        // A legacy holder with no contact keeps an explicit unavailable entry in the frozen snapshot.
        val legacy = CredentialState(Mode.TRUSTED, mapOf(anna to rig.credentials().people.getValue(anna).copy(contact = null)),
            recoveryProfile = RecoveryProfile(mapOf(anna to "account-anna"), null, FrictionSettings(1_200_000, 1)))
        val legacyRig = Rig()
        assertTrue(legacyRig.store.credentials.compareAndSet(legacyRig.credentials(), legacy))
        val removeId = legacyRig.startAdd()
        val text = legacyRig.journal().texts.single { it.operation.identity.requestId == removeId }
        assertTrue(text.recipientContacts.containsKey(anna))
        assertNull(text.recipientContacts[anna])
        assertEquals(id, op.identity.requestId)
    }

    @Test fun removeAndAddPatchesDecodeThroughThePolicyRestoreChecks() {
        val rig = Rig(people = 2)
        rig.start(rig.waiting.prepareRemove(anna, ben))
        val op = rig.journal().operation!!
        assertEquals(WaitingChangeKind.REMOVE_PERSON, op.kind)
        assertNull(op.peoplePatch!!.incoming)
        val add = Rig()
        add.startAdd()
        assertEquals(WaitingChangeKind.ADD_PERSON, add.journal().operation!!.kind)
    }

    // ---- Cancel intents ----

    @Test fun cancelIntentsAreDurableMonotonicAndUnknownWhenMissing() {
        val rig = Rig()
        val intents = rig.store.cancelIntents
        assertEquals(CancelIntent.UNKNOWN, intents.read("r1"))
        assertTrue(intents.initialize("r1"))
        assertEquals(CancelIntent.CLEAR, rig.disk().cancelIntents.read("r1"))
        assertTrue(intents.request("r1"))
        assertTrue(intents.request("r1"))
        assertEquals(CancelIntent.REQUESTED, rig.disk().cancelIntents.read("r1"))
        assertFalse(rig.disk().cancelIntents.initialize("r1"))
        assertEquals(CancelIntent.REQUESTED, rig.disk().cancelIntents.read("r1"))
        // REQUESTED persists even with no CLEAR entry and an unreadable operation record.
        File(rig.dir, "authority-a.rec").writeBytes(byteArrayOf(1)); File(rig.dir, "authority-b.rec").writeBytes(byteArrayOf(1))
        assertTrue(rig.disk().cancelIntents.request("r2"))
        assertEquals(CancelIntent.REQUESTED, rig.disk().cancelIntents.read("r2"))
    }

    /** PLA-128 F1: the REQUESTED marker alone must not be the only record of a cancel. */
    @Test fun losingEitherIntentFileAfterRequestStaysRequested() {
        for (lost in listOf("intent-requested-", "intent-clear-")) {
            val rig = Rig()
            val id = rig.startAdd()
            // The intent write succeeded but the cancelled-state write never happened.
            assertTrue(rig.store.cancelIntents.request(id))
            rig.dir.listFiles()!!.filter { it.name.startsWith(lost) }.forEach { assertTrue(it.delete()) }
            rig.wire()
            assertEquals(lost, CancelIntent.REQUESTED, rig.store.cancelIntents.read(id))
            assertFalse(lost, rig.store.cancelIntents.initialize(id))
            rig.now = ClockReading(RunningTime("epoch", day), BootTime("boot", day))
            assertEquals(lost, WaitingOutcome.CANCELLED, rig.waiting.tick(id, battery))
            assertEquals(lost, WaitingPhase.CANCELLED, rig.journal().operation!!.phase)
        }
    }

    @Test fun losingBothIntentFilesReadsUnknownAndBlocksMaturity() {
        val rig = Rig()
        val id = rig.startAdd()
        assertTrue(rig.store.cancelIntents.request(id))
        rig.dir.listFiles()!!.filter { it.name.startsWith("intent-") }.forEach { assertTrue(it.delete()) }
        rig.wire()
        assertEquals(CancelIntent.UNKNOWN, rig.store.cancelIntents.read(id))
        rig.now = ClockReading(RunningTime("epoch", day), BootTime("boot", day))
        assertEquals(WaitingOutcome.NOT_READY, rig.waiting.tick(id, battery))
        assertEquals(WaitingPhase.PENDING, rig.journal().operation!!.phase)
    }

    @Test fun damagedIntentEntriesNeverReadAsClear() {
        val rig = Rig()
        val intents = rig.store.cancelIntents
        assertTrue(intents.initialize("r1"))
        rig.dir.listFiles()!!.filter { it.name.startsWith("intent-clear-") }.forEach { it.writeBytes(byteArrayOf(9, 9)) }
        assertEquals(CancelIntent.UNKNOWN, rig.disk().cancelIntents.read("r1"))
        assertTrue(intents.initialize("r2")); assertTrue(intents.request("r2"))
        // Even a damaged REQUESTED marker stays restrictive.
        rig.dir.listFiles()!!.filter { it.name.startsWith("intent-requested-") }.forEach { it.writeBytes(byteArrayOf(9)) }
        assertEquals(CancelIntent.REQUESTED, rig.disk().cancelIntents.read("r2"))
        assertFalse(rig.disk().cancelIntents.initialize("r2"))
    }

    // ---- Forward commit ----

    @Test fun forwardAddAppliesAtomicallyThenCompletesWithFixedReceipt() {
        val rig = Rig()
        val id = rig.startAdd(); rig.mature(id)
        val before = rig.credentials()
        assertEquals(PeopleChangeOutcome.APPLIED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        assertEquals(listOf("set", "broadcast", "readback"), rig.effects.calls)
        assertEquals(setOf("account-anna", "account-cleo", "account-backup"), rig.effects.applied)
        val after = rig.credentials()
        assertEquals(setOf(anna, cleo), after.people.keys)
        assertEquals(before.structureGeneration + 1, after.structureGeneration)
        assertEquals(AppliedPeopleChange(id, "live", after.structureGeneration), after.appliedPeopleChange)
        assertEquals(WaitingPhase.COMMITTING, rig.journal().operation!!.phase)
        // Unaffected holder kept byte for byte.
        assertArrayEquals(holderBytes(before, anna), holderBytes(after, anna))

        rig.wire()
        assertEquals(PeopleChangeOutcome.COMPLETED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        val done = rig.journal()
        assertEquals(WaitingPhase.COMPLETED, done.operation!!.phase)
        val receipt = done.texts.last()
        assertEquals(WaitingEvent.COMPLETED, receipt.event)
        assertEquals(setOf(anna, cleo), receipt.recipients)
        assertEquals("synthetic-number-cleo", receipt.recipientContacts[cleo]!!.number)
        // Replay after completion neither re-applies nor rewrites the reset list.
        rig.effects.calls.clear()
        assertEquals(PeopleChangeOutcome.IGNORED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        assertTrue(rig.effects.calls.isEmpty())
        assertEquals(after.structureGeneration, rig.credentials().structureGeneration)
    }

    @Test fun failedResetEffectKeepsCommittingAndOldHolders() {
        for (step in listOf("set", "broadcast", "readback")) {
            val rig = Rig()
            val id = rig.startAdd(); rig.mature(id)
            rig.effects.failAt = step
            val result = rig.people.tick(id, WaitingPhase.COMMITTING)
            assertEquals(step, PeopleChangeOutcome.NEEDS_ATTENTION, result.outcome)
            assertEquals(step, setOf(anna), rig.credentials().people.keys)
            assertEquals(step, WaitingPhase.COMMITTING, rig.journal().operation!!.phase)
            rig.effects.failAt = null
            assertEquals(step, PeopleChangeOutcome.APPLIED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        }
    }

    /** "After set" is "before broadcast", "after broadcast" is "before readback"; after readback is the patch write point. */
    @Test fun crashAroundEveryResetEffectResumesInTheSameDirection() {
        for (rollback in listOf(false, true)) for (step in listOf("set", "broadcast", "readback")) {
            val rig = Rig()
            val id = rig.startAdd(); rig.mature(id)
            if (rollback) {
                val stop = checkNotNull(rig.people.prepareStop(id, anna))
                assertEquals(PeopleChangeOutcome.ROLLING_BACK, rig.people.stop(stop, rig.auth(anna, stop.action)).outcome)
            }
            val direction = if (rollback) WaitingPhase.ROLLING_BACK else WaitingPhase.COMMITTING
            rig.effects.onCall = { if (it == step) throw SimulatedCrash() }
            runCatching { rig.people.tick(id, direction) }
            rig.effects.onCall = null
            rig.wire()
            assertEquals("$rollback $step", setOf(anna), rig.credentials().people.keys)
            assertEquals("$rollback $step", direction, rig.journal().operation!!.phase)
            // A stale callback in the other direction does nothing.
            val other = if (rollback) WaitingPhase.COMMITTING else WaitingPhase.ROLLING_BACK
            assertEquals("$rollback $step", PeopleChangeOutcome.IGNORED, rig.people.tick(id, other).outcome)
            val expected = if (rollback) PeopleChangeOutcome.CANCELLED else PeopleChangeOutcome.APPLIED
            assertEquals("$rollback $step", expected, rig.people.tick(id, direction).outcome)
            assertEquals("$rollback $step", if (rollback) setOf(anna) else setOf(anna, cleo), rig.credentials().people.keys)
        }
    }

    @Test fun crashAtEveryPatchWritePointLeavesOldOrNewHoldersNeverNeither() {
        for (point in WritePoint.entries) {
            val rig = Rig()
            val id = rig.startAdd(); rig.mature(id)
            rig.faults = StorageFaults { at, _ -> if (at == point) throw SimulatedCrash() }
            runCatching { rig.people.tick(id, WaitingPhase.COMMITTING) }
            rig.faults = StorageFaults { _, _ -> }
            rig.wire()
            val holders = rig.credentials().people.keys
            assertTrue("$point: $holders", holders == setOf(anna) || holders == setOf(anna, cleo))
            // Old or new holders can still authenticate with their own PIN.
            holders.forEach { assertTrue("$point", rig.vault.authenticate(it, if (it == cleo) CharArray(6) { '5' } else rig.pin,
                org.plainphone.policy.PrivilegedAction("probe", "probe")) is PinResult.Authorized) }
            // Resuming finishes exactly once.
            var outcome = rig.people.tick(id, WaitingPhase.COMMITTING).outcome
            if (outcome == PeopleChangeOutcome.APPLIED) outcome = rig.people.tick(id, WaitingPhase.COMMITTING).outcome
            assertEquals("$point", PeopleChangeOutcome.COMPLETED, outcome)
            assertEquals("$point", setOf(anna, cleo), rig.credentials().people.keys)
            assertEquals("$point", 1, rig.journal().texts.count { it.event == WaitingEvent.COMPLETED })
        }
    }

    @Test fun incomingPinRecoveryAfterPatchCannotReinstallTheSetupPin() {
        val rig = Rig()
        val id = rig.startAdd(); rig.mature(id)
        assertEquals(PeopleChangeOutcome.APPLIED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        val applied = rig.credentials()
        // A credential-only write (a wrong PIN) keeps the applied marker.
        assertTrue(rig.vault.authenticate(cleo, CharArray(6) { '0' }, org.plainphone.policy.PrivilegedAction("probe", "probe")) is PinResult.Wrong)
        val counted = rig.credentials()
        assertEquals(applied.appliedPeopleChange, counted.appliedPeopleChange)
        assertEquals(applied.revision + 1, counted.revision)
        rig.wire()
        assertEquals(PeopleChangeOutcome.COMPLETED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        assertEquals(counted.people.getValue(cleo).pinCounter, rig.credentials().people.getValue(cleo).pinCounter)
    }

    @Test fun patchIsRefusedOutsideItsExactCommittingRequest() {
        val rig = Rig()
        val id = rig.startAdd()
        val credentials = rig.credentials(); val journal = rig.journal()
        val marker = AppliedPeopleChange(id, "live", credentials.structureGeneration + 1)
        val patched = CredentialState(credentials.mode, credentials.people, credentials.revision + 1, credentials.structureGeneration + 1,
            credentials.pendingNotices, credentials.recoveryProfile, marker)
        // Still PENDING: refused, nothing written.
        assertFalse(rig.store.peopleCommitter.applyPatch(credentials, patched, journal))
        rig.mature(id)
        // Holders not matching the stored patch (no incoming person) are refused.
        assertFalse(rig.store.peopleCommitter.applyPatch(rig.credentials(), patched, rig.journal()))
        assertEquals(credentials.revision, rig.credentials().revision)
    }

    // ---- Stop and rollback ----

    @Test fun stopPersistsRollingBackBeforeAnyEffectThenRestoresTheCurrentList() {
        val rig = Rig()
        val id = rig.startAdd(); rig.mature(id)
        val stop = checkNotNull(rig.people.prepareStop(id, anna))
        var phaseAtFirstEffect: WaitingPhase? = null
        rig.effects.onCall = { if (phaseAtFirstEffect == null) phaseAtFirstEffect = rig.journal().operation!!.phase }
        assertEquals(PeopleChangeOutcome.ROLLING_BACK, rig.people.stop(stop, rig.auth(anna, stop.action)).outcome)
        assertTrue(rig.effects.calls.isEmpty())
        val rolling = rig.journal()
        assertEquals(WaitingPhase.ROLLING_BACK, rolling.operation!!.phase)
        assertEquals(anna, rolling.operation!!.stopper)
        assertEquals(WaitingEvent.ROLLING_BACK, rolling.texts.last().event)
        // A stale forward callback never resumes forward after rollback.
        assertEquals(PeopleChangeOutcome.IGNORED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        rig.wire()
        assertEquals(PeopleChangeOutcome.CANCELLED, rig.people.tick(id, WaitingPhase.ROLLING_BACK).outcome)
        assertEquals(WaitingPhase.ROLLING_BACK, phaseAtFirstEffect)
        assertEquals(setOf("account-anna", "account-backup"), rig.effects.applied)
        assertEquals(setOf(anna), rig.credentials().people.keys)
        assertEquals(WaitingEvent.CANCELLED_AFTER_ROLLBACK, rig.journal().texts.last().event)
    }

    @Test fun failedRollbackReadbackStaysRollingBack() {
        val rig = Rig()
        val id = rig.startAdd(); rig.mature(id)
        val stop = checkNotNull(rig.people.prepareStop(id, anna))
        rig.people.stop(stop, rig.auth(anna, stop.action))
        rig.effects.failAt = "readback"
        assertEquals(PeopleChangeOutcome.NEEDS_ATTENTION, rig.people.tick(id, WaitingPhase.ROLLING_BACK).outcome)
        assertEquals(WaitingPhase.ROLLING_BACK, rig.journal().operation!!.phase)
        rig.effects.failAt = null
        assertEquals(PeopleChangeOutcome.CANCELLED, rig.people.tick(id, WaitingPhase.ROLLING_BACK).outcome)
    }

    @Test fun stopIsUnavailableAfterThePatch() {
        val rig = Rig()
        val id = rig.startAdd(); rig.mature(id)
        val early = checkNotNull(rig.people.prepareStop(id, anna))
        assertEquals(PeopleChangeOutcome.APPLIED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        assertNull(rig.people.prepareStop(id, anna))
        assertEquals(PeopleChangeOutcome.REFUSED, rig.people.stop(early, rig.auth(anna, early.action)).outcome)
        val credentials = rig.credentials(); val journal = rig.journal()
        val forced = journal.operation!!.let {
            org.plainphone.policy.WaitingOperation(it.identity, it.starter, it.generation, it.structureGeneration, it.mode, it.startedAt,
                WaitingPhase.ROLLING_BACK, it.recipientGenerations, 0, null, null, it.peoplePatch, it.recipientContacts, anna, 0)
        }
        // The committer itself refuses rollback direction once the marker matches this request.
        assertFalse(rig.store.peopleCommitter.move(credentials, journal, WaitingState(journal.revision + 1, forced,
            journal.texts + org.plainphone.policy.WaitingText("forced", WaitingEvent.ROLLING_BACK, forced,
                credentials.people.mapValues { it.value.generation }, credentials.people.mapValues { it.value.contact }), journal.storageGeneration)))
    }

    @Test fun crashAtEveryStopWritePointLeavesCommittingOrRollingBack() {
        for (point in WritePoint.entries) {
            val rig = Rig()
            val id = rig.startAdd(); rig.mature(id)
            val stop = checkNotNull(rig.people.prepareStop(id, anna))
            val grant = rig.auth(anna, stop.action)
            rig.faults = StorageFaults { at, _ -> if (at == point) throw SimulatedCrash() }
            runCatching { rig.people.stop(stop, grant) }
            rig.faults = StorageFaults { _, _ -> }
            val phase = rig.journal().operation!!.phase
            assertTrue("$point: $phase", phase == WaitingPhase.COMMITTING || phase == WaitingPhase.ROLLING_BACK)
            assertTrue("$point", rig.effects.calls.isEmpty())
            assertEquals("$point", setOf(anna), rig.credentials().people.keys)
        }
    }

    @Test fun staleCommittingReplicaAfterAcknowledgedRollbackReadsAsLost() {
        val rig = Rig()
        val id = rig.startAdd(); rig.mature(id)
        val committing = rig.files()
        val stop = checkNotNull(rig.people.prepareStop(id, anna))
        assertEquals(PeopleChangeOutcome.ROLLING_BACK, rig.people.stop(stop, rig.auth(anna, stop.action)).outcome)
        // Only the older COMMITTING copies survive; the fence still records the rollback sequence.
        rig.restore(committing, listOf("authority-a.rec", "authority-b.rec"))
        rig.wire()
        assertNull(rig.store.waitingStore.read())
        assertNull(rig.store.credentials.read())
        val result = rig.people.tick(id, WaitingPhase.COMMITTING)
        assertEquals(PeopleChangeOutcome.NEEDS_ATTENTION, result.outcome)
        assertEquals(PeopleChangeFailure.JOURNAL_READ, result.failure)
        assertTrue(rig.effects.calls.isEmpty())
    }

    @Test fun staleCredentialReplicaCannotResurrectOutgoingHolder() {
        val rig = Rig(people = 2)
        val id = rig.start(rig.waiting.prepareHandOver(anna, rig.incoming()))
        rig.mature(id)
        val outgoing = rig.files()
        assertEquals(PeopleChangeOutcome.APPLIED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        rig.restore(outgoing, listOf("authority-a.rec"))
        File(rig.dir, "authority-b.rec").delete()
        rig.wire()
        assertNull(rig.store.credentials.read())
        assertFalse(rig.vault.authenticate(anna, rig.pin, org.plainphone.policy.PrivilegedAction("probe", "probe")) is PinResult.Authorized)
    }

    @Test fun storageGenerationChangeIsRefusedByEveryPeopleWrite() {
        val rig = Rig()
        val id = rig.startAdd(); rig.mature(id)
        val credentials = rig.credentials(); val journal = rig.journal()
        val other = WaitingState(journal.revision, journal.operation, journal.texts, "other", journal.recoveryReceipts)
        val op = journal.operation!!.let {
            org.plainphone.policy.WaitingOperation(it.identity, it.starter, it.generation, it.structureGeneration, it.mode, it.startedAt,
                WaitingPhase.ROLLING_BACK, it.recipientGenerations, 0, null, null, it.peoplePatch, it.recipientContacts, anna, 0)
        }
        val text = org.plainphone.policy.WaitingText("k", WaitingEvent.ROLLING_BACK, op,
            credentials.people.mapValues { it.value.generation }, credentials.people.mapValues { it.value.contact })
        assertFalse(rig.store.peopleCommitter.move(credentials, other, WaitingState(journal.revision + 1, op, journal.texts + text, "other")))
        assertFalse(rig.store.peopleCommitter.move(credentials, journal, WaitingState(journal.revision + 1, op, journal.texts + text, "other")))
        assertEquals(WaitingPhase.COMMITTING, rig.journal().operation!!.phase)
    }

    // ---- Skip and hand-over joint transactions ----

    @Test fun skipRotatesOnlyTheSkipperAndEntersCommittingInOneTransaction() {
        val rig = Rig(people = 2)
        val id = rig.startRemove()
        val before = rig.credentials()
        val result = rig.waiting.skip(id, ben, rig.codes.getValue(ben).toCharArray(), rig.newPin, true, battery)
        assertTrue(result is WaitingSkipResult.Committing)
        val after = rig.credentials()
        assertEquals(before.people.getValue(ben).generation + 1, after.people.getValue(ben).generation)
        assertArrayEquals(holderBytes(before, anna), holderBytes(after, anna))
        val journal = rig.journal()
        assertEquals(WaitingPhase.COMMITTING, journal.operation!!.phase)
        assertEquals(ben, journal.operation!!.skipper)
        assertEquals(WaitingEvent.SKIPPED, journal.texts.last().event)
    }

    /** PLA-128 F2: a failed fence write must not report NotSkipped over rotated credentials. */
    @Test fun failedFenceWritesReportExactlyWhatIsDurable() {
        for ((point, fence) in listOf(WritePoint.AFTER_COPY_A to "fence-a.rec", WritePoint.AFTER_FENCE_A to "fence-b.rec")) {
            val rig = Rig(people = 2)
            val id = rig.startRemove()
            val before = rig.credentials()
            rig.faults = StorageFaults { at, d ->
                if (at == point) { File(d, fence).deleteRecursively(); File(d, "$fence/x").apply { parentFile.mkdirs(); writeText("x") } }
            }
            val result = rig.waiting.skip(id, ben, rig.codes.getValue(ben).toCharArray(), rig.newPin, true, battery)
            rig.faults = StorageFaults { _, _ -> }
            rig.wire()
            val after = rig.credentials(); val phase = rig.journal().operation!!.phase
            assertEquals(fence, point == WritePoint.AFTER_FENCE_A, result is WaitingSkipResult.Committing)
            if (result is WaitingSkipResult.Committing) {
                assertEquals(fence, before.revision + 1, after.revision)
                assertEquals(fence, WaitingPhase.COMMITTING, phase)
            } else {
                assertEquals(fence, before.revision, after.revision)
                assertEquals(fence, before.people.getValue(ben).generation, after.people.getValue(ben).generation)
                assertEquals(fence, WaitingPhase.PENDING, phase)
            }
        }
    }

    @Test fun failedFenceDuringPatchKeepsOldHoldersAndReportsFailure() {
        val rig = Rig()
        val id = rig.startAdd(); rig.mature(id)
        rig.faults = StorageFaults { at, d ->
            if (at == WritePoint.AFTER_COPY_A) { File(d, "fence-a.rec").deleteRecursively(); File(d, "fence-a.rec/x").apply { parentFile.mkdirs(); writeText("x") } }
        }
        val result = rig.people.tick(id, WaitingPhase.COMMITTING)
        rig.faults = StorageFaults { _, _ -> }
        assertEquals(PeopleChangeOutcome.NEEDS_ATTENTION, result.outcome)
        assertEquals(PeopleChangeFailure.PERSISTENCE, result.failure)
        rig.wire()
        assertEquals(setOf(anna), rig.credentials().people.keys)
        assertEquals(WaitingPhase.COMMITTING, rig.journal().operation!!.phase)
        // Once storage recovers, the retry applies.
        File(rig.dir, "fence-a.rec").deleteRecursively()
        assertEquals(PeopleChangeOutcome.APPLIED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
    }

    @Test fun crashAtEverySkipWritePointExposesAllOldOrAllNew() {
        for (point in WritePoint.entries) {
            val rig = Rig(people = 2)
            val id = rig.startRemove()
            val generation = rig.credentials().people.getValue(ben).generation
            rig.faults = StorageFaults { at, _ -> if (at == point) throw SimulatedCrash() }
            runCatching { rig.waiting.skip(id, ben, rig.codes.getValue(ben).toCharArray(), rig.newPin, true, battery) }
            rig.faults = StorageFaults { _, _ -> }
            val rotated = rig.credentials().people.getValue(ben).generation == generation + 1
            val committing = rig.journal().operation!!.phase == WaitingPhase.COMMITTING
            assertEquals("$point", rotated, committing)
        }
    }

    @Test fun skipIsRefusedInsideTheTransactionWhenCancelWasRequested() {
        val rig = Rig(people = 2)
        val id = rig.startRemove()
        val credentials = rig.credentials(); val journal = rig.journal()
        val rotated = credentials.replacing(ben, credentials.people.getValue(ben).copy(generation = credentials.people.getValue(ben).generation + 1))
        val op = journal.operation!!
        val committing = org.plainphone.policy.WaitingOperation(op.identity, op.starter, op.generation, op.structureGeneration, op.mode,
            op.startedAt, WaitingPhase.COMMITTING, op.recipientGenerations, 0, ben, rotated.people.getValue(ben).generation, op.peoplePatch, op.recipientContacts)
        val text = org.plainphone.policy.WaitingText("k", WaitingEvent.SKIPPED, committing,
            rotated.people.mapValues { it.value.generation }, rotated.people.mapValues { it.value.contact })
        val replacement = WaitingState(journal.revision + 1, committing, journal.texts + text, journal.storageGeneration)
        assertTrue(rig.store.cancelIntents.request(id))
        assertFalse(rig.store.skipCommitter.commit(credentials, rotated, journal, replacement))
        assertEquals(credentials.revision, rig.credentials().revision)
        assertEquals(WaitingPhase.PENDING, rig.journal().operation!!.phase)
    }

    @Test fun handOverWithRecoveryJoinsRotationStartedSkippedAndCommitting() {
        val rig = Rig(people = 2)
        val request = checkNotNull(rig.waiting.prepareHandOver(anna, rig.incoming()))
        val before = rig.credentials(); val journalBefore = rig.journal()
        val result = rig.waiting.startHandOverWithRecovery(request, rig.codes.getValue(anna).toCharArray(), rig.newPin, true, true)
        assertTrue(result is WaitingSkipResult.Committing)
        val after = rig.credentials(); val journal = rig.journal()
        assertEquals(before.people.getValue(anna).generation + 1, after.people.getValue(anna).generation)
        assertArrayEquals(holderBytes(before, ben), holderBytes(after, ben))
        assertEquals(WaitingPhase.COMMITTING, journal.operation!!.phase)
        assertEquals(listOf(WaitingEvent.STARTED, WaitingEvent.SKIPPED), journal.texts.drop(journalBefore.texts.size).map { it.event })
        // Incoming credentials grant nothing until the patch.
        assertFalse(cleo in after.people)
    }

    @Test fun crashAtEveryHandOverWritePointExposesAllOldOrAllNew() {
        for (point in WritePoint.entries) {
            val rig = Rig(people = 2)
            val request = checkNotNull(rig.waiting.prepareHandOver(anna, rig.incoming()))
            val generation = rig.credentials().people.getValue(anna).generation
            rig.faults = StorageFaults { at, _ -> if (at == point) throw SimulatedCrash() }
            runCatching { rig.waiting.startHandOverWithRecovery(request, rig.codes.getValue(anna).toCharArray(), rig.newPin, true, true) }
            rig.faults = StorageFaults { _, _ -> }
            val rotated = rig.credentials().people.getValue(anna).generation == generation + 1
            val op = rig.journal().operation
            assertEquals("$point", rotated, op?.phase == WaitingPhase.COMMITTING)
            if (!rotated) assertNull("$point", op)
        }
    }

    @Test fun handOverIsRefusedWhenTheSlotIsBusyOrCancelRequested() {
        val rig = Rig(people = 2)
        val request = checkNotNull(rig.waiting.prepareHandOver(anna, rig.incoming()))
        assertTrue(rig.store.cancelIntents.initialize(request.requestId))
        assertTrue(rig.store.cancelIntents.request(request.requestId))
        val result = rig.waiting.startHandOverWithRecovery(request, rig.codes.getValue(anna).toCharArray(), rig.newPin, true, true)
        assertTrue(result is WaitingSkipResult.NotSkipped)
        assertNull(rig.journal().operation)
        assertEquals(0L, rig.credentials().people.getValue(anna).generation)
    }

    private fun Rig.startRemove(): String = start(waiting.prepareRemove(anna, ben))

    private fun holderBytes(state: CredentialState, person: TrustedPersonId): ByteArray =
        AuthorityCodec.encodeCredentials(CredentialState(Mode.TRUSTED, mapOf(person to state.people.getValue(person))))!!
}
