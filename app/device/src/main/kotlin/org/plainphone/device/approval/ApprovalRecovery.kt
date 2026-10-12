// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.plainphone.device.SerialDispatcher
import org.plainphone.policy.AppApprovalController
import org.plainphone.policy.AppApprovalOutcome
import org.plainphone.policy.AppApprovalState
import org.plainphone.policy.AppApprovalStore

/**
 * The app's one [AppApprovalController], on the shared [dispatcher] (PLA-281). Every controller call runs
 * whole as one dispatcher task: startup recovery, retries and trusted-person operations alike.
 *
 * Retry triggers. Every policy pass calls [beforePass] before it decides, so boot, user unlock, Home
 * start, package events, the alarm and the pass after a trusted-person action each give a stuck
 * operation one retry. The first unlocked pass runs [AppApprovalController.onStart]; until one returns
 * other than NOT_READY, later passes run it again only while the record reads. Startup revokes every
 * session window, and with no record (no trusted people yet) there is nothing to recover, so repeating
 * it would only close a window on each package event. Once startup succeeds, passes run
 * [AppApprovalController.resume].
 *
 * Ordinary passes ([forPass]). Every reconcile decides with the stored approval state, but only while no
 * operation is pending. A failed activation contains the attempted plan's discretionary apps, including
 * other approved apps, and nothing records which. Any phase can follow such a failure (a COMMITTED op can
 * be withdrawn), so with any pending operation, or a record that does not read, a pass decides with no
 * approved app at all. Only the controller's verified activation brings contained apps back; after it
 * the next pass keeps them.
 *
 * Hand-back gate ([handback]). A pass that decided with approvals hands the phone back only if those
 * approvals are still the stored ones with nothing pending, after its apply and readback.
 */
class ApprovalRecovery(
    private val dispatcher: SerialDispatcher,
    private val store: AppApprovalStore,
    private val controller: AppApprovalController,
) {
    /** Startup recovery has once returned other than NOT_READY in this process. Dispatcher only. */
    private var started = false
    /** Startup recovery has run at least once in this process. Dispatcher only. */
    private var attempted = false

    /**
     * One controller retry before a pass decides; null when none ran. None for a locked pass: the
     * controller's plans need the full policy input, which exists only after unlock, and the locked plan
     * never unhides an approved app.
     */
    fun beforePass(locked: Boolean): AppApprovalOutcome? = dispatcher.run {
        if (locked) null
        else try {
            when {
                started -> controller.resume()
                attempted && read() == null -> null
                else -> {
                    attempted = true
                    controller.onStart().also { started = it != AppApprovalOutcome.NOT_READY }
                }
            }
        } catch (_: RuntimeException) {
            AppApprovalOutcome.NOT_READY
        }
    }

    /** The approval state an ordinary pass decides with; null (no approved app) while anything is pending. */
    fun forPass(): AppApprovalState? = dispatcher.run { read()?.takeIf { it.pending == null } }

    /** Whether an approval operation is pending; null when the record does not read. */
    fun pending(): Boolean? = dispatcher.run { read()?.let { it.pending != null } }

    /** Whether a pass that decided with [used] (from [forPass]) may hand the phone back. */
    fun handback(used: AppApprovalState?): Boolean = dispatcher.run {
        used == null || read()?.let { it.pending == null && it.revision == used.revision } == true
    }

    /** Runs one whole trusted-person operation on the controller as one dispatcher task. */
    fun <T> operation(block: AppApprovalController.() -> T): T = dispatcher.run { controller.block() }

    private fun read(): AppApprovalState? = try {
        store.read()
    } catch (_: RuntimeException) {
        null
    }
}
