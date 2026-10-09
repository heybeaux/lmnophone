// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import java.util.Collections
import org.plainphone.core.*

/** Active settings only, stored atomically with the credentials. Never populated from a waiting patch. */
data class FrictionSettings(val countdownMillis: Long, val dailyLimit: Int) {
    init { require(countdownMillis in 600_000L..86_400_000L && dailyLimit in 0..3) }
}

/** User IDs are sensitive adapter data. No generated diagnostic may print them. */
class RecoveryProfile(holderUserIds: Map<TrustedPersonId, String>, val backupUserId: String?, val friction: FrictionSettings) {
    val holderUserIds: Map<TrustedPersonId, String> = Collections.unmodifiableMap(LinkedHashMap(holderUserIds))
    init {
        require(holderUserIds.isNotEmpty() && holderUserIds.values.all { it.isNotBlank() && it.none(Char::isWhitespace) })
        require(backupUserId == null || (backupUserId.isNotBlank() && backupUserId.none(Char::isWhitespace)))
    }
    val resetProtectionUserIds: Set<String> get() = immutableSet(holderUserIds.values + listOfNotNull(backupUserId))
    override fun toString() = "RecoveryProfile(redacted)"
}

enum class RecoveredState { ACTIVE }
/** A recovery report, never a cancellation/completion claim about an unknowable lost change. */
class RecoveryReceipt(val key: String, val mode: Mode, recipientGenerations: Map<TrustedPersonId, Long>,
    val friction: FrictionSettings, val state: RecoveredState = RecoveredState.ACTIVE) {
    val recipientGenerations: Map<TrustedPersonId, Long> = Collections.unmodifiableMap(LinkedHashMap(recipientGenerations))
    init { require(key.isNotBlank() && recipientGenerations.isNotEmpty() && recipientGenerations.values.all { it >= 0 }) }
}

/** Stable, never-reused loss episode ID, durably reserved by the adapter. */
class LostWaitingRecord(val id: String) {
    init { require(id.isNotBlank()) }
}
interface WaitingReconciliationStore : WaitingChangeStore {
    /**
     * Only after confirming both operation copies are invalid. Transient I/O errors return null.
     * Reserve this ID durably before returning it; repeat the same ID until completion.
     * All store/credential/effect writers use the same serialized dispatcher.
     */
    fun lostRecord(): LostWaitingRecord?
    /**
     * Compare the still-lost episode AND current credential revision. Atomically install the replacement,
     * clear the loss marker, release the slot, and append its recovery receipt to the durable audit/outbox.
     * Retain delivery identities/addresses from expectedCredentials in that same transaction.
     * A false result never permits reporting recovery. Crash replay must expose either held/lost or
     * the complete replacement, never a vacant slot without its receipt. Fence all pre-loss copies
     * with replacement.storageGeneration, even if their numeric revision is larger.
     */
    fun completeReconciliation(lost: LostWaitingRecord, expectedCredentials: CredentialState, replacement: WaitingState): Boolean
}

/**
 * Synchronous idempotent lane D ports. Every readback checks the exact supplied active values.
 * Revocation invalidates grants/journals first. Preserve calls, Emergency and alerts on every failure.
 * applyPolicy covers the entire emitted plan; reset protection includes its required broadcast.
 * No effect may adopt pending credentials/friction, remove Device Owner, or wipe the phone.
 */
interface ReconciliationEffects {
    fun revokeTemporaryAccess(): Boolean
    fun isDeviceOwner(): Boolean?
    fun applyPolicy(plan: PolicyPlan): Boolean
    fun applyResetProtection(userIds: Set<String>): Boolean
    fun applyFriction(settings: FrictionSettings): Boolean
    fun readBackPolicy(plan: PolicyPlan): Boolean
    fun readBackResetProtection(userIds: Set<String>): Boolean
    fun readBackFriction(settings: FrictionSettings): Boolean
}

enum class ReconcileOutcome { RECOVERED, OFF, NEEDS_ATTENTION, REFUSED, IGNORED }
enum class ReconcileFailure {
    REVOCATION, OWNER_READ, LOSS_RECORD, CREDENTIALS, POLICY_INPUT, POLICY_APPLY, RESET_APPLY,
    FRICTION_APPLY, POLICY_READBACK, RESET_READBACK, FRICTION_READBACK, CREDENTIALS_CHANGED, PERSISTENCE,
}
data class ReconcileResult(val outcome: ReconcileOutcome, val failure: ReconcileFailure? = null)
class RepairRequest internal constructor(internal val lossId: String, internal val person: TrustedPersonId) {
    val action = PrivilegedAction("waiting:repair", "${lossId.length}:$lossId${person.value.length}:${person.value}")
}

