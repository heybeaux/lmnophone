// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.BootTime
import org.plainphone.core.BuildInventory
import org.plainphone.core.ClockReading
import org.plainphone.core.Mode
import org.plainphone.core.NeverHideList
import org.plainphone.core.PackageName
import org.plainphone.core.RunningClock
import org.plainphone.core.RunningTime
import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.AppApprovalIdSource
import org.plainphone.policy.BatteryState
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationController
import org.plainphone.policy.ElevationDebit
import org.plainphone.policy.ElevationLedger
import org.plainphone.policy.ElevationOutcome
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.ElevationState
import org.plainphone.policy.ElevationTarget
import org.plainphone.policy.EnrollmentConfirmations
import org.plainphone.policy.EnrollmentWitness
import org.plainphone.policy.FloorIdentity
import org.plainphone.policy.FloorRole
import org.plainphone.policy.FrictionSettings
import org.plainphone.policy.IdentitySource
import org.plainphone.policy.IncomingPersonEnrollment
import org.plainphone.policy.IncomingSelfManagedEnrollment
import org.plainphone.policy.InstalledPackage
import org.plainphone.policy.KeyDerivation
import org.plainphone.policy.ModeSwitchEffects
import org.plainphone.policy.PeopleChangeController
import org.plainphone.policy.PeopleChangeOutcome
import org.plainphone.policy.PersonContact
import org.plainphone.policy.PinResult
import org.plainphone.policy.PinVault
import org.plainphone.policy.PolicyInput
import org.plainphone.policy.PrivilegedAction
import org.plainphone.policy.RecoveryProfile
import org.plainphone.policy.SecretEntropy
import org.plainphone.policy.SelfManagedEnrollmentConfirmations
import org.plainphone.policy.SessionAccess
import org.plainphone.policy.SessionEffects
import org.plainphone.policy.WaitingChangeController
import org.plainphone.policy.WaitingOutcome
import org.plainphone.policy.WaitingPhase
import org.plainphone.policy.WaitingStart

/**
 * PLA-97 slice 2b: the store's [org.plainphone.policy.ModeSwitchCommitter] under the real waiting,
 * people-change and elevation controllers over the file-backed joint record. Every check reopens the
 * directory. JVM file-system tests: they say nothing about Android durability or real wipe accounts.
 */
