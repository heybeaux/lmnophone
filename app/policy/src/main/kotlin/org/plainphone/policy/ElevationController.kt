// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*

/** Saved identity binding is data, never sufficient to restore a countdown or grant after restart. */
data class ElevationPersonBinding(val person: TrustedPersonId, val generation: Long, val structureGeneration: Long) {
    init { require(generation >= 0 && structureGeneration >= 0) }
    internal fun matches(credentials: CredentialState?) = credentials?.mode == Mode.SELF_MANAGED &&
        credentials.structureGeneration == structureGeneration && credentials.people[person]?.generation == generation
}

/** One device-protected record. Decode invalid combinations as null, not as an empty ledger. */
class ElevationRecord(val revision: Long = 0, val state: ElevationState = ElevationState(),
    val journal: SessionJournal = SessionJournal.Empty, val person: ElevationPersonBinding? = null) {
    init {
        require(revision >= 0 && journal != SessionJournal.Damaged)
        require((state.countdown != null || state.grant != null) == (person != null))
        val grant = state.grant
        val active = journal as? SessionJournal.Active
        if (active != null && grant != null) {
            require(active.access.id == grant.id && active.access.deadline == grant.deadline)
            require(active.access.kind == when (grant.target) {
                ElevationTarget.Play -> WindowKind.PLAY
                ElevationTarget.Settings -> WindowKind.SETTINGS
                else -> null
            })
            require(grant.packageAccessOpen == (active.phase != SessionPhase.CLOSING))
        } else {
            require(grant?.packageAccessOpen != true)
            // An active journal without an elevation belongs to a supervised session.
            require(active == null || state.countdown == null)
        }
    }
    internal fun replacing(state: ElevationState, journal: SessionJournal = this.journal,
        person: ElevationPersonBinding? = this.person) = ElevationRecord(Math.addExact(revision, 1), state, journal, person)
}
abstract class ElevationStore {
    // One instance per combined record, shared by both controllers on the serialized coordinator.
    // A failed restrictive write suppresses both readers until successful cleanup, without altering debits.
    internal var revocationPending: Boolean = false
    /** Null means damaged/missing, including an invalid session scope. Only enrollment creates an empty ledger. */
    abstract fun read(): ElevationRecord?
    /**
     * Single atomic durable write of ledger, grant/countdown, person and the SHARED session journal.
     * Compare the whole record revision and, when supplied, credential revision in the same transaction.
     * Null expected is a repair CAS against the exact missing/damaged observation, never unconditional overwrite.
     * All JournalStore/credential/operation writers share this coordinator and preserve each other's fields.
     */
    abstract fun compareAndSet(expected: ElevationRecord?, replacement: ElevationRecord, expectedCredentials: CredentialState?): Boolean
}
class ElevationRequest internal constructor(val id: String, val target: ElevationTarget,
    internal val person: ElevationPersonBinding, internal val boot: BootTime) {
    val action = PrivilegedAction("elevation:request", id)
}

/**
 * Serialized, non-reentrant coordinator shared with PinVault, session, waiting, approval and updater writes.
 * This slice opens Play/Settings only. Approve/Update and approval consumption remain unavailable until
 * their final transactions are integrated. Candidate data is not an approval capability.
 */
