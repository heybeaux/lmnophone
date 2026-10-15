// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
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
import org.plainphone.policy.ElevationLimits
import org.plainphone.policy.ElevationOutcome
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.ElevationTarget
import org.plainphone.policy.FloorIdentity
import org.plainphone.policy.FloorRole
import org.plainphone.policy.FrictionSettings
import org.plainphone.policy.FrictionSettingsController
import org.plainphone.policy.FrictionSettingsOutcome
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
 * Part 2 of PLA-100: every elevation limit comes from the active friction stored in the joint
 * record, and a stricter edit during a live countdown or grant is one durable transaction.
 */
class ElevationFrictionOnDiskTest {
    @get:Rule val tmp = TemporaryFolder()
    private val minute = 60_000L
    private val base = FrictionSettings(20 * minute, 1)
    private val self = TrustedPersonId("self")
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

    private inner class Rig(val dir: File = tmp.newFolder()) {
        val pin get() = CharArray(6) { '1' }
        var now = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
        val clock = RunningClock { now }
        var faults = StorageFaults { _, _ -> }
        var sequence = 0
        lateinit var store: JointAuthorityStore
        lateinit var vault: PinVault
        lateinit var elevations: ElevationController

        init {
            open()
            val credential = vault.prepareCredential(pin).credential
            assertTrue(store.initialize(CredentialState(Mode.SELF_MANAGED, mapOf(self to credential),
                recoveryProfile = RecoveryProfile(mapOf(self to "synthetic-account"), null, base)),
                WaitingState(storageGeneration = "live"), ElevationRecord()))
            boot()
        }
        fun open() {
            store = JointAuthorityStore(dir, { point, d -> faults.at(point, d) }, inventory)
            vault = PinVault(store.credentials, KeyDerivation { chars, salt, _ ->
                MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
            }, SecretEntropy { n -> ByteArray(n) { (++sequence).toByte() } }, clock)
        }
        /** A new process: new store object, vault and controller, started in the documented order. */
        fun boot() {
            open()
            elevations = ElevationController(store.elevationStore, { store.credentials.read() }, vault, clock,
                store::activeLimits, { policy }, { BatteryState(50, false) }, effects, AppApprovalIdSource { "turn-${++sequence}" })
            assertEquals(ElevationOutcome.IDLE, elevations.onStart())
        }
        fun at(elapsed: Long) { now = ClockReading(RunningTime("epoch", elapsed), BootTime(now.boot.bootId, elapsed)) }
        fun disk() = JointAuthorityStore(dir, inventory)
        fun request(): String {
            val r = checkNotNull(elevations.prepare(self, ElevationTarget.Settings))
            val fresh = (vault.authenticate(self, pin, r.action) as PinResult.Authorized).authority
            assertEquals(ElevationOutcome.COUNTING, elevations.request(r, fresh))
            return r.id
        }
        fun stricter(target: FrictionSettings): FrictionSettingsOutcome = authenticatedStricter(target)()
        /** Prepares and authenticates now; the returned call runs only the edit's own transaction. */
        fun authenticatedStricter(target: FrictionSettings): () -> FrictionSettingsOutcome {
            val editor = FrictionSettingsController(store, store.credentials, vault, clock, AppApprovalIdSource { "edit-${++sequence}" })
            val r = checkNotNull(editor.prepareStricter(self, target))
            val fresh = (vault.authenticate(self, pin, r.action) as PinResult.Authorized).authority
            return { editor.applyStricter(r, fresh) }
        }
    }

    // PLA-160: a countdown write that lands unacknowledged is NOT_READY. The controller never adopts
    // the landed countdown it was not told about; the next tick closes it, and no turn is spent.
    @Test fun unacknowledgedCountdownIsNotReadyAndTheNextTickClosesItWithoutSpendingATurn() {
        val rig = Rig()
        val r = checkNotNull(rig.elevations.prepare(self, ElevationTarget.Settings))
        val fresh = (rig.vault.authenticate(self, rig.pin, r.action) as PinResult.Authorized).authority
        val outcome = SearchDenial(WritePoint.AFTER_COPY_B).run(rig.dir, { rig.faults = it }) { rig.elevations.request(r, fresh) }
        assertEquals(ElevationOutcome.NOT_READY, outcome)
        assertEquals(r.id, rig.disk().elevationStore.read()!!.state.countdown!!.id)
        assertEquals(ElevationOutcome.LAPSED, rig.elevations.tick())
        val after = rig.disk().elevationStore.read()!!
        assertNull(after.state.countdown); assertNull(after.state.grant)
        assertTrue(after.state.ledger.debits.isEmpty())
        rig.at(20 * minute)
        assertEquals(ElevationOutcome.REFUSED, rig.elevations.start(r.id, ElevationTarget.Settings))
    }

