// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

/**
 * Explicit alternative to PIN-starting then recovery-skipping a handover. Same stores and serialized
 * dispatcher as PinVault/WaitingChangeController. Atomically compare credential revision, waiting
 * generation/revision, a vacant or terminal slot and this reserved request's CLEAR cancel intent;
 * persist outgoing credential rotation, STARTED/SKIPPED fixed-recipient receipts, and COMMITTING.
 * No partial write may be exposed after failure or crash. Retain the contact snapshots in the durable
 * outbox/audit, including removed holders and explicit unavailable legacy addresses. Never resolve a
 * queued receipt against a later credential record. Do not implement as two independent writes.
 * Incoming credentials remain pending; this transaction does not apply the people patch.
 */
fun interface WaitingHandOverCommitter {
    fun commit(expectedCredentials: CredentialState, replacementCredentials: CredentialState,
        expectedWaiting: WaitingState, replacementWaiting: WaitingState): Boolean
}

/** Unwired means unavailable; an existing turn-off skip adapter does not implicitly support this. */
class WaitingHandOverSupport(val vault: PinVault, val committer: WaitingHandOverCommitter)