class ElevationController(
    private val store: ElevationStore,
    private val credentials: () -> CredentialState?,
    private val authority: PersonAuthorityConsumer,
    private val clock: RunningClock,
    private val limits: () -> ElevationLimits,
    private val policyInput: () -> PolicyInput?,
    private val battery: () -> BatteryState,
    private val effects: SessionEffects,
    private val ids: AppApprovalIdSource,
) {
    private var initialized = false
    private var recoveryRequired = false
    private var ownedId: String? = null
    private val requests = mutableSetOf<ElevationRequest>()
    private var play: PlaySessionState? = null
    private data class Close(val id: String, val retain: Boolean, val outcome: ElevationOutcome)
    private var closing: Close? = null

    /** Before any access or other controller work, including Direct Boot. No credential read is needed. */
    @Synchronized fun onStart(): ElevationOutcome {
        initialized = false; recoveryRequired = true; ownedId = null; requests.clear(); play = null; closing = null
        store.revocationPending = true
        val before = store.read()
        if (!effects.revokeAllRelaxations() || !effects.protectedStateCheck() || !effects.readBackPolicy()) return ElevationOutcome.NOT_READY
        val state = if (before == null) {
            val now = clock.checkpoint() ?: return ElevationOutcome.NOT_READY
            ElevationState(ElevationLedger.repair(now.proven))
        } else ElevationMachine.onRestart(before.state)
        if (before?.revision == Long.MAX_VALUE) return ElevationOutcome.NOT_READY
        val next = ElevationRecord((before?.revision ?: -1) + 1, state)
        if (!store.compareAndSet(before, next, null)) return ElevationOutcome.NOT_READY
        initialized = true; recoveryRequired = false; store.revocationPending = false
        return ElevationOutcome.IDLE
    }

    @Synchronized fun prepare(person: TrustedPersonId, target: ElevationTarget): ElevationRequest? {
        if (!initialized || closing != null || store.revocationPending || !supported(target)) return null
        val current = credentials() ?: return null
        if (current.mode != Mode.SELF_MANAGED) return null
        val credential = current.people[person] ?: return null
        val before = store.read() ?: return null
        if (before.revision == Long.MAX_VALUE || before.state.countdown != null || before.state.grant != null || before.journal != SessionJournal.Empty) return null
        val now = clock.checkpoint() ?: return null
        if (packages(target) == null) return null
        val id = ids.next()
        if (id.isBlank() || before.state.ledger.debits.any { it.id == id }) return null
        return ElevationRequest(id, target, ElevationPersonBinding(person, credential.generation, current.structureGeneration), now.boot)
            .also { requests.add(it) }
    }

    @Synchronized fun request(request: ElevationRequest, fresh: FreshAuthority): ElevationOutcome {
        if (!initialized || closing != null || store.revocationPending) return ElevationOutcome.NOT_READY
        if (!requests.remove(request)) return ElevationOutcome.REFUSED
        val before = store.read() ?: return ElevationOutcome.NOT_READY
        val current = credentials() ?: return ElevationOutcome.NOT_READY
        val now = clock.checkpoint() ?: return ElevationOutcome.NOT_READY
        if (!request.person.matches(current) || request.boot.bootId != now.boot.bootId || now.boot.elapsedMillis < request.boot.elapsedMillis ||
            packages(request.target) == null || before.journal != SessionJournal.Empty) return ElevationOutcome.REFUSED
        val proposal = ElevationMachine.request(before.state, request.id, request.target, limits(), now)
        if (proposal.outcome != ElevationOutcome.COUNTING) return proposal.outcome
        if (!authority.consumeFor(fresh, request.action, request.person.person)) return ElevationOutcome.REFUSED
        val checked = clock.checkpoint() ?: return ElevationOutcome.NOT_READY
        if (checked.boot.bootId != now.boot.bootId || checked.boot.elapsedMillis < now.boot.elapsedMillis ||
            !request.person.matches(credentials()) || packages(request.target) == null) return ElevationOutcome.REFUSED
        val anchored = ElevationMachine.request(before.state, request.id, request.target, limits(), checked)
        if (anchored.outcome != ElevationOutcome.COUNTING) return anchored.outcome
        if (!save(before, anchored.state, SessionJournal.Empty, request.person, current)) return ElevationOutcome.NOT_READY
        ownedId = request.id; requests.clear()
        return ElevationOutcome.COUNTING
    }

    @Synchronized fun start(id: String, confirmedTarget: ElevationTarget): ElevationOutcome {
        if (!initialized || closing != null || store.revocationPending) return ElevationOutcome.NOT_READY
        val before = store.read() ?: return ElevationOutcome.NOT_READY
        if (before.state.countdown?.id != id || ownedId != id) return ElevationOutcome.REFUSED
        val current = credentials() ?: return ElevationOutcome.NOT_READY
        if (before.person?.matches(current) != true || !battery().allowsDiscretionaryUnhide) return ElevationOutcome.REFUSED
        val packages = packages(confirmedTarget) ?: return ElevationOutcome.REFUSED
        val now = clock.checkpoint() ?: return ElevationOutcome.NOT_READY
        val proposal = ElevationMachine.start(before.state, id, confirmedTarget, limits(), now)
        if (proposal.outcome != ElevationOutcome.STARTED) {
            if (proposal.state !== before.state && !save(before, proposal.state, SessionJournal.Empty,
                    before.person.takeIf { proposal.state.countdown != null }, current)) return ElevationOutcome.NOT_READY
            return proposal.outcome
        }
        val grant = proposal.state.grant!!
        val kind = if (confirmedTarget == ElevationTarget.Play) WindowKind.PLAY else WindowKind.SETTINGS
        val access = SessionAccess(id, kind, packages, grant.deadline)
        val opening = SessionJournal.Active(access, SessionPhase.OPENING)
        if (!save(before, proposal.state, opening, before.person, current)) return ElevationOutcome.NOT_READY
        val saved = store.read() ?: return failStart(id)
        if (!live(saved) || !effects.open(access) || !effects.readBackPolicy() || !live(saved)) return failStart(id)
        if (!save(saved, saved.state, opening.copy(phase = SessionPhase.OPEN), saved.person, credentials())) return failStart(id)
        val opened = store.read() ?: return failStart(id)
        if (!live(opened)) return failStart(id)
        if (kind == WindowKind.PLAY) {
            val reading = clock.checkpoint() ?: return failStart(id)
            play = PlaySessionMachine.begin(access, reading.boot) ?: return failStart(id)
        }
        return ElevationOutcome.STARTED
    }

    /** Never resurrect access merely because storage contains an OPEN grant. */
    @Synchronized fun access(): SessionAccess? {
        if (!initialized || closing != null || store.revocationPending) return null
        val before = store.read() ?: return null
        val journal = before.journal as? SessionJournal.Active ?: return null
        if (journal.phase != SessionPhase.OPEN || !live(before)) return null
        return journal.access
    }

    @Synchronized fun tick(): ElevationOutcome {
        if (!initialized) return if (recoveryRequired) onStart() else ElevationOutcome.NOT_READY
        closing?.let { return close(it) }
        val before = store.read() ?: return onStart().let { ElevationOutcome.NOT_READY }
        if (store.revocationPending) {
            val id = before.state.grant?.id ?: before.state.countdown?.id
            return if (id == null) onStart() else close(Close(id, true, ElevationOutcome.ACCESS_CLOSED))
        }
        if (before.state.grant != null) {
            if (!live(before)) return close(Close(before.state.grant.id, false, ElevationOutcome.LAPSED))
            return ElevationOutcome.ACTIVE
        }
        val countdown = before.state.countdown ?: return ElevationOutcome.IDLE
        if (ownedId != countdown.id || before.person?.matches(credentials()) != true || packages(countdown.target) == null)
            return close(Close(countdown.id, false, ElevationOutcome.LAPSED))
        val proposal = ElevationMachine.tick(before.state, limits(), clock.checkpoint())
        if (proposal.outcome == ElevationOutcome.NOT_READY) return close(Close(countdown.id, false, ElevationOutcome.LAPSED))
        if (proposal.state !== before.state && !save(before, proposal.state, SessionJournal.Empty,
                before.person.takeIf { proposal.state.countdown != null }, null)) return ElevationOutcome.NOT_READY
        return proposal.outcome
    }

    @Synchronized fun cancel(id: String): ElevationOutcome {
        if (!initialized) return ElevationOutcome.NOT_READY
        val before = store.read() ?: return ElevationOutcome.NOT_READY
        if (before.state.countdown?.id != id) return ElevationOutcome.REFUSED
        return close(Close(id, false, ElevationOutcome.ENDED))
    }
    @Synchronized fun done(id: String): ElevationOutcome {
        if (!initialized) return ElevationOutcome.NOT_READY
        if (store.read()?.state?.grant?.id != id) return ElevationOutcome.REFUSED
        return close(Close(id, false, ElevationOutcome.ENDED))
    }
    @Synchronized fun endPackageAccess(id: String, reason: SessionEnd): ElevationOutcome {
        if (!initialized) return ElevationOutcome.NOT_READY
        val before = store.read() ?: return ElevationOutcome.NOT_READY
        if (before.state.grant?.id != id) return ElevationOutcome.REFUSED
        play = play?.let { PlaySessionMachine.end(it, id, reason) }
        val retain = reason != SessionEnd.DONE && reason != SessionEnd.DEADLINE
        return close(Close(id, retain, if (retain) ElevationOutcome.ACCESS_CLOSED else ElevationOutcome.ENDED))
    }

    /** Adapter delivers actual package-added events; identities are read fresh, never supplied by the UI. */
    @Synchronized fun installed(eventSessionId: String, packageName: PackageName): Boolean {
        val history = play ?: return false
        val access = access() ?: return false
        val identity = installedIdentity(packageName) ?: return false
        val now = clock.checkpoint() ?: return false
        val result = PlaySessionMachine.installed(history, eventSessionId, identity, now.boot, access)
        play = result.state
        return result.accepted
    }
    /** Display/review data only. Future approval integration must recheck this inside its final transaction. */
    @Synchronized fun candidate(packageName: PackageName): ApprovalBinding? {
        if (!initialized || closing != null || store.revocationPending) return null
        val before = store.read() ?: return null
        if (!live(before) || before.state.grant?.target != ElevationTarget.Play) return null
        val history = play?.takeIf { it.access.id == before.state.grant.id } ?: return null
        val candidate = PlaySessionMachine.candidate(history, packageName) ?: return null
        return candidate.takeIf { it == installedIdentity(packageName) }
    }

    private fun installedIdentity(packageName: PackageName): ApprovalBinding? {
        val input = policyInput() ?: return null
        if (input.installed.count { it.name == packageName } != 1) return null
        return input.installedIdentities?.filter { it.packageName == packageName }?.singleOrNull()
    }
    private fun supported(target: ElevationTarget) = target == ElevationTarget.Play || target == ElevationTarget.Settings
    private fun packages(target: ElevationTarget): Set<PackageName>? {
        if (!supported(target)) return null
        val input = policyInput() ?: return null
        if (input.lockedBoot || input.health != PolicyHealth.VALID || input.approvalState?.pending != null || input.waitingOperation?.phase in setOf(WaitingPhase.COMMITTING, WaitingPhase.ROLLING_BACK)) return null
        val inventory = input.inventory?.takeIf { it.model == input.model && it.fingerprint == input.fingerprint } ?: return null
        if (!EmergencyFloor.compute(input.installed, input.identities).hasSystemDialer) return null
        return sessionPackages(if (target == ElevationTarget.Play) WindowKind.PLAY else WindowKind.SETTINGS, inventory)
    }
    private fun live(record: ElevationRecord): Boolean {
        val grant = record.state.grant ?: return false
        if (ownedId != grant.id || record.person?.matches(credentials()) != true) return false
        val now = clock.checkpoint() ?: return false
        if (!grant.live(now.boot) || packages(grant.target) == null) return false
        val journal = record.journal as? SessionJournal.Active
        return !grant.packageAccessOpen || (journal != null && journal.access.packages == packages(grant.target) && battery().allowsDiscretionaryUnhide)
    }
    private fun failStart(id: String): ElevationOutcome {
        close(Close(id, false, ElevationOutcome.ENDED))
        return ElevationOutcome.NOT_READY
    }
    private fun close(proposed: Close): ElevationOutcome {
        // A later package-only end must never weaken a terminal Done/deadline already awaiting cleanup.
        val request = closing?.let { pending ->
            if (pending.id != proposed.id) return ElevationOutcome.NOT_READY
            if (!pending.retain) pending else proposed
        } ?: proposed
        store.revocationPending = true
        closing = request // Latch before reading/writing; a failed closing CAS cannot expose old OPEN access.
        val before = store.read() ?: return onStart().let { ElevationOutcome.NOT_READY }
        if (before.state.grant?.id != request.id && before.state.countdown?.id != request.id) {
            return onStart().let { ElevationOutcome.NOT_READY }
        }
        val retain = request.retain && live(before)
        // Persist the effective decision in the retry latch before CLOSING removes the live-access
        // preconditions (notably battery). Recovery of a failed effect must not resurrect this grant.
        val effective = request.copy(retain = retain,
            outcome = if (request.retain && !retain) ElevationOutcome.ENDED else request.outcome)
        closing = effective
        val state = if (retain) ElevationMachine.closePackageAccess(before.state, request.id).state else ElevationMachine.onRestart(before.state)
        val active = before.journal as? SessionJournal.Active
        // While closing, retain the grant data solely to validate the journal; no access() can return it.
        val closingState = if (active != null) ElevationMachine.closePackageAccess(before.state, request.id).state else state
        val journal = active?.copy(phase = SessionPhase.CLOSING) ?: SessionJournal.Empty
        if (!save(before, closingState, journal, before.person.takeIf { closingState.grant != null || closingState.countdown != null }, null)) {
            effects.revokeAllRelaxations(); return ElevationOutcome.NOT_READY
        }
        if (!effects.revokeAllRelaxations() || !effects.protectedStateCheck() || !effects.readBackPolicy()) return ElevationOutcome.NOT_READY
        val saved = store.read() ?: return ElevationOutcome.NOT_READY
        if (!save(saved, state, SessionJournal.Empty, before.person.takeIf { state.grant != null }, null)) return ElevationOutcome.NOT_READY
        if (!retain) { ownedId = null; play = null }
        closing = null; store.revocationPending = false
        return effective.outcome
    }
    private fun save(before: ElevationRecord, state: ElevationState, journal: SessionJournal,
        person: ElevationPersonBinding?, current: CredentialState?): Boolean {
        if (before.revision == Long.MAX_VALUE) return false
        return store.compareAndSet(before, before.replacing(state, journal, person), current)
    }
}
