// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

/**
 * Optional extension of the reviewed people-change transaction. Without BOTH this port and
 * ModeSwitchEffects, PeopleChangeController ignores mode switches. Product actions stay disabled
 * until lane D implements and independently verifies this contract.
 */
interface ModeSwitchCommitter : PeopleChangeCommitter {
    /**
     * Exact combined record instance shared by SessionController and ElevationController, constructed
     * with ElevationStore(theSameWaitingChangeStore). This binding is required, immutable, and checked
     * by the executor. Its durable operation-aware gate survives ordinary controller cleanup and is
     * restored from waiting storage before any executor pass after startup. No alternate waiting view.
     */
    val elevationStore: ElevationStore

    /**
     * On the common non-reentrant coordinator, compare credential revision, waiting storage
     * generation/revision/request/direction and elevation revision in one transaction. Require the
     * exact mode request still COMMITTING, with no applied marker for this request and no temporary
     * authority in expectedElevation (empty shared journal, no countdown/grant/person binding).
     * Write mode, holders, recovery profile, raised structure generation and appliedPeopleChange
     * together. Leave the elevation record and every debit unchanged, including expired debits;
     * switching never creates or resets a ledger. Leave the operation COMMITTING for completion.
     * False changes nothing. Crash exposes old or new credential state, never mixed authority.
     * Fence stale replicas just as for PeopleChangeCommitter. Never route this through applyPatch.
     */
    fun applyModePatch(expectedCredentials: CredentialState, replacementCredentials: CredentialState,
        expectedWaiting: WaitingState, expectedElevation: ElevationRecord): Boolean

    /**
     * Terminal COMPLETED or CANCELLED write plus fixed-contact receipts/audit, comparing the same
     * credential/waiting/elevation revisions as applyModePatch. expectedElevation must be clear of
     * temporary authority. Preserve it and its entire ledger. False leaves the journal and latch
     * held; success makes the durable operation terminal and permits release of the local cleanup latch. Rollback
     * intent still uses move, since writing a restrictive direction never releases authority.
     */
    fun finishMode(expectedCredentials: CredentialState, expectedWaiting: WaitingState,
        replacementWaiting: WaitingState, expectedElevation: ElevationRecord): Boolean
}

/** Additional synchronous idempotent effects, serialized with every controller/store mutation. */
interface ModeSwitchEffects : PeopleResetProtectionEffects {
    /**
     * Close all physical temporary access and invalidate process-local session, Play candidate,
     * elevation, approval and staged-update authority. The executor has already durably cleared
     * the shared elevation/session journal while retaining the exact ledger. Apply the normal
     * protected policy, preserving emergency calling/alerts. Never change credentials, the waiting
     * direction, or the debit ledger here. No reentrant privileged action is permitted.
     */
    fun revokeTemporaryAuthority(): Boolean
    /** Read back complete protected policy, with no old temporary package access. */
    fun readBackProtectedState(): Boolean
}