class ModeSwitchCommitTest {
    @get:Rule val tmp = TemporaryFolder()
    private val minute = 60_000L
    private val day = 86_400_000L
    private val battery = BatteryState(80, false)
    private val alex = TrustedPersonId("alex")
    private val jo = TrustedPersonId("jo")
    private val sam = TrustedPersonId("sam")
    private val home = PackageName("test.home")
    private val sms = PackageName("test.sms")
    private val dialer = PackageName("test.dialer")
    private val play = PackageName("com.android.vending")
    private val inventory = BuildInventory("model", "build", 1, NeverHideList(setOf(sms, play)), setOf(sms), setOf(PackageName("test.settings")))
    private val policy = PolicyInput("model", "build", home, sms, listOf(home, sms, dialer, play).map { InstalledPackage(it, true, 1) },
        listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)), inventory)
    private val sessions = object : SessionEffects {
        override fun revokeAllRelaxations() = true
        override fun open(access: SessionAccess) = true
        override fun protectedStateCheck() = true
        override fun readBackPolicy() = true
    }

    /** Synthetic effects; the reset list it holds is what a readback would see. */
    private class Effects : ModeSwitchEffects {
        var applied: Set<String>? = null
        val calls = mutableListOf<String>()
        override fun revokeTemporaryAuthority(): Boolean { calls += "revoke"; return true }
        override fun readBackProtectedState(): Boolean { calls += "protected"; return true }
        override fun setResetProtection(userIds: Set<String>): Boolean { calls += "set"; applied = userIds; return true }
        override fun broadcastResetProtectionChanged(): Boolean { calls += "broadcast"; return true }
        override fun readBackResetProtection(userIds: Set<String>): Boolean { calls += "readback"; return applied == userIds }
    }

    private inner class Rig(mode: Mode, val friction: FrictionSettings = FrictionSettings(20 * minute, 2), val dir: File = tmp.newFolder()) {
        val pin get() = CharArray(6) { '1' }
        val newPin get() = CharArray(6) { '5' }
        var now = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
        val clock = RunningClock { now }
        var sequence = 0
        val effects = Effects()
        lateinit var store: JointAuthorityStore
        lateinit var vault: PinVault
        lateinit var waiting: WaitingChangeController
        lateinit var people: PeopleChangeController

        init {
            wire()
            val holders = (if (mode == Mode.TRUSTED) listOf(alex, jo) else listOf(sam)).associateWith { id ->
                vault.prepareCredential(pin).credential.let {
                    if (mode == Mode.TRUSTED) it.copy(contact = PersonContact("synthetic-${id.value}", "synthetic-number-${id.value}")) else it
                }
            }
            val credentials = CredentialState(mode, holders,
                recoveryProfile = RecoveryProfile(holders.keys.associateWith { "account-${it.value}" }, "account-backup", friction))
            assertEquals(ProvisionOutcome.CREATED, InitialProvisioning(store).provision(credentials, "live"))
        }

        /** Restart: new store, vault and controllers over the same directory. */
        fun wire() {
            store = JointAuthorityStore(dir, inventory)
            vault = PinVault(store.credentials, KeyDerivation { chars, salt, _ ->
                MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
            }, SecretEntropy { n -> ByteArray(n) { (++sequence).toByte() } }, clock)
            waiting = WaitingChangeController(store.waitingStore, { store.credentials.read() }, clock, vault, store.identities, store.cancelIntents)
            people = PeopleChangeController(store.waitingStore, store.credentials, store.modeCommitter, effects, clock, vault)
        }
        fun disk() = JointAuthorityStore(dir, inventory)
        fun credentials() = disk().credentials.read()!!
        fun journal() = disk().waitingStore.read()!!
        fun ledger() = disk().elevationStore.read()!!
        fun auth(person: TrustedPersonId, action: PrivilegedAction, code: CharArray = pin) =
            (vault.authenticate(person, code, action) as PinResult.Authorized).authority
        fun at(elapsed: Long) { now = ClockReading(RunningTime("epoch", elapsed), BootTime("boot", elapsed)) }
        fun start(prepared: WaitingStart?, starter: TrustedPersonId): String {
            val r = checkNotNull(prepared)
            assertEquals(WaitingOutcome.PENDING, waiting.startModeSwitch(r, auth(starter, r.action), true))
            return r.requestId
        }
        fun toSelfManaged() = start(waiting.prepareSelfManaged(alex, checkNotNull(IncomingSelfManagedEnrollment.confirm(sam, "account-sam",
            vault.prepareCredential(newPin), SelfManagedEnrollmentConfirmations(true, true, true, true, true, true),
            EnrollmentWitness.CurrentPerson(alex)))), alex)
        fun toSupervised() = start(waiting.prepareSupervised(sam, checkNotNull(IncomingPersonEnrollment.confirm(alex,
            PersonContact("synthetic-alex", "synthetic-number-alex"), "account-alex", vault.prepareCredential(newPin),
            EnrollmentConfirmations(true, true, true, true, true, true, true, true, true), EnrollmentWitness.Helper))), sam)
        fun mature(id: String) { at(now.proven.totalMillis + day); assertEquals(WaitingOutcome.COMMITTING, waiting.tick(id, battery)) }
        fun elevations() = ElevationController(store.elevationStore, { store.credentials.read() }, vault, clock, store::activeLimits,
            { policy }, { battery }, sessions, AppApprovalIdSource { "turn-${++sequence}" })
        fun debit(id: String) {
            val before = store.elevationStore.read()!!
            val ledger = ElevationLedger(before.state.ledger.debits + ElevationDebit(id, now.proven))
            assertTrue(store.elevationStore.compareAndSet(before, ElevationRecord(before.revision + 1, ElevationState(ledger)), store.credentials.read()))
        }
    }

    private fun ElevationController.ask(rig: Rig, person: TrustedPersonId, code: CharArray): ElevationOutcome {
        val r = checkNotNull(prepare(person, ElevationTarget.Settings))
        return request(r, rig.auth(person, r.action, code))
    }

    @Test fun supervisedSetupSwitchesToSelfManagedAndTheFirstRequestHasTheFullAllowance() {
        val rig = Rig(Mode.TRUSTED)
        val id = rig.toSelfManaged(); rig.mature(id)
        assertEquals(PeopleChangeOutcome.APPLIED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        // Restrict first: revoke and read back protected state, then the target list, then the swap.
        assertEquals(listOf("revoke", "protected", "set", "broadcast", "readback"), rig.effects.calls)
        assertEquals(setOf("account-sam", "account-backup"), rig.effects.applied)
        val swapped = rig.credentials()
        assertEquals(Mode.SELF_MANAGED, swapped.mode); assertEquals(setOf(sam), swapped.people.keys)
        assertEquals(1L, swapped.structureGeneration); assertEquals(id, swapped.appliedPeopleChange?.requestId)
        assertEquals(WaitingPhase.COMMITTING, rig.journal().operation?.phase)
        assertNull(rig.people.prepareStop(id, alex)) // no stop after the swap
        rig.wire()
        assertEquals(PeopleChangeOutcome.COMPLETED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        assertEquals(WaitingPhase.COMPLETED, rig.journal().operation?.phase)
        assertTrue(rig.ledger().state.ledger.debits.isEmpty())
        rig.wire()
        val elevations = rig.elevations(); assertEquals(ElevationOutcome.IDLE, elevations.onStart())
        assertEquals(ElevationOutcome.COUNTING, elevations.ask(rig, sam, rig.newPin))
        assertEquals(2, rig.ledger().state.ledger.availability(2, rig.now.proven).turnsLeft)
    }

    @Test fun switchingKeepsExhaustedDebitsAndTheImmediateRequestIsRefused() {
        val rig = Rig(Mode.TRUSTED, FrictionSettings(20 * minute, 1))
        val id = rig.toSelfManaged(); rig.mature(id)
        rig.debit("spent-before-switch")
        val debits = rig.ledger().state.ledger.debits.map { it.id to it.at }
        assertEquals(PeopleChangeOutcome.APPLIED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        assertEquals(PeopleChangeOutcome.COMPLETED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        assertEquals(debits, rig.ledger().state.ledger.debits.map { it.id to it.at })
        rig.wire()
        val elevations = rig.elevations(); assertEquals(ElevationOutcome.IDLE, elevations.onStart())
        assertEquals(ElevationOutcome.LIMIT_REACHED, elevations.ask(rig, sam, rig.newPin))
    }

    @Test fun aCountdownContinuesWhilePendingAndEndsWithTheStructureChange() {
        val rig = Rig(Mode.SELF_MANAGED)
        val elevations = rig.elevations(); assertEquals(ElevationOutcome.IDLE, elevations.onStart())
        assertEquals(ElevationOutcome.COUNTING, elevations.ask(rig, sam, rig.pin))
        val id = rig.toSupervised()
        rig.at(5 * minute)
        assertEquals(ElevationOutcome.COUNTING, elevations.tick())
        assertTrue(rig.ledger().state.countdown != null)
        rig.mature(id)
        assertEquals(PeopleChangeOutcome.APPLIED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        val after = rig.ledger()
        assertNull(after.state.countdown); assertNull(after.state.grant); assertNull(after.person)
        assertEquals(Mode.TRUSTED, rig.credentials().mode)
    }

    @Test fun aFreshPinStopBeforeTheSwapRollsBackToTheOldHolders() {
        val rig = Rig(Mode.TRUSTED)
        val old = rig.credentials()
        val id = rig.toSelfManaged(); rig.mature(id)
        val stop = checkNotNull(rig.people.prepareStop(id, jo))
        assertEquals(PeopleChangeOutcome.ROLLING_BACK, rig.people.stop(stop, rig.auth(jo, stop.action)).outcome)
        assertEquals(WaitingPhase.ROLLING_BACK, rig.journal().operation?.phase)
        rig.wire()
        // A stale forward callback never resumes a rollback.
        assertEquals(PeopleChangeOutcome.IGNORED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        assertEquals(PeopleChangeOutcome.CANCELLED, rig.people.tick(id, WaitingPhase.ROLLING_BACK).outcome)
        assertEquals(WaitingPhase.CANCELLED, rig.journal().operation?.phase)
        assertEquals(setOf("account-alex", "account-jo", "account-backup"), rig.effects.applied)
        val now = rig.credentials()
        assertEquals(Mode.TRUSTED, now.mode); assertEquals(old.people.keys, now.people.keys)
        assertEquals(old.structureGeneration, now.structureGeneration)
    }

    @Test fun theSwapComparesTheElevationRecordAndRefusesTemporaryAuthority() {
        val rig = Rig(Mode.TRUSTED)
        val id = rig.toSelfManaged(); rig.mature(id)
        val current = rig.credentials(); val journal = rig.journal(); val record = rig.ledger()
        val patch = checkNotNull(journal.operation?.modePatch)
        val incoming = checkNotNull(patch.incomingSelfManaged)
        val structure = current.structureGeneration + 1
        val replacement = CredentialState(Mode.SELF_MANAGED, mapOf(sam to incoming.credential), current.revision + 1, structure,
            current.pendingNotices, RecoveryProfile(mapOf(sam to "account-sam"), "account-backup", rig.friction),
            org.plainphone.policy.AppliedPeopleChange(id, journal.storageGeneration, structure))
        val before = rig.files()
        // A stale elevation observation is refused, as is any other replacement.
        assertFalse(rig.store.modeCommitter.applyModePatch(current, replacement, journal, ElevationRecord(record.revision + 1)))
        val other = CredentialState(Mode.SELF_MANAGED, mapOf(sam to incoming.credential), current.revision + 1, structure,
            current.pendingNotices, RecoveryProfile(mapOf(sam to "account-other"), "account-backup", rig.friction),
            org.plainphone.policy.AppliedPeopleChange(id, journal.storageGeneration, structure))
        assertFalse(rig.store.modeCommitter.applyModePatch(current, other, journal, record))
        assertEquals(before.keys, rig.files().keys)
        before.forEach { (name, bytes) -> assertTrue(name, bytes.contentEquals(rig.files().getValue(name))) }
        // The exact candidate against the exact records lands, and leaves the ledger record untouched.
        assertTrue(rig.store.modeCommitter.applyModePatch(current, replacement, journal, record))
        assertEquals(record.revision, rig.ledger().revision)
        assertEquals(Mode.SELF_MANAGED, rig.credentials().mode)
    }

    @Test fun theTerminalWriteComparesTheElevationRecordToo() {
        val rig = Rig(Mode.TRUSTED)
        val id = rig.toSelfManaged(); rig.mature(id)
        assertEquals(PeopleChangeOutcome.APPLIED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        // A debit lands between the swap and completion: the completion built on the old record is refused.
        val stale = rig.ledger()
        rig.debit("between")
        val current = rig.credentials(); val journal = rig.journal(); val op = checkNotNull(journal.operation)
        val completed = org.plainphone.policy.WaitingOperation(op.identity, op.starter, op.generation, op.structureGeneration, op.mode,
            op.startedAt, WaitingPhase.COMPLETED, op.recipientGenerations, op.restoredSteps, op.skipper, op.skipperGeneration,
            op.peoplePatch, op.recipientContacts, op.stopper, op.stopperGeneration, op.modePatch, op.frictionPatch)
        val revision = journal.revision + 1
        val text = org.plainphone.policy.WaitingText("${id.length}:$id:$revision", org.plainphone.policy.WaitingEvent.COMPLETED, completed,
            current.people.mapValues { it.value.generation }, current.people.mapValues { it.value.contact })
        val done = org.plainphone.policy.WaitingState(revision, completed, journal.texts + text, journal.storageGeneration, journal.recoveryReceipts)
        assertFalse(rig.store.modeCommitter.finishMode(current, journal, done, stale))
        assertEquals(WaitingPhase.COMMITTING, rig.journal().operation?.phase)
        // The controller re-reads and completes against the current record, keeping both debits.
        rig.wire()
        assertEquals(PeopleChangeOutcome.COMPLETED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        assertEquals(listOf("between"), rig.ledger().state.ledger.debits.map { it.id })
    }

    @Test fun aControllerOnAnotherWaitingStoreInstanceIgnoresTheSwitch() {
        val rig = Rig(Mode.TRUSTED)
        val id = rig.toSelfManaged(); rig.mature(id)
        val elsewhere = JointAuthorityStore(rig.dir, inventory)
        val wrong = PeopleChangeController(elsewhere.waitingStore, rig.store.credentials, rig.store.modeCommitter, rig.effects, rig.clock, rig.vault)
        assertEquals(PeopleChangeOutcome.IGNORED, wrong.tick(id, WaitingPhase.COMMITTING).outcome)
        assertTrue(rig.effects.calls.isEmpty())
        assertEquals(Mode.TRUSTED, rig.credentials().mode)
    }

    private fun Rig.files() = dir.listFiles()!!.filter { it.isFile }.associate { it.name to it.readBytes() }
}
