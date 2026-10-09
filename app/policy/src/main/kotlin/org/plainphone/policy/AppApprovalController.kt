// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import java.util.Collections
import org.plainphone.core.*

/** Validated persistent data, not a public authorization capability. D validates schema/checksum too. */
class AppApprovalRecord(val id: String, selection: PermissionSelection) {
    val permissions: PermissionPlan = (PermissionChoices.plan(selection, selection.manifest) as? PermissionPlanning.Ready)?.plan
        ?: throw IllegalArgumentException("Incomplete permission approval")
    val identity: ApprovalBinding get() = permissions.manifest.identity
    init { require(id.isNotBlank()) }
}

enum class AppApprovalPhase { PREPARING, COMMITTED }
class AppApprovalReceipt(val id: String, val actor: TrustedPersonId, recipientGenerations: Map<TrustedPersonId, Long>) {
    val recipientGenerations: Map<TrustedPersonId, Long> = Collections.unmodifiableMap(LinkedHashMap(recipientGenerations))
    init { require(id.isNotBlank() && actor in this.recipientGenerations && this.recipientGenerations.values.all { it >= 0 }) }
}
class AppApprovalOperation(val record: AppApprovalRecord, val receipt: AppApprovalReceipt, val phase: AppApprovalPhase) {
    init { require(record.id == receipt.id) }
}
class AppApprovalState(
    val revision: Long = 0,
    apps: Map<PackageName, AppApprovalRecord> = emptyMap(),
    val pending: AppApprovalOperation? = null,
    receipts: List<AppApprovalReceipt> = emptyList(),
) {
    val apps: Map<PackageName, AppApprovalRecord> = Collections.unmodifiableMap(LinkedHashMap(apps))
    val receipts: List<AppApprovalReceipt> = immutableList(receipts)
    init {
        require(revision >= 0 && this.apps.all { (name, record) -> name == record.identity.packageName })
        require(this.apps.values.map { it.id }.toSet().size == this.apps.size)
        require(this.receipts.map { it.id }.toSet().size == this.receipts.size)
        if (pending != null) {
            val existing = this.apps[pending.record.identity.packageName]
            if (pending.phase == AppApprovalPhase.PREPARING) {
                require(existing == null && this.receipts.none { it.id == pending.record.id })
            } else {
                require(existing != null && existing.id == pending.record.id &&
                    existing.permissions.manifest.sameFacts(pending.record.permissions.manifest) &&
                    existing.permissions.choices == pending.record.permissions.choices)
                val receipt = this.receipts.singleOrNull { it.id == pending.receipt.id }
                require(receipt != null && receipt.actor == pending.receipt.actor &&
                    receipt.recipientGenerations == pending.receipt.recipientGenerations)
            }
        }
    }
    internal fun replacing(apps: Map<PackageName, AppApprovalRecord> = this.apps,
        pending: AppApprovalOperation? = this.pending, receipts: List<AppApprovalReceipt> = this.receipts) =
        AppApprovalState(Math.addExact(revision, 1), apps, pending, receipts)

    /** Updates retain approval only for the exact complete signer set; duplicate OS facts fail closed. */
    fun approvedPackages(installed: List<InstalledPackage>, identities: List<ApprovalBinding>): Set<PackageName> {
        val packages = installed.groupBy { it.name }
        val signers = identities.groupBy { it.packageName }
        return immutableSet(apps.filter { (name, record) ->
            name != pending?.record?.identity?.packageName && packages[name]?.size == 1 && signers[name]?.singleOrNull() == record.identity
        }.keys)
    }
}

interface AppApprovalStore {
    /** Null for missing/invalid/temporarily unreadable storage. Never silently initialize an empty policy. */
    fun read(): AppApprovalState?
    /**
     * Durable CAS of revision plus (when supplied) the current credential revision. Shared serialized
     * coordinator with PinVault, package events and every policy writer. Preserve fixed receipt delivery
     * identities and audit alongside state/outbox. A returned false cannot authorize visibility.
     */
    fun compareAndSet(expected: AppApprovalState, replacement: AppApprovalState, expectedCredentials: CredentialState?): Boolean
}
interface AppApprovalEffects {
    /** Hide only this eligible non-floor candidate and confirm it remains hidden before permission writes. */
    fun hideAndReadBack(identity: ApprovalBinding): Boolean
    fun setPermission(identity: ApprovalBinding, write: PermissionWrite): Boolean
    fun readPermissions(identity: ApprovalBinding): Map<PermissionName, PermissionReadback>
    /** Apply only this plan; every discretionary unhide needs the synchronous flush and live readback. */
    fun applyPolicy(plan: PolicyPlan): Boolean
    fun readBackPolicy(plan: PolicyPlan): Boolean
}
fun interface AppApprovalIdSource { /** Unique across restart, approvals and receipts. */ fun next(): String }
class AppApprovalRequest internal constructor(
    internal val record: AppApprovalRecord,
    internal val person: TrustedPersonId,
    internal val credentialGeneration: Long,
    internal val structureGeneration: Long,
    internal val preparedAt: BootTime,
) {
    val action: PrivilegedAction get() = PrivilegedAction("app:approve", record.id)
}
enum class AppApprovalOutcome { READY, APPROVED, REFUSED, BUSY, LOW_BATTERY, NOT_READY }

