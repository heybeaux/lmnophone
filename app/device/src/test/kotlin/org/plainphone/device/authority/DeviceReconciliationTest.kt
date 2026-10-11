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
import org.plainphone.core.PackageName
import org.plainphone.core.TrustedPersonId
import org.plainphone.device.FakeDevicePolicyPort
import org.plainphone.device.PackageState
import org.plainphone.device.ReadbackPolicyApplier
import org.plainphone.device.ResetProtectionEffects
import org.plainphone.device.ResetProtectionPort
import org.plainphone.device.ResetProtectionReading
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.FloorIdentity
import org.plainphone.policy.FloorRole
import org.plainphone.policy.FrictionSettings
import org.plainphone.policy.IdentitySource
import org.plainphone.policy.InstalledPackage
import org.plainphone.policy.PersonAuthorityConsumer
import org.plainphone.policy.PersonContact
import org.plainphone.policy.PersonCredential
import org.plainphone.policy.PolicyEngine
import org.plainphone.policy.PolicyInput
import org.plainphone.policy.ReconcileFailure
import org.plainphone.policy.ReconcileOutcome
import org.plainphone.policy.RecoveryProfile
import org.plainphone.policy.SecretVerifier
import org.plainphone.policy.WaitingState

/**
 * The Android [org.plainphone.policy.ReconciliationEffects] on the real joint store and the real
 * [org.plainphone.policy.WaitingReconciler], with in-memory DevicePolicyManager and reset-protection
 * ports. JVM evidence only: it shows the order of effects and that every failure keeps the slot held,
 * not what Android or a real wipe does.
 */
