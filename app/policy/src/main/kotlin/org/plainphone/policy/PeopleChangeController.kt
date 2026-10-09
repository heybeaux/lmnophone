// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*

/** Written atomically with the changed holders; retained by every subsequent credential-only write. */
data class AppliedPeopleChange(val requestId: String, val waitingGeneration: String, val structureGeneration: Long) {
    init { require(requestId.isNotBlank() && waitingGeneration.isNotBlank() && structureGeneration >= 1) }
}

/**
 * Lane D's joint-store boundary, using the SAME stores as the waiting controller and vault.
 * Every method compares credential revision and waiting generation/revision/direction atomically.
 * False leaves both records unchanged. A crash exposes the entire old or new write, never part of it.
 * Reads must fence acknowledged newer records even if only an older journal replica remains.
 * All readers, writers and effects share one non-reentrant serialized coordinator.
 */
interface PeopleChangeCommitter {
    /**
     * Require this exact people request still COMMITTING and not already applied. Persist holders,
     * recovery profile, raised structure generation and appliedPeopleChange in ONE credential write.
     * The journal remains COMMITTING. A later completion write must never reapply this patch.
     */
    fun applyPatch(expectedCredentials: CredentialState, replacementCredentials: CredentialState,
        expectedWaiting: WaitingState): Boolean

    /**
     * Atomically persist journal direction/terminal state and fixed-contact receipt/audit obligations,
     * conditional on BOTH current records. Used for rollback intent, rollback cancellation and completion.
     * Do not implement the comparison and write as separately acknowledged operations.
     */
    fun move(expectedCredentials: CredentialState, expectedWaiting: WaitingState, replacementWaiting: WaitingState): Boolean
}

/** Synchronous, idempotent platform effects; exact supplied list includes the backup account. */
interface PeopleResetProtectionEffects {
    fun setResetProtection(userIds: Set<String>): Boolean
    fun broadcastResetProtectionChanged(): Boolean
    /** Checks policy values, not which Google account a real wipe would accept. */
    fun readBackResetProtection(userIds: Set<String>): Boolean
}

enum class PeopleChangeOutcome { APPLIED, COMPLETED, ROLLING_BACK, CANCELLED, NEEDS_ATTENTION, REFUSED, IGNORED }
enum class PeopleChangeFailure { JOURNAL_READ, CREDENTIALS, CLOCK, STATE_CHANGED, RESET_APPLY, RESET_BROADCAST, RESET_READBACK, PATCH_READBACK, PERSISTENCE }
data class PeopleChangeResult(val outcome: PeopleChangeOutcome, val failure: PeopleChangeFailure? = null)

/** Process-local stop preparation, bound to the precise request, journal generation and current PIN. */
class PeopleStopRequest internal constructor(internal val requestId: String, internal val waitingGeneration: String,
    internal val person: TrustedPersonId, internal val generation: Long, internal val structureGeneration: Long,
    internal val owner: Any) {
    val action = PrivilegedAction("waiting:stop-people",
        "${waitingGeneration.length}:$waitingGeneration${requestId.length}:$requestId${person.value.length}:${person.value}")
    internal var used = false
}

/**
 * Owns only already-COMMITTING people operations. It cannot start/skip waits or execute turn-off.
 * Startup revocation and lost-record reconciliation remain the common coordinator's responsibility.
 * All calls run in that coordinator; method synchronization alone cannot serialize multiple stores.
 */
