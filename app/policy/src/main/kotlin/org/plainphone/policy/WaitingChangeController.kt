// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*

/** Requires the grant's actual PIN holder to match the recorded starter dependency. */
fun interface PersonAuthorityConsumer {
    fun consumeFor(authority: FreshAuthority, action: PrivilegedAction, person: TrustedPersonId): Boolean
}
class WaitingIdentity(val requestId: String, val cancelCode: String) {
    init { require(requestId.isNotBlank() && cancelCode.isNotBlank()) }
    override fun toString(): String = "WaitingIdentity(redacted)"
}
/** Reserve IDs and cancel codes durably; neither may ever be reused, including after abandoned preparation. */
fun interface WaitingIdentitySource { fun reserve(): WaitingIdentity }

enum class WaitingEvent { STARTED, CANCELLED_ON_PHONE, DEPENDENCY_CHANGED, COMMITTING, COMPLETED, SKIPPED }
class WaitingText(val key: String, val event: WaitingEvent, val operation: WaitingOperation,
    recipientGenerations: Map<TrustedPersonId, Long>,
    recipientContacts: Map<TrustedPersonId, PersonContact?> = recipientGenerations.keys.associateWith { null }) {
    val recipientGenerations: Map<TrustedPersonId, Long> = java.util.Collections.unmodifiableMap(LinkedHashMap(recipientGenerations))
    val recipients = immutableSet(recipientGenerations.keys)
    /** Null is an explicit unavailable delivery address; never look it up from a later holder record. */
    val recipientContacts: Map<TrustedPersonId, PersonContact?> = java.util.Collections.unmodifiableMap(LinkedHashMap(recipientContacts))
    init { require(recipientContacts.keys == recipientGenerations.keys) }
}
/** Immutable turn-off or people operation. A people patch never authorizes turn-off effects. */
class WaitingOperation(
    val identity: WaitingIdentity, val starter: TrustedPersonId, val generation: Long,
    val structureGeneration: Long, val mode: Mode, val startedAt: RunningTime, val phase: WaitingPhase,
    recipientGenerations: Map<TrustedPersonId, Long>,
    val restoredSteps: Int = 0,
    val skipper: TrustedPersonId? = null,
    val skipperGeneration: Long? = null,
    val peoplePatch: TrustedPersonPatch? = null,
    recipientContacts: Map<TrustedPersonId, PersonContact?> = recipientGenerations.keys.associateWith { null },
) {
    init {
        require(generation >= 0 && structureGeneration >= 0)
        require((skipper == null) == (skipperGeneration == null))
        require(skipperGeneration == null || (skipperGeneration >= 1 && mode == Mode.TRUSTED &&
            phase in setOf(WaitingPhase.COMMITTING, WaitingPhase.COMPLETED, WaitingPhase.ROLLING_BACK, WaitingPhase.CANCELLED)))
        require(restoredSteps in 0..TurnOffStep.entries.size)
        require(restoredSteps == 0 || phase == WaitingPhase.COMMITTING || phase == WaitingPhase.COMPLETED)
        require(recipientGenerations.isNotEmpty() && recipientGenerations.values.all { it >= 0 })
        require(recipientContacts.keys == recipientGenerations.keys)
        require(peoplePatch == null || (mode == Mode.TRUSTED && restoredSteps == 0 &&
            peoplePatch.starter == starter && peoplePatch.structureGeneration == structureGeneration &&
            peoplePatch.dependencyGenerations[starter] == generation))
    }
    /** Start-time fallback retained independently of acknowledged outbox texts. */
    val recipientGenerations: Map<TrustedPersonId, Long> = java.util.Collections.unmodifiableMap(LinkedHashMap(recipientGenerations))
    val recipientContacts: Map<TrustedPersonId, PersonContact?> = java.util.Collections.unmodifiableMap(LinkedHashMap(recipientContacts))
    val kind: WaitingChangeKind get() = peoplePatch?.kind ?: WaitingChangeKind.TURN_OFF
    val authorityPerson: TrustedPersonId get() = skipper ?: starter
    val authorityGeneration: Long get() = skipperGeneration ?: generation
    /** A skipper replaces starter authority, but never drops a slot the patch removes/replaces. */
    val dependencyGenerations: Map<TrustedPersonId, Long> get() {
        val dependencies = (peoplePatch?.dependencyGenerations ?: mapOf(starter to generation)).toMutableMap()
        if (skipper != null) {
            if (starter !in peoplePatch?.removedSlots.orEmpty()) dependencies.remove(starter)
            dependencies[skipper] = skipperGeneration!!
        }
        return java.util.Collections.unmodifiableMap(dependencies)
    }
    internal fun dependenciesMatch(current: CredentialState): Boolean = current.mode == mode &&
        current.structureGeneration == structureGeneration &&
        dependencyGenerations.all { (person, generation) -> current.people[person]?.generation == generation } &&
        (peoplePatch == null || current.recoveryProfile != null)
    internal fun withSkipper(person: TrustedPersonId, generation: Long) = WaitingOperation(identity, starter, this.generation,
        structureGeneration, mode, startedAt, WaitingPhase.COMMITTING, recipientGenerations, 0, person, generation, peoplePatch, recipientContacts)
    internal fun withPhase(phase: WaitingPhase) = WaitingOperation(identity, starter, generation, structureGeneration, mode, startedAt, phase, recipientGenerations, restoredSteps, skipper, skipperGeneration, peoplePatch, recipientContacts)
    internal fun withRestoredSteps(count: Int) = WaitingOperation(identity, starter, generation, structureGeneration, mode, startedAt, phase, recipientGenerations, count, skipper, skipperGeneration, peoplePatch, recipientContacts)
}
class WaitingState(val revision: Long = 0, val operation: WaitingOperation? = null, texts: List<WaitingText> = emptyList(),
    val storageGeneration: String = "initial", recoveryReceipts: List<RecoveryReceipt> = emptyList()) {
    init { require(revision >= 0 && storageGeneration.isNotBlank()) }
    val texts = immutableList(texts)
    val recoveryReceipts = immutableList(recoveryReceipts)
    internal fun replacing(revision: Long, operation: WaitingOperation? = this.operation,
        texts: List<WaitingText> = this.texts, recoveryReceipts: List<RecoveryReceipt> = this.recoveryReceipts) =
        WaitingState(revision, operation, texts, storageGeneration, recoveryReceipts)
}
interface WaitingChangeStore {
    /** Highest-revision valid checksum copy in the current storage generation. null means damaged, not vacant. */
    fun read(): WaitingState?
    /** Atomically persist operation + texts; compare storage generation AND durable revision; preserve generation and increase revision on every write. */
    fun compareAndSet(expected: WaitingState, replacement: WaitingState): Boolean
}
/** UNKNOWN includes missing, corrupt or temporarily unreadable data; it never authorizes commit. */
enum class CancelIntent { CLEAR, REQUESTED, UNKNOWN }
/**
 * Separate durable, device-protected intent journal, serialized with operation writes.
 * True means the write survived a crash (including directory metadata); failures return false.
 * Request IDs are never reused. REQUESTED is monotonic and initialize must never clear it.
 */
