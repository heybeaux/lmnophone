// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import java.util.Collections

enum class UpdatePhase { SUBMITTED, INSTALLED, FAILED }
enum class UpdateSelfCheck { PENDING, PASSED, FAILED }
enum class UpdateOutcome { REFUSED, SUBMITTED, COMMIT_UNCERTAIN, INSTALLED, FAILED, NOT_READY }

/** Durable notice intent, captured with use-up before an installer call can kill this process. */
class UpdateReceipt(val id: String, val actor: TrustedPersonId, val actorGeneration: Long,
    recipientGenerations: Map<TrustedPersonId, Long>) {
    val recipientGenerations: Map<TrustedPersonId, Long> = Collections.unmodifiableMap(recipientGenerations.toMap())
    init {
        require(id.isNotBlank() && actorGeneration >= 0)
        require(this.recipientGenerations[actor] == actorGeneration && this.recipientGenerations.values.all { it >= 0 })
    }
}

/** Durable evidence only: never authorizes an installer call, including after a process restart. */
data class UpdateSubmission(val sessionId: Int, val sha256: String, val versionCode: Long, val receipt: UpdateReceipt,
    val elevationDeadline: BootTime? = null, val phase: UpdatePhase = UpdatePhase.SUBMITTED,
    val selfCheck: UpdateSelfCheck = UpdateSelfCheck.PENDING) {
    init {
        require(sessionId >= 0)
        StagedUpdateBinding(sha256, versionCode)
        require(phase == UpdatePhase.INSTALLED || selfCheck == UpdateSelfCheck.PENDING)
    }
}
class UpdateRecord(val revision: Long = 0, val submission: UpdateSubmission? = null) {
    init { require(revision >= 0) }
    internal fun replacing(submission: UpdateSubmission) = UpdateRecord(Math.addExact(revision, 1), submission)
}

data class UpdateFinalFacts(val sessionId: Int, val staged: StagedApkFacts, val installedVersionCode: Long) {
    init { require(sessionId >= 0 && installedVersionCode >= 0) }
}

/** Implemented by the device adapter; all writers share the elevation/credential coordinator. */
interface UpdateUseStore {
    val elevationStore: ElevationStore
    /** Null means missing/damaged. Only enrollment may create an empty record. */
    fun readUpdate(): UpdateRecord?
    /** Result-only CAS. Preserve all elevation, credential and unrelated fields. */
    fun compareAndSetUpdate(expected: UpdateRecord, replacement: UpdateRecord): Boolean
    /**
     * ONE durable transaction, never consume-then-act exposed as two public controller operations.
     * Compare exact elevation, credential and update records/revisions. After all blocking work call
     * finalCheck synchronously exactly once. Compare again, then write replacementElevation and a
     * SUBMITTED record (including its actor/receipt) at expectedUpdate.revision + 1, without yielding.
     * Capture immutable recipient delivery destinations in this transaction too; preserve undelivered
     * receipts in the durable outbox before replacing any prior submission. Never enqueue after commit.
     * A false check/CAS changes nothing; a crash exposes either the original pair or complete pair.
     * Reject an outstanding SUBMITTED record or pending installed self-check. For supervised use,
     * replacementElevation is the exact unchanged expectedElevation and finalCheck uses fresh PIN
     * authority; do not invent an elevation/debit. Preserve original deadlines and every debit.
     * Every session/journal/credential/mode writer uses this same coordinator. Reject reentrant
     * controller mutations, but allow finalCheck's reads and PinVault.consumeFor on this thread.
     * The caller reads the boot clock on success before installer commit. A passed deadline spends
     * the turn without installing; a result-only CAS records FAILED and preserves the receipt/outbox.
     * There is no intervening adapter work on the successful install path.
     */
    fun commitUpdateUse(expectedElevation: ElevationRecord, replacementElevation: ElevationRecord,
        expectedCredentials: CredentialState, expectedUpdate: UpdateRecord, submission: UpdateSubmission,
        finalCheck: () -> Boolean): Boolean
}