class DeviceReconciliationTest {
    @get:Rule val tmp = TemporaryFolder()
    private val anna = TrustedPersonId("anna")
    private val ben = TrustedPersonId("ben")
    private val friction = FrictionSettings(1_200_000, 1)
    private val pp = PackageName("org.plainphone")
    private val dialer = PackageName("system.dialer")
    private val messages = PackageName("system.messages")
    private val input = PolicyInput("model", "build", pp, messages,
        listOf(InstalledPackage(dialer, true, 1), InstalledPackage(pp, false, 1), InstalledPackage(messages, true, 1)),
        listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)))

    private fun person(seed: Int, contact: PersonContact?) = PersonCredential(
        SecretVerifier(ByteArray(16) { seed.toByte() }, ByteArray(32) { seed.toByte() }, 600_000),
        SecretVerifier(ByteArray(16) { (seed + 8).toByte() }, ByteArray(32) { (seed + 8).toByte() }, 600_000),
        generation = seed.toLong(), contact = contact)
    private fun credentials(profileFriction: FrictionSettings = friction) = CredentialState(Mode.TRUSTED,
        mapOf(anna to person(1, PersonContact("synthetic-anna", "synthetic-number-anna")), ben to person(2, null)), 0,
        recoveryProfile = RecoveryProfile(mapOf(anna to "account-anna", ben to "account-ben"), "account-backup", profileFriction))

    /** Records every call in [ops]; [throwOn] makes one call throw, [sticks] = false makes set a no-op. */
    private class ResetPort : ResetProtectionPort {
        var policy: ResetProtectionReading? = null
        val ops = mutableListOf<String>()
        var throwOn: String? = null
        var sticks = true
        override fun setPolicy(accounts: List<String>) {
            ops += "set $accounts"
            if (throwOn == "set") throw UnsupportedOperationException("no persistent data block")
            if (sticks) policy = ResetProtectionReading(accounts, true)
        }
        override fun readPolicy(): ResetProtectionReading? {
            ops += "read"
            if (throwOn == "read") throw IllegalStateException("unreadable")
            return policy
        }
        override fun sendConfigChanged() {
            ops += "broadcast"
            if (throwOn == "broadcast") throw SecurityException("denied")
        }
    }

    private fun visible() = PackageState(hidden = false, suspended = false, enabled = true)
    private fun dpm() = FakeDevicePolicyPort(mapOf(pp.value to visible(), dialer.value to visible(), messages.value to visible()))

    private inner class Rig(
        val dir: File = tmp.newFolder(),
        val store: JointAuthorityStore = JointAuthorityStore(dir),
        val dpm: FakeDevicePolicyPort = dpm(),
        val reset: ResetPort = ResetPort(),
        var revoked: () -> Boolean = { true },
        var owner: () -> Boolean = { true },
        fresh: Boolean = true,
    ) {
        val order = mutableListOf<String>()
        val effects = DeviceReconciliationEffects(
            revoke = { order += "revoke"; revoked() },
            owner = DeviceOwnerReader { order += "owner"; owner() },
            applier = ReadbackPolicyApplier(dpm),
            reset = ResetProtectionEffects(reset),
            store = store,
        )
        val reconciler = store.waitingReconciler({ input }, effects, PersonAuthorityConsumer { _, _, _ -> true })
        init { if (fresh) assertTrue(store.initialize(credentials(), WaitingState(storageGeneration = "live"), ElevationRecord())) }
        fun lose() = assertTrue(store.replaceSectionForTest(Section.WAITING, byteArrayOf(7, 7, 7)))
    }

    private fun files(dir: File) = dir.walk().filter { it.isFile }.associate { it.relativeTo(dir).path to it.readBytes().toList() }

    @Test fun eachEffectMapsOntoItsPortAndAThrowIsAFailure() {
        val rig = Rig()
        assertTrue(rig.effects.revokeTemporaryAccess())
        rig.revoked = { false }; assertFalse(rig.effects.revokeTemporaryAccess())
        rig.revoked = { throw IllegalStateException("journal unreadable") }; assertFalse(rig.effects.revokeTemporaryAccess())
        assertEquals(true, rig.effects.isDeviceOwner())
        rig.owner = { false }; assertEquals(false, rig.effects.isDeviceOwner())
        rig.owner = { throw SecurityException("no binder") }; assertNull(rig.effects.isDeviceOwner())
    }

    @Test fun resetProtectionSetsTheExactListThenBroadcastsAndReadsBackSeparately() {
        val rig = Rig()
        val ids = setOf("account-ben", "account-anna", "account-backup")
        assertTrue(rig.effects.applyResetProtection(ids))
        assertEquals(listOf("set [account-anna, account-backup, account-ben]", "broadcast"), rig.reset.ops)
        assertTrue(rig.effects.readBackResetProtection(ids))
        assertFalse(rig.effects.readBackResetProtection(ids - "account-backup"))
        assertFalse(rig.effects.applyResetProtection(emptySet()))
        rig.reset.ops.clear(); rig.reset.throwOn = "set"
        assertFalse(rig.effects.applyResetProtection(ids))
        assertEquals("a failed set sends no broadcast", listOf("set [account-anna, account-backup, account-ben]"), rig.reset.ops)
        rig.reset.throwOn = "broadcast"
        assertFalse(rig.effects.applyResetProtection(ids))
    }

    @Test fun policyApplyIsTheWholePlanAndItsReadbackWritesNothing() {
        val rig = Rig()
        val plan = PolicyEngine.decide(input)
        assertTrue(rig.effects.applyPolicy(plan))
        val writes = rig.dpm.ops.size
        assertTrue(rig.effects.readBackPolicy(plan))
        assertEquals("readback writes nothing", writes, rig.dpm.ops.size)
        rig.dpm.restrictionSet.clear()
        assertFalse(rig.effects.readBackPolicy(plan))
        val failing = Rig(dpm = dpm().apply { throwOnWrite = "restrictions" })
        assertFalse(failing.effects.applyPolicy(plan))
    }

    /** Friction has no Android state: every countdown and daily limit reads the active credential friction. */
    @Test fun frictionIsOnlyTheActiveCredentialSettingAndIsNeverWritten() {
        val rig = Rig()
        val before = files(rig.dir)
        assertTrue(rig.effects.applyFriction(friction))
        assertTrue(rig.effects.readBackFriction(friction))
        val looser = FrictionSettings(600_000, 3)
        assertFalse("a pending looser target is never adopted", rig.effects.applyFriction(looser))
        assertFalse(rig.effects.readBackFriction(looser))
        assertEquals("nothing was written", before, files(rig.dir))
        assertTrue(rig.store.replaceSectionForTest(Section.CREDENTIALS, byteArrayOf(1)))
        assertFalse("unreadable credentials never match, not even the fail-closed limits",
            rig.effects.readBackFriction(FrictionSettings(86_400_000, 0)))
    }

    @Test fun aLostJournalRecoversWithHoldersPlusBackupAfterRevocationAndOwnerCheck() {
        val rig = Rig()
        rig.lose()
        val lost = rig.store.reconciliation.lostRecord()!!
        assertEquals(ReconcileOutcome.RECOVERED, rig.reconciler.reconcile().outcome)
        assertEquals(listOf("revoke", "owner", "owner"), rig.order)
        assertEquals(ResetProtectionReading(listOf("account-anna", "account-backup", "account-ben"), true), rig.reset.policy)
        assertEquals(listOf("set [account-anna, account-backup, account-ben]", "broadcast", "read"), rig.reset.ops)
        val waiting = JointAuthorityStore(rig.dir).waitingStore.read()!!
        assertEquals(lost.id, waiting.storageGeneration)
        assertEquals(1, waiting.recoveryReceipts.size)
        assertEquals(ReconcileOutcome.IGNORED, rig.reconciler.reconcile().outcome)
    }

    @Test fun noDeviceOwnerIsOffWithNoResetWriteAndNoReceipt() {
        val rig = Rig(owner = { false })
        rig.lose()
        assertEquals(ReconcileOutcome.OFF, rig.reconciler.reconcile().outcome)
        assertTrue(rig.reset.ops.isEmpty())
        assertTrue(rig.dpm.ops.isEmpty())
        assertNull(rig.store.waitingStore.read())
    }

    /** The emulator case: every reset-protection DPM call throws, so recovery stops at RESET_APPLY and stays held. */
    @Test fun aPhoneWithoutResetProtectionStaysHeldAtResetApply() {
        val rig = Rig(reset = ResetPort().apply { throwOn = "set" })
        rig.lose()
        val lost = rig.store.reconciliation.lostRecord()!!
        assertEquals(ReconcileFailure.RESET_APPLY, rig.reconciler.reconcile().failure)
        assertNull(rig.store.waitingStore.read())
        assertEquals(lost.id, rig.store.reconciliation.lostRecord()!!.id)
    }

    /** One failure at each effect in turn: the slot stays held on the same episode, and a clean retry recovers it. */
    @Test fun aFailureAtEveryStepKeepsTheSameEpisodeHeldUntilACleanRetry() {
        val cases = listOf<Pair<ReconcileFailure, (Rig) -> Unit>>(
            ReconcileFailure.REVOCATION to { it.revoked = { false } },
            ReconcileFailure.OWNER_READ to { it.owner = { throw SecurityException("no binder") } },
            ReconcileFailure.POLICY_APPLY to { it.dpm.throwOnWrite = "restrictions" },
            ReconcileFailure.RESET_APPLY to { it.reset.throwOn = "broadcast" },
            ReconcileFailure.POLICY_READBACK to { it.dpm.restrictionsStick = false },
            ReconcileFailure.RESET_READBACK to { it.reset.sticks = false },
        )
        for ((expected, fault) in cases) {
            val rig = Rig()
            rig.lose()
            val lost = rig.store.reconciliation.lostRecord()!!
            fault(rig)
            val result = rig.reconciler.reconcile()
            assertEquals("$expected", ReconcileOutcome.NEEDS_ATTENTION, result.outcome)
            assertEquals("$expected", expected, result.failure)
            assertNull("$expected leaves the slot held", JointAuthorityStore(rig.dir).waitingStore.read())
            assertEquals(lost.id, JointAuthorityStore(rig.dir).reconciliation.lostRecord()!!.id)
            val healthy = Rig(dir = rig.dir, store = JointAuthorityStore(rig.dir), fresh = false)
            assertEquals("$expected then retry", ReconcileOutcome.RECOVERED, healthy.reconciler.reconcile().outcome)
            assertEquals(lost.id, JointAuthorityStore(rig.dir).waitingStore.read()!!.storageGeneration)
        }
    }

    /** The reconciler, its credential reader and its friction effect all read the one store they were built from. */
    @Test fun theReconcilerReadsTheSameStoreItsEffectsUse() {
        val rig = Rig()
        rig.lose()
        val other = Rig()
        val crossed = DeviceReconciliationEffects({ true }, DeviceOwnerReader { true }, ReadbackPolicyApplier(dpm()),
            ResetProtectionEffects(ResetPort()), other.store)
        assertFalse("effects bound to another store are refused", runCatching {
            rig.store.waitingReconciler({ input }, crossed, PersonAuthorityConsumer { _, _, _ -> true })
        }.isSuccess)
    }
}
