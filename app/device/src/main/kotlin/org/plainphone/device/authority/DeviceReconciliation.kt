// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import android.app.admin.DevicePolicyManager
import android.content.Context
import org.plainphone.device.ApplyResult
import org.plainphone.device.ReadbackPolicyApplier
import org.plainphone.device.ResetProtectionEffects
import org.plainphone.policy.CredentialState
import org.plainphone.policy.FrictionSettings
import org.plainphone.policy.PersonAuthorityConsumer
import org.plainphone.policy.PolicyInput
import org.plainphone.policy.PolicyPlan
import org.plainphone.policy.ReconciliationEffects
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
 * idempotent, and a throw is a failure (null for the owner read), so the reconciler keeps the slot held.
 *
 * - [revokeTemporaryAccess] runs [revoke], the common startup revocation (sessions and elevation).
 * - [applyPolicy] runs the whole plan through [applier]; it is true only when no stage failed. Packages
 *   the plan could not enable and failed flips stay in the [ApplyResult] for the attention UI
 *   ([onApplied]) and do not hold the slot. [readBackPolicy] reads every readback stage and writes nothing.
 * - [applyResetProtection] sets the exact list and then sends the required broadcast; a failed set sends
 *   none. [readBackResetProtection] checks the exact list, enabled. Neither says which account a wipe accepts.
 * - Friction has no Android state. Every countdown and daily limit reads the active friction in
 *   [store]'s credential record ([JointAuthorityStore.activeLimits]), so applying it is checking that
 *   record holds exactly [FrictionSettings]: nothing is written, no daily debit is reset and no pending
 *   target is adopted. Unreadable credentials never match, not even the fail-closed limits.
 *
 * No effect removes Device Owner, wipes the phone or adopts pending credentials. Calls, Emergency and
 * alerts are untouched by every failure: the applier's floor stages come first and never hide them.
 */
class DeviceReconciliationEffects(
    private val revoke: () -> Boolean,
    private val owner: DeviceOwnerReader,
    private val applier: ReadbackPolicyApplier,
    private val reset: ResetProtectionEffects,
    internal val store: JointAuthorityStore,
    private val onApplied: (ApplyResult) -> Unit = {},
) : ReconciliationEffects {
    override fun revokeTemporaryAccess(): Boolean = attempt { revoke() }
    override fun isDeviceOwner(): Boolean? = try { owner.isDeviceOwner() } catch (_: Exception) { null }

    override fun applyPolicy(plan: PolicyPlan): Boolean = attempt { applier.apply(plan).also(onApplied).complete }
    override fun readBackPolicy(plan: PolicyPlan): Boolean = attempt { applier.readBack(plan) }

    override fun applyResetProtection(userIds: Set<String>): Boolean =
        reset.setResetProtection(userIds) && reset.broadcastResetProtectionChanged()
    override fun readBackResetProtection(userIds: Set<String>): Boolean = reset.readBackResetProtection(userIds)

    override fun applyFriction(settings: FrictionSettings): Boolean = activeFriction() == settings
    override fun readBackFriction(settings: FrictionSettings): Boolean = activeFriction() == settings

    private fun activeFriction(): FrictionSettings? = try { store.credentials.read()?.recoveryProfile?.friction } catch (_: Exception) { null }

    private fun attempt(block: () -> Boolean): Boolean = try { block() } catch (_: Exception) { false }
}

/**
 * The [WaitingReconciler] for this store: its loss record, its credentials and [effects] built on
 * this same store, so every reader, writer and effect goes through one coordinator. Effects bound
 * to another store are refused.
 */
fun JointAuthorityStore.waitingReconciler(
    policyInput: (CredentialState) -> PolicyInput?,
    effects: DeviceReconciliationEffects,
    authority: PersonAuthorityConsumer,
): WaitingReconciler {
    require(effects.store === this) { "reconciliation effects belong to another store" }
    return WaitingReconciler(reconciliation, { credentials.read() }, policyInput, effects, authority)
}