interface UpdateInstaller {
    /**
     * Abandon only UNCOMMITTED own sessions before ElevationController.onStart. Keep committed
     * sessions (X6); reconciliation tracks them until installed or confirmed gone. Any memory of
     * commit attempts belongs to one adapter instance, not the entire process.
     */
    fun abandonOwnSessions(): Boolean
    /**
     * Fresh facts from adapter-owned staging, never UI/intent data. Hash Session.openRead after the
     * LAST write; package, exact signer set and version come from verified immutable private staging
     * with the SAME hash. No write to that session after this read (G3). The session must be private
     * to the coordinator and setAppPackageName must name the pinned self package. Return null on
     * any validation/read failure. Re-reading is allowed; never silently create a different session.
     * Download obligations remain local host/port, no redirects, 100 MB/2 min, private staging.
     */
    fun finalFacts(): UpdateFinalFacts?
    /** Submit this session once. Never retry, including if this call throws. */
    fun commit(sessionId: Int)
    /** Read-only: true while the committed session exists, false when gone, null if unknown. */
    fun committedSessionPending(sessionId: Int): Boolean?
    fun installedVersionCode(): Long?
    /** Idempotent handover self-check. Installation success alone does not prove app health. */
    fun postUpdateSelfCheck(): Boolean
}

class SupervisedUpdateRequest internal constructor(val id: String, val staged: StagedUpdateBinding,
    internal val person: TrustedPersonId, internal val generation: Long,
    internal val structureGeneration: Long, internal val boot: BootTime) {
    val action = PrivilegedAction("update:submit", id)
    internal fun matches(current: CredentialState?) = current?.mode == Mode.TRUSTED &&
        current.structureGeneration == structureGeneration && current.people[person]?.generation == generation
}

/**
 * Internal rollout seam, deliberately not wired by any public constructor/factory. Update remains
 * disabled in production until policy AND device adapter pass the Lead's cross-model review.
 * The JVM tests exercise the protocol through this seam; it is not a user preference or runtime flag.
 */
