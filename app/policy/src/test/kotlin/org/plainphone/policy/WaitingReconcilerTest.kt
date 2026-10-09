// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class WaitingReconcilerTest {
    private val holder = TrustedPersonId("current-holder")
    private val other = TrustedPersonId("other-holder")
    private val secret = SecretVerifier(ByteArray(16), ByteArray(32), 600_000)
    private val profile = RecoveryProfile(mapOf(holder to "synthetic-user", other to "synthetic-other"),
        "synthetic-backup", FrictionSettings(900_000, 2))
    private var credentials: CredentialState? = CredentialState(Mode.TRUSTED,
        mapOf(holder to PersonCredential(secret, secret), other to PersonCredential(secret, secret)), recoveryProfile = profile)
    private val events = mutableListOf<String>()
    private val store = Store()
    private val effects = Effects()
    private var allowed = true
    private val authority = PersonAuthorityConsumer { _, _, person -> allowed && person == holder }
    private var input = PolicyInput("model", "build", PackageName("org.plainphone"), PackageName("system.messages"),
        listOf(InstalledPackage(PackageName("system.dialer"), true, 1)),
        listOf(FloorIdentity(PackageName("system.dialer"), FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)))
    private fun controller(consumer: PersonAuthorityConsumer = authority) = WaitingReconciler(store, { credentials }, { input }, effects, consumer)

    @Test fun `lost record restores active truth and atomically releases with recovered state text`() {
        assertEquals(ReconcileOutcome.RECOVERED, controller().reconcile().outcome)
        assertEquals(listOf("revoke", "owner", "policy", "reset", "friction", "read-policy", "read-reset", "read-friction", "owner", "save"), events)
        assertEquals(setOf("synthetic-user", "synthetic-other", "synthetic-backup"), effects.resetIds)
        assertEquals(profile.friction, effects.friction)
        assertTrue(ApplyStage.RESTRICTIONS in effects.plan!!.steps.map { it.stage })
        assertNotNull(effects.plan!!.expected.restrictions)
        val recovered = store.state!!
        assertNull(recovered.operation)
        assertEquals("loss-1", recovered.storageGeneration)
        val receipt = recovered.recoveryReceipts.single()
        assertEquals(Mode.TRUSTED, receipt.mode)
        assertEquals(mapOf(holder to 0L, other to 0L), receipt.recipientGenerations)
        assertEquals(profile.friction, receipt.friction)
        assertEquals(RecoveredState.ACTIVE, receipt.state)
        assertTrue(recovered.texts.isEmpty())
        assertEquals(ReconcileOutcome.IGNORED, controller().reconcile().outcome)
        assertEquals(1, store.saves)
    }

    @Test fun `post patch credentials are truth even with stale committing input`() {
        val newPerson = TrustedPersonId("new-holder")
        credentials = CredentialState(Mode.SELF_MANAGED, mapOf(newPerson to PersonCredential(secret, secret, generation = 4)),
            recoveryProfile = RecoveryProfile(mapOf(newPerson to "synthetic-new"), null, FrictionSettings(1_200_000, 0)))
        input = input.copy(waitingOperation = WaitingOperation(WaitingIdentity("old", "code"), holder, 0, 0,
            Mode.TRUSTED, RunningTime("epoch", 0), WaitingPhase.COMMITTING, mapOf(holder to 0L)))
        assertEquals(ReconcileOutcome.RECOVERED, controller().reconcile().outcome)
        assertEquals(setOf("synthetic-new"), effects.resetIds)
        assertNotNull(effects.plan!!.expected.restrictions)
        assertEquals(Mode.SELF_MANAGED, store.state!!.recoveryReceipts.single().mode)
        assertEquals(mapOf(newPerson to 4L), store.state!!.recoveryReceipts.single().recipientGenerations)
    }

    @Test fun `owner absence means off despite unreadable credentials and revocation failure`() {
        effects.owner = false; effects.failure = "revoke"; credentials = null
        assertEquals(ReconcileOutcome.OFF, controller().reconcile().outcome)
        assertNull(store.state)
        assertEquals(listOf("revoke", "owner"), events)
    }

    @Test fun `unknown ownership credentials profile or loss token never frees slot`() {
        effects.owner = null
        assertEquals(ReconcileFailure.OWNER_READ, controller().reconcile().failure)
        effects.owner = true; val saved = credentials; credentials = null
        assertEquals(ReconcileFailure.CREDENTIALS, controller().reconcile().failure)
        credentials = CredentialState(saved!!.mode, saved.people)
        assertEquals(ReconcileFailure.CREDENTIALS, controller().reconcile().failure)
        credentials = saved; store.loss = null
        assertEquals(ReconcileFailure.LOSS_RECORD, controller().reconcile().failure)
        assertNull(store.state)
    }

    @Test fun `every failed effect or readback holds slot and identifies failure`() {
        val failures = mapOf("revoke" to ReconcileFailure.REVOCATION, "policy" to ReconcileFailure.POLICY_APPLY,
            "reset" to ReconcileFailure.RESET_APPLY, "friction" to ReconcileFailure.FRICTION_APPLY,
            "read-policy" to ReconcileFailure.POLICY_READBACK, "read-reset" to ReconcileFailure.RESET_READBACK,
            "read-friction" to ReconcileFailure.FRICTION_READBACK)
        for ((event, reason) in failures) {
            effects.failure = event
            val result = controller().reconcile()
            assertEquals(ReconcileOutcome.NEEDS_ATTENTION, result.outcome)
            assertEquals(reason, result.failure)
            assertNull(store.state)
        }
        effects.failure = null
        assertEquals(ReconcileOutcome.RECOVERED, controller().reconcile().outcome)
    }

    @Test fun `invalid policy or missing emergency dialer never claims recovery`() {
        input = input.copy(health = PolicyHealth.BOTH_INVALID)
        assertEquals(ReconcileFailure.POLICY_INPUT, controller().reconcile().failure)
        input = input.copy(health = PolicyHealth.VALID, identities = emptyList())
        assertEquals(ReconcileFailure.POLICY_INPUT, controller().reconcile().failure)
        assertNull(store.state)
    }

    @Test fun `failed atomic completion retries full reconcile with one receipt`() {
        store.fail = true
        assertEquals(ReconcileFailure.PERSISTENCE, controller().reconcile().failure)
        assertNull(store.state)
        store.fail = false
        assertEquals(ReconcileOutcome.RECOVERED, controller().reconcile().outcome)
        assertEquals(1, store.state!!.recoveryReceipts.size)
    }

    @Test fun `crash after every effect and readback restarts with revocation and no premature release`() {
        for (event in listOf("revoke", "policy", "reset", "friction", "read-policy", "read-reset", "read-friction")) {
            store.reset(); effects.crash = event
            assertFailsWith<IllegalStateException> { controller().reconcile() }
            assertNull(store.state)
            effects.crash = null; events.clear()
            assertEquals(ReconcileOutcome.RECOVERED, controller().reconcile().outcome)
            assertEquals("revoke", events.first())
            assertEquals(1, store.state!!.recoveryReceipts.size)
        }
    }

    @Test fun `crash before or after atomic save has no partial release or duplicate receipt`() {
        for (after in listOf(false, true)) {
            store.reset(); store.crash = true; store.crashAfter = after
            assertFailsWith<IllegalStateException> { controller().reconcile() }
            assertEquals(after, store.state != null)
            store.crash = false
            assertEquals(if (after) ReconcileOutcome.IGNORED else ReconcileOutcome.RECOVERED, controller().reconcile().outcome)
            assertEquals(1, store.state!!.recoveryReceipts.size)
        }
    }

    @Test fun `credential change during effect never publishes stale recovered state`() {
        effects.onRead = { credentials = credentials!!.replacing(holder, PersonCredential(secret, secret, generation = 1)) }
        assertEquals(ReconcileFailure.CREDENTIALS_CHANGED, controller().reconcile().failure)
        assertNull(store.state)
        effects.onRead = null
        assertEquals(ReconcileOutcome.RECOVERED, controller().reconcile().outcome)
        assertEquals(1L, store.state!!.recoveryReceipts.single().recipientGenerations[holder])
    }

    @Test fun `repair requires current person action and matching loss and never touches valid operation`() {
        val c = controller()
        val request = c.prepareRepair(holder)!!
        allowed = false
        assertEquals(ReconcileOutcome.REFUSED, c.repair(request, FreshAuthority()).outcome)
        allowed = true; store.loss = LostWaitingRecord("loss-2")
        assertEquals(ReconcileOutcome.IGNORED, c.repair(request, FreshAuthority()).outcome)
        assertTrue(events.isEmpty())
        store.reset()
        store.state = WaitingState(3, WaitingOperation(WaitingIdentity("pending", "code"), holder, 0, 0,
            Mode.TRUSTED, RunningTime("epoch", 0), WaitingPhase.PENDING, mapOf(holder to 0L)))
        assertNull(c.prepareRepair(holder))
        assertEquals(ReconcileOutcome.IGNORED, c.repair(request, FreshAuthority()).outcome)
        assertEquals(WaitingPhase.PENDING, store.state!!.operation!!.phase)
        assertTrue(events.isEmpty())
    }

    @Test fun `PIN repair uses same reconcile and an actual single use vault grant`() {
        val credentialStore = object : CredentialStore {
            override fun read() = credentials
            override fun compareAndSet(expected: CredentialState, replacement: CredentialState): Boolean {
                if (credentials !== expected) return false
                credentials = replacement; return true
            }
        }
        var bootMillis = 0L
        val vault = PinVault(credentialStore, KeyDerivation { chars, salt, _ ->
            java.security.MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
        }, SecretEntropy { n -> ByteArray(n) { 7 } }, RunningClock { ClockReading(RunningTime("epoch", bootMillis), BootTime("boot", bootMillis)) })
        credentials = credentials!!.replacing(holder, vault.prepareCredential(CharArray(6) { '1' }).credential)
        val c = controller(vault)
        val request = c.prepareRepair(holder)!!
        val wrong = (vault.authenticate(holder, CharArray(6) { '1' }, PrivilegedAction("other", "other")) as PinResult.Authorized).authority
        assertEquals(ReconcileOutcome.REFUSED, c.repair(request, wrong).outcome)
        val expired = (vault.authenticate(holder, CharArray(6) { '1' }, request.action) as PinResult.Authorized).authority
        bootMillis = 120_000
        assertEquals(ReconcileOutcome.REFUSED, c.repair(request, expired).outcome)
        val fresh = (vault.authenticate(holder, CharArray(6) { '1' }, request.action) as PinResult.Authorized).authority
        effects.failure = "read-reset"
        assertEquals(ReconcileFailure.RESET_READBACK, c.repair(request, fresh).failure)
        assertEquals(ReconcileOutcome.REFUSED, c.repair(request, fresh).outcome)
        effects.failure = null
        val retry = (vault.authenticate(holder, CharArray(6) { '1' }, request.action) as PinResult.Authorized).authority
        assertEquals(ReconcileOutcome.RECOVERED, c.repair(request, retry).outcome)
        assertSame(profile, credentials!!.recoveryProfile)
    }

    @Test fun `profile is immutable complete and preserved by credential replacements`() {
        val ids = mutableMapOf(holder to "synthetic-user", other to "synthetic-other")
        val p = RecoveryProfile(ids, null, FrictionSettings(600_000, 3)); ids.clear()
        assertEquals(2, p.holderUserIds.size)
        assertFailsWith<IllegalArgumentException> { CredentialState(Mode.TRUSTED, credentials!!.people,
            recoveryProfile = RecoveryProfile(mapOf(holder to "synthetic-user"), null, p.friction)) }
        assertFailsWith<IllegalArgumentException> { FrictionSettings(599_999, 3) }
        assertFailsWith<IllegalArgumentException> { FrictionSettings(86_400_001, 3) }
        assertFailsWith<IllegalArgumentException> { FrictionSettings(600_000, 4) }
        assertSame(profile, credentials!!.replacing(holder, PersonCredential(secret, secret)).recoveryProfile)
        assertFalse(profile.toString().contains("synthetic-user"))
    }

    private inner class Store : WaitingReconciliationStore {
        var state: WaitingState? = null
        var loss: LostWaitingRecord? = LostWaitingRecord("loss-1")
        var fail = false; var crash = false; var crashAfter = false; var saves = 0
        fun reset() { state = null; loss = LostWaitingRecord("loss-1"); fail = false; crash = false; saves = 0 }
        override fun read() = state
        override fun compareAndSet(expected: WaitingState, replacement: WaitingState): Boolean {
            if (fail || state?.revision != expected.revision || state?.storageGeneration != expected.storageGeneration) return false
            state = replacement; return true
        }
        override fun lostRecord() = loss.takeIf { state == null }
        override fun completeReconciliation(lost: LostWaitingRecord, expectedCredentials: CredentialState, replacement: WaitingState): Boolean {
            events += "save"
            if (crash && !crashAfter) error("simulated crash")
            if (fail || state != null || lost.id != loss?.id || credentials?.revision != expectedCredentials.revision) return false
            state = replacement; loss = null; saves++
            if (crash) error("simulated crash")
            return true
        }
    }
    private inner class Effects : ReconciliationEffects {
        var owner: Boolean? = true
        var failure: String? = null; var crash: String? = null; var onRead: (() -> Unit)? = null
        var resetIds = emptySet<String>(); var friction: FrictionSettings? = null; var plan: PolicyPlan? = null
        private fun step(name: String): Boolean { events += name; if (crash == name) error("simulated crash"); return failure != name }
        override fun revokeTemporaryAccess() = step("revoke")
        override fun isDeviceOwner(): Boolean? { events += "owner"; return owner }
        override fun applyPolicy(plan: PolicyPlan): Boolean { this.plan = plan; return step("policy") }
        override fun applyResetProtection(userIds: Set<String>): Boolean { resetIds = userIds; return step("reset") }
        override fun applyFriction(settings: FrictionSettings): Boolean { friction = settings; return step("friction") }
        override fun readBackPolicy(plan: PolicyPlan) = step("read-policy")
        override fun readBackResetProtection(userIds: Set<String>) = step("read-reset")
        override fun readBackFriction(settings: FrictionSettings): Boolean { onRead?.invoke(); return step("read-friction") }
    }
}
