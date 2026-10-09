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

enum class WaitingEvent { STARTED, CANCELLED_ON_PHONE, DEPENDENCY_CHANGED, COMMITTING, COMPLETED }
class WaitingText(val key: String, val event: WaitingEvent, val operation: WaitingOperation,
    recipientGenerations: Map<TrustedPersonId, Long>) {
    val recipientGenerations: Map<TrustedPersonId, Long> = java.util.Collections.unmodifiableMap(LinkedHashMap(recipientGenerations))
    val recipients = immutableSet(recipientGenerations.keys)
}
/** Immutable turn-off operation, including forward restore/readback progress. */
class WaitingOperation(
    val identity: WaitingIdentity, val starter: TrustedPersonId, val generation: Long,
    val structureGeneration: Long, val mode: Mode, val startedAt: RunningTime, val phase: WaitingPhase,
    recipientGenerations: Map<TrustedPersonId, Long>,
    val restoredSteps: Int = 0,
) {
    init {
        require(generation >= 0 && structureGeneration >= 0)
        require(restoredSteps in 0..TurnOffStep.entries.size)
        require(restoredSteps == 0 || phase == WaitingPhase.COMMITTING || phase == WaitingPhase.COMPLETED)
        require(recipientGenerations.isNotEmpty() && recipientGenerations.values.all { it >= 0 })
    }
    /** Start-time fallback retained independently of acknowledged outbox texts. */
    val recipientGenerations: Map<TrustedPersonId, Long> = java.util.Collections.unmodifiableMap(LinkedHashMap(recipientGenerations))
    val kind = WaitingChangeKind.TURN_OFF
    internal fun withPhase(phase: WaitingPhase) = WaitingOperation(identity, starter, generation, structureGeneration, mode, startedAt, phase, recipientGenerations, restoredSteps)
    internal fun withRestoredSteps(count: Int) = WaitingOperation(identity, starter, generation, structureGeneration, mode, startedAt, phase, recipientGenerations, count)
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
    internal val generation: Long, internal val structureGeneration: Long, internal val mode: Mode) {
    val requestId: String get() = identity.requestId
    val action = PrivilegedAction("waiting:turn-off", "${identity.requestId.length}:${identity.requestId}${starter.value.length}:${starter.value}")
    internal var used = false
}
enum class WaitingOutcome { PENDING, COMMITTING, CANCELLED, REFUSED, BUSY, NOT_READY, LOW_BATTERY, ALREADY_IN_PROGRESS, IGNORED }

/**
 * The common serialized dispatcher owns this controller, PinVault and all credential/operation writes.
 * This slice starts only turn-off waits. It never restores settings, clears Device Owner, skips a wait,
 * applies a credential patch, or reports Off. Damaged state remains held until WaitingReconciler completes.
 */
class WaitingChangeController(
    private val store: WaitingChangeStore,
    private val credentials: () -> CredentialState?,
    private val clock: RunningClock,
    private val authority: PersonAuthorityConsumer,
    private val identities: WaitingIdentitySource,
    private val cancelIntents: CancelIntentStore,
) {
    private val cancellationRequests = mutableSetOf<String>()

    @Synchronized
    fun prepareTurnOff(starter: TrustedPersonId): WaitingStart? {
        val current = credentials() ?: return null
        val person = current.people[starter] ?: return null
        return WaitingStart(identities.reserve(), starter, person.generation, current.structureGeneration, current.mode)
    }

    @Synchronized
    fun startTurnOff(request: WaitingStart, freshAuthority: FreshAuthority, firstConfirmation: Boolean, secondConfirmation: Boolean): WaitingOutcome {
        if (!firstConfirmation || !secondConfirmation || request.used) return WaitingOutcome.REFUSED
        val before = store.read() ?: return WaitingOutcome.NOT_READY
        reconcileCancellations(before)
        if (before.operation?.phase in setOf(WaitingPhase.PENDING, WaitingPhase.COMMITTING, WaitingPhase.ROLLING_BACK)) return WaitingOutcome.BUSY
        val current = credentials() ?: return WaitingOutcome.NOT_READY
        if (current.mode != request.mode || current.structureGeneration != request.structureGeneration ||
            current.people[request.starter]?.generation != request.generation) return WaitingOutcome.REFUSED
        val now = clock.checkpoint() ?: return WaitingOutcome.NOT_READY
        if (!authority.consumeFor(freshAuthority, request.action, request.starter)) return WaitingOutcome.REFUSED
        request.used = true // Fresh authorization and preparation cannot be replayed after a failed write.
        if (!cancelIntents.initialize(request.requestId)) return WaitingOutcome.NOT_READY
        val operation = WaitingOperation(request.identity, request.starter, request.generation,
            request.structureGeneration, request.mode, now.proven, WaitingPhase.PENDING, current.people.mapValues { it.value.generation })
        return if (write(before, operation, WaitingEvent.STARTED, current.people.mapValues { it.value.generation })) WaitingOutcome.PENDING else WaitingOutcome.NOT_READY
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
        if (current.mode != operation.mode || current.structureGeneration != operation.structureGeneration ||
            current.people[operation.starter]?.generation != operation.generation) {
            return cancelPending(before, operation, WaitingEvent.DEPENDENCY_CHANGED)
        }
        if (intent == CancelIntent.UNKNOWN) return WaitingOutcome.NOT_READY
        val now = clock.checkpoint() ?: return WaitingOutcome.NOT_READY
        if (now.proven.since(operation.startedAt) < 86_400_000L) return WaitingOutcome.PENDING
        if (!battery.allowsDiscretionaryUnhide) return WaitingOutcome.LOW_BATTERY
        return if (write(before, operation.withPhase(WaitingPhase.COMMITTING), WaitingEvent.COMMITTING, current.people.mapValues { it.value.generation }))
            WaitingOutcome.COMMITTING else WaitingOutcome.NOT_READY
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
        val recipients = credentials()?.people?.mapValues { it.value.generation } ?: operation.recipientGenerations
        clock.checkpoint() // A failed clock cannot prevent a restrictive cancellation.
        if (!write(before, operation.withPhase(WaitingPhase.CANCELLED), event, recipients)) return WaitingOutcome.NOT_READY
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
    private fun write(before: WaitingState, operation: WaitingOperation, event: WaitingEvent, recipients: Map<TrustedPersonId, Long>): Boolean {
        val revision = nextRevision(before) ?: return false
        val id = operation.identity.requestId
        val text = WaitingText("${id.length}:$id:$revision", event, operation, recipients)
        return store.compareAndSet(before, before.replacing(revision, operation, before.texts + text))
    }
}
