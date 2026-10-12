// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import org.plainphone.policy.BatteryState
import org.plainphone.policy.PersonAuthorityConsumer
import org.plainphone.core.RunningClock
import org.plainphone.policy.WaitingChangeController
import org.plainphone.policy.WaitingNotice
import org.plainphone.policy.WaitingOutcome
import org.plainphone.policy.WaitingPhase

/**
 * The waiting operation's checkpoints and the phone's PIN-free cancel, on the one authority [store]
 * (policy README, "Waiting changes"). Build one per process and call it only from the common
 * serialized dispatcher: the [WaitingChangeController] inside keeps the process's cancel latch.
 *
 * - [checkpoint] ticks a PENDING operation: it checkpoints the proven [clock], cancels on a recorded
 *   cancel or a changed dependency, and moves a matured wait to COMMITTING. Every policy pass calls it
 *   before deciding, so a wait that commits is applied in the same pass, and a credential change is
 *   checked at once by the pass that follows it.
 * - Nothing pending (no store, a vacant or finished slot, or an unreadable record) means no checkpoint
 *   and no write. An unreadable record belongs to reconciliation.
 * - An unknown [battery] counts as low, so it never lets a turn-off commit.
 * - [periodMillis] is the checkpoint alarm's period: [PENDING_PERIOD_MILLIS] while a wait is pending.
 * - [cancel] needs no PIN: the phone's own cancel of the request it shows.
 * - [notice] is what Home shows for the operation (policy NoticeLine input).
 *
 * Nothing here starts a wait, skips one or completes a committing one.
 */
class WaitingCheckpoints(
    private val store: JointAuthorityStore,
    private val clock: RunningClock,
    private val battery: () -> BatteryState?,
    authority: PersonAuthorityConsumer,
) {
    private val controller = WaitingChangeController(
        store.waitingStore, { store.credentials.read() }, clock, authority, store.identities, store.cancelIntents,
    )

    /** The pending operation's request ID; null when nothing is pending or the record does not read. */
    fun pendingRequest(): String? =
        store.waitingStore.read()?.operation?.takeIf { it.phase == WaitingPhase.PENDING }?.identity?.requestId

    /** [PENDING_PERIOD_MILLIS] while a wait is pending, else [idleMillis]. */
    fun periodMillis(idleMillis: Long): Long = if (pendingRequest() != null) PENDING_PERIOD_MILLIS else idleMillis

    /** One checkpoint of the pending operation; null when nothing is pending. */
    fun checkpoint(): WaitingOutcome? {
        val requestId = pendingRequest() ?: return null
        return controller.tick(requestId, battery() ?: UNKNOWN_BATTERY)
    }

    /**
     * The Home notice projection (`NoticeLine` input) of the current operation; null with none, or once it
     * finished. A pending wait carries the proven time still to run; with no clock reading the whole wait
     * remains, so Home never names an earlier time. Committing or rolling back carries 0.
     */
    fun notice(): WaitingNotice? {
        val op = store.waitingStore.read()?.operation ?: return null
        if (op.phase == WaitingPhase.COMPLETED || op.phase == WaitingPhase.CANCELLED) return null
        val remaining = if (op.phase != WaitingPhase.PENDING) 0L else
            clock.checkpoint()?.proven?.let { (WAIT_MILLIS - it.since(op.startedAt)).coerceIn(0L, WAIT_MILLIS) } ?: WAIT_MILLIS
        return WaitingNotice(op.identity.requestId, op.kind, op.phase, remaining)
    }

    /** The phone's own cancel of [requestId]. No PIN. */
    fun cancel(requestId: String): WaitingOutcome = controller.cancel(requestId)

    companion object {
        const val PENDING_PERIOD_MILLIS = 5 * 60 * 1000L
        /** The 24 hours of proven running time every wait needs (the controller's maturity rule). */
        const val WAIT_MILLIS = 86_400_000L
        /** Treated as low and not charging. */
        private val UNKNOWN_BATTERY = BatteryState(0, false)
    }
}
