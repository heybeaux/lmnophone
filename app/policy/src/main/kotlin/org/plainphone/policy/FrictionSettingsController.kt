// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*

enum class FrictionDirection { STRICTER, LOOSER }

/** Immutable proposal, never authority. Mixed-direction and unchanged proposals are refused. */
class FrictionSettingsPatch private constructor(
    val starter: TrustedPersonId,
    val generation: Long,
    val structureGeneration: Long,
    val source: FrictionSettings,
    val target: FrictionSettings,
    val direction: FrictionDirection,
) {
    /** A stricter intervening edit invalidates the old target, rather than silently undoing that edit. */
    fun dependenciesMatch(current: CredentialState): Boolean = current.mode == Mode.SELF_MANAGED &&
        current.structureGeneration == structureGeneration && current.people[starter]?.generation == generation &&
        current.recoveryProfile?.friction == source

    internal val waitingKind: WaitingChangeKind get() = if (target.countdownMillis < source.countdownMillis)
        WaitingChangeKind.SHORTEN_COUNTDOWN else WaitingChangeKind.RAISE_DAILY_LIMIT

    internal fun replacement(current: CredentialState): CredentialState? =
        if (dependenciesMatch(current)) replacementForCommit(current) else null

    /** After COMMITTING, a replaced PIN must survive and cannot stop this already-decided operation. */
    internal fun replacementForCommit(current: CredentialState): CredentialState? {
        if (current.mode != Mode.SELF_MANAGED || current.structureGeneration != structureGeneration ||
            starter !in current.people || current.recoveryProfile?.friction != source || current.revision == Long.MAX_VALUE) return null
        val profile = current.recoveryProfile!!
        return CredentialState(current.mode, current.people, current.revision + 1, current.structureGeneration,
            current.pendingNotices, RecoveryProfile(profile.holderUserIds, profile.backupUserId, target), current.appliedPeopleChange)
    }

    companion object {
        /**
         * Both fields may change together only in the same direction. For mixed edits the caller must
         * request the stricter edit separately, then prepare the looser edit from the new active values.
         * No splitting, partial success, or immediate application of a looser component occurs here.
         */
        fun create(current: CredentialState, starter: TrustedPersonId, target: FrictionSettings): FrictionSettingsPatch? {
            if (current.mode != Mode.SELF_MANAGED) return null
            val person = current.people[starter] ?: return null
            val source = current.recoveryProfile?.friction ?: return null
            return restore(starter, person.generation, current.structureGeneration, source, target)
        }

        /**
         * Decode immutable journal inputs only; this never creates PIN authority or a WaitingStart.
         * Reject malformed generations/settings/direction. The adapter must validate the surrounding
         * operation, checksum, storage generation and durable revision before exposing this data.
         */
        fun restore(starter: TrustedPersonId, generation: Long, structureGeneration: Long,
            source: FrictionSettings, target: FrictionSettings): FrictionSettingsPatch? {
            if (generation < 0 || structureGeneration < 0) return null
            val stricter = target.countdownMillis > source.countdownMillis || target.dailyLimit < source.dailyLimit
            val looser = target.countdownMillis < source.countdownMillis || target.dailyLimit > source.dailyLimit
            if (stricter == looser) return null
            return FrictionSettingsPatch(starter, generation, structureGeneration, source, target,
                if (stricter) FrictionDirection.STRICTER else FrictionDirection.LOOSER)
        }
    }
}

/** Process-local preparation; a restored proposal is not an authenticated request. */
class FrictionSettingsRequest internal constructor(val patch: FrictionSettingsPatch, internal val id: String, internal val owner: Any) {
    val action = PrivilegedAction("friction:stricter",
        "${id.length}:$id${patch.starter.value.length}:${patch.starter.value}:${patch.target.countdownMillis}:${patch.target.dailyLimit}")
    internal var used = false
}

/** Routine audit/outbox item. Recipients are fixed at commit, never resolved from later credentials. */
class FrictionSettingsReceipt internal constructor(val key: String, val actor: TrustedPersonId,
    val source: FrictionSettings, val target: FrictionSettings, current: CredentialState) {
    val recipientGenerations: Map<TrustedPersonId, Long> = java.util.Collections.unmodifiableMap(
        current.people.mapValues { it.value.generation })
    /** Null explicitly means no available address. Do not infer or synthesize one. */
    val recipientContacts: Map<TrustedPersonId, PersonContact?> = java.util.Collections.unmodifiableMap(
        current.people.mapValues { it.value.contact })
}

enum class FrictionSettingsOutcome { APPLIED, REFUSED, NOT_READY }

/** Optional lane D transaction port. Product setting actions remain disabled until integration review. */
interface FrictionSettingsStore {
    /** Same waiting record used by WaitingChangeController, never an independent cached view. */
    val waitingStore: WaitingChangeStore
    /** Exact shared elevation/session store constructed with waitingStore. */
    val elevationStore: ElevationStore

    /**
     * Under the common non-reentrant coordinator, atomically compare credential revision, waiting
     * storage generation/revision, and elevation revision. Write the new active friction and proposed
     * countdown and the supplied receipt in the audit/outbox together, incrementing both record revisions; preserve all credentials,
     * profile/backup data, notices, applied markers, exact debit list and session/grant data. Keep the
     * waiting record unchanged. Require the waiting operation not COMMITTING or ROLLING_BACK.
     * False changes nothing. Crash exposes the old pair or the entire new pair. Fence stale replicas;
     * a subsequent read must never return a record older than the acknowledged transaction.
     * Do not implement this as consecutive CredentialStore/ElevationStore writes. All consumers,
     * including elevation Start, must read limits from active credentials on this same coordinator.
     */
    fun applyStricter(expectedCredentials: CredentialState, replacementCredentials: CredentialState,
        expectedWaiting: WaitingState, expectedElevation: ElevationRecord, replacementElevation: ElevationRecord,
        receipt: FrictionSettingsReceipt): Boolean
}