    @Test fun limitsAreTheStoredActiveFrictionAndFailClosedWhenUnreadable() {
        val rig = Rig()
        assertEquals(ElevationLimits(base.countdownMillis, base.dailyLimit), rig.store.activeLimits())
        assertEquals(FrictionSettingsOutcome.APPLIED, rig.stricter(FrictionSettings(30 * minute, 0)))
        assertEquals(ElevationLimits(30 * minute, 0), rig.disk().activeLimits())
        listOf("fence-a", "fence-b", "authority-a").forEach { File(rig.dir, "$it.rec").delete() }
        // Unreadable credentials grant no turns and the longest countdown.
        assertEquals(ElevationLimits(86_400_000L, 0), rig.disk().activeLimits())
    }

    @Test fun missingCredentialsStillHaveNoOpenings() {
        assertEquals(ElevationLimits(86_400_000L, 0), JointAuthorityStore(tmp.newFolder()).activeLimits())
    }

    @Test fun pinOnlySelfManagedCredentialsUseDefaultsWithoutWritingAProfile() {
        val rig = Rig()
        val before = rig.store.credentials.read()!!
        assertTrue(rig.store.credentials.compareAndSet(before,
            CredentialState(Mode.SELF_MANAGED, before.people)))
        assertEquals(ElevationLimits(), rig.disk().activeLimits())
        assertNull(rig.disk().credentials.read()!!.recoveryProfile)
    }

    @Test fun supervisedCredentialsWithoutProfileKeepFailClosedLimits() {
        val rig = Rig()
        val before = rig.store.credentials.read()!!
        assertTrue(rig.store.credentials.compareAndSet(before,
            CredentialState(Mode.TRUSTED, before.people)))
        assertEquals(ElevationLimits(86_400_000L, 0), rig.disk().activeLimits())
    }

    @Test fun defaultsNeverLoosenStoredStricterProfile() {
        val rig = Rig()
        repeat(3) {
            assertEquals(ElevationLimits(20 * minute, 1), rig.disk().activeLimits())
            assertEquals(base, rig.disk().credentials.read()!!.recoveryProfile!!.friction)
        }
    }

    @Test fun stricterEditExtendsALiveCountdownInTheSameDurableTransaction() {
        val rig = Rig(); val id = rig.request()
        rig.at(10 * minute)
        assertEquals(FrictionSettingsOutcome.APPLIED, rig.stricter(FrictionSettings(30 * minute, 1)))
        val saved = rig.disk().elevationStore.read()!!
        assertEquals(30 * minute, saved.state.countdown!!.durationMillis)
        assertEquals(FrictionSettings(30 * minute, 1), rig.disk().credentials.read()!!.recoveryProfile!!.friction)
        // The old ready time no longer opens anything; the extended one does.
        rig.at(20 * minute)
        assertEquals(ElevationOutcome.REFUSED, rig.elevations.start(id, ElevationTarget.Settings))
        rig.at(30 * minute)
        assertEquals(ElevationOutcome.STARTED, rig.elevations.start(id, ElevationTarget.Settings))
    }

    @Test fun limitLoweredDuringTheCountdownAppliesAtStartWithoutSpendingATurn() {
        val rig = Rig(); val id = rig.request()
        rig.at(5 * minute)
        assertEquals(FrictionSettingsOutcome.APPLIED, rig.stricter(FrictionSettings(20 * minute, 0)))
        rig.at(20 * minute)
        assertEquals(ElevationOutcome.LIMIT_REACHED, rig.elevations.start(id, ElevationTarget.Settings))
        val saved = rig.disk().elevationStore.read()!!
        assertNull(saved.state.grant)
        assertTrue(saved.state.ledger.debits.isEmpty())
    }