interface CancelIntentStore {
    /** Establish a known CLEAR record before starting a new operation; false if already REQUESTED. */
    fun initialize(requestId: String): Boolean
    /** Persist REQUESTED even when the operation record is temporarily unreadable. Idempotent. */
    fun request(requestId: String): Boolean
    fun read(requestId: String): CancelIntent
}

/** Opaque, process-local preparation. Never deserialize it or treat it as authority. */
class WaitingStart internal constructor(internal val identity: WaitingIdentity, internal val starter: TrustedPersonId,
    internal val generation: Long, internal val structureGeneration: Long, internal val mode: Mode,
    internal val peoplePatch: TrustedPersonPatch? = null, internal val owner: Any? = null) {
    val requestId: String get() = identity.requestId
    val action = PrivilegedAction(peoplePatch?.let { "waiting:${it.kind.name}" } ?: "waiting:turn-off",
        "${identity.requestId.length}:${identity.requestId}${starter.value.length}:${starter.value}")
    internal var used = false
}
enum class WaitingOutcome { PENDING, COMMITTING, CANCELLED, REFUSED, BUSY, NOT_READY, LOW_BATTERY, ALREADY_IN_PROGRESS, IGNORED }

/**
 * The common serialized dispatcher owns this controller, PinVault and all credential/operation writes.
 * This controller starts turn-off/people waits and skips through explicitly wired recovery transactions.
 * It never restores settings, clears Device Owner, applies people/mode patches or reports Off.
 * Damaged state remains held until WaitingReconciler completes.
 */
