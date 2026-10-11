// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import android.app.admin.DevicePolicyManager
import android.content.Context
import org.plainphone.device.ApplyResult
import org.plainphone.device.ReadbackPolicyApplier
import org.plainphone.device.ResetProtectionEffects
import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.CredentialState
import org.plainphone.policy.FreshAuthority
import org.plainphone.policy.FrictionSettings
import org.plainphone.policy.PersonAuthorityConsumer
import org.plainphone.device.boot.RestrictionFilter
import org.plainphone.policy.PolicyEngine
import org.plainphone.policy.PolicyInput
import org.plainphone.policy.PolicyPlan
import org.plainphone.policy.ReconcileFailure
import org.plainphone.policy.ReconcileOutcome
import org.plainphone.policy.ReconcileResult
import org.plainphone.policy.ReconciliationEffects
import org.plainphone.policy.RepairRequest
import org.plainphone.policy.WaitingReconciler

/** Whether this app is the Device Owner now. May throw when Android cannot answer. */
fun interface DeviceOwnerReader {
    fun isDeviceOwner(): Boolean
}

/** [DeviceOwnerReader] through `DevicePolicyManager.isDeviceOwnerApp` for this app's own package. */
class DpmDeviceOwnerReader(private val context: Context) : DeviceOwnerReader {
    override fun isDeviceOwner(): Boolean =
        context.getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(context.packageName)
}

/**
 * The Android [ReconciliationEffects] for a lost waiting record. Every call is synchronous and
 * idempotent, and any exception is a failure (null for the owner read), so the reconciler keeps the
 * slot held and returns a typed reason.
 *
 * - [revokeTemporaryAccess] runs [revoke], the common startup revocation (sessions and elevation).
 *   It is a store writer taking part in the reconciliation, so it may write [store].
 * - Every Android effect (owner read, policy, reset protection) runs as a [JointAuthorityStore.androidEffect]:
 *   a store call made from it is refused, and a call from outside a running reconciliation waits for the
 *   whole run (refused as a failure past the wait limit), so it never interleaves with one.
 * - Every plan goes through [filter], the same build/SDK restriction filter as the boot re-apply, before
 *   the write, the readback and the emergency repair (PLA-350 R1): a test build never applies the
 *   recovery restrictions it withholds, and no build expects keys its API level lacks.
 * - [applyPolicy] runs the whole plan through [applier]; it is true only when no stage failed. When a
 *   stage fails it then runs the applier's independent emergency repair of the same plan, so an early
 *   failure (such as restrictions) never skips the dialer and never-hide repair, and still returns
 *   false. Packages the plan could not enable and failed flips stay in the [ApplyResult] for the
 *   attention UI ([onApplied]) and do not hold the slot. [readBackPolicy] reads every readback stage
 *   and writes nothing.
 * - [applyResetProtection] sets the exact list and then sends the required broadcast; a failed set sends
 *   none. [readBackResetProtection] checks the exact list, enabled. Neither says which account a wipe accepts.
 * - Friction has no Android state. Every countdown and daily limit reads the active friction in
 *   [store]'s credential record ([JointAuthorityStore.activeLimits]), so applying it is checking that
 *   record holds exactly [FrictionSettings]: nothing is written, no daily debit is reset and no pending
 *   target is adopted. Unreadable credentials never match, not even the fail-closed limits.
 *
 * No effect removes Device Owner, wipes the phone or adopts pending credentials.
 */
class DeviceReconciliationEffects(
    private val revoke: () -> Boolean,
    private val owner: DeviceOwnerReader,
    private val applier: ReadbackPolicyApplier,
    private val reset: ResetProtectionEffects,
    internal val store: JointAuthorityStore,
    private val filter: RestrictionFilter,
    private val onApplied: (ApplyResult) -> Unit = {},
) : ReconciliationEffects {
    override fun revokeTemporaryAccess(): Boolean = attempt { revoke() }
    override fun isDeviceOwner(): Boolean? = android(null) { try { owner.isDeviceOwner() } catch (_: Exception) { null } }

    override fun applyPolicy(plan: PolicyPlan): Boolean = android(false) {
        attempt { applier.apply(filter.apply(plan)).also(onApplied).complete }.also { if (!it) repairFloorHeld(plan) }
    }
    override fun readBackPolicy(plan: PolicyPlan): Boolean = android(false) { attempt { applier.readBack(filter.apply(plan)) } }

    override fun applyResetProtection(userIds: Set<String>): Boolean =
        android(false) { attempt { reset.setResetProtection(userIds) && reset.broadcastResetProtectionChanged() } }
    override fun readBackResetProtection(userIds: Set<String>): Boolean = android(false) { attempt { reset.readBackResetProtection(userIds) } }

    override fun applyFriction(settings: FrictionSettings): Boolean = activeFriction() == settings
    override fun readBackFriction(settings: FrictionSettings): Boolean = activeFriction() == settings

    /** The applier's emergency repair of [plan]: the dialer, never-hide packages and their lock-task floor. Best effort. */
    internal fun repairFloor(plan: PolicyPlan) = android(Unit) { repairFloorHeld(plan) }

    private fun repairFloorHeld(plan: PolicyPlan) {
        try { onApplied(applier.repairEmergency(filter.apply(plan))) } catch (_: Exception) { }
    }

    private fun activeFriction(): FrictionSettings? = try { store.credentials.read()?.recoveryProfile?.friction } catch (_: Exception) { null }

    /** [block] as a [JointAuthorityStore.androidEffect]; [refused] when another thread's operation held the store past the wait limit. */
    private fun <T> android(refused: T, block: () -> T): T =
        try { store.androidEffect(block) } catch (_: JointAuthorityStore.EffectRefused) { refused }
    private fun attempt(block: () -> Boolean): Boolean = try { block() } catch (_: Exception) { false }
}