/**
 * Supervised new-app approval. One shared dispatcher owns this, SessionController, PinVault, facts and
 * storage. Candidate intake is data only; each approval consumes new person/action-bound PIN authority.
 * A restart discards uncommitted intent. A committed approval may finish its idempotent activation.
 */
class AppApprovalController(
    private val store: AppApprovalStore,
    private val credentials: () -> CredentialState?,
    private val authority: PersonAuthorityConsumer,
    private val sessions: SessionController,
    private val clock: RunningClock,
    private val policyInput: () -> PolicyInput?,
    private val manifest: (PackageName) -> PermissionManifest?,
    private val battery: () -> BatteryState,
    private val effects: AppApprovalEffects,
    private val ids: AppApprovalIdSource,
) {
    private var initialized = false
    private val requests = mutableSetOf<AppApprovalRequest>()

    @Synchronized
    fun onStart(): AppApprovalOutcome {
        initialized = false; requests.clear()
        if (!sessions.onStart()) return AppApprovalOutcome.NOT_READY
        val state = store.read() ?: return AppApprovalOutcome.NOT_READY
        val op = state.pending
        if (op?.phase == AppApprovalPhase.PREPARING) {
            // Never resume pre-commit PIN authority after process death. The ordinary policy excludes it.
            val input = policyInput() ?: return AppApprovalOutcome.NOT_READY
            val plan = PolicyEngine.decide(input.copy(approvalState = state, sessionAccess = null))
            if (!effects.applyPolicy(plan) || !effects.readBackPolicy(plan)) return AppApprovalOutcome.NOT_READY
            if (state.revision == Long.MAX_VALUE || !store.compareAndSet(state, state.replacing(pending = null), null)) return AppApprovalOutcome.NOT_READY
        }
        initialized = true
        return if (op?.phase == AppApprovalPhase.COMMITTED) resume() else AppApprovalOutcome.READY
    }

    @Synchronized
    fun prepare(play: PlaySessionState, packageName: PackageName, person: TrustedPersonId, selection: PermissionSelection): AppApprovalRequest? {
        if (!initialized) return null
        val state = store.read() ?: return null
        if (state.pending != null || packageName in state.apps || state.revision == Long.MAX_VALUE) return null
        val current = credentials() ?: return null
        if (current.mode != Mode.TRUSTED) return null
        val credential = current.people[person] ?: return null
        val candidate = PlaySessionMachine.candidate(play, packageName) ?: return null
        if (candidate != selection.manifest.identity || !battery().allowsDiscretionaryUnhide) return null
        val live = manifest(packageName)
        if (PermissionChoices.plan(selection, live) !is PermissionPlanning.Ready) return null
        val id = ids.next()
        if (id.isBlank() || state.apps.values.any { it.id == id } || state.receipts.any { it.id == id }) return null
        val record = AppApprovalRecord(id, selection)
        if (planFor(record, state) == null) return null
        val now = clock.checkpoint() ?: return null
        return AppApprovalRequest(record, person, credential.generation, current.structureGeneration, now.boot).also { requests.add(it) }
    }

    @Synchronized
    fun approve(request: AppApprovalRequest, fresh: FreshAuthority): AppApprovalOutcome {
        if (!requests.remove(request)) return AppApprovalOutcome.REFUSED
        if (!initialized) return AppApprovalOutcome.NOT_READY
        val state = store.read() ?: return AppApprovalOutcome.NOT_READY
        if (state.pending != null) return AppApprovalOutcome.BUSY
        if (request.record.identity.packageName in state.apps || state.revision > Long.MAX_VALUE - 3) return AppApprovalOutcome.REFUSED
        val current = matchingCredentials(request) ?: return AppApprovalOutcome.REFUSED
        if (!battery().allowsDiscretionaryUnhide) return AppApprovalOutcome.LOW_BATTERY
        if (planFor(request.record, state) == null || !sameBoot(request)) return AppApprovalOutcome.REFUSED
        if (!authority.consumeFor(fresh, request.action, request.person)) return AppApprovalOutcome.REFUSED
        // Close Play and verify protected state before any permission or approval mutation.
        if (!sessions.onStart()) return AppApprovalOutcome.NOT_READY
        val receipt = AppApprovalReceipt(request.record.id, request.person, current.people.mapValues { it.value.generation })
        val op = AppApprovalOperation(request.record, receipt, AppApprovalPhase.PREPARING)
        val preparing = state.replacing(pending = op)
        if (!store.compareAndSet(state, preparing, current)) return AppApprovalOutcome.NOT_READY
        if (!effects.hideAndReadBack(request.record.identity)) return AppApprovalOutcome.NOT_READY
        for (write in request.record.permissions.writes) {
            if (planFor(request.record, preparing) == null || !effects.setPermission(request.record.identity, write)) return AppApprovalOutcome.NOT_READY
        }
        if (!permissionsMatch(request.record)) return AppApprovalOutcome.NOT_READY
        val atCommit = matchingCredentials(request) ?: return AppApprovalOutcome.NOT_READY
        if (!sameBoot(request) || !battery().allowsDiscretionaryUnhide || planFor(request.record, preparing) == null) return AppApprovalOutcome.NOT_READY
        val committed = preparing.replacing(apps = preparing.apps + (request.record.identity.packageName to request.record),
            pending = AppApprovalOperation(request.record, receipt, AppApprovalPhase.COMMITTED), receipts = preparing.receipts + receipt)
        if (!store.compareAndSet(preparing, committed, atCommit)) return AppApprovalOutcome.NOT_READY
        return resume()
    }

    @Synchronized
    fun resume(): AppApprovalOutcome {
        if (!initialized) return AppApprovalOutcome.NOT_READY
        val state = store.read() ?: return AppApprovalOutcome.NOT_READY
        val op = state.pending ?: return AppApprovalOutcome.READY
        if (op.phase != AppApprovalPhase.COMMITTED || state.revision == Long.MAX_VALUE) return AppApprovalOutcome.NOT_READY
        if (!battery().allowsDiscretionaryUnhide) return AppApprovalOutcome.LOW_BATTERY
        if (!permissionsMatch(op.record)) return AppApprovalOutcome.NOT_READY
        val plan = planFor(op.record, state) ?: return AppApprovalOutcome.NOT_READY
        if (!effects.applyPolicy(plan) || !effects.readBackPolicy(plan)) return AppApprovalOutcome.NOT_READY
        if (planFor(op.record, state) == null || !permissionsMatch(op.record)) return AppApprovalOutcome.NOT_READY
        if (!store.compareAndSet(state, state.replacing(pending = null), null)) return AppApprovalOutcome.NOT_READY
        return AppApprovalOutcome.APPROVED
    }

    private fun matchingCredentials(request: AppApprovalRequest): CredentialState? = credentials()?.takeIf {
        it.mode == Mode.TRUSTED && it.structureGeneration == request.structureGeneration &&
            it.people[request.person]?.generation == request.credentialGeneration
    }
    private fun sameBoot(request: AppApprovalRequest): Boolean = clock.checkpoint()?.boot?.let {
        it.bootId == request.preparedAt.bootId && it.elapsedMillis >= request.preparedAt.elapsedMillis
    } == true
    private fun permissionsMatch(record: AppApprovalRecord): Boolean {
        val readback = effects.readPermissions(record.identity)
        return PermissionChoices.verify(record.permissions, manifest(record.identity.packageName), readback) == PermissionVerification.Verified
    }
    private fun planFor(record: AppApprovalRecord, state: AppApprovalState): PolicyPlan? {
        val input = policyInput() ?: return null
        val name = record.identity.packageName
        val live = manifest(name) ?: return null
        if (!record.permissions.manifest.sameFacts(live) || input.fingerprint != live.buildFingerprint ||
            input.installed.singleOrNull { it.name == name }?.versionCode != live.versionCode ||
            input.installedIdentities.singleOrNull { it.packageName == name } != live.identity) return null
        if (input.lockedBoot || input.health != PolicyHealth.VALID || input.inventory?.model != input.model ||
            input.inventory.fingerprint != input.fingerprint || input.waitingOperation?.phase in setOf(WaitingPhase.COMMITTING, WaitingPhase.ROLLING_BACK)) return null
        val candidateState = AppApprovalState(state.revision, state.apps + (name to record))
        val plan = PolicyEngine.decide(input.copy(approvalState = candidateState, sessionAccess = null))
        if (name in plan.neverHide || name !in plan.unhide || name !in plan.lockTaskPackages || !EmergencyFloor.compute(input.installed, input.identities).hasSystemDialer) return null
        return plan
    }
}
