// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*

/**
 * Optional lane D extension; no default multi-write implementation. All methods share the same
 * non-reentrant coordinator and stores as PinVault, waiting, stricter edits, sessions and elevations.
 */
interface FrictionWaitingStore : FrictionSettingsStore {
    /**
     * Compare credential revision, waiting storage generation/revision/request/direction and elevation
     * revision in one durable transaction. Require this exact friction operation COMMITTING and its
     * mode, structure and source friction still current. Write active target friction and COMPLETED
     * journal plus fixed-recipient completion receipt/audit together. Preserve every other credential
     * field and the ENTIRE elevation record, including expired/foreign-epoch debits and boot deadlines.
     * Do not restore starter credentials: post-COMMITTING PIN recovery remains current.
     * False is not acknowledged (see [CredentialStore.compareAndSet]); the completion may have landed.
     * A crash exposes old+COMMITTING or target+COMPLETED+receipt, never a mix.
     * Fence stale copies across all stores before acknowledging; reads cannot return older replicas.
     * Atomic terminal activation itself is the replay marker; no intermediate credential-only patch.
     */
    fun completeFriction(expectedCredentials: CredentialState, replacementCredentials: CredentialState,
        expectedWaiting: WaitingState, replacementWaiting: WaitingState, expectedElevation: ElevationRecord): Boolean

    /**
     * applyStricter's entire transaction PLUS cancellation of the exact pending friction operation,
     * its fixed-recipient DEPENDENCY_CHANGED receipt/audit, and the stricter routine receipt. Compare
     * all three revisions and waiting generation/request/phase. Preserve cancel intent records;
     * terminal journal fencing must survive loss of the newest copy. Never leave a now-stale target
     * pending after acknowledging the stricter setting. No other waiting kind is cancelled here.
     */
    fun tightenAndCancel(expectedCredentials: CredentialState, replacementCredentials: CredentialState,
        expectedWaiting: WaitingState, replacementWaiting: WaitingState, expectedElevation: ElevationRecord,
        replacementElevation: ElevationRecord, receipt: FrictionSettingsReceipt): Boolean
}

enum class FrictionWaitingOutcome { COMPLETED, CANCELLED, IGNORED, NOT_READY }

/**
 * Executes only the shared waiting controller's already-COMMITTING friction operations. It neither
 * starts nor skips a wait. Active credential-store friction is the sole settings authority; consumers
 * may not require a separate physical/cache activation step. Lost records use WaitingReconciler.
 */
class FrictionWaitingController(
    private val store: FrictionWaitingStore,
    private val credentials: CredentialStore,
    private val clock: RunningClock,
) {
    private val waitingStore = store.waitingStore
    private val elevationStore = store.elevationStore

    @Synchronized
    fun finish(requestId: String): FrictionWaitingOutcome {
        if (store.waitingStore !== waitingStore || store.elevationStore !== elevationStore ||
            elevationStore.waitingChanges !== waitingStore) return FrictionWaitingOutcome.NOT_READY
        val before = waitingStore.read() ?: return FrictionWaitingOutcome.NOT_READY
        val op = before.operation ?: return FrictionWaitingOutcome.IGNORED
        val patch = op.frictionPatch ?: return FrictionWaitingOutcome.IGNORED
        if (op.identity.requestId != requestId) return FrictionWaitingOutcome.IGNORED
        when (op.phase) {
            WaitingPhase.COMPLETED -> return FrictionWaitingOutcome.COMPLETED
            WaitingPhase.CANCELLED -> return FrictionWaitingOutcome.CANCELLED
            WaitingPhase.COMMITTING -> Unit
            else -> return FrictionWaitingOutcome.IGNORED
        }
        if (elevationStore.revocationPending) return FrictionWaitingOutcome.NOT_READY
        val current = credentials.read() ?: return FrictionWaitingOutcome.NOT_READY
        val elevation = elevationStore.read() ?: return FrictionWaitingOutcome.NOT_READY
        val nextCredentials = patch.replacementForCommit(current) ?: return FrictionWaitingOutcome.NOT_READY
        val nextWaiting = frictionTransition(before, op.withPhase(WaitingPhase.COMPLETED), WaitingEvent.COMPLETED, current)
            ?: return FrictionWaitingOutcome.NOT_READY
        if (clock.checkpoint() == null) return FrictionWaitingOutcome.NOT_READY
        if (!store.completeFriction(current, nextCredentials, before, nextWaiting, elevation)) return FrictionWaitingOutcome.NOT_READY
        val saved = waitingStore.read() ?: return FrictionWaitingOutcome.NOT_READY
        val savedCredentials = credentials.read() ?: return FrictionWaitingOutcome.NOT_READY
        val savedElevation = elevationStore.read() ?: return FrictionWaitingOutcome.NOT_READY
        if (saved.storageGeneration != nextWaiting.storageGeneration || saved.revision != nextWaiting.revision ||
            saved.operation?.identity?.requestId != requestId || saved.operation?.phase != WaitingPhase.COMPLETED ||
            savedCredentials.revision != nextCredentials.revision || savedCredentials.recoveryProfile?.friction != patch.target ||
            savedElevation.revision != elevation.revision) return FrictionWaitingOutcome.NOT_READY
        return FrictionWaitingOutcome.COMPLETED
    }
}

/** Same revision/key/fixed-recipient convention as the shared waiting controller. */
internal fun frictionTransition(before: WaitingState, operation: WaitingOperation, event: WaitingEvent,
    current: CredentialState): WaitingState? {
    if (before.revision == Long.MAX_VALUE) return null
    val revision = before.revision + 1
    val id = operation.identity.requestId
    val receipt = WaitingText("${id.length}:$id:$revision", event, operation,
        current.people.mapValues { it.value.generation }, current.people.mapValues { it.value.contact })
    return before.replacing(revision, operation, before.texts + receipt)
}