/**
 * Lost-record reconciliation and PIN Repair for one store. Each entry point runs as one
 * [JointAuthorityStore.serialized] operation, so every reader, writer and effect of a run goes through
 * that store's one coordinator: other threads' store calls wait for the whole run, and a run started
 * from inside another (for example from an effect) is refused with [NOT_RUN].
 *
 * After any NEEDS_ATTENTION result, including a failed revocation before any policy write, the
 * emergency floor is repaired inside the same run: from the plan this run's own reconciler applied when
 * it got that far (so a later input failure cannot lose it, and a direct or nested effect call cannot
 * replace it), otherwise from [emergencyPlan]. The result and the held slot are unchanged. No plan crosses runs. With no plan at all (no readable credentials or
 * policy input) nothing is repaired here; startup's own floor repair still runs.
 */
class DeviceWaitingReconciliation internal constructor(
    private val store: JointAuthorityStore,
    private val policyInput: (CredentialState) -> PolicyInput?,
    private val effects: DeviceReconciliationEffects,
    private val authority: PersonAuthorityConsumer,
    private val emergencyPlan: () -> PolicyPlan?,
) {
    private fun reconciler(effects: ReconciliationEffects) =
        WaitingReconciler(store.reconciliation, { store.credentials.read() }, policyInput, effects, authority)

    fun reconcile(): ReconcileResult = store.serialized(NOT_RUN) { runOnce { it.reconcile() } }
    fun prepareRepair(person: TrustedPersonId): RepairRequest? = store.serialized(null) { reconciler(effects).prepareRepair(person) }
    fun repair(request: RepairRequest, grant: FreshAuthority): ReconcileResult =
        store.serialized(NOT_RUN) { runOnce { it.repair(request, grant) } }

    /**
     * One run, with its own [RunEffects] that only this run's reconciler holds: the plan it keeps comes
     * from that reconciler's own [ReconciliationEffects.applyPolicy] and nowhere else, so no direct or
     * nested call to [effects], and no other run, can replace it. After NEEDS_ATTENTION the floor is
     * repaired from that plan if the run got that far.
     */
    private fun runOnce(block: (WaitingReconciler) -> ReconcileResult): ReconcileResult {
        val run = RunEffects(effects)
        val result = block(reconciler(run))
        if (result.outcome == ReconcileOutcome.NEEDS_ATTENTION) {
            (run.plan ?: try { emergencyPlan() } catch (_: Exception) { null })?.let(effects::repairFloor)
        }
        return result
    }

    /** [effects] for one run, keeping the last plan that run's reconciler applied. */
    private class RunEffects(private val effects: DeviceReconciliationEffects) : ReconciliationEffects by effects {
        var plan: PolicyPlan? = null
            private set
        override fun applyPolicy(plan: PolicyPlan): Boolean {
            this.plan = plan
            return effects.applyPolicy(plan)
        }
    }

    companion object {
        /** Refused: another reconciliation is running or this one was started from inside it. Nothing ran. */
        val NOT_RUN = ReconcileResult(ReconcileOutcome.NEEDS_ATTENTION, ReconcileFailure.PERSISTENCE)
    }
}

/**
 * The reconciliation for this store: its loss record, its credentials and [effects] built on this
 * same store. Effects bound to another store are refused. The emergency plan after a failure is the
 * reconciler's own plan: the normal plan with no waiting operation, session access or boot record.
 */
fun JointAuthorityStore.waitingReconciler(
    policyInput: (CredentialState) -> PolicyInput?,
    effects: DeviceReconciliationEffects,
    authority: PersonAuthorityConsumer,
): DeviceWaitingReconciliation {
    require(effects.store === this) { "reconciliation effects belong to another store" }
    return DeviceWaitingReconciliation(this, policyInput, effects, authority) {
        credentials.read()?.let(policyInput)?.let { PolicyEngine.decide(it.copy(waitingOperation = null, sessionAccess = null, currentBoot = null)) }
    }
}