internal class UpdateProtocol(val store: UpdateUseStore, val installer: UpdateInstaller, val pinnedSelf: ApprovalBinding,
    /** Pure monotonic boot-clock read: no persistence, checkpoint, staging or other adapter work. */
    private val bootTime: () -> BootTime?) {
    var ready = false
        private set
    fun invalidate() { ready = false }

    /** Adapter startup wrapper: abandon, revoke temporary authority, then classify (never commit). */
    fun onStart(elevation: ElevationController): ElevationOutcome {
        ready = false
        if (elevation.updateProtocol !== this || !installer.abandonOwnSessions()) return ElevationOutcome.NOT_READY
        val started = elevation.onStart()
        if (started != ElevationOutcome.IDLE) return started
        if (reconcile() == UpdateOutcome.NOT_READY) return ElevationOutcome.NOT_READY
        ready = true
        return ElevationOutcome.IDLE
    }

    fun available(): UpdateRecord? = store.readUpdate()?.takeIf {
        it.revision < Long.MAX_VALUE && it.submission?.phase != UpdatePhase.SUBMITTED &&
            !(it.submission?.phase == UpdatePhase.INSTALLED && it.submission.selfCheck == UpdateSelfCheck.PENDING)
    }
    fun valid(facts: UpdateFinalFacts?, binding: StagedUpdateBinding): Boolean = facts != null &&
        facts.staged.signatureValid && facts.staged.identity == pinnedSelf && facts.staged.binding == binding &&
        facts.staged.binding.versionCode > facts.installedVersionCode

    /** Read time once after durable use-up; a late use stays spent but never reaches the installer. */
    fun commit(submission: UpdateSubmission): UpdateOutcome {
        submission.elevationDeadline?.let { deadline ->
            val now = bootTime()
            if (now == null || now.bootId != deadline.bootId || now.elapsedMillis > deadline.elapsedMillis) {
                val before = store.readUpdate() ?: return UpdateOutcome.NOT_READY
                val saved = before.submission ?: return UpdateOutcome.NOT_READY
                if (saved.sessionId != submission.sessionId || saved.receipt.id != submission.receipt.id ||
                    saved.phase != UpdatePhase.SUBMITTED || before.revision == Long.MAX_VALUE) return UpdateOutcome.NOT_READY
                return if (store.compareAndSetUpdate(before, before.replacing(saved.copy(phase = UpdatePhase.FAILED))))
                    UpdateOutcome.FAILED else UpdateOutcome.NOT_READY
            }
        }
        return try {
            installer.commit(submission.sessionId)
            UpdateOutcome.SUBMITTED
        } catch (_: Exception) {
            // Even a thrown call may have reached the platform. Startup/result readback decides later.
            UpdateOutcome.COMMIT_UNCERTAIN
        }
    }

    /** Adapter-only result routing. Status callbacks never authorize/retry installation. */
    @Suppress("UNUSED_PARAMETER") // Both terminal statuses require live platform readback.
    fun onResult(sessionId: Int, success: Boolean): UpdateOutcome {
        if (!ready) return UpdateOutcome.NOT_READY
        val before = store.readUpdate() ?: return UpdateOutcome.NOT_READY
        val submission = before.submission ?: return UpdateOutcome.REFUSED
        if (submission.sessionId != sessionId || submission.phase == UpdatePhase.INSTALLED) return UpdateOutcome.REFUSED
        return readBack(before)
    }

    private fun reconcile(): UpdateOutcome {
        val before = store.readUpdate() ?: return UpdateOutcome.NOT_READY
        return when (before.submission?.phase) {
            null -> UpdateOutcome.REFUSED
            UpdatePhase.SUBMITTED, UpdatePhase.FAILED -> readBack(before)
            UpdatePhase.INSTALLED -> selfCheck(before)
        }
    }
    /** Read-only journal status; querying never runs a check, grants authority or sends a notice. */
    fun needsAttention(): Boolean = store.readUpdate()?.submission?.let {
        it.phase == UpdatePhase.INSTALLED && it.selfCheck == UpdateSelfCheck.FAILED
    } == true

    private fun readBack(before: UpdateRecord): UpdateOutcome {
        // Read session presence first: completion between these reads must not combine an old
        // installed version with a now-gone session and prematurely allow journal replacement.
        val pending = installer.committedSessionPending(before.submission!!.sessionId)
        val installed = installer.installedVersionCode() ?: return UpdateOutcome.NOT_READY
        return classify(before, installed == before.submission.versionCode, pending)
    }
    private fun classify(before: UpdateRecord, installed: Boolean, pending: Boolean?): UpdateOutcome {
        val submission = before.submission!!
        val phase = if (installed) UpdatePhase.INSTALLED else {
            when (pending) {
                true -> UpdatePhase.SUBMITTED
                false -> UpdatePhase.FAILED
                null -> return UpdateOutcome.NOT_READY
            }
        }
        val next = if (submission.phase == phase) before else {
            if (before.revision == Long.MAX_VALUE) return UpdateOutcome.NOT_READY
            before.replacing(submission.copy(phase = phase)).also {
                if (!store.compareAndSetUpdate(before, it)) return UpdateOutcome.NOT_READY
            }
        }
        return when (phase) {
            UpdatePhase.INSTALLED -> selfCheck(next)
            UpdatePhase.SUBMITTED -> UpdateOutcome.SUBMITTED
            UpdatePhase.FAILED -> UpdateOutcome.FAILED
        }
    }
    private fun selfCheck(before: UpdateRecord): UpdateOutcome {
        val submission = before.submission!!
        if (submission.selfCheck == UpdateSelfCheck.PENDING) {
            if (before.revision == Long.MAX_VALUE) return UpdateOutcome.NOT_READY
            val passed = installer.postUpdateSelfCheck()
            if (!store.compareAndSetUpdate(before, before.replacing(submission.copy(
                    selfCheck = if (passed) UpdateSelfCheck.PASSED else UpdateSelfCheck.FAILED)))) return UpdateOutcome.NOT_READY
        }
        return UpdateOutcome.INSTALLED
    }
}
