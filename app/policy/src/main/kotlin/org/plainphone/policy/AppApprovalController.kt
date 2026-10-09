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

enum class AppApprovalPhase { PREPARING, COMMITTED, WITHDRAWING, REMOVING }
enum class AppApprovalReceiptKind { APPROVED, WITHDRAWN, REMOVED }
class AppApprovalReceipt(val id: String, val actor: TrustedPersonId, recipientGenerations: Map<TrustedPersonId, Long>,
    val kind: AppApprovalReceiptKind = AppApprovalReceiptKind.APPROVED, val approvalId: String = id,
) {
    val recipientGenerations: Map<TrustedPersonId, Long> = Collections.unmodifiableMap(LinkedHashMap(recipientGenerations))
    init {
        require(id.isNotBlank() && approvalId.isNotBlank() && actor in this.recipientGenerations && this.recipientGenerations.values.all { it >= 0 })
        require(if (kind == AppApprovalReceiptKind.APPROVED) id == approvalId else id != approvalId)
    }
}
class AppApprovalOperation(val record: AppApprovalRecord, val receipt: AppApprovalReceipt?, val phase: AppApprovalPhase,
    val withdrawalReceipt: AppApprovalReceipt? = null,
) {
    init {
        // An active app's approval receipt may already be acknowledged; never recreate it for removal.
        require(phase == AppApprovalPhase.REMOVING || receipt != null)
        require(receipt == null || (record.id == receipt.id && receipt.kind == AppApprovalReceiptKind.APPROVED))
        val finalKind = when (phase) {
            AppApprovalPhase.WITHDRAWING -> AppApprovalReceiptKind.WITHDRAWN
            AppApprovalPhase.REMOVING -> AppApprovalReceiptKind.REMOVED
            else -> null
        }
        if (finalKind != null) {
            require(withdrawalReceipt != null && withdrawalReceipt.kind == finalKind && withdrawalReceipt.approvalId == record.id)
        } else require(withdrawalReceipt == null)
    }
}
class AppApprovalState(
    val revision: Long = 0,
    apps: Map<PackageName, AppApprovalRecord> = emptyMap(),
    val pending: AppApprovalOperation? = null,
    receipts: List<AppApprovalReceipt> = emptyList(),
    removedApps: Map<PackageName, ApprovalBinding> = emptyMap(),
) {
    val apps: Map<PackageName, AppApprovalRecord> = Collections.unmodifiableMap(LinkedHashMap(apps))
    val receipts: List<AppApprovalReceipt> = immutableList(receipts)
    /** Prior removal provenance only; never included in approvedPackages. */
    val removedApps: Map<PackageName, ApprovalBinding> = Collections.unmodifiableMap(LinkedHashMap(removedApps))
    init {
        require(this.removedApps.all { (name, identity) -> name == identity.packageName && name !in this.apps })
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
                val original = pending.receipt
                val receipt = this.receipts.singleOrNull { it.id == pending.record.id }
                if (original == null) require(receipt == null)
                else require(receipt != null && receipt.actor == original.actor &&
                    receipt.recipientGenerations == original.recipientGenerations &&
                    receipt.kind == original.kind && receipt.approvalId == original.approvalId)
                require(pending.withdrawalReceipt?.let { withdrawal ->
                    this.receipts.none { it.id == withdrawal.id } && this.apps.values.none { it.id == withdrawal.id }
                } != false)
            }
        }
    }
    internal fun replacing(apps: Map<PackageName, AppApprovalRecord> = this.apps,
        pending: AppApprovalOperation? = this.pending, receipts: List<AppApprovalReceipt> = this.receipts,
        removedApps: Map<PackageName, ApprovalBinding> = this.removedApps) =
        AppApprovalState(Math.addExact(revision, 1), apps, pending, receipts, removedApps)

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
/** Exact pending approval plus process-local, person-bound fresh authority for withdrawal. */
class AppApprovalWithdrawalRequest internal constructor(
    val approvalId: String,
    internal val receiptId: String,
    internal val person: TrustedPersonId,
    internal val credentialGeneration: Long,
    internal val structureGeneration: Long,
    internal val preparedAt: BootTime,
) {
    val action: PrivilegedAction get() = PrivilegedAction("app:withdraw-pending", receiptId)
}
class AppRemovalRequest internal constructor(
    internal val record: AppApprovalRecord,
    internal val receiptId: String,
    internal val person: TrustedPersonId,
    internal val credentialGeneration: Long,
    internal val structureGeneration: Long,
    internal val preparedAt: BootTime,
) {
    val approvalId: String get() = record.id
    val packageName: PackageName get() = record.identity.packageName
    val action: PrivilegedAction get() = PrivilegedAction("app:remove", receiptId)
}
enum class AppApprovalOutcome { READY, APPROVED, WITHDRAWN, REMOVED, REFUSED, BUSY, LOW_BATTERY, NOT_READY }

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
    private val withdrawals = mutableSetOf<AppApprovalWithdrawalRequest>()
    private val removals = mutableSetOf<AppRemovalRequest>()

    @Synchronized
    fun onStart(): AppApprovalOutcome {
        initialized = false; requests.clear(); withdrawals.clear(); removals.clear()
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
        return if (op != null && op.phase != AppApprovalPhase.PREPARING) resume() else AppApprovalOutcome.READY
    }

    @Synchronized
    fun prepare(play: PlaySessionState, packageName: PackageName, person: TrustedPersonId, selection: PermissionSelection): AppApprovalRequest? =
        prepareCandidate(PlaySessionMachine.candidate(play, packageName), packageName, person, selection)

    /** Previously removed apps can be reviewed again without another install; the marker grants nothing. */
    @Synchronized
    fun prepareReapproval(packageName: PackageName, person: TrustedPersonId, selection: PermissionSelection): AppApprovalRequest? =
        prepareCandidate(store.read()?.removedApps?.get(packageName), packageName, person, selection)

    private fun prepareCandidate(candidate: ApprovalBinding?, packageName: PackageName, person: TrustedPersonId,
        selection: PermissionSelection): AppApprovalRequest? {
        if (!initialized || candidate == null || candidate.packageName != packageName) return null
        val state = store.read() ?: return null
        if (state.pending != null || packageName in state.apps || state.revision == Long.MAX_VALUE) return null
        val current = credentials() ?: return null
        if (current.mode != Mode.TRUSTED) return null
        val credential = current.people[person] ?: return null
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
            pending = AppApprovalOperation(request.record, receipt, AppApprovalPhase.COMMITTED), receipts = preparing.receipts + receipt,
            removedApps = preparing.removedApps - request.record.identity.packageName)
        if (!store.compareAndSet(preparing, committed, atCommit)) return AppApprovalOutcome.NOT_READY
        return resume()
    }

    @Synchronized
    fun resume(): AppApprovalOutcome {
        if (!initialized) return AppApprovalOutcome.NOT_READY
        val state = store.read() ?: return AppApprovalOutcome.NOT_READY
        val op = state.pending ?: return AppApprovalOutcome.READY
        if (state.revision == Long.MAX_VALUE) return AppApprovalOutcome.NOT_READY
        if (op.phase == AppApprovalPhase.WITHDRAWING || op.phase == AppApprovalPhase.REMOVING) return finishWithdrawal(state, op)
        if (op.phase != AppApprovalPhase.COMMITTED) return AppApprovalOutcome.NOT_READY
        if (!battery().allowsDiscretionaryUnhide) return AppApprovalOutcome.LOW_BATTERY
        if (!permissionsMatch(op.record)) return AppApprovalOutcome.NOT_READY
        val plan = planFor(op.record, state) ?: return AppApprovalOutcome.NOT_READY
        if (!effects.applyPolicy(plan) || !effects.readBackPolicy(plan)) return AppApprovalOutcome.NOT_READY
        if (planFor(op.record, state) == null || !permissionsMatch(op.record)) return AppApprovalOutcome.NOT_READY
        if (!store.compareAndSet(state, state.replacing(pending = null), null)) return AppApprovalOutcome.NOT_READY
        return AppApprovalOutcome.APPROVED
    }

    /** Review the exact stuck approval; no stale-manifest guess or old PIN can withdraw it. */
    @Synchronized
    fun prepareWithdrawal(person: TrustedPersonId): AppApprovalWithdrawalRequest? {
        if (!initialized) return null
        val state = store.read() ?: return null
        val op = state.pending?.takeIf { it.phase == AppApprovalPhase.COMMITTED } ?: return null
        if (state.revision > Long.MAX_VALUE - 2) return null
        val current = credentials()?.takeIf { it.mode == Mode.TRUSTED } ?: return null
        val credential = current.people[person] ?: return null
        val id = ids.next()
        if (id.isBlank() || state.apps.values.any { it.id == id } || state.receipts.any { it.id == id }) return null
        val now = clock.checkpoint() ?: return null
        return AppApprovalWithdrawalRequest(op.record.id, id, person, credential.generation,
            current.structureGeneration, now.boot).also { withdrawals.add(it) }
    }

    @Synchronized
    fun withdrawPending(request: AppApprovalWithdrawalRequest, confirmedApprovalId: String, fresh: FreshAuthority): AppApprovalOutcome {
        if (!withdrawals.remove(request) || confirmedApprovalId != request.approvalId) return AppApprovalOutcome.REFUSED
        if (!initialized) return AppApprovalOutcome.NOT_READY
        val state = store.read() ?: return AppApprovalOutcome.NOT_READY
        val op = state.pending ?: return AppApprovalOutcome.REFUSED
        if (op.phase != AppApprovalPhase.COMMITTED || op.record.id != request.approvalId ||
            state.revision > Long.MAX_VALUE - 2 || state.receipts.any { it.id == request.receiptId } ||
            state.apps.values.any { it.id == request.receiptId }) return AppApprovalOutcome.REFUSED
        if (matchingCredentials(request) == null || !sameBoot(request.preparedAt)) return AppApprovalOutcome.REFUSED
        if (!authority.consumeFor(fresh, request.action, request.person)) return AppApprovalOutcome.REFUSED
        if (!sessions.onStart()) return AppApprovalOutcome.NOT_READY
        val current = matchingCredentials(request) ?: return AppApprovalOutcome.NOT_READY
        if (!sameBoot(request.preparedAt)) return AppApprovalOutcome.NOT_READY
        val receipt = AppApprovalReceipt(request.receiptId, request.person, current.people.mapValues { it.value.generation },
            AppApprovalReceiptKind.WITHDRAWN, request.approvalId)
        val withdrawing = state.replacing(pending = AppApprovalOperation(op.record, op.receipt, AppApprovalPhase.WITHDRAWING, receipt))
        if (!store.compareAndSet(state, withdrawing, current)) return AppApprovalOutcome.NOT_READY
        return resume()
    }

    @Synchronized
    fun prepareRemoval(packageName: PackageName, person: TrustedPersonId): AppRemovalRequest? {
        if (!initialized) return null
        val state = store.read() ?: return null
        if (state.pending != null || state.revision > Long.MAX_VALUE - 2) return null
        val record = state.apps[packageName] ?: return null
        val current = credentials()?.takeIf { it.mode == Mode.TRUSTED } ?: return null
        val credential = current.people[person] ?: return null
        val id = ids.next()
        if (id.isBlank() || state.apps.values.any { it.id == id } || state.receipts.any { it.id == id }) return null
        val now = clock.checkpoint() ?: return null
        return AppRemovalRequest(record, id, person, credential.generation, current.structureGeneration, now.boot).also { removals.add(it) }
    }

    @Synchronized
    fun removeApproved(request: AppRemovalRequest, confirmedApprovalId: String, fresh: FreshAuthority): AppApprovalOutcome {
        if (!removals.remove(request) || request.approvalId != confirmedApprovalId) return AppApprovalOutcome.REFUSED
        if (!initialized) return AppApprovalOutcome.NOT_READY
        val state = store.read() ?: return AppApprovalOutcome.NOT_READY
        if (state.pending != null) return AppApprovalOutcome.BUSY
        val currentRecord = state.apps[request.packageName] ?: return AppApprovalOutcome.REFUSED
        if (currentRecord.id != request.approvalId || !currentRecord.permissions.manifest.sameFacts(request.record.permissions.manifest) ||
            currentRecord.permissions.choices != request.record.permissions.choices || state.revision > Long.MAX_VALUE - 2 ||
            state.receipts.any { it.id == request.receiptId } || state.apps.values.any { it.id == request.receiptId }) return AppApprovalOutcome.REFUSED
        if (matchingCredentials(request) == null || !sameBoot(request.preparedAt)) return AppApprovalOutcome.REFUSED
        if (!authority.consumeFor(fresh, request.action, request.person)) return AppApprovalOutcome.REFUSED
        if (!sessions.onStart()) return AppApprovalOutcome.NOT_READY
        val current = matchingCredentials(request) ?: return AppApprovalOutcome.NOT_READY
        if (!sameBoot(request.preparedAt)) return AppApprovalOutcome.NOT_READY
        val receipt = AppApprovalReceipt(request.receiptId, request.person, current.people.mapValues { it.value.generation },
            AppApprovalReceiptKind.REMOVED, request.approvalId)
        val removing = state.replacing(pending = AppApprovalOperation(currentRecord,
            state.receipts.singleOrNull { it.id == currentRecord.id }, AppApprovalPhase.REMOVING, receipt))
        if (!store.compareAndSet(state, removing, current)) return AppApprovalOutcome.NOT_READY
        return resume()
    }

    private fun matchingCredentials(request: AppRemovalRequest): CredentialState? = credentials()?.takeIf {
        it.mode == Mode.TRUSTED && it.structureGeneration == request.structureGeneration &&
            it.people[request.person]?.generation == request.credentialGeneration
    }

    private fun finishWithdrawal(state: AppApprovalState, op: AppApprovalOperation): AppApprovalOutcome {
        val checked = withdrawalPlan(state, op) ?: return AppApprovalOutcome.NOT_READY
        if (!effects.applyPolicy(checked.plan) || !effects.readBackPolicy(checked.plan)) return AppApprovalOutcome.NOT_READY
        // Facts can change even when the resulting policy sets are identical.
        if (withdrawalPlan(state, op) != checked) return AppApprovalOutcome.NOT_READY
        val finished = state.replacing(apps = state.apps - op.record.identity.packageName, pending = null,
            receipts = state.receipts + checkNotNull(op.withdrawalReceipt),
            removedApps = if (op.phase == AppApprovalPhase.REMOVING)
                state.removedApps + (op.record.identity.packageName to op.record.identity) else state.removedApps)
        if (!store.compareAndSet(state, finished, null)) return AppApprovalOutcome.NOT_READY
        return if (op.phase == AppApprovalPhase.REMOVING) AppApprovalOutcome.REMOVED else AppApprovalOutcome.WITHDRAWN
    }

    private data class WithdrawalPlan(val plan: PolicyPlan, val installed: InstalledPackage?,
        val signers: List<ApprovalBinding>, val model: String, val fingerprint: String)

    private fun withdrawalPlan(state: AppApprovalState, op: AppApprovalOperation): WithdrawalPlan? {
        val input = policyInput() ?: return null
        if (input.lockedBoot || input.waitingOperation?.phase in setOf(WaitingPhase.COMMITTING, WaitingPhase.ROLLING_BACK) ||
            !EmergencyFloor.compute(input.installed, input.identities).hasSystemDialer) return null
        val name = op.record.identity.packageName
        val installed = input.installed.filter { it.name == name }
        if (installed.size > 1) return null
        val plan = PolicyEngine.decide(input.copy(approvalState = state, sessionAccess = null))
        if (name in plan.neverHide || name in plan.unhide || name in plan.lockTaskPackages ||
            (installed.isNotEmpty() && name !in plan.hidden)) return null
        return WithdrawalPlan(plan, installed.singleOrNull(),
            immutableList(input.installedIdentities.filter { it.packageName == name }), input.model, input.fingerprint)
    }

    private fun matchingCredentials(request: AppApprovalWithdrawalRequest): CredentialState? = credentials()?.takeIf {
        it.mode == Mode.TRUSTED && it.structureGeneration == request.structureGeneration &&
            it.people[request.person]?.generation == request.credentialGeneration
    }
    private fun sameBoot(preparedAt: BootTime): Boolean = clock.checkpoint()?.boot?.let {
        it.bootId == preparedAt.bootId && it.elapsedMillis >= preparedAt.elapsedMillis
    } == true

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