/**
 * Automatic lost-record recovery and PIN Repair run the same reconciliation. No kind is inferred.
 * The trusted input source reads the active policy store and live OS facts, under the shared dispatcher.
 * A locked-boot partial policy cannot finish reconciliation; retry with full inputs after unlock.
 */
class WaitingReconciler(
    private val store: WaitingReconciliationStore,
    private val credentials: () -> CredentialState?,
    private val policyInput: (CredentialState) -> PolicyInput?,
    private val effects: ReconciliationEffects,
    private val authority: PersonAuthorityConsumer,
) {
    @Synchronized
    fun prepareRepair(person: TrustedPersonId): RepairRequest? {
        if (store.read() != null || credentials()?.people?.containsKey(person) != true) return null
        return store.lostRecord()?.let { RepairRequest(it.id, person) }
    }

    @Synchronized
    fun repair(request: RepairRequest, grant: FreshAuthority): ReconcileResult {
        if (store.read() != null || store.lostRecord()?.id != request.lossId) return ReconcileResult(ReconcileOutcome.IGNORED)
        if (credentials()?.people?.containsKey(request.person) != true || !authority.consumeFor(grant, request.action, request.person)) {
            return ReconcileResult(ReconcileOutcome.REFUSED)
        }
        return reconcile()
    }

    @Synchronized
    fun reconcile(): ReconcileResult {
        if (store.read() != null) return ReconcileResult(ReconcileOutcome.IGNORED)
        val revoked = effects.revokeTemporaryAccess()
        when (effects.isDeviceOwner()) {
            false -> return ReconcileResult(ReconcileOutcome.OFF)
            null -> return failed(ReconcileFailure.OWNER_READ)
            true -> Unit
        }
        if (!revoked) return failed(ReconcileFailure.REVOCATION)
        val loss = store.lostRecord() ?: return failed(ReconcileFailure.LOSS_RECORD)
        val current = credentials() ?: return failed(ReconcileFailure.CREDENTIALS)
        val profile = current.recoveryProfile ?: return failed(ReconcileFailure.CREDENTIALS)
        val input = policyInput(current) ?: return failed(ReconcileFailure.POLICY_INPUT)
        if (input.health == PolicyHealth.BOTH_INVALID || input.lockedBoot) return failed(ReconcileFailure.POLICY_INPUT)
        // A lost journal can never authorize forward restore or a surviving session relaxation.
        val plan = PolicyEngine.decide(input.copy(waitingOperation = null, sessionAccess = null, currentBoot = null))
        // Diagnostic conflicts/refusals may persist on a build; they must not strand the waiting slot.
        // Preserve them on the applied plan, but require the live system dialer independently.
        if (!EmergencyFloor.compute(input.installed, input.identities).hasSystemDialer) return failed(ReconcileFailure.POLICY_INPUT)
        val resetIds = profile.resetProtectionUserIds
        if (!effects.applyPolicy(plan)) return failed(ReconcileFailure.POLICY_APPLY)
        if (!effects.applyResetProtection(resetIds)) return failed(ReconcileFailure.RESET_APPLY)
        if (!effects.applyFriction(profile.friction)) return failed(ReconcileFailure.FRICTION_APPLY)
        // Read all categories after all writes; don't trust an earlier stage's transient success.
        if (!effects.readBackPolicy(plan)) return failed(ReconcileFailure.POLICY_READBACK)
        if (!effects.readBackResetProtection(resetIds)) return failed(ReconcileFailure.RESET_READBACK)
        if (!effects.readBackFriction(profile.friction)) return failed(ReconcileFailure.FRICTION_READBACK)
        if (credentials()?.revision != current.revision) return failed(ReconcileFailure.CREDENTIALS_CHANGED)
        when (effects.isDeviceOwner()) {
            false -> return ReconcileResult(ReconcileOutcome.OFF)
            null -> return failed(ReconcileFailure.OWNER_READ)
            true -> Unit
        }
        val receipt = RecoveryReceipt("recovered:${loss.id.length}:${loss.id}", current.mode,
            current.people.mapValues { it.value.generation }, profile.friction)
        val replacement = WaitingState(storageGeneration = loss.id, recoveryReceipts = listOf(receipt))
        return if (store.completeReconciliation(loss, current, replacement)) ReconcileResult(ReconcileOutcome.RECOVERED)
            else failed(ReconcileFailure.PERSISTENCE)
    }

    private fun failed(reason: ReconcileFailure) = ReconcileResult(ReconcileOutcome.NEEDS_ATTENTION, reason)
}
