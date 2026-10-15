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
abstract class ElevationStore(internal val waitingChanges: WaitingChangeStore? = null) {
    // One instance per combined record, shared by both controllers on the serialized coordinator.
    // Ordinary cleanup owns only its local latch. It cannot release the durable mode-operation gate.
    private var cleanupPending: Boolean = false
    internal var revocationPending: Boolean
        get() = cleanupPending || modeExecutionPending()
        set(value) { cleanupPending = value }

    /**
     * Read the durable source on every authority check, including before the first executor pass
     * after process start. COMMITTING/ROLLING_BACK mode operations hold the gate until a terminal
     * write. Missing/unreadable waiting data holds it until reconciliation; never assume vacancy.
     * Pending waits preserve normal session/elevation rules. Legacy stores without this binding
     * remain supported for other operations, but cannot be used by the mode executor.
     */
    private fun modeExecutionPending(): Boolean {
        val source = waitingChanges ?: return false
        val state = source.read() ?: return true
        val op = state.operation ?: return false
        return op.kind == WaitingChangeKind.SWITCH_MODE &&
            op.phase in setOf(WaitingPhase.COMMITTING, WaitingPhase.ROLLING_BACK)
    }
    /** Null means damaged/missing, including an invalid session scope. Only enrollment creates an empty ledger. */
    abstract fun read(): ElevationRecord?
    /**
     * Single atomic durable write of ledger, grant/countdown, person and the SHARED session journal.
     * Compare the whole record revision and, when supplied, credential revision in the same transaction.
     * Null expected is a repair CAS against the exact missing/damaged observation, never unconditional overwrite.
     * All JournalStore/credential/operation writers share this coordinator and preserve each other's fields.
     * False is not acknowledged (see [CredentialStore.compareAndSet]).
     */
    abstract fun compareAndSet(expected: ElevationRecord?, replacement: ElevationRecord, expectedCredentials: CredentialState?): Boolean
}
class ElevationRequest internal constructor(val id: String, val target: ElevationTarget,
    internal val person: ElevationPersonBinding, internal val boot: BootTime) {
    val action = PrivilegedAction("elevation:request", id)
}

