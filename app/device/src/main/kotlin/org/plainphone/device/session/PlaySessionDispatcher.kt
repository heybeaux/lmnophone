// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.session

import org.plainphone.core.*
import org.plainphone.device.authority.JointAuthorityStore
import org.plainphone.device.authority.PinEntry
import org.plainphone.device.authority.PreviewPinAccess
import org.plainphone.policy.*

data class PlaySessionView(val id: String? = null, val countdownMillis: Long? = null,
    val readyMillis: Long? = null, val active: Boolean = false, val accessMillis: Long? = null,
    val handbackReady: Boolean = false, val left: Int = 0, val limit: Int = 0,
    val nextTurnMillis: Long? = null, val waitMillis: Long = 600_000)

sealed interface PlayRequestResult {
    data object Counting : PlayRequestResult
    data object Busy : PlayRequestResult
    data object LimitReached : PlayRequestResult
    data object Unavailable : PlayRequestResult
    data class Wrong(val remainingMillis: Long) : PlayRequestResult
    data class Delayed(val remainingMillis: Long) : PlayRequestResult
}

/**
 * One process-local owner, called only on the application's serialized policy dispatcher.
 * Both controllers use the SAME combined device-protected elevation/journal record. The boot
 * pass receives [sessions], never a controller backed by a second standalone journal.
 * UI values and persisted grants cannot restore ownership after process death.
 */
class PlaySessionDispatcher(
    private val store: JointAuthorityStore,
    private val vault: PinVault,
    private val clock: RunningClock,
    policyInput: () -> PolicyInput?,
    battery: () -> BatteryState,
    effects: SessionEffects,
    ids: AppApprovalIdSource,
    recoveryEffects: SessionEffects = effects,
    journal: JournalStore = ElevationSessionJournal(store.elevationStore),
) {
    val sessions = SessionController(journal, recoveryEffects, clock, null,
        AuthorityConsumer(vault::consume), { store.credentials.read()?.mode == Mode.TRUSTED },
        { store.credentials.read()?.mode == Mode.SELF_MANAGED })
    private val elevation = ElevationController(store.elevationStore, store.credentials::read,
        PersonAuthorityConsumer(vault::consumeFor), clock, store::activeLimits, policyInput,
        battery, effects, ids)
    private val pins = PreviewPinAccess(store, vault)
    private var initialized = false
    var handbackReady = false
        private set

    /** Startup is restrictive. It never grants authority from decoded state. */
    fun onStart(): Boolean {
        initialized = false
        handbackReady = false
        // Elevation repairs damaged ledgers conservatively; the supervised projection cannot do so.
        if (elevation.onStart() != ElevationOutcome.IDLE) return false
        if (!sessions.onStart()) return false
        initialized = true
        handbackReady = true
        return true
    }

    /** The menu PIN is consumed by the menu. This action requires its own fresh PIN. */
    fun request(pin: CharArray): PlayRequestResult = try {
        if (!initialized || pins.entry() != PinEntry.ENTER) PlayRequestResult.Unavailable
        else {
            val record = store.elevationStore.read()
            when {
                record == null -> PlayRequestResult.Unavailable
                record.state.countdown != null || record.state.grant != null || record.journal != SessionJournal.Empty -> PlayRequestResult.Busy
                !handbackReady -> PlayRequestResult.Unavailable
                else -> {
                    val request = elevation.prepare(SELF, ElevationTarget.Play)
                    if (request == null) PlayRequestResult.Unavailable
                    else when (val auth = vault.authenticate(SELF, pin, request.action)) {
                        is PinResult.Wrong -> PlayRequestResult.Wrong(auth.remainingMillis)
                        is PinResult.Delayed -> PlayRequestResult.Delayed(auth.remainingMillis)
                        PinResult.Unavailable -> PlayRequestResult.Unavailable
                        is PinResult.Authorized -> when (observe(elevation.request(request, auth.authority))) {
                            ElevationOutcome.COUNTING -> PlayRequestResult.Counting
                            ElevationOutcome.BUSY -> PlayRequestResult.Busy
                            ElevationOutcome.LIMIT_REACHED -> PlayRequestResult.LimitReached
                            else -> PlayRequestResult.Unavailable
                        }
                    }
                }
            }
        }
    } finally { pin.fill('\u0000') }

    fun view(): PlaySessionView {
        val record = store.elevationStore.read() ?: return PlaySessionView()
        val now = clock.checkpoint() ?: return PlaySessionView()
        val limits = store.activeLimits()
        val available = record.state.ledger.availability(limits.dailyLimit, now.proven)
        val countdown = record.state.countdown?.takeIf { it.startedAt.bootId == now.boot.bootId }
        val untilReady = countdown?.let { (it.startedAt.elapsedMillis + it.durationMillis - now.boot.elapsedMillis).coerceAtLeast(0) }
        val remainingStart = countdown?.let { (it.startedAt.elapsedMillis + it.durationMillis + 300_000 - now.boot.elapsedMillis).coerceAtLeast(0) }
        val access = elevation.access()
        return PlaySessionView(countdown?.id ?: record.state.grant?.id,
            untilReady?.takeIf { it > 0 }, remainingStart?.takeIf { untilReady == 0L && it > 0 },
            access != null, access?.let { (it.deadline.elapsedMillis - now.boot.elapsedMillis).coerceAtLeast(0) },
            handbackReady, available.turnsLeft, limits.dailyLimit, available.nextTurnInMillis, limits.countdownMillis)
    }

    fun start(id: String): ElevationOutcome = observe(elevation.start(id, ElevationTarget.Play))
    fun tick(): ElevationOutcome = observe(elevation.tick())
    fun cancel(id: String): ElevationOutcome = observe(elevation.cancel(id))
    /** Closing never requires eligibility to open. Read the durable grant, not display state or live access. */
    fun endCurrent(trigger: SessionEnd): ElevationOutcome {
        val record = store.elevationStore.read() ?: return observe(ElevationOutcome.NOT_READY)
        val id = record.state.grant?.id ?: return observe(ElevationOutcome.IDLE)
        return observe(if (trigger == SessionEnd.DONE) elevation.done(id) else elevation.endPackageAccess(id, trigger))
    }
    fun done(id: String): ElevationOutcome = observe(elevation.done(id))
    fun end(id: String, trigger: SessionEnd): ElevationOutcome = observe(elevation.endPackageAccess(id, trigger))
    fun access(): SessionAccess? = elevation.access()
    fun installed(sessionId: String, name: PackageName): Boolean = elevation.installed(sessionId, name)

    private fun observe(outcome: ElevationOutcome): ElevationOutcome {
        handbackReady = outcome != ElevationOutcome.NOT_READY &&
            store.elevationStore.read()?.journal == SessionJournal.Empty
        return outcome
    }

    companion object { private val SELF = TrustedPersonId("self") }
}
