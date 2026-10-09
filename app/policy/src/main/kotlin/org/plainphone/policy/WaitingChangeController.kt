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

enum class WaitingEvent { STARTED, CANCELLED_ON_PHONE, DEPENDENCY_CHANGED, COMMITTING }
class WaitingText(val key: String, val event: WaitingEvent, val operation: WaitingOperation,
    recipientGenerations: Map<TrustedPersonId, Long>) {
    val recipientGenerations: Map<TrustedPersonId, Long> = java.util.Collections.unmodifiableMap(LinkedHashMap(recipientGenerations))
    val recipients = immutableSet(recipientGenerations.keys)
}
/** Immutable turn-off gate record. The forward restore journal is a subsequent controller slice. */
class WaitingOperation(
    val identity: WaitingIdentity, val starter: TrustedPersonId, val generation: Long,
    val structureGeneration: Long, val mode: Mode, val startedAt: RunningTime, val phase: WaitingPhase,
) {
    init { require(generation >= 0 && structureGeneration >= 0) }
    val kind = WaitingChangeKind.TURN_OFF
    internal fun withPhase(phase: WaitingPhase) = WaitingOperation(identity, starter, generation, structureGeneration, mode, startedAt, phase)
}
class WaitingState(val revision: Long = 0, val operation: WaitingOperation? = null, texts: List<WaitingText> = emptyList()) {
    init { require(revision >= 0) }
    val texts = immutableList(texts)
}
interface WaitingChangeStore {
    /** Highest-revision valid checksum copy from two durable copies. null means damaged, not vacant. */
    fun read(): WaitingState?
    /** Atomically persist operation + texts; compare durable revision, increase it on every write. */
    fun compareAndSet(expected: WaitingState, replacement: WaitingState): Boolean
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
 * applies a credential patch, or reports Off. Damaged state remains held for later explicit reconcile.
 */
class WaitingChangeController(
    private val store: WaitingChangeStore,
    private val credentials: () -> CredentialState?,
    private val clock: RunningClock,
    private val authority: PersonAuthorityConsumer,
    private val identities: WaitingIdentitySource,
) {
    private var cancellationRequested: String? = null

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
        if (before.operation?.phase in setOf(WaitingPhase.PENDING, WaitingPhase.COMMITTING, WaitingPhase.ROLLING_BACK)) return WaitingOutcome.BUSY
        val current = credentials() ?: return WaitingOutcome.NOT_READY
        if (current.mode != request.mode || current.structureGeneration != request.structureGeneration ||
            current.people[request.starter]?.generation != request.generation) return WaitingOutcome.REFUSED
        val now = clock.checkpoint() ?: return WaitingOutcome.NOT_READY
        if (!authority.consumeFor(freshAuthority, request.action, request.starter)) return WaitingOutcome.REFUSED
        request.used = true // Fresh authorization and preparation cannot be replayed after a failed write.
        val operation = WaitingOperation(request.identity, request.starter, request.generation,
            request.structureGeneration, request.mode, now.proven, WaitingPhase.PENDING)
        return if (write(before, operation, WaitingEvent.STARTED, current.people.mapValues { it.value.generation })) WaitingOutcome.PENDING else WaitingOutcome.NOT_READY
    }

    @Synchronized
    fun cancel(requestId: String): WaitingOutcome {
        val before = store.read() ?: return WaitingOutcome.NOT_READY
        val operation = before.operation ?: return WaitingOutcome.IGNORED
        if (operation.identity.requestId != requestId) return WaitingOutcome.IGNORED
        if (operation.phase != WaitingPhase.PENDING) return phaseResult(operation.phase)
        cancellationRequested = requestId // Deny commit even if cancellation's atomic write fails.
        return cancelPending(before, operation, WaitingEvent.CANCELLED_ON_PHONE)
    }

    @Synchronized
    fun tick(requestId: String, battery: BatteryState): WaitingOutcome {
        val before = store.read() ?: return WaitingOutcome.NOT_READY
        val operation = before.operation ?: return WaitingOutcome.IGNORED
        if (operation.identity.requestId != requestId) return WaitingOutcome.IGNORED
        if (operation.phase != WaitingPhase.PENDING) return phaseResult(operation.phase)
        if (cancellationRequested == requestId) return cancelPending(before, operation, WaitingEvent.CANCELLED_ON_PHONE)
        val current = credentials() ?: return WaitingOutcome.NOT_READY
        if (current.mode != operation.mode || current.structureGeneration != operation.structureGeneration ||
            current.people[operation.starter]?.generation != operation.generation) {
            return cancelPending(before, operation, WaitingEvent.DEPENDENCY_CHANGED)
        }
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
        return store.compareAndSet(before, WaitingState(revision, before.operation, before.texts.filterNot { it.key == key }))
    }

    private fun cancelPending(before: WaitingState, operation: WaitingOperation, event: WaitingEvent): WaitingOutcome {
        val current = credentials() ?: return WaitingOutcome.NOT_READY
        clock.checkpoint() // A failed clock cannot prevent a restrictive cancellation.
        if (!write(before, operation.withPhase(WaitingPhase.CANCELLED), event, current.people.mapValues { it.value.generation })) return WaitingOutcome.NOT_READY
        if (cancellationRequested == operation.identity.requestId) cancellationRequested = null
        return WaitingOutcome.CANCELLED
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
        return store.compareAndSet(before, WaitingState(revision, operation, before.texts + text))
    }
}
