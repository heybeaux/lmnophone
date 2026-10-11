// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

/** Optional adapter capability. Without this combined transaction, elevated approvals stay unavailable. */
interface ElevationApprovalStore : AppApprovalStore {
    /** The exact shared instance used by ElevationController and SessionController. */
    val elevationStore: ElevationStore

    /**
     * Join approval state/outbox, elevation ledger/grant/journal and credential revision in ONE durable
     * transaction on the shared non-reentrant coordinator. Compare both full record revisions and the
     * credential revision; preserve every unrelated field. Invoke finalCheck synchronously, once, at
     * the final write boundary after any blocking work, then compare again and write without yielding.
     * A false check refuses before any write. Any other false is not acknowledged (see
     * [CredentialStore.compareAndSet]): the pair may have landed. Never implement as consume-then-approve.
     * Crash recovery exposes either the original pair or the complete replacement pair. A committed
     * approval resumes activation without calling this method again. Storage durability is an adapter
     * obligation; this port and JVM fakes do not prove Android atomicity.
     */
    fun commitElevationApproval(expected: AppApprovalState, replacement: AppApprovalState,
        expectedElevation: ElevationRecord, replacementElevation: ElevationRecord,
        expectedCredentials: CredentialState, finalCheck: () -> Boolean): Boolean
}

/** Process-local review binding, issued only from this controller's own grant and Play history. */
internal class ApprovalElevation(val grantId: String, val identity: ApprovalBinding,
    val person: ElevationPersonBinding)