class WaitingChangeController(
    private val store: WaitingChangeStore,
    private val credentials: () -> CredentialState?,
    private val clock: RunningClock,
    private val authority: PersonAuthorityConsumer,
    private val identities: WaitingIdentitySource,
    private val cancelIntents: CancelIntentStore,
    private val skipSupport: WaitingSkipSupport? = null,
    private val handOverSupport: WaitingHandOverSupport? = null,
) {
    private val cancellationRequests = mutableSetOf<String>()
    private val preparationOwner = Any()

    @Synchronized
    fun prepareTurnOff(starter: TrustedPersonId): WaitingStart? {
        val current = credentials() ?: return null
        val person = current.people[starter] ?: return null
        return WaitingStart(identities.reserve(), starter, person.generation, current.structureGeneration, current.mode, owner = preparationOwner)
    }

    @Synchronized
    fun prepareAdd(starter: TrustedPersonId, incoming: IncomingPersonEnrollment): WaitingStart? =
        credentials()?.let { TrustedPersonPatch.add(it, starter, incoming) }?.let(::preparePeople)

    @Synchronized
    fun prepareHandOver(outgoing: TrustedPersonId, incoming: IncomingPersonEnrollment): WaitingStart? =
        credentials()?.let { TrustedPersonPatch.handOver(it, outgoing, incoming) }?.let(::preparePeople)

    @Synchronized
    fun prepareRemove(starter: TrustedPersonId, removed: TrustedPersonId): WaitingStart? =
        credentials()?.let { TrustedPersonPatch.remove(it, starter, removed) }?.let(::preparePeople)

    private fun preparePeople(patch: TrustedPersonPatch) = WaitingStart(identities.reserve(), patch.starter,
        patch.dependencyGenerations.getValue(patch.starter), patch.structureGeneration, Mode.TRUSTED, patch, preparationOwner)

    @Synchronized
    fun startTurnOff(request: WaitingStart, freshAuthority: FreshAuthority, firstConfirmation: Boolean, secondConfirmation: Boolean): WaitingOutcome {
        if (!firstConfirmation || !secondConfirmation || request.peoplePatch != null) return WaitingOutcome.REFUSED
        return start(request, freshAuthority)
    }

    @Synchronized
    fun startPeopleChange(request: WaitingStart, freshAuthority: FreshAuthority, confirmed: Boolean): WaitingOutcome {
        if (!confirmed || request.peoplePatch == null) return WaitingOutcome.REFUSED
        return start(request, freshAuthority)
    }

    private fun start(request: WaitingStart, freshAuthority: FreshAuthority): WaitingOutcome {
        if (request.used || request.owner !== preparationOwner) return WaitingOutcome.REFUSED
        val before = store.read() ?: return WaitingOutcome.NOT_READY
        reconcileCancellations(before)
        if (before.operation?.phase in setOf(WaitingPhase.PENDING, WaitingPhase.COMMITTING, WaitingPhase.ROLLING_BACK)) return WaitingOutcome.BUSY
        val current = credentials() ?: return WaitingOutcome.NOT_READY
        if (!startDependenciesMatch(request, current)) return WaitingOutcome.REFUSED
        val now = clock.checkpoint() ?: return WaitingOutcome.NOT_READY
        if (!authority.consumeFor(freshAuthority, request.action, request.starter)) return WaitingOutcome.REFUSED
        request.used = true // Fresh authorization and preparation cannot be replayed after a failed write.
        if (!cancelIntents.initialize(request.requestId)) return WaitingOutcome.NOT_READY
        val operation = WaitingOperation(request.identity, request.starter, request.generation,
            request.structureGeneration, request.mode, now.proven, WaitingPhase.PENDING, current.people.mapValues { it.value.generation },
            peoplePatch = request.peoplePatch, recipientContacts = current.contacts())
        return if (write(before, operation, WaitingEvent.STARTED, current.people.mapValues { it.value.generation }, current.contacts())) WaitingOutcome.PENDING else WaitingOutcome.NOT_READY
    }

    private fun startDependenciesMatch(request: WaitingStart, current: CredentialState): Boolean =
        current.mode == request.mode && current.structureGeneration == request.structureGeneration &&
            current.people[request.starter]?.generation == request.generation &&
            (request.peoplePatch?.dependenciesMatch(current) != false)

    /**
     * Explicit outgoing-code alternative: the screen names this exact handover and confirms both
     * starting and skipping. Recovery, STARTED/SKIPPED receipts and COMMITTING are one transaction.
     * An ordinary forgotten-PIN recovery never calls this method and never starts/skips anything.
     */
    @Synchronized
    fun startHandOverWithRecovery(request: WaitingStart, code: CharArray, newPin: CharArray,
        confirmed: Boolean, skipConfirmed: Boolean): WaitingSkipResult {
        val support = handOverSupport ?: return WaitingSkipResult.NotSkipped(WaitingOutcome.REFUSED)
        if (!confirmed || !skipConfirmed || request.peoplePatch?.kind != WaitingChangeKind.HAND_OVER ||
            request.used || request.owner !== preparationOwner || newPin.size < 6 || newPin.any { it !in '0'..'9' }) {
            return WaitingSkipResult.NotSkipped(WaitingOutcome.REFUSED)
        }
        val before = store.read() ?: return WaitingSkipResult.NotSkipped(WaitingOutcome.NOT_READY)
        if (before.operation?.phase in activePhases) return WaitingSkipResult.NotSkipped(WaitingOutcome.BUSY)
        val current = credentials() ?: return WaitingSkipResult.NotSkipped(WaitingOutcome.NOT_READY)
        if (!startDependenciesMatch(request, current)) return WaitingSkipResult.NotSkipped(WaitingOutcome.REFUSED)
        if (!cancelIntents.initialize(request.requestId)) return WaitingSkipResult.NotSkipped(WaitingOutcome.NOT_READY)
        val result = support.vault.recoverWithCommit(request.starter, code, newPin) { oldCredentials, newCredentials ->
            val latest = store.read()
            val now = clock.checkpoint()
            if (latest == null || now == null || latest.storageGeneration != before.storageGeneration ||
                latest.revision != before.revision || latest.operation?.phase in activePhases ||
                credentials()?.revision != oldCredentials.revision || !startDependenciesMatch(request, oldCredentials) ||
                cancelIntents.read(request.requestId) != CancelIntent.CLEAR) false
            else {
                val revision = nextRevision(latest)
                if (revision == null) false else {
                    request.used = true
                    val started = WaitingOperation(request.identity, request.starter, request.generation, request.structureGeneration,
                        request.mode, now.proven, WaitingPhase.PENDING, oldCredentials.people.mapValues { it.value.generation },
                        peoplePatch = request.peoplePatch, recipientContacts = oldCredentials.contacts())
                    val committing = started.withSkipper(request.starter, newCredentials.people.getValue(request.starter).generation)
                    val key = "${request.requestId.length}:${request.requestId}:$revision"
                    val texts = listOf(
                        WaitingText("$key:started", WaitingEvent.STARTED, started, started.recipientGenerations, started.recipientContacts),
                        WaitingText("$key:skipped", WaitingEvent.SKIPPED, committing,
                            newCredentials.people.mapValues { it.value.generation }, newCredentials.contacts()),
                    )
                    support.committer.commit(oldCredentials, newCredentials, latest,
                        latest.replacing(revision, committing, latest.texts + texts))
                }
            }
        }
        return skipResult(result, WaitingOutcome.NOT_READY)
    }

    @Synchronized
    fun cancel(requestId: String): WaitingOutcome {
        // Keep each intent until a valid record can identify the pending request.
        cancellationRequests.add(requestId)
        // Still attempt the restrictive operation write if this independent write fails.
        cancelIntents.request(requestId)
        val before = store.read() ?: return WaitingOutcome.NOT_READY
        reconcileCancellations(before)
        val operation = before.operation ?: return WaitingOutcome.IGNORED
        if (operation.identity.requestId != requestId) return WaitingOutcome.IGNORED
        if (operation.phase != WaitingPhase.PENDING) return phaseResult(operation.phase)
        return cancelPending(before, operation, WaitingEvent.CANCELLED_ON_PHONE)
    }

    @Synchronized
    fun tick(requestId: String, battery: BatteryState): WaitingOutcome {
        val before = store.read() ?: return WaitingOutcome.NOT_READY
        reconcileCancellations(before)
        val operation = before.operation ?: return WaitingOutcome.IGNORED
        if (operation.identity.requestId != requestId) return WaitingOutcome.IGNORED
        if (operation.phase != WaitingPhase.PENDING) return phaseResult(operation.phase)
        val intent = cancelIntents.read(requestId)
        if (requestId in cancellationRequests || intent == CancelIntent.REQUESTED) {
            // Retry the independent journal as well while the process-local latch is available.
            cancelIntents.request(requestId)
            return cancelPending(before, operation, WaitingEvent.CANCELLED_ON_PHONE)
        }
        val current = credentials() ?: return WaitingOutcome.NOT_READY
        if (!operation.dependenciesMatch(current)) {
            return cancelPending(before, operation, WaitingEvent.DEPENDENCY_CHANGED)
        }
        if (intent == CancelIntent.UNKNOWN) return WaitingOutcome.NOT_READY
        val now = clock.checkpoint() ?: return WaitingOutcome.NOT_READY
        if (now.proven.since(operation.startedAt) < 86_400_000L) return WaitingOutcome.PENDING
        if (operation.kind == WaitingChangeKind.TURN_OFF && !battery.allowsDiscretionaryUnhide) return WaitingOutcome.LOW_BATTERY
        return if (write(before, operation.withPhase(WaitingPhase.COMMITTING), WaitingEvent.COMMITTING,
                current.people.mapValues { it.value.generation }, current.contacts()))
            WaitingOutcome.COMMITTING else WaitingOutcome.NOT_READY
    }

    /** Exact request shown and confirmed on the skip screen, never ordinary forgotten-PIN recovery. */
    @Synchronized
    fun skip(requestId: String, skipper: TrustedPersonId, code: CharArray, newPin: CharArray,
        confirmed: Boolean, battery: BatteryState): WaitingSkipResult {
        val support = skipSupport ?: return WaitingSkipResult.NotSkipped(WaitingOutcome.REFUSED)
        if (!confirmed || newPin.size < 6 || newPin.any { it !in '0'..'9' }) return WaitingSkipResult.NotSkipped(WaitingOutcome.REFUSED)
        val before = store.read() ?: return WaitingSkipResult.NotSkipped(WaitingOutcome.NOT_READY)
        skipBlocker(before, requestId, skipper, battery)?.let { return WaitingSkipResult.NotSkipped(it) }
        var rejected = WaitingOutcome.NOT_READY
        val result = support.vault.recoverWithCommit(skipper, code, newPin) { oldCredentials, newCredentials ->
            // The success callback runs before any credential replacement is saved. Re-read all guards.
            val current = store.read()
            if (current == null) false else {
                val blocked = skipBlocker(current, requestId, skipper, battery)
                if (blocked != null) { rejected = blocked; false }
                else if (credentials()?.revision != oldCredentials.revision) false
                else {
                    val revision = nextRevision(current)
                    if (revision == null) false else {
                        val operation = current.operation!!.withSkipper(skipper, newCredentials.people.getValue(skipper).generation)
                        val text = WaitingText("${requestId.length}:$requestId:$revision", WaitingEvent.SKIPPED,
                            operation, newCredentials.people.mapValues { it.value.generation }, newCredentials.contacts())
                        support.committer.commit(oldCredentials, newCredentials, current,
                            current.replacing(revision, operation, current.texts + text))
                    }
                }
            }
        }
        return skipResult(result, rejected)
    }

    private fun skipResult(result: RecoveryResult, rejected: WaitingOutcome): WaitingSkipResult = when (result) {
        is RecoveryResult.Replaced -> WaitingSkipResult.Committing(result.recoveryCode)
        is RecoveryResult.Wrong -> WaitingSkipResult.Wrong(result.remainingMillis, result.notifyLockout)
        is RecoveryResult.Delayed -> WaitingSkipResult.Delayed(result.remainingMillis)
        RecoveryResult.Unavailable -> WaitingSkipResult.NotSkipped(rejected)
    }

    private fun skipBlocker(before: WaitingState, requestId: String, skipper: TrustedPersonId, battery: BatteryState): WaitingOutcome? {
        reconcileCancellations(before)
        val operation = before.operation ?: return WaitingOutcome.IGNORED
        if (operation.identity.requestId != requestId) return WaitingOutcome.IGNORED
        if (operation.phase != WaitingPhase.PENDING) return phaseResult(operation.phase)
        val intent = cancelIntents.read(requestId)
        if (requestId in cancellationRequests || intent == CancelIntent.REQUESTED) {
            cancelIntents.request(requestId)
            return cancelPending(before, operation, WaitingEvent.CANCELLED_ON_PHONE)
        }
        val current = credentials() ?: return WaitingOutcome.NOT_READY
        // A pre-existing dependency change cancels before a skipper can replace starter authority.
        if (!operation.dependenciesMatch(current)) {
            return cancelPending(before, operation, WaitingEvent.DEPENDENCY_CHANGED)
        }
        if (current.mode != Mode.TRUSTED || skipper !in current.people) return WaitingOutcome.REFUSED
        if (intent == CancelIntent.UNKNOWN) return WaitingOutcome.NOT_READY
        if (operation.kind == WaitingChangeKind.TURN_OFF && !battery.allowsDiscretionaryUnhide) return WaitingOutcome.LOW_BATTERY
        return null
    }

    /** Only after the durable outbox acknowledges this exact receipt key. Delivery never gates progress. */
    @Synchronized
    fun acknowledge(key: String): Boolean {
        val before = store.read() ?: return false
        if (before.texts.none { it.key == key }) return true
        val revision = nextRevision(before) ?: return false
        return store.compareAndSet(before, before.replacing(revision, texts = before.texts.filterNot { it.key == key }))
    }

    /** Delivery acknowledgement removes only this recovery outbox item, never the durable audit entry. */
    @Synchronized
    fun acknowledgeRecovery(key: String): Boolean {
        val before = store.read() ?: return false
        if (before.recoveryReceipts.none { it.key == key }) return true
        val revision = nextRevision(before) ?: return false
        return store.compareAndSet(before, before.replacing(revision,
            recoveryReceipts = before.recoveryReceipts.filterNot { it.key == key }))
    }

    private fun cancelPending(before: WaitingState, operation: WaitingOperation, event: WaitingEvent): WaitingOutcome {
        // Choose generations and contacts from the same read, or the same frozen start fallback.
        // Even under serialized mutation, two reads may disagree because storage is transiently unreadable.
        val current = credentials()
        val recipients = current?.people?.mapValues { it.value.generation } ?: operation.recipientGenerations
        val contacts = current?.contacts() ?: operation.recipientContacts
        clock.checkpoint() // A failed clock cannot prevent a restrictive cancellation.
        if (!write(before, operation.withPhase(WaitingPhase.CANCELLED), event, recipients, contacts)) return WaitingOutcome.NOT_READY
        cancellationRequests.remove(operation.identity.requestId)
        return WaitingOutcome.CANCELLED
    }

    private fun reconcileCancellations(state: WaitingState) {
        val pendingId = state.operation?.takeIf { it.phase == WaitingPhase.PENDING }?.identity?.requestId
        // Discard stale/terminal intents only after a successful read; never replace a matching intent.
        cancellationRequests.retainAll(setOfNotNull(pendingId))
    }

    private fun phaseResult(phase: WaitingPhase) = when (phase) {
        WaitingPhase.COMMITTING, WaitingPhase.ROLLING_BACK -> WaitingOutcome.ALREADY_IN_PROGRESS
        else -> WaitingOutcome.IGNORED
    }
    private fun nextRevision(before: WaitingState): Long? = try { Math.addExact(before.revision, 1) } catch (_: ArithmeticException) { null }
    private fun write(before: WaitingState, operation: WaitingOperation, event: WaitingEvent,
        recipients: Map<TrustedPersonId, Long>, contacts: Map<TrustedPersonId, PersonContact?>): Boolean {
        val revision = nextRevision(before) ?: return false
        val id = operation.identity.requestId
        val text = WaitingText("${id.length}:$id:$revision", event, operation, recipients, contacts)
        return store.compareAndSet(before, before.replacing(revision, operation, before.texts + text))
    }

    private fun CredentialState.contacts(): Map<TrustedPersonId, PersonContact?> = people.mapValues { it.value.contact }
    private val activePhases = setOf(WaitingPhase.PENDING, WaitingPhase.COMMITTING, WaitingPhase.ROLLING_BACK)
}
