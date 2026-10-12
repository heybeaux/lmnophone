// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
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
import org.plainphone.policy.FloorIdentity
import org.plainphone.policy.FloorRole
import org.plainphone.policy.FrictionSettings
import org.plainphone.policy.IdentitySource
import org.plainphone.policy.InstalledPackage
import org.plainphone.policy.KeyDerivation
import org.plainphone.policy.PinResult
import org.plainphone.policy.PinVault
import org.plainphone.policy.PolicyInput
import org.plainphone.policy.RecoveryProfile
import org.plainphone.policy.SecretEntropy
import org.plainphone.policy.SessionAccess
import org.plainphone.policy.SessionEffects
import org.plainphone.policy.WaitingState

/**
 * PLA-97: initial setup durably creates the combined elevation record with an EMPTY debit ledger in
 * both supervised and self-managed modes. It is initial provisioning only: it never writes over an
 * existing or damaged record, so existing debits survive every later setup attempt and restart.
 */
class InitialProvisioningTest {
    @get:Rule val tmp = TemporaryFolder()
    private val minute = 60_000L
    private val friction = FrictionSettings(20 * minute, 2)
    private val self = TrustedPersonId("self")
    private val helper = TrustedPersonId("helper")
    private val home = PackageName("test.home")
    private val sms = PackageName("test.sms")
    private val dialer = PackageName("test.dialer")
    private val play = PackageName("com.android.vending")
    private val inventory = BuildInventory("model", "build", 1, NeverHideList(setOf(sms, play)), setOf(sms), setOf(PackageName("test.settings")))
    private val policy = PolicyInput("model", "build", home, sms, listOf(home, sms, dialer, play).map { InstalledPackage(it, true, 1) },
        listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)), inventory)
    private val effects = object : SessionEffects {
        override fun revokeAllRelaxations() = true
        override fun open(access: SessionAccess) = true
        override fun protectedStateCheck() = true
        override fun readBackPolicy() = true
    }
    private val pin = CharArray(6) { '1' }
    private var sequence = 0
    private var now = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
    private val clock = RunningClock { now }

    private fun open(dir: File, faults: StorageFaults = StorageFaults { _, _ -> }) = JointAuthorityStore(dir, faults, inventory)
    private fun vault(store: JointAuthorityStore) = PinVault(store.credentials, KeyDerivation { chars, salt, _ ->
        MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
    }, SecretEntropy { n -> ByteArray(n) { (++sequence).toByte() } }, clock)

    private fun selfManaged(store: JointAuthorityStore) = CredentialState(Mode.SELF_MANAGED,
        mapOf(self to vault(store).prepareCredential(pin).credential),
        recoveryProfile = RecoveryProfile(mapOf(self to "synthetic-account"), null, friction))
    private fun supervised(store: JointAuthorityStore) = CredentialState(Mode.TRUSTED,
        mapOf(helper to vault(store).prepareCredential(pin).credential),
        recoveryProfile = RecoveryProfile(mapOf(helper to "synthetic-account"), null, friction))

    private fun elevations(store: JointAuthorityStore, vault: PinVault) = ElevationController(store.elevationStore, { store.credentials.read() },
        vault, clock, store::activeLimits, { policy }, { BatteryState(50, false) }, effects,
        AppApprovalIdSource { "turn-${++sequence}" }).also { assertEquals(ElevationOutcome.IDLE, it.onStart()) }

    private fun files(dir: File) = dir.listFiles()!!.filter { it.isFile }.associate { it.name to it.readBytes() }
    private fun assertUntouched(before: Map<String, ByteArray>, dir: File) {
        val after = files(dir)
        assertEquals(before.keys, after.keys)
        before.forEach { (name, bytes) -> assertArrayEquals(name, bytes, after.getValue(name)) }
    }

    private fun assertGenesis(dir: File, mode: Mode) {
        val disk = open(dir)
        assertEquals(mode, disk.credentials.read()!!.mode)
        val waiting = disk.waitingStore.read()!!
        assertNull(waiting.operation)
        val record = checkNotNull(disk.elevationStore.read()) { "no elevation record" }
        assertEquals(0L, record.revision)
        assertTrue(record.state.ledger.debits.isEmpty())
        assertNull(record.state.countdown); assertNull(record.state.grant); assertNull(record.person)
    }

    @Test fun supervisedSetupCreatesAnEmptyLedger() {
        val dir = tmp.newFolder(); val store = open(dir)
        assertEquals(ProvisionOutcome.CREATED, InitialProvisioning(store).provision(supervised(store)))
        assertGenesis(dir, Mode.TRUSTED)
    }

    @Test fun selfManagedSetupCreatesAnEmptyLedgerAndTheFirstRequestHasTheFullAllowance() {
        val dir = tmp.newFolder(); val store = open(dir)
        assertEquals(ProvisionOutcome.CREATED, InitialProvisioning(store).provision(selfManaged(store)))
        assertGenesis(dir, Mode.SELF_MANAGED)
        // A new process: the record is durable, and an immediate request counts down against the full allowance.
        val restarted = open(dir)
        val v = vault(restarted)
        val controller = elevations(restarted, v)
        val r = checkNotNull(controller.prepare(self, ElevationTarget.Settings))
        val fresh = (v.authenticate(self, pin, r.action) as PinResult.Authorized).authority
        assertEquals(ElevationOutcome.COUNTING, controller.request(r, fresh))
        assertEquals(friction.dailyLimit, restarted.elevationStore.read()!!.state.ledger
            .availability(friction.dailyLimit, now.proven).turnsLeft)
    }

    @Test fun repeatingSetupOnItsOwnGenesisIsCreatedWithoutAWrite() {
        val dir = tmp.newFolder(); val store = open(dir)
        val credentials = selfManaged(store)
        assertEquals(ProvisionOutcome.CREATED, InitialProvisioning(store).provision(credentials))
        val before = files(dir)
        assertEquals(ProvisionOutcome.CREATED, InitialProvisioning(open(dir)).provision(credentials))
        assertUntouched(before, dir)
    }

    @Test fun setupNeverWritesOverAnExistingLedgerWithDebits() {
        val dir = tmp.newFolder(); val store = open(dir)
        val credentials = selfManaged(store)
        assertEquals(ProvisionOutcome.CREATED, InitialProvisioning(store).provision(credentials))
        // Spend a turn: countdown, then start, which debits the ledger.
        val v = vault(store); val controller = elevations(store, v)
        val r = checkNotNull(controller.prepare(self, ElevationTarget.Settings))
        assertEquals(ElevationOutcome.COUNTING, controller.request(r, (v.authenticate(self, pin, r.action) as PinResult.Authorized).authority))
        now = ClockReading(RunningTime("epoch", 20 * minute), BootTime("boot", 20 * minute))
        assertEquals(ElevationOutcome.STARTED, controller.start(r.id, ElevationTarget.Settings))
        val debited = open(dir).elevationStore.read()!!
        assertEquals(1, debited.state.ledger.debits.size)
        val before = files(dir)
        // Same credentials, other mode, a restart: every setup attempt is refused and changes nothing.
        assertEquals(ProvisionOutcome.EXISTING, InitialProvisioning(open(dir)).provision(credentials))
        assertEquals(ProvisionOutcome.EXISTING, InitialProvisioning(open(dir)).provision(supervised(open(dir))))
        assertUntouched(before, dir)
        assertEquals(1, open(dir).elevationStore.read()!!.state.ledger.debits.size)
    }

    @Test fun setupNeverWritesOverADamagedRecord() {
        val dir = tmp.newFolder(); val store = open(dir)
        val credentials = selfManaged(store)
        assertEquals(ProvisionOutcome.CREATED, InitialProvisioning(store).provision(credentials))
        // Both copies damaged: nothing reads, but the fences remain. Missing is not vacant.
        listOf("authority-a", "authority-b").forEach { File(dir, "$it.rec").writeBytes(ByteArray(7)) }
        assertNull(open(dir).elevationStore.read())
        val before = files(dir)
        assertEquals(ProvisionOutcome.EXISTING, InitialProvisioning(open(dir)).provision(credentials))
        assertEquals(ProvisionOutcome.EXISTING, InitialProvisioning(open(dir)).provision(supervised(open(dir))))
        assertUntouched(before, dir)
    }

    @Test fun setupOverAnotherSetupsGenesisIsRefused() {
        val dir = tmp.newFolder(); val store = open(dir)
        assertEquals(ProvisionOutcome.CREATED, InitialProvisioning(store).provision(selfManaged(store)))
        val before = files(dir)
        // Different credentials (another PIN salt) are not this setup's genesis.
        assertEquals(ProvisionOutcome.EXISTING, InitialProvisioning(open(dir)).provision(selfManaged(open(dir))))
        assertUntouched(before, dir)
    }

    @Test fun anUnacknowledgedFirstWriteIsUnavailableAndARetryCompletesIt() {
        val dir = tmp.newFolder()
        val blocked = open(dir) { point, d -> if (point == WritePoint.AFTER_COPY_A) d.setWritable(false, false) }
        val credentials = selfManaged(blocked)
        val outcome = try { InitialProvisioning(blocked).provision(credentials) } finally { dir.setWritable(true, false) }
        assertEquals(ProvisionOutcome.UNAVAILABLE, outcome)
        assertEquals(ProvisionOutcome.CREATED, InitialProvisioning(open(dir)).provision(credentials))
        assertGenesis(dir, Mode.SELF_MANAGED)
    }

    @Test fun setupRefusesCredentialsThatAreNotAFirstRecord() {
        val dir = tmp.newFolder(); val store = open(dir)
        val first = selfManaged(store)
        val later = CredentialState(first.mode, first.people, revision = 3, recoveryProfile = first.recoveryProfile)
        assertEquals(ProvisionOutcome.REFUSED, InitialProvisioning(store).provision(later))
        assertTrue(files(dir).isEmpty())
    }

    /** A genesis with one debit written straight to the ledger: a used record. */
    private fun used(dir: File): CredentialState {
        val store = open(dir); val credentials = selfManaged(store)
        assertEquals(ProvisionOutcome.CREATED, InitialProvisioning(store).provision(credentials))
        val debit = ElevationRecord(1, ElevationState(ElevationLedger(listOf(ElevationDebit("used", RunningTime("epoch", 1_000))))))
        assertTrue(store.elevationStore.compareAndSet(store.elevationStore.read(), debit, credentials))
        return credentials
    }

    // PLA-409 R1: no fence and no readable record is not vacant while a used copy is still on disk.
    @Test fun aDamagedUsedRecordWithoutFencesIsNeverReinitialized() {
        val dir = tmp.newFolder(); val credentials = used(dir)
        assertTrue(File(dir, "fence-a.rec").delete()); assertTrue(File(dir, "fence-b.rec").delete())
        File(dir, "authority-b.rec").writeBytes(ByteArray(7))
        assertNull(open(dir).elevationStore.read())
        val before = files(dir)
        assertEquals(ProvisionOutcome.EXISTING, InitialProvisioning(open(dir)).provision(credentials))
        assertUntouched(before, dir)
        // The low-level genesis write refuses it too.
        assertFalse(open(dir).initialize(credentials, WaitingState(storageGeneration = "g"), ElevationRecord()))
        assertUntouched(before, dir)
    }

    @Test fun aSoleUsedCopyOrAnUndecodableCopyWithoutFencesIsNeverReinitialized() {
        val dir = tmp.newFolder(); val credentials = used(dir)
        listOf("fence-a", "fence-b", "authority-b").forEach { assertTrue(File(dir, "$it.rec").delete()) }
        var before = files(dir)
        assertEquals(ProvisionOutcome.EXISTING, InitialProvisioning(open(dir)).provision(credentials))
        assertUntouched(before, dir)
        // Garbage in the only copy (a damaged record, or another schema's) is not a vacant store either.
        File(dir, "authority-a.rec").writeBytes(ByteArray(7))
        before = files(dir)
        assertEquals(ProvisionOutcome.EXISTING, InitialProvisioning(open(dir)).provision(credentials))
        assertUntouched(before, dir)
    }

    // PLA-409 R2: CREATED needs a successful directory sync after the genesis is visible, not visibility alone.
    @Test fun aGenesisWhoseDirectorySyncFailsIsUnavailableUntilABarrierSucceeds() {
        val dir = tmp.newFolder()
        var syncs = 0; var failing = true
        val store = open(dir).withDirectorySync { d ->
            syncs++
            if (syncs >= 2 && failing) {
                d.setWritable(false, false) // rollback cannot remove the renamed copies either
                throw IOException("directory sync failed")
            }
            PlatformDirectorySync.sync(d)
        }
        val credentials = selfManaged(store)
        val outcome = try { InitialProvisioning(store).provision(credentials) } finally { dir.setWritable(true, false) }
        assertEquals(ProvisionOutcome.UNAVAILABLE, outcome)
        assertTrue(open(dir).credentials.read() != null) // both copies read, but no barrier held
        val before = files(dir)
        // The early (already visible) path also needs the barrier.
        assertEquals(ProvisionOutcome.UNAVAILABLE, InitialProvisioning(store).provision(credentials))
        dir.setWritable(true, false)
        failing = false
        val synced = syncs
        assertEquals(ProvisionOutcome.CREATED, InitialProvisioning(store).provision(credentials))
        assertTrue(syncs > synced)
        assertUntouched(before, dir)
    }

    @Test fun theReviewSequenceIsCreatedOnlyByALaterSuccessfulBarrier() {
        val dir = tmp.newFolder()
        var syncs = 0; var succeeded = 0
        val store = open(dir).withDirectorySync { d ->
            if (++syncs == 2) { d.setWritable(false, false); throw IOException("second copy sync failed") }
            PlatformDirectorySync.sync(d); succeeded++
        }
        val credentials = selfManaged(store)
        val outcome = try { InitialProvisioning(store).provision(credentials) } finally { dir.setWritable(true, false) }
        // A successful directory sync after both renames makes them durable, so CREATED is acknowledged.
        assertEquals(ProvisionOutcome.CREATED, outcome)
        assertTrue(succeeded >= 2 && syncs >= 3)
        assertGenesis(dir, Mode.SELF_MANAGED)
    }
}
