// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

/** Stable wire names; order keeps grants after visibility and emergency access until lock task ends. */
enum class GiveBackKind {
    RESTRICTION, VISIBLE, UNSUSPENDED, UNINSTALLABLE, PERMISSION, PERMISSION_POLICY,
    INPUT_METHODS, ACCESSIBILITY, KEYGUARD_FEATURES, RESET_PROTECTION, PLAY_RESTRICTIONS,
    SYSTEM_UPDATE, LOCK_TASK, PREFERRED_HOME,
}
data class GiveBackAction(val kind: GiveBackKind, val target: String = "", val detail: String = "")
enum class GiveBackStatus { PENDING, DONE, FAILED }
data class GiveBackEntry(val action: GiveBackAction, val status: GiveBackStatus = GiveBackStatus.PENDING)
class GiveBackJournal(val requestId: String, entries: List<GiveBackEntry>) {
    val entries = immutableList(entries)
    init { require(requestId.isNotBlank()); require(entries.map { it.action }.distinct().size == entries.size) }
}

/** Android effects only. Inventory must fail closed if any relevant state cannot be enumerated. */
interface GiveBackPort {
    fun isDeviceOwner(): Boolean?
    fun inventory(): List<GiveBackAction>
    /** Idempotent, includes synchronous mitigation B after each unhide, even when its readback fails. */
    fun restore(action: GiveBackAction)
    fun readBack(action: GiveBackAction): Boolean
    fun anotherHomeResolves(): Boolean
    fun systemDialerResolves(): Boolean
    fun removeDeviceOwner()
}
enum class GiveBackOutcome { OFF, FAILED, NOT_READY, LOW_BATTERY, IGNORED }
data class GiveBackResult(val outcome: GiveBackOutcome, val remaining: Int = 0)

/**
 * Consumes existing COMMITTING authority, never starts it. Run on the shared privileged dispatcher.
 * Inventory and every result are durable before the next effect. Failed items stay held for Retry;
 * process death resumes only PENDING entries. An effect interrupted before its save is repeated.
 * No rollback and no policy reapply. The caller keeps all other policy writers out while committing.
 */
class GiveBackController(private val store: WaitingChangeStore, private val port: GiveBackPort) {
    fun run(requestId: String, battery: BatteryState, retry: Boolean = false): GiveBackResult {
        var state = store.read() ?: return GiveBackResult(GiveBackOutcome.NOT_READY)
        val op = state.operation ?: return GiveBackResult(GiveBackOutcome.IGNORED)
        if (op.kind != WaitingChangeKind.TURN_OFF || op.identity.requestId != requestId ||
            op.phase !in setOf(WaitingPhase.COMMITTING, WaitingPhase.COMPLETED)) return GiveBackResult(GiveBackOutcome.IGNORED)
        val owner = attempt { port.isDeviceOwner() } ?: return GiveBackResult(GiveBackOutcome.NOT_READY)
        if (!owner) return complete(state)
        if (op.phase != WaitingPhase.COMMITTING) return GiveBackResult(GiveBackOutcome.FAILED, 1)
        if (!battery.allowsDiscretionaryUnhide) return GiveBackResult(GiveBackOutcome.LOW_BATTERY)
        var journal: GiveBackJournal = state.giveBack?.takeIf { it.requestId == requestId } ?: run {
            val inventory = attempt { port.inventory() } ?: return GiveBackResult(GiveBackOutcome.NOT_READY)
            val initial = GiveBackJournal(requestId, inventory.distinct().sortedBy { it.kind.ordinal }.map(::GiveBackEntry))
            state = save(state, initial) ?: return GiveBackResult(GiveBackOutcome.NOT_READY)
            initial
        }
        if (retry) {
            journal = GiveBackJournal(requestId, journal.entries.map {
                if (it.status == GiveBackStatus.FAILED) it.copy(status = GiveBackStatus.PENDING) else it
            })
            state = save(state, journal) ?: return GiveBackResult(GiveBackOutcome.NOT_READY)
        }
        for (index in journal.entries.indices) {
            val entry = journal.entries[index]
            if (entry.status != GiveBackStatus.PENDING) continue
            // A throwing write is uncertain even if a later read happens to match.
            val restored = attempt { port.restore(entry.action); true } == true
            val read = attempt { port.readBack(entry.action) } == true
            journal = GiveBackJournal(requestId, journal.entries.mapIndexed { i, item ->
                if (i == index) item.copy(status = if (restored && read) GiveBackStatus.DONE else GiveBackStatus.FAILED) else item
            })
            state = save(state, journal) ?: return GiveBackResult(GiveBackOutcome.NOT_READY)
        }
        // Revalidate every earlier success, including after restart; never trust only a saved DONE.
        for (index in journal.entries.indices) {
            val entry = journal.entries[index]
            if (entry.status != GiveBackStatus.DONE || attempt { port.readBack(entry.action) } == true) continue
            journal = GiveBackJournal(requestId, journal.entries.mapIndexed { i, item ->
                if (i == index) item.copy(status = GiveBackStatus.FAILED) else item
            })
            state = save(state, journal) ?: return GiveBackResult(GiveBackOutcome.NOT_READY)
        }
        // A package/permission writer may have appeared after the initial inventory. Journal any
        // new obligations before returning; never clear ownership with an incomplete restore list.
        val live = attempt { port.inventory() } ?: return GiveBackResult(GiveBackOutcome.NOT_READY)
        val added = live.distinct().filter { action -> journal.entries.none { it.action == action } }
        if (added.isNotEmpty()) {
            journal = GiveBackJournal(requestId, (journal.entries + added.map(::GiveBackEntry)).sortedBy { it.action.kind.ordinal })
            save(state, journal) ?: return GiveBackResult(GiveBackOutcome.NOT_READY)
            return GiveBackResult(GiveBackOutcome.NOT_READY)
        }
        val failed = journal.entries.count { it.status != GiveBackStatus.DONE }
        if (failed > 0) return GiveBackResult(GiveBackOutcome.FAILED, failed)
        val successorFailures = listOf(attempt { port.anotherHomeResolves() }, attempt { port.systemDialerResolves() }).count { it != true }
        if (successorFailures > 0) return GiveBackResult(GiveBackOutcome.FAILED, successorFailures)
        attempt { port.removeDeviceOwner() }
        return if (attempt { port.isDeviceOwner() } == false) complete(state) else GiveBackResult(GiveBackOutcome.FAILED, 1)
    }

    private fun save(before: WaitingState, journal: GiveBackJournal): WaitingState? {
        if (before.revision == Long.MAX_VALUE) return null
        val next = before.replacing(before.revision + 1, giveBack = journal)
        if (attempt { store.compareAndSet(before, next) } != true) return null
        return store.read()?.takeIf { it.revision == next.revision && it.storageGeneration == next.storageGeneration &&
            it.giveBack?.requestId == journal.requestId && it.giveBack.entries == journal.entries }
    }
    private fun complete(state: WaitingState): GiveBackResult {
        val op = state.operation!!
        if (op.phase != WaitingPhase.COMPLETED && state.revision < Long.MAX_VALUE) {
            attempt { store.compareAndSet(state, state.replacing(state.revision + 1, op.withPhase(WaitingPhase.COMPLETED))) }
        }
        // Actual absence is Off even if completion persistence must be retried on the next startup.
        return GiveBackResult(GiveBackOutcome.OFF)
    }
    private inline fun <T> attempt(block: () -> T): T? = try { block() } catch (_: Exception) { null }
}