class PeopleChangeController(
    private val store: WaitingChangeStore,
    private val credentials: CredentialStore,
    private val committer: PeopleChangeCommitter,
    private val effects: PeopleResetProtectionEffects,
    private val clock: RunningClock,
    private val authority: PersonAuthorityConsumer,
) {
    private val preparationOwner = Any()

    /** Scheduled work carries its direction. A stale forward callback can never resume rollback. */
    @Synchronized
    fun tick(requestId: String, direction: WaitingPhase): PeopleChangeResult {
        val before = store.read() ?: return failed(PeopleChangeFailure.JOURNAL_READ)
        val op = before.operation ?: return ignored()
        if (op.identity.requestId != requestId || op.peoplePatch == null || op.phase != direction ||
            direction !in setOf(WaitingPhase.COMMITTING, WaitingPhase.ROLLING_BACK)) return ignored()
        val current = credentials.read() ?: return failed(PeopleChangeFailure.CREDENTIALS)
        if (applied(before, current)) {
            if (direction != WaitingPhase.COMMITTING) return failed(PeopleChangeFailure.STATE_CHANGED)
            return finish(before, current, WaitingPhase.COMPLETED, WaitingEvent.COMPLETED, PeopleChangeOutcome.COMPLETED)
        }
        if (!beforePatch(op, current)) return failed(PeopleChangeFailure.STATE_CHANGED)
        if (direction == WaitingPhase.ROLLING_BACK) {
            return resetAndCheck(before, current, current.recoveryProfile!!.resetProtectionUserIds)
                ?: finish(before, current, WaitingPhase.CANCELLED, WaitingEvent.CANCELLED_AFTER_ROLLBACK, PeopleChangeOutcome.CANCELLED)
        }
        val candidate = op.peoplePatch.replacementForCommit(current) ?: return failed(PeopleChangeFailure.STATE_CHANGED)
        if (clock.checkpoint() == null) return failed(PeopleChangeFailure.CLOCK)
        val marker = AppliedPeopleChange(requestId, before.storageGeneration, candidate.structureGeneration)
        val replacement = CredentialState(candidate.mode, candidate.people, candidate.revision, candidate.structureGeneration,
            candidate.pendingNotices, candidate.recoveryProfile, marker)
        resetAndCheck(before, current, replacement.recoveryProfile!!.resetProtectionUserIds)?.let { return it }
        if (!committer.applyPatch(current, replacement, before)) return failed(PeopleChangeFailure.PERSISTENCE)
        // Never reinstall setup credentials if acknowledgement/readback is interrupted or unavailable.
        val readback = credentials.read() ?: return failed(PeopleChangeFailure.PATCH_READBACK)
        if (readback.revision < replacement.revision || !applied(before, readback)) return failed(PeopleChangeFailure.PATCH_READBACK)
        return PeopleChangeResult(PeopleChangeOutcome.APPLIED)
    }

    @Synchronized
    fun prepareStop(requestId: String, person: TrustedPersonId): PeopleStopRequest? {
        val before = store.read() ?: return null
        val op = before.operation ?: return null
        if (op.identity.requestId != requestId || op.phase != WaitingPhase.COMMITTING || op.peoplePatch == null) return null
        val current = credentials.read() ?: return null
        val holder = current.people[person] ?: return null
        if (applied(before, current) || !beforePatch(op, current)) return null
        return PeopleStopRequest(requestId, before.storageGeneration, person, holder.generation, current.structureGeneration, preparationOwner)
    }

    /** Fresh PIN first; durable ROLLING_BACK plus its receipt before any restoring effect. */
    @Synchronized
    fun stop(request: PeopleStopRequest, grant: FreshAuthority): PeopleChangeResult {
        if (request.used || request.owner !== preparationOwner) return refused()
        val before = store.read() ?: return failed(PeopleChangeFailure.JOURNAL_READ)
        val op = before.operation ?: return refused()
        val current = credentials.read() ?: return failed(PeopleChangeFailure.CREDENTIALS)
        if (op.identity.requestId != request.requestId || before.storageGeneration != request.waitingGeneration ||
            op.phase != WaitingPhase.COMMITTING || !beforePatch(op, current) || applied(before, current) ||
            current.structureGeneration != request.structureGeneration || current.people[request.person]?.generation != request.generation) return refused()
        if (!authority.consumeFor(grant, request.action, request.person)) return refused()
        request.used = true
        clock.checkpoint() // A failed checkpoint after fresh authentication cannot prevent a restrictive stop.
        val rollback = op.withRollback(request.person, request.generation)
        return move(before, current, rollback, WaitingEvent.ROLLING_BACK, PeopleChangeOutcome.ROLLING_BACK)
    }

    private fun resetAndCheck(before: WaitingState, current: CredentialState, ids: Set<String>): PeopleChangeResult? {
        if (!stillCurrent(before, current)) return failed(PeopleChangeFailure.STATE_CHANGED)
        if (!effects.setResetProtection(ids)) return failed(PeopleChangeFailure.RESET_APPLY)
        if (!stillCurrent(before, current)) return failed(PeopleChangeFailure.STATE_CHANGED)
        if (!effects.broadcastResetProtectionChanged()) return failed(PeopleChangeFailure.RESET_BROADCAST)
        if (!stillCurrent(before, current)) return failed(PeopleChangeFailure.STATE_CHANGED)
        if (!effects.readBackResetProtection(ids)) return failed(PeopleChangeFailure.RESET_READBACK)
        if (!stillCurrent(before, current)) return failed(PeopleChangeFailure.STATE_CHANGED)
        return null
    }

    private fun stillCurrent(before: WaitingState, current: CredentialState): Boolean {
        val live = store.read() ?: return false
        return live.storageGeneration == before.storageGeneration && live.revision == before.revision &&
            live.operation?.identity?.requestId == before.operation?.identity?.requestId &&
            live.operation?.phase == before.operation?.phase && credentials.read()?.revision == current.revision
    }

    private fun beforePatch(op: WaitingOperation, current: CredentialState): Boolean {
        val patch = op.peoplePatch ?: return false
        return current.mode == Mode.TRUSTED && current.structureGeneration == op.structureGeneration &&
            current.recoveryProfile != null && current.people.keys.containsAll(patch.dependencyGenerations.keys) &&
            (patch.incoming == null || patch.incoming.person !in current.people)
    }

    private fun applied(before: WaitingState, current: CredentialState): Boolean {
        val marker = current.appliedPeopleChange ?: return false
        val op = before.operation ?: return false
        val target = try { Math.addExact(op.structureGeneration, 1) } catch (_: ArithmeticException) { return false }
        return marker.requestId == op.identity.requestId && marker.waitingGeneration == before.storageGeneration &&
            marker.structureGeneration == target && current.structureGeneration == target && current.mode == Mode.TRUSTED &&
            current.recoveryProfile != null
    }

    private fun finish(before: WaitingState, current: CredentialState, phase: WaitingPhase,
        event: WaitingEvent, outcome: PeopleChangeOutcome): PeopleChangeResult {
        clock.checkpoint()
        return move(before, current, before.operation!!.withPhase(phase), event, outcome)
    }

    private fun move(before: WaitingState, current: CredentialState, op: WaitingOperation,
        event: WaitingEvent, outcome: PeopleChangeOutcome): PeopleChangeResult {
        val revision = try { Math.addExact(before.revision, 1) } catch (_: ArithmeticException) { return failed(PeopleChangeFailure.PERSISTENCE) }
        // Both recipient maps are from this single snapshot, compared again by the joint transaction.
        val id = op.identity.requestId
        val text = WaitingText("${id.length}:$id:$revision", event, op,
            current.people.mapValues { it.value.generation }, current.people.mapValues { it.value.contact })
        val replacement = before.replacing(revision, op, before.texts + text)
        return if (committer.move(current, before, replacement)) PeopleChangeResult(outcome)
            else failed(PeopleChangeFailure.PERSISTENCE)
    }

    private fun failed(reason: PeopleChangeFailure) = PeopleChangeResult(PeopleChangeOutcome.NEEDS_ATTENTION, reason)
    private fun ignored() = PeopleChangeResult(PeopleChangeOutcome.IGNORED)
    private fun refused() = PeopleChangeResult(PeopleChangeOutcome.REFUSED)
}