/**
 * Serialized, non-reentrant coordinator shared with PinVault, session, waiting, approval and updater writes.
 * Play/Approve consumption requires the optional combined approval store. Update remains unavailable
 * until both the policy protocol and device adapter pass cross-model review. Candidate data is not an approval capability.
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
    private val approvalStore: ElevationApprovalStore? = null,
) {
    // No public configuration enables this seam. Only the policy module's protocol tests wire it.
    internal var updateProtocol: UpdateProtocol? = null
    private var submittingUpdate = false
    private val supervisedUpdates = mutableSetOf<SupervisedUpdateRequest>()
    private var initialized = false
    private var recoveryRequired = false
    private var ownedId: String? = null
    private val requests = mutableSetOf<ElevationRequest>()
    private var play: PlaySessionState? = null
    private val previewApproved = mutableSetOf<Pair<String, PackageName>>()
    private data class Close(val id: String, val retain: Boolean, val outcome: ElevationOutcome)
    private var closing: Close? = null

    /** Before any access or other controller work, including Direct Boot. No credential read is needed. */
    @Synchronized fun onStart(): ElevationOutcome {
        updateProtocol?.invalidate(); supervisedUpdates.clear()
        previewApproved.clear()
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
        if (target is ElevationTarget.Update && updateProtocol?.available() == null) return null
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
        if (confirmedTarget is ElevationTarget.Approve || confirmedTarget is ElevationTarget.Update) {
            if (!save(before, proposal.state, SessionJournal.Empty, before.person, current)) return ElevationOutcome.NOT_READY
            return ElevationOutcome.STARTED
        }
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
    /** Display data only; the commit path independently checks identity and the grant. */
    @Synchronized fun candidates(): List<ApprovalBinding> = play?.candidates?.keys.orEmpty().mapNotNull(::candidate)

    /** Preview membership is the approval record. The adapter writes it atomically on the same
     * non-reentrant dispatcher and invokes finalCheck after staging/fsync, immediately before rename.
     * No authority is returned to UI, persisted or restored. A Play grant remains reusable for other
     * candidates; the membership record survives restart, while this grant and intake never do.
     */
    @Synchronized fun commitPlayApproval(identity: ApprovalBinding, write: ((() -> Boolean) -> Boolean)): Boolean {
        val grantId = store.read()?.state?.grant?.id ?: return false
        fun valid(): Boolean {
            val record = store.read() ?: return false
            return initialized && closing == null && !store.revocationPending &&
                record.state.grant?.id == grantId && record.state.grant?.target == ElevationTarget.Play &&
                record.state.grant?.packageAccessOpen == false && record.journal == SessionJournal.Empty &&
                (grantId to identity.packageName) !in previewApproved &&
                approvalMatches(record, identity) && live(record)
        }
        if (!valid()) return false
        var checked = false
        var accepted = false
        val saved = write {
            if (checked) false else { checked = true; accepted = valid(); accepted }
        }
        if (!saved || !checked || !accepted) return false
        previewApproved += grantId to identity.packageName
        return true
    }

    @Synchronized fun candidate(packageName: PackageName): ApprovalBinding? {
        if (!initialized || closing != null || store.revocationPending) return null
        val before = store.read() ?: return null
        if (!live(before) || before.state.grant?.target != ElevationTarget.Play) return null
        val history = play?.takeIf { it.access.id == before.state.grant.id } ?: return null
        val candidate = PlaySessionMachine.candidate(history, packageName) ?: return null
        return candidate.takeIf { it == installedIdentity(packageName) &&
            (before.state.grant.id to packageName) !in previewApproved }
    }

    /** No caller-supplied grant, person, session history or candidate ID enters this path. */
    @Synchronized internal fun prepareApproval(identity: ApprovalBinding, approvals: AppApprovalStore): ApprovalElevation? {
        if (approvals !== approvalStore || approvalStore.elevationStore !== store ||
            !initialized || closing != null || store.revocationPending) return null
        val before = store.read() ?: return null
        if (!live(before) || !approvalMatches(before, identity)) return null
        return ApprovalElevation(before.state.grant!!.id, identity, before.person!!)
    }

    /**
     * True when package access is closed and the grant still matches. False refuses: the grant no
     * longer matches or is not live. Null is not ready: the close was not acknowledged (its write may
     * have landed, PLA-160) or the record could not be read, so the caller reads again next time.
     */
    @Synchronized internal fun closeForApproval(binding: ApprovalElevation): Boolean? {
        if (!initialized || closing != null || store.revocationPending) return null
        val before = store.read() ?: return null
        if (!matchesApproval(before, binding) || !live(before)) return false
        if (before.state.grant!!.packageAccessOpen) {
            when (endPackageAccess(binding.grantId, SessionEnd.LEFT_PACKAGES)) {
                ElevationOutcome.ACCESS_CLOSED -> Unit
                ElevationOutcome.NOT_READY -> return null
                else -> return false
            }
        }
        val closed = store.read() ?: return null
        return closed.journal == SessionJournal.Empty && matchesApproval(closed, binding) && live(closed)
    }

    /** Called only by AppApprovalController after its permission transaction is PREPARING. */
    @Synchronized internal fun commitApproval(binding: ApprovalElevation, approvals: AppApprovalStore,
        preparing: AppApprovalState, committed: AppApprovalState, current: CredentialState,
        finalChecks: () -> Boolean): Boolean {
        val port = approvalStore ?: return false
        if (port !== approvals || port.elevationStore !== store || !initialized || closing != null || store.revocationPending) return false
        val before = store.read() ?: return false
        if (before.revision == Long.MAX_VALUE || before.journal != SessionJournal.Empty ||
            before.state.grant?.packageAccessOpen != false || !matchesApproval(before, binding) ||
            !live(before, preparing) || !binding.person.matches(current)) return false
        val proposal = ElevationMachine.consume(before.state, binding.grantId,
            ElevationAction.Approve(binding.identity, binding.grantId), clock.checkpoint())
        if (proposal.outcome != ElevationOutcome.CONSUMED) return false
        val next = before.replacing(proposal.state, person = before.person.takeIf { proposal.state.grant != null })
        var checked = false
        return port.commitElevationApproval(preparing, committed, before, next, current) {
            // Adapter invokes inside its serialized transaction, immediately before the joint write.
            if (checked) false else {
                checked = true
                finalChecks() && !store.revocationPending && closing == null &&
                    matchesApproval(before, binding) && live(before, preparing)
            }
        } && checked
    }

    /** Last journaled Update self-check failed. Read-only; the adapter owns UI and notice delivery. */
    @Synchronized fun updateNeedsAttention(): Boolean = updateProtocol?.needsAttention() == true

    /** The caller supplies no grant, grant ID, session ID or staged facts. */
    @Synchronized fun submitUpdate(): UpdateOutcome {
        val protocol = updateProtocol ?: return UpdateOutcome.REFUSED
        if (!updateReady(protocol)) return UpdateOutcome.REFUSED
        submittingUpdate = true
        try {
            val before = store.read() ?: return UpdateOutcome.REFUSED
            val grant = before.state.grant ?: return UpdateOutcome.REFUSED
            val target = grant.target as? ElevationTarget.Update ?: return UpdateOutcome.REFUSED
            if (ownedId != grant.id || before.revision == Long.MAX_VALUE || !live(before) ||
                before.journal != SessionJournal.Empty || !closeUpdatePackages()) return UpdateOutcome.REFUSED
            val current = credentials() ?: return UpdateOutcome.REFUSED
            val update = protocol.available() ?: return UpdateOutcome.REFUSED
            val facts = protocol.installer.finalFacts() ?: return UpdateOutcome.REFUSED
            val proposal = UpdateCommitPolicy.propose(before, grant.id, current, protocol.pinnedSelf,
                facts.installedVersionCode, facts.staged, clock.checkpoint())
            if (proposal.outcome != ElevationOutcome.CONSUMED) return UpdateOutcome.REFUSED
            val next = before.replacing(proposal.state, person = null)
            val actor = before.person!!
            val receipt = UpdateReceipt(grant.id, actor.person, actor.generation, current.people.mapValues { it.value.generation })
            val submission = UpdateSubmission(facts.sessionId, target.staged.sha256, target.staged.versionCode, receipt, grant.deadline)
            var checks = 0
            var passed = false
            val committed = protocol.store.commitUpdateUse(before, next, current, update, submission) {
                checks++
                if (checks != 1) false else {
                    // Hash again after the store's blocking work; then check live ownership/time.
                    val finalFacts = protocol.installer.finalFacts()
                    passed = finalFacts == facts && updateReady(protocol, inFlight = true) &&
                        ownedId == grant.id && live(before) &&
                        UpdateCommitPolicy.propose(before, grant.id, credentials(), protocol.pinnedSelf,
                            finalFacts!!.installedVersionCode, finalFacts.staged, clock.checkpoint()).outcome == ElevationOutcome.CONSUMED
                    passed
                }
            }
            if (!committed && checks == 1 && passed) {
                // Not acknowledged after a passing final check: SUBMITTED may have landed (PLA-160).
                // Same as an ambiguous storage failure: no retry path; startup reads what landed.
                ownedId = null; protocol.invalidate()
                return UpdateOutcome.NOT_READY
            }
            if (!committed || checks != 1 || !passed) return UpdateOutcome.REFUSED
            ownedId = null // Process-local invalidation only; no adapter work between joint use and commit.
            return protocol.commit(submission)
        } catch (_: Exception) {
            // An ambiguous storage failure cannot leave a process-local retry path.
            ownedId = null; protocol.invalidate()
            return UpdateOutcome.NOT_READY
        } finally { submittingUpdate = false }
    }

    @Synchronized fun prepareSupervisedUpdate(person: TrustedPersonId, staged: StagedUpdateBinding): SupervisedUpdateRequest? {
        val protocol = updateProtocol ?: return null
        if (!updateReady(protocol)) return null
        val current = credentials() ?: return null
        if (current.mode != Mode.TRUSTED) return null
        val credential = current.people[person] ?: return null
        val before = store.read() ?: return null
        if (before.state.countdown != null || before.state.grant != null || before.revision == Long.MAX_VALUE ||
            before.journal != SessionJournal.Empty || packages(ElevationTarget.Update(staged)) == null ||
            protocol.available() == null) return null
        // An open supervised session must finish through SessionController's normal CLOSING path.
        val now = clock.checkpoint() ?: return null
        val id = ids.next().takeIf { it.isNotBlank() } ?: return null
        return SupervisedUpdateRequest(id, staged, person, credential.generation, current.structureGeneration, now.boot)
            .also { supervisedUpdates.add(it) }
    }

    @Synchronized fun submitSupervisedUpdate(request: SupervisedUpdateRequest, fresh: FreshAuthority): UpdateOutcome {
        val protocol = updateProtocol ?: return UpdateOutcome.REFUSED
        if (!updateReady(protocol) || !supervisedUpdates.remove(request)) return UpdateOutcome.REFUSED
        submittingUpdate = true
        try {
            val before = store.read() ?: return UpdateOutcome.REFUSED
            val current = credentials() ?: return UpdateOutcome.REFUSED
            if (!supervisedUpdateLive(request, before, current) || !closeUpdatePackages()) return UpdateOutcome.REFUSED
            val update = protocol.available() ?: return UpdateOutcome.REFUSED
            val facts = protocol.installer.finalFacts() ?: return UpdateOutcome.REFUSED
            if (!protocol.valid(facts, request.staged)) return UpdateOutcome.REFUSED
            val receipt = UpdateReceipt(request.id, request.person, request.generation, current.people.mapValues { it.value.generation })
            val submission = UpdateSubmission(facts.sessionId, request.staged.sha256, request.staged.versionCode, receipt)
            var checks = 0
            var passed = false
            val committed = protocol.store.commitUpdateUse(before, before, current, update, submission) {
                checks++
                if (checks != 1) false else {
                    val finalFacts = protocol.installer.finalFacts()
                    passed = finalFacts == facts && protocol.valid(finalFacts, request.staged) &&
                        updateReady(protocol, inFlight = true) && supervisedUpdateLive(request, before, credentials()) &&
                        authority.consumeFor(fresh, request.action, request.person) &&
                        supervisedUpdateLive(request, before, credentials())
                    passed
                }
            }
            if (!committed && checks == 1 && passed) {
                // Not acknowledged after the PIN was used: SUBMITTED may have landed (PLA-160).
                protocol.invalidate(); supervisedUpdates.clear()
                return UpdateOutcome.NOT_READY
            }
            if (!committed || checks != 1 || !passed) return UpdateOutcome.REFUSED
            supervisedUpdates.clear()
            return protocol.commit(submission)
        } catch (_: Exception) {
            protocol.invalidate(); supervisedUpdates.clear()
            return UpdateOutcome.NOT_READY
        } finally { submittingUpdate = false }
    }

    private fun updateReady(protocol: UpdateProtocol, inFlight: Boolean = false) =
        updateProtocol === protocol && protocol.ready && protocol.store.elevationStore === store &&
            initialized && closing == null && !store.revocationPending && (inFlight || !submittingUpdate)

    private fun closeUpdatePackages(): Boolean {
        store.revocationPending = true
        if (!effects.revokeAllRelaxations() || !effects.protectedStateCheck() || !effects.readBackPolicy()) return false
        store.revocationPending = false
        return !store.revocationPending
    }

    private fun supervisedUpdateLive(request: SupervisedUpdateRequest, before: ElevationRecord, current: CredentialState?): Boolean {
        if (!request.matches(current) || before.state.countdown != null || before.state.grant != null ||
            before.journal != SessionJournal.Empty || packages(ElevationTarget.Update(request.staged)) == null) return false
        val now = clock.checkpoint() ?: return false
        return now.boot.bootId == request.boot.bootId && now.boot.elapsedMillis >= request.boot.elapsedMillis
    }

    private fun matchesApproval(record: ElevationRecord, binding: ApprovalElevation): Boolean =
        record.state.grant?.id == binding.grantId && record.person == binding.person && approvalMatches(record, binding.identity)

    private fun approvalMatches(record: ElevationRecord, identity: ApprovalBinding): Boolean {
        val grant = record.state.grant ?: return false
        if (identity != installedIdentity(identity.packageName)) return false
        return when (val target = grant.target) {
            ElevationTarget.Play -> play?.takeIf { it.access.id == grant.id }?.let {
                PlaySessionMachine.candidate(it, identity.packageName) == identity &&
                    grant.approved.none { approved -> approved.packageName == identity.packageName }
            } == true
            is ElevationTarget.Approve -> target.identity == identity
            else -> false
        }
    }

    private fun installedIdentity(packageName: PackageName): ApprovalBinding? {
        val input = policyInput() ?: return null
        if (input.installed.count { it.name == packageName } != 1) return null
        return input.installedIdentities?.filter { it.packageName == packageName }?.singleOrNull()
    }
    private fun supported(target: ElevationTarget) = target == ElevationTarget.Play || target == ElevationTarget.Settings ||
        (target is ElevationTarget.Approve && approvalStore?.elevationStore === store) ||
        (target is ElevationTarget.Update && updateProtocol?.let { it.ready && it.store.elevationStore === store } == true)
    private fun packages(target: ElevationTarget, preparing: AppApprovalState? = null): Set<PackageName>? {
        if (!supported(target)) return null
        val input = policyInput() ?: return null
        if (input.lockedBoot || input.health != PolicyHealth.VALID ||
            (input.approvalState?.pending != null && !ownedPreparing(input.approvalState, preparing)) ||
            input.waitingOperation?.phase in setOf(WaitingPhase.COMMITTING, WaitingPhase.ROLLING_BACK)) return null
        if (!EmergencyFloor.compute(input.installed, input.identities).hasSystemDialer) return null
        // Play has one fixed package and its journal can be revoked without an inventory.
        // Other targets still require an exact reviewed build; never derive one from live facts.
        if (target == ElevationTarget.Play) return sessionPackages(WindowKind.PLAY, null)
        val inventory = input.inventory?.takeIf { it.model == input.model && it.fingerprint == input.fingerprint } ?: return null
        if (target is ElevationTarget.Update) return emptySet()
        if (target is ElevationTarget.Approve) {
            if (installedIdentity(target.identity.packageName) != target.identity) return null
            val plan = PolicyEngine.decide(input.copy(sessionAccess = null))
            return emptySet<PackageName>().takeIf { target.identity.packageName in plan.hidden &&
                target.identity.packageName !in plan.neverHide }
        }
        return sessionPackages(if (target == ElevationTarget.Play) WindowKind.PLAY else WindowKind.SETTINGS, inventory)
    }
    private fun ownedPreparing(actual: AppApprovalState, expected: AppApprovalState?): Boolean {
        val op = actual.pending ?: return false
        val own = expected?.pending ?: return false
        return actual.revision == expected.revision && op.phase == AppApprovalPhase.PREPARING &&
            own.phase == AppApprovalPhase.PREPARING && op.record.id == own.record.id &&
            op.record.permissions.manifest.sameFacts(own.record.permissions.manifest) &&
            op.record.permissions.choices == own.record.permissions.choices && op.receipt?.id == own.receipt?.id &&
            op.receipt?.actor == own.receipt?.actor && op.receipt?.recipientGenerations == own.receipt?.recipientGenerations
    }
    private fun live(record: ElevationRecord, preparing: AppApprovalState? = null): Boolean {
        val grant = record.state.grant ?: return false
        if (ownedId != grant.id || record.person?.matches(credentials()) != true) return false
        val packages = packages(grant.target, preparing) ?: return false
        val journal = record.journal as? SessionJournal.Active
        if (grant.packageAccessOpen && (journal == null || journal.access.packages != packages || !battery().allowsDiscretionaryUnhide)) return false
        // Read the deadline clock after any package/battery sources that can delay this check.
        val now = clock.checkpoint() ?: return false
        return grant.live(now.boot)
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
