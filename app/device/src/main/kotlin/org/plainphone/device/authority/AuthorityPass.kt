// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import org.plainphone.policy.ReconcileResult
import org.plainphone.policy.WaitingChangeKind
import org.plainphone.policy.WaitingOperation
import org.plainphone.policy.WaitingPhase

/**
 * What every policy pass reads from the one authority [store], and the automatic lost-record
 * reconciliation (policy README, "Policy re-apply during turn-off" and "Lost operation records").
 *
 * - Every read is fresh from [store], never cached: a pass reads it right before it decides.
 * - A store that was never set up, a vacant slot, and a missing or damaged record all read as no
 *   operation, so they follow normal policy. Only a readable TURN_OFF in COMMITTING selects the
 *   floor-only plan.
 * - [reconcileIfLost] runs [reconciliation] only when [store] confirms the loss. It needs no PIN.
 *   A failure keeps the slot held, and the next pass tries again.
 *
 * Nothing here writes to a store that does not exist yet.
 */
class AuthorityPass(
    private val store: JointAuthorityStore,
    private val reconciliation: DeviceWaitingReconciliation,
) {
    /** For `PolicyInput.waitingOperation`. */
    fun waitingOperation(): WaitingOperation? = store.waitingStore.read()?.operation

    /** For the boot fallback plan: true only for a readable turn-off that is committing. */
    fun turnOffCommitting(): Boolean =
        waitingOperation()?.let { it.kind == WaitingChangeKind.TURN_OFF && it.phase == WaitingPhase.COMMITTING } == true

    /** The automatic reconciliation of a confirmed loss; null when nothing is lost. */
    fun reconcileIfLost(): ReconcileResult? =
        if (store.reconciliation.lostRecord() == null) null else reconciliation.reconcile()
}