/**
 * Immediate stricter edits only. This controller cannot execute looser patches or skip waits.
 * PinVault, every controller/store mutation and readback share one non-reentrant coordinator.
 * Startup revocation/ledger repair must finish before enabling this path; never restore process-local
 * countdown/grant ownership. Missing state is unavailable, never an invitation to reset a ledger.
 */
class FrictionSettingsController(
    private val store: FrictionSettingsStore,
    private val credentials: CredentialStore,
    private val authority: PersonAuthorityConsumer,
    private val clock: RunningClock,
    private val ids: AppApprovalIdSource,
) {
    private val owner = Any()
    private val waitingStore = store.waitingStore
    private val elevationStore = store.elevationStore

    private fun available(): Boolean = store.waitingStore === waitingStore && store.elevationStore === elevationStore &&
        elevationStore.waitingChanges === waitingStore && !elevationStore.revocationPending

    @Synchronized
    fun prepareStricter(starter: TrustedPersonId, target: FrictionSettings): FrictionSettingsRequest? {
        if (!available()) return null
        val current = credentials.read() ?: return null
        val patch = FrictionSettingsPatch.create(current, starter, target)?.takeIf { it.direction == FrictionDirection.STRICTER } ?: return null
        val id = ids.next().takeIf { it.isNotBlank() } ?: return null
        return FrictionSettingsRequest(patch, id, owner)
    }

    @Synchronized
    fun applyStricter(request: FrictionSettingsRequest, fresh: FreshAuthority): FrictionSettingsOutcome {
        if (request.owner !== owner || request.used || request.patch.direction != FrictionDirection.STRICTER) return FrictionSettingsOutcome.REFUSED
        if (!available()) return FrictionSettingsOutcome.NOT_READY
        val waiting = waitingStore.read() ?: return FrictionSettingsOutcome.NOT_READY
        if (waiting.operation?.phase in setOf(WaitingPhase.COMMITTING, WaitingPhase.ROLLING_BACK)) return FrictionSettingsOutcome.NOT_READY
        val current = credentials.read() ?: return FrictionSettingsOutcome.NOT_READY
        val before = elevationStore.read() ?: return FrictionSettingsOutcome.NOT_READY
        if (current.revision == Long.MAX_VALUE || before.revision == Long.MAX_VALUE) return FrictionSettingsOutcome.NOT_READY
        if (!request.patch.dependenciesMatch(current)) return FrictionSettingsOutcome.REFUSED
        if (clock.checkpoint() == null) return FrictionSettingsOutcome.NOT_READY
        if (!authority.consumeFor(fresh, request.action, request.patch.starter)) return FrictionSettingsOutcome.REFUSED
        request.used = true
        val now = clock.checkpoint() ?: return FrictionSettingsOutcome.NOT_READY
        val replacement = request.patch.replacement(current) ?: return FrictionSettingsOutcome.REFUSED
        // Persist the longer countdown now, even if the next elevation tick arrives after its old
        // lapse. The machine refuses to resurrect an already-lapsed or foreign-boot countdown.
        // Grants, their fixed deadlines and open package journals are never altered by a setting edit.
        val state = if (before.state.countdown != null) ElevationMachine.tick(before.state,
            ElevationLimits(request.patch.target.countdownMillis, request.patch.target.dailyLimit), now).state else before.state
        val next = before.replacing(state, person = before.person.takeIf { state.countdown != null || state.grant != null })
        val receipt = FrictionSettingsReceipt("friction:${request.id.length}:${request.id}", request.patch.starter,
            request.patch.source, request.patch.target, current)
        val pending = waiting.operation?.takeIf { it.phase == WaitingPhase.PENDING && it.frictionPatch != null }
        val cancelled = pending?.let { frictionTransition(waiting, it.withPhase(WaitingPhase.CANCELLED), WaitingEvent.DEPENDENCY_CHANGED, current) }
        val savedJoint = if (pending != null) {
            // A restrictive setting and invalidation of a pending looser target are one decision.
            // Without the joint port, never acknowledge only half of that decision.
            val joint = store as? FrictionWaitingStore ?: return FrictionSettingsOutcome.NOT_READY
            if (cancelled == null) return FrictionSettingsOutcome.NOT_READY
            joint.tightenAndCancel(current, replacement, waiting, cancelled, before, next, receipt)
        } else store.applyStricter(current, replacement, waiting, before, next, receipt)
        if (!savedJoint) return FrictionSettingsOutcome.NOT_READY
        val saved = credentials.read() ?: return FrictionSettingsOutcome.NOT_READY
        val savedElevation = elevationStore.read() ?: return FrictionSettingsOutcome.NOT_READY
        if (saved.revision != replacement.revision || saved.recoveryProfile?.friction != request.patch.target ||
            savedElevation.revision != next.revision) return FrictionSettingsOutcome.NOT_READY
        if (cancelled != null) {
            val savedWaiting = waitingStore.read() ?: return FrictionSettingsOutcome.NOT_READY
            if (savedWaiting.storageGeneration != cancelled.storageGeneration || savedWaiting.revision != cancelled.revision ||
                savedWaiting.operation?.identity?.requestId != pending!!.identity.requestId ||
                savedWaiting.operation?.phase != WaitingPhase.CANCELLED) return FrictionSettingsOutcome.NOT_READY
        }
        return FrictionSettingsOutcome.APPLIED
    }
}