    @Test fun stricterEditDuringALiveGrantKeepsItsDeadlineAndAddsNoTurn() {
        val rig = Rig(); val id = rig.request()
        rig.at(20 * minute)
        assertEquals(ElevationOutcome.STARTED, rig.elevations.start(id, ElevationTarget.Settings))
        val before = rig.disk().elevationStore.read()!!
        rig.at(21 * minute)
        assertEquals(FrictionSettingsOutcome.APPLIED, rig.stricter(FrictionSettings(60 * minute, 0)))
        val after = rig.disk().elevationStore.read()!!
        assertEquals(before.state.grant!!.deadline, after.state.grant!!.deadline)
        assertEquals(before.state.ledger.debits, after.state.ledger.debits)
        assertEquals(before.journal, after.journal)
        assertEquals(before.revision + 1, after.revision)
    }

    @Test fun restartNeverRestoresTheCountdownAndNeverRefundsATurn() {
        val rig = Rig(); val id = rig.request()
        rig.at(20 * minute)
        assertEquals(ElevationOutcome.STARTED, rig.elevations.start(id, ElevationTarget.Settings))
        val debits = rig.disk().elevationStore.read()!!.state.ledger.debits
        rig.boot()
        val saved = rig.disk().elevationStore.read()!!
        assertNull(saved.state.grant); assertNull(saved.state.countdown)
        assertEquals(debits, saved.state.ledger.debits)
        // The limit of one is spent; a new request reaches it immediately.
        val r = checkNotNull(rig.elevations.prepare(self, ElevationTarget.Settings))
        val fresh = (rig.vault.authenticate(self, rig.pin, r.action) as PinResult.Authorized).authority
        assertEquals(ElevationOutcome.LIMIT_REACHED, rig.elevations.request(r, fresh))
    }

    @Test fun crashAroundAStricterEditWithALiveCountdownIsAllOldOrAllNew() {
        // PLA-147: authenticate first with no faults armed, so the crash lands inside the edit's own write.
        val target = FrictionSettings(30 * minute, 0)
        // Both copies agreeing is the commit point.
        val visibleAfterCrash = mapOf(WritePoint.BEFORE_COPY_A to false, WritePoint.AFTER_COPY_A to false,
            WritePoint.AFTER_COPY_B to true, WritePoint.AFTER_FENCE_A to true, WritePoint.AFTER_FENCE to true)
        for (point in WritePoint.entries) {
            val rig = Rig(); rig.request(); rig.at(10 * minute)
            val before = rig.disk().elevationStore.read()!!
            val edit = rig.authenticatedStricter(target)
            var fired = false
            rig.faults = StorageFaults { at, _ -> if (at == point) { fired = true; throw SimulatedCrash() } }
            try { edit() } catch (_: SimulatedCrash) { }
            rig.faults = StorageFaults { _, _ -> }
            assertTrue("$point hook fired", fired)
            val disk = rig.disk()
            val friction = disk.credentials.read()!!.recoveryProfile!!.friction
            val elevation = disk.elevationStore.read()!!
            val receipts = disk.frictionReceipts()!!.size
            if (visibleAfterCrash.getValue(point)) {
                assertEquals("$point", target, friction)
                assertEquals("$point", 30 * minute, elevation.state.countdown!!.durationMillis)
                assertEquals("$point", before.revision + 1, elevation.revision)
                assertEquals("$point", 1, receipts)
            } else {
                assertEquals("$point", base, friction)
                assertEquals("$point", base.countdownMillis, elevation.state.countdown!!.durationMillis)
                assertEquals("$point", before.revision, elevation.revision)
                assertEquals("$point", 0, receipts)
            }
            assertEquals("$point", before.state.ledger.debits, elevation.state.ledger.debits)
            assertEquals("$point", before.journal, elevation.journal)
            assertEquals("$point", ElevationLimits(friction.countdownMillis, friction.dailyLimit), disk.activeLimits())
        }
    }
}
