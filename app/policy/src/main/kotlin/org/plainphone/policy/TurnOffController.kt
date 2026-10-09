// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

/** Each category restores every recorded change, not a guessed platform default. */
enum class TurnOffStep {
    USER_RESTRICTIONS, HIDDEN_APPS, LOCK_TASK, PERSISTENT_PREFERRED_ACTIVITIES,
    INPUT_METHODS_AND_ACCESSIBILITY, KEYGUARD_FEATURES, BROWSER_ROLE,
    DEFAULT_SMS_AND_DIALER, RESET_PROTECTION, PLAY_RESTRICTIONS,
}

/**
 * Lane D supplies synchronous, idempotent effects against the durable original-settings inventory.
 * Missing inventory or an unverified readback must fail. Unchanged categories are verified no-ops.
 * Temporary access revocation must not re-apply the full lockdown during a committing turn-off.
 * No method may wipe data, guess defaults, or silently fall back to another destructive action.
 */
interface TurnOffEffects {
    fun revokeTemporaryAccess(): Boolean
    /** null is unreadable, never an inference that Device Owner is gone. */
    fun isDeviceOwner(): Boolean?
    fun restoreRecorded(step: TurnOffStep): Boolean
    fun readBackRecorded(step: TurnOffStep): Boolean
    /** An enabled/resolving home other than Plainphone, checked live. */
    fun anotherHomeResolves(): Boolean
    fun systemDialerResolves(): Boolean
    fun removeDeviceOwner(): Boolean
}

enum class TurnOffOutcome { PROGRESS, OFF, NEEDS_ATTENTION, IGNORED }
enum class TurnOffFailure {
    REVOCATION, OWNER_READ, JOURNAL_READ, JOURNAL_WRITE, RESTORE, READBACK,
    SUCCESSOR_HOME, SUCCESSOR_DIALER, OWNER_REMOVAL, STATE_MISMATCH,
}
data class TurnOffResult(
    val outcome: TurnOffOutcome,
    val failure: TurnOffFailure? = null,
    val failedStep: TurnOffStep? = null,
    /** OFF may be observed while the completion receipt still needs a durable retry. */
    val journalPending: Boolean = false,
)

/**
 * Serialized with WaitingChangeController and all policy/effect writers. No asynchronous callbacks.
 * Advances one read-back restore step per tick. It consumes only an already-COMMITTING operation;
 * it does not authorize or skip waits. A new instance revokes temporary access before all other work.
 * Damaged operation storage holds for the separate lost-record reconciler; never infer forward state.
 */
class TurnOffController(
    private val store: WaitingChangeStore,
    private val effects: TurnOffEffects,
    private val credentials: () -> CredentialState?,
) {
    private var initialized = false

    @Synchronized
    fun tick(requestId: String): TurnOffResult {
        if (!initialized) {
            initialized = effects.revokeTemporaryAccess()
            if (!initialized) {
                // Actual absence still means Off; revocation failure cannot invent continuing ownership.
                if (effects.isDeviceOwner() == false) return complete(store.read(), requestId)
                return failed(TurnOffFailure.REVOCATION)
            }
        }
        val owner = effects.isDeviceOwner() ?: return failed(TurnOffFailure.OWNER_READ)
        val before = store.read()
        if (!owner) return complete(before, requestId)
        if (before == null) return failed(TurnOffFailure.JOURNAL_READ)
        val operation = before.operation ?: return TurnOffResult(TurnOffOutcome.IGNORED)
        if (operation.identity.requestId != requestId) return TurnOffResult(TurnOffOutcome.IGNORED)
        if (operation.phase == WaitingPhase.COMPLETED) return failed(TurnOffFailure.STATE_MISMATCH)
        if (operation.phase != WaitingPhase.COMMITTING) return TurnOffResult(TurnOffOutcome.IGNORED)

        if (operation.restoredSteps < TurnOffStep.entries.size) {
            val step = TurnOffStep.entries[operation.restoredSteps]
            if (!effects.restoreRecorded(step)) return failed(TurnOffFailure.RESTORE, step)
            if (!effects.readBackRecorded(step)) return failed(TurnOffFailure.READBACK, step)
            if (!checkpoint(before, operation.withRestoredSteps(operation.restoredSteps + 1))) {
                return failed(TurnOffFailure.JOURNAL_WRITE, step)
            }
            return TurnOffResult(TurnOffOutcome.PROGRESS)
        }

        // Startup revocation or external changes may have invalidated an earlier readback.
        for (step in TurnOffStep.entries) {
            if (!effects.readBackRecorded(step)) {
                if (!checkpoint(before, operation.withRestoredSteps(step.ordinal))) return failed(TurnOffFailure.JOURNAL_WRITE, step)
                return failed(TurnOffFailure.READBACK, step)
            }
        }
        if (!effects.anotherHomeResolves()) return failed(TurnOffFailure.SUCCESSOR_HOME)
        if (!effects.systemDialerResolves()) return failed(TurnOffFailure.SUCCESSOR_DIALER)
        effects.removeDeviceOwner() // Read actual ownership even if the adapter reports an uncertain failure.
        return when (effects.isDeviceOwner()) {
            false -> complete(before, requestId)
            true -> failed(TurnOffFailure.OWNER_REMOVAL)
            null -> failed(TurnOffFailure.OWNER_READ)
        }
    }

    private fun checkpoint(before: WaitingState, operation: WaitingOperation): Boolean {
        val revision = nextRevision(before) ?: return false
        return store.compareAndSet(before, before.replacing(revision, operation))
    }

    private fun complete(before: WaitingState?, requestId: String): TurnOffResult {
        if (before == null) return TurnOffResult(TurnOffOutcome.OFF, journalPending = true)
        val operation = before.operation ?: return TurnOffResult(TurnOffOutcome.OFF)
        if (operation.identity.requestId != requestId) return TurnOffResult(TurnOffOutcome.OFF, journalPending = true)
        if (operation.phase == WaitingPhase.COMPLETED) return TurnOffResult(TurnOffOutcome.OFF)
        val revision = nextRevision(before) ?: return TurnOffResult(TurnOffOutcome.OFF, journalPending = true)
        val completed = operation.withPhase(WaitingPhase.COMPLETED)
        val recipients = credentials()?.people?.mapValues { it.value.generation } ?: operation.recipientGenerations
        val receipt = WaitingText("${requestId.length}:$requestId:$revision", WaitingEvent.COMPLETED, completed, recipients)
        val saved = store.compareAndSet(before, before.replacing(revision, completed, before.texts + receipt))
        return TurnOffResult(TurnOffOutcome.OFF, journalPending = !saved)
    }

    private fun nextRevision(state: WaitingState): Long? = try { Math.addExact(state.revision, 1) } catch (_: ArithmeticException) { null }
    private fun failed(reason: TurnOffFailure, step: TurnOffStep? = null) = TurnOffResult(TurnOffOutcome.NEEDS_ATTENTION, reason, step)
}
