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
        /** Runs inside set: lets a test change Android state after the policy apply. */
        var onSet: () -> Unit = {}
        /** The call ([throwOn]) that throws a checked exception instead of an unchecked one. */
        var checked: String? = null
        override fun setPolicy(accounts: List<String>) {
            ops += "set $accounts"
            onSet()
            if (checked == "set") throw java.io.IOException("synthetic set failure")
            if (throwOn == "set") throw UnsupportedOperationException("no persistent data block")
            if (sticks) policy = ResetProtectionReading(accounts, true)
        }
        override fun readPolicy(): ResetProtectionReading? {
            ops += "read"
            if (checked == "read") throw java.io.IOException("synthetic read failure")
            if (throwOn == "read") throw IllegalStateException("unreadable")
            return policy
        }
        override fun sendConfigChanged() {
            ops += "broadcast"
            if (checked == "broadcast") throw java.io.IOException("synthetic broadcast failure")
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
            // A restriction that will not stick already fails the apply's own per-step readback.
            ReconcileFailure.POLICY_APPLY to { it.dpm.restrictionsStick = false },
            // The final readback catches a restriction lost after the apply (here, during the reset write).
            ReconcileFailure.POLICY_READBACK to { rig -> rig.reset.onSet = { rig.dpm.restrictionSet.clear() } },
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
    // The four PLA-308 review probes, kept as regressions unchanged.
    @Test fun reviewRestrictionFailureStillRepairsEmergencyFloor() {
        val rig = Rig()
        rig.lose()
        rig.dpm.state[dialer] = PackageState(hidden = true, suspended = true, enabled = true)
        rig.dpm.throwOnWrite = "restrictions"
        assertEquals(ReconcileFailure.POLICY_APPLY, rig.reconciler.reconcile().failure)
        assertFalse("restriction failure must not skip dialer repair", rig.dpm.state.getValue(dialer).hidden)
        assertFalse(rig.dpm.state.getValue(dialer).suspended)
        assertTrue(dialer in rig.dpm.lockTask)
    }

    @Test fun reviewFactoryDoesNotAllowStoreWriteInsideAnEffect() {
        val rig = Rig()
        rig.lose()
        var interleaved = false
        rig.reset.onSet = {
            val before = rig.store.approvals.read()!!
            interleaved = rig.store.approvals.compareAndSet(before,
                org.plainphone.policy.AppApprovalState(revision = before.revision + 1), rig.store.credentials.read())
        }
        val result = rig.reconciler.reconcile()
        assertFalse("store write interleaved during reset effect; reconciliation returned $result", interleaved)
    }

    @Test fun reviewCheckedResetExceptionBecomesTypedFailure() {
        val rig = Rig()
        rig.lose()
        rig.reset.onSet = { throw java.io.IOException("synthetic adapter failure") }
        assertEquals(ReconcileFailure.RESET_APPLY, rig.reconciler.reconcile().failure)
    }

    @Test fun reviewDiagnosticsRemainReportedWithoutHoldingRecoveredSlot() {
        val rig = Rig()
        rig.lose()
        rig.dpm.state[messages] = PackageState(hidden = true, suspended = false, enabled = false)
        rig.dpm.flipsIgnored = 100
        var reported: org.plainphone.device.ApplyResult? = null
        val effects = DeviceReconciliationEffects({ true }, DeviceOwnerReader { true },
            ReadbackPolicyApplier(rig.dpm), ResetProtectionEffects(rig.reset), rig.store,
            onApplied = { reported = it })
        val reconciler = rig.store.waitingReconciler({ input }, effects, PersonAuthorityConsumer { _, _, _ -> true })
        assertEquals(ReconcileOutcome.RECOVERED, reconciler.reconcile().outcome)
        assertTrue(reported!!.complete)
        assertTrue(messages in reported!!.unrepairable)
        assertTrue(messages in reported!!.flipFailures)
        assertTrue(reported!!.needsAttention)
    }

    private fun hideDialer(rig: Rig) {
        rig.dpm.state[dialer] = PackageState(hidden = true, suspended = true, enabled = true)
        rig.dpm.lockTask = emptySet()
    }
    private fun assertDialerRepaired(rig: Rig, case: String) {
        assertFalse("$case: dialer unhidden", rig.dpm.state.getValue(dialer).hidden)
        assertFalse("$case: dialer unsuspended", rig.dpm.state.getValue(dialer).suspended)
        assertTrue("$case: dialer in lock task", dialer in rig.dpm.lockTask)
    }

    /** A failure before the policy apply (revocation) or after it (reset) still repairs the emergency floor; the slot stays held. */
    @Test fun everyNeedsAttentionResultRepairsTheEmergencyFloor() {
        val cases = listOf<Pair<ReconcileFailure, (Rig) -> Unit>>(
            ReconcileFailure.REVOCATION to { it.revoked = { false } },
            ReconcileFailure.RESET_APPLY to { it.reset.throwOn = "set" },
        )
        for ((expected, fault) in cases) {
            val rig = Rig()
            rig.lose()
            fault(rig)
            rig.dpm.ops.clear()
            hideDialer(rig)
            assertEquals("$expected", expected, rig.reconciler.reconcile().failure)
            assertDialerRepaired(rig, "$expected")
            assertNull("$expected leaves the slot held", rig.store.waitingStore.read())
        }
    }

    /** Checked exceptions from any reset-protection call are typed failures, never thrown out of reconcile. */
    @Test fun aCheckedExceptionAtEveryResetCallIsATypedFailure() {
        for ((call, expected) in listOf("set" to ReconcileFailure.RESET_APPLY, "broadcast" to ReconcileFailure.RESET_APPLY,
                "read" to ReconcileFailure.RESET_READBACK)) {
            val rig = Rig()
            rig.lose()
            rig.reset.checked = call
            val result = rig.reconciler.reconcile()
            assertEquals(call, ReconcileOutcome.NEEDS_ATTENTION, result.outcome)
            assertEquals(call, expected, result.failure)
        }
    }

    /** A writer on another thread waits for the whole reconciliation, then lands after it. */
    @Test fun aCompetingWriterWaitsForTheWholeReconciliation() {
        val rig = Rig()
        rig.lose()
        val inEffect = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        rig.reset.onSet = { inEffect.countDown(); check(release.await(10, java.util.concurrent.TimeUnit.SECONDS)) }
        var result: org.plainphone.policy.ReconcileResult? = null
        val a = Thread { result = rig.reconciler.reconcile() }
        a.start()
        assertTrue(inEffect.await(10, java.util.concurrent.TimeUnit.SECONDS))
        var written: Boolean? = null
        var recoveredFirst: Boolean? = null
        val b = Thread {
            val before = rig.store.approvals.read()!!
            recoveredFirst = rig.store.waitingStore.read() != null
            written = rig.store.approvals.compareAndSet(before,
                org.plainphone.policy.AppApprovalState(revision = before.revision + 1), rig.store.credentials.read())
        }
        b.start()
        Thread.sleep(300)
        assertTrue("the competing writer waits during the effect", b.isAlive)
        release.countDown()
        a.join(10_000); b.join(10_000)
        assertEquals(ReconcileOutcome.RECOVERED, result!!.outcome)
        assertEquals(true, recoveredFirst)
        assertEquals(true, written)
    }

    /** Reconciliation inside one of its own effects is refused, not run nested; the outer one still finishes. */
    @Test fun aNestedReconciliationInsideAnEffectIsRefused() {
        val rig = Rig()
        rig.lose()
        var nested: org.plainphone.policy.ReconcileResult? = null
        rig.reset.onSet = { nested = rig.reconciler.reconcile() }
        assertEquals(ReconcileOutcome.RECOVERED, rig.reconciler.reconcile().outcome)
        assertEquals(ReconcileOutcome.NEEDS_ATTENTION, nested!!.outcome)
        assertEquals(1, rig.store.waitingStore.read()!!.recoveryReceipts.size)
    }

    /** The startup revocation is a participating store writer: it may write inside the boundary. */
    @Test fun revocationMayWriteTheStoreInsideTheBoundary() {
        val rig = Rig()
        rig.lose()
        var wrote = false
        rig.revoked = {
            val before = rig.store.approvals.read()!!
            wrote = rig.store.approvals.compareAndSet(before,
                org.plainphone.policy.AppApprovalState(revision = before.revision + 1), rig.store.credentials.read())
            wrote
        }
        assertEquals(ReconcileOutcome.RECOVERED, rig.reconciler.reconcile().outcome)
        assertTrue(wrote)
    }
    /** Revocation runs inside the boundary but outside effect mode: a reconciliation started from it is still refused, not run nested. */
    @Test fun aReconciliationStartedFromTheRevocationIsRefused() {
        val rig = Rig()
        rig.lose()
        var nested: org.plainphone.policy.ReconcileResult? = null
        rig.revoked = { if (nested == null) nested = rig.reconciler.reconcile(); true }
        assertEquals(ReconcileOutcome.RECOVERED, rig.reconciler.reconcile().outcome)
        assertEquals(DeviceWaitingReconciliation.NOT_RUN, nested)
    }

    /** The policy effect on its own: a failed stage still runs the emergency repair of the same plan, and reports false. */
    @Test fun aFailedPolicyApplyRepairsTheFloorInTheEffectItself() {
        val rig = Rig()
        hideDialer(rig)
        rig.dpm.throwOnWrite = "restrictions"
        assertFalse(rig.effects.applyPolicy(PolicyEngine.decide(input)))
        assertDialerRepaired(rig, "effect")
    }

    // The PLA-323 review probes, kept as regressions unchanged.
    @Test fun reviewKnownPlanStillRepairsIfTheSecondInputReadReturnsNull() = reviewKnownPlan(false)
    @Test fun reviewKnownPlanStillRepairsIfTheSecondInputReadThrows() = reviewKnownPlan(true)
    @Test fun reviewKnownPlanControlWithStableInput() = reviewKnownPlan(false, true)

    private fun reviewKnownPlan(throws: Boolean, stable: Boolean = false) {
        run {
            val rig = Rig()
            rig.lose()
            var reads = 0
            val reconciler = rig.store.waitingReconciler({
                reads++
                if (reads == 1 || stable) input else if (throws) throw java.io.IOException("synthetic input failure") else null
            }, rig.effects, PersonAuthorityConsumer { _, _, _ -> true })
            rig.reset.onSet = { hideDialer(rig) }
            rig.reset.throwOn = "set"
            val result = reconciler.reconcile()
            assertEquals(ReconcileOutcome.NEEDS_ATTENTION, result.outcome)
            assertEquals(ReconcileFailure.RESET_APPLY, result.failure)
            assertNull(rig.store.waitingStore.read())
            assertDialerRepaired(rig, "already built plan; second read throws=$throws")
        }
    }

    @Test fun reviewEffectsRefuseCapturedWritesAcrossStoreInstances() {
        val rig = Rig()
        rig.lose()
        val alias = JointAuthorityStore(rig.dir)
        val before = alias.approvals.read()!!
        val creds = alias.credentials.read()!!
        var ran = false
        rig.reset.onSet = {
            ran = true
            assertNull(alias.credentials.read())
            assertNull(alias.approvals.read())
            assertNull(alias.elevationStore.read())
            assertNull(alias.update.readUpdate())
            assertNull(alias.reconciliation.lostRecord())
            assertFalse(alias.approvals.compareAndSet(before,
                org.plainphone.policy.AppApprovalState(revision = before.revision + 1), creds))
            assertFalse(alias.cancelIntents.initialize("review-synthetic"))
            assertFalse(alias.cancelIntents.request("review-synthetic"))
            assertNull(rig.reconciler.prepareRepair(anna))
        }
        assertEquals(ReconcileOutcome.RECOVERED, rig.reconciler.reconcile().outcome)
        assertTrue(ran)
        assertEquals(before.revision, alias.approvals.read()!!.revision)
    }

    @Test fun reviewOperationTimeoutRefusesWithoutAcknowledging() {
        val rig = Rig()
        rig.lose()
        val before = rig.store.approvals.read()!!
        val creds = rig.store.credentials.read()!!
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val old = JointAuthorityStore.coordinatorWaitMillis
        val result = java.util.concurrent.atomic.AtomicReference<org.plainphone.policy.ReconcileResult>()
        val worker = Thread { result.set(rig.reconciler.reconcile()) }
        try {
            JointAuthorityStore.coordinatorWaitMillis = 150
            rig.reset.onSet = { entered.countDown(); check(release.await(5, java.util.concurrent.TimeUnit.SECONDS)) }
            worker.start()
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val alias = JointAuthorityStore(rig.dir)
            assertFalse(alias.approvals.compareAndSet(before,
                org.plainphone.policy.AppApprovalState(revision = before.revision + 1), creds))
            assertEquals(DeviceWaitingReconciliation.NOT_RUN, rig.reconciler.reconcile())
            release.countDown()
            worker.join(5000)
            assertFalse(worker.isAlive)
            assertEquals(ReconcileOutcome.RECOVERED, result.get().outcome)
            assertEquals(before.revision, rig.store.approvals.read()!!.revision)
        } finally {
            release.countDown()
            worker.join(5000)
            JointAuthorityStore.coordinatorWaitMillis = old
        }
    }

    @Test fun reviewOperationCannotDeadlockAnOutsideMonitor() {
        val rig = Rig()
        val monitor = Object()
        val monitorHeld = java.util.concurrent.CountDownLatch(1)
        val insideOperation = java.util.concurrent.CountDownLatch(1)
        val old = JointAuthorityStore.coordinatorWaitMillis
        val refusedRead = java.util.concurrent.atomic.AtomicBoolean(false)
        val worker = Thread {
            synchronized(monitor) {
                monitorHeld.countDown()
                check(insideOperation.await(5, java.util.concurrent.TimeUnit.SECONDS))
                refusedRead.set(rig.store.credentials.read() == null)
            }
        }
        try {
            JointAuthorityStore.coordinatorWaitMillis = 150
            worker.start()
            assertTrue(monitorHeld.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(rig.store.serialized(false) {
                insideOperation.countDown()
                synchronized(monitor) { true }
            })
            worker.join(5000)
            assertFalse(worker.isAlive)
            assertTrue(refusedRead.get())
            assertTrue(rig.store.credentials.read() != null)
        } finally {
            insideOperation.countDown()
            worker.join(5000)
            JointAuthorityStore.coordinatorWaitMillis = old
        }
    }

    /** A grant for the always-true test authority; FreshAuthority's constructor is internal to :policy. */
    private fun grant(): org.plainphone.policy.FreshAuthority =
        org.plainphone.policy.FreshAuthority::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()

    /** PIN Repair runs the same entry point: a failure after the plan was applied repairs from that plan, even when input then fails. */
    @Test fun pinRepairRepairsTheFloorFromTheRunsOwnPlan() {
        val rig = Rig()
        rig.lose()
        var reads = 0
        val reconciler = rig.store.waitingReconciler({ if (++reads == 1) input else null }, rig.effects, PersonAuthorityConsumer { _, _, _ -> true })
        val request = reconciler.prepareRepair(anna)!!
        rig.reset.onSet = { hideDialer(rig) }
        rig.reset.throwOn = "set"
        val result = reconciler.repair(request, grant())
        assertEquals(ReconcileFailure.RESET_APPLY, result.failure)
        assertDialerRepaired(rig, "PIN Repair")
        assertNull(rig.store.waitingStore.read())
    }

    /** The plan of one run is never used by the next: a later run with no input repairs nothing. */
    @Test fun aRunsPlanIsNotCarriedIntoTheNextRun() {
        val rig = Rig()
        rig.lose()
        var available = true
        val reconciler = rig.store.waitingReconciler({ if (available) input else null }, rig.effects, PersonAuthorityConsumer { _, _, _ -> true })
        rig.reset.throwOn = "set"
        assertEquals(ReconcileFailure.RESET_APPLY, reconciler.reconcile().failure)
        available = false
        rig.revoked = { false }
        rig.dpm.ops.clear()
        assertEquals(ReconcileFailure.REVOCATION, reconciler.reconcile().failure)
        assertTrue("no plan, so no DPM write: ${rig.dpm.ops}", rig.dpm.ops.isEmpty())
    }

    /** A plan left by a direct effect call outside any run is not used by the next run either. */
    @Test fun aPlanFromOutsideARunIsNotUsedByTheNextRun() {
        val rig = Rig()
        rig.lose()
        assertTrue(rig.effects.applyPolicy(PolicyEngine.decide(input)))
        val reconciler = rig.store.waitingReconciler({ null }, rig.effects, PersonAuthorityConsumer { _, _, _ -> true })
        rig.revoked = { false }
        rig.dpm.ops.clear()
        assertEquals(ReconcileFailure.REVOCATION, reconciler.reconcile().failure)
        assertTrue("no plan in this run, so no DPM write: ${rig.dpm.ops}", rig.dpm.ops.isEmpty())
    }
}
