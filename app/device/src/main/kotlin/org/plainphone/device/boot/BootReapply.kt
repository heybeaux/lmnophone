// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.boot

import org.plainphone.core.PackageName
import org.plainphone.device.ApplyResult
import org.plainphone.device.DevicePolicyPort
import org.plainphone.device.ReadbackPolicyApplier
import org.plainphone.policy.ApplyStage
import org.plainphone.policy.JournalStore
import org.plainphone.policy.PolicyPlan
import org.plainphone.policy.Restriction
import org.plainphone.policy.SessionAccess
import org.plainphone.policy.SessionController
import org.plainphone.policy.SessionEffects

/**
 * What one re-apply achieved.
 * @property ready every step was written and read back and the journal is clear. Home may show only then.
 * @property apply the plan's apply; after a failed run, the emergency repair's result. Null when no plan could be built.
 * @property withheld restrictions the build's [RestrictionFilter] left out.
 */
data class BootOutcome(
    val ready: Boolean,
    val locked: Boolean,
    val apply: ApplyResult?,
    val withheld: Set<Restriction>,
    /** Emergency-UI or never-hide packages that stay disabled. Calls stay reachable through the rest. */
    val disabledFloor: Set<PackageName> = emptySet(),
    /** [PolicyPlan.needsAttention]: baseline after corrupt policy, no system dialer, and similar. */
    val planNeedsAttention: Boolean = false,
) {
    /** Show "Plainphone needs attention" (Home still draws when [ready]). */
    val needsAttention: Boolean get() =
        planNeedsAttention || apply?.needsAttention != false || disabledFloor.isNotEmpty()
    /** Setup must not finish while a floor package is disabled. */
    val setupMayFinish: Boolean get() = ready && disabledFloor.isEmpty()
}

/**
 * The boot and start re-apply (device-lockdown 4.11 and 4.3). Every run goes through
 * [SessionController.onStart]: it latches revocation, revokes every session relaxation (all of
 * [sessionPackages], whatever the journal says, so a damaged journal revokes them all), applies the
 * plan through [ReadbackPolicyApplier], reads it back, and only then clears the journal.
 *
 * - Locked boot ([run] with `locked = true`): [decide] must return the locked-boot plan, which has
 *   only restrictions, the emergency floor (with the emergency UI's lock-task membership) and the
 *   never-hide guard. The journal store must be in device-protected storage.
 * - Unlocked start: the full plan, ending with lock task and Home.
 *
 * If the run is not ready and the ordinary apply did not complete restrictions, the floor and the
 * guard (a failed revoke, a failed stage), [ReadbackPolicyApplier.repairEmergency] still runs them.
 *
 * Revoking a relaxation takes the package out of lock task and hides it, unless the plan's never-hide
 * list names it. With no plan the packages still leave lock task, and the run is not ready.
 * The controller has no clock or authority, so it cannot open a session: [SessionEffects.open] is
 * refused. Run on the one serialized policy dispatcher.
 */
class BootReapply(
    journal: JournalStore,
    private val port: DevicePolicyPort,
    private val sessionPackages: Set<PackageName>,
    private val decide: (locked: Boolean) -> PolicyPlan,
    private val filter: RestrictionFilter,
    onFailure: (ApplyStage, Throwable?) -> Unit = { _, _ -> },
) {
    private val applier = ReadbackPolicyApplier(port, onFailure)
    private var plan: PolicyPlan? = null
    private var applied: ApplyResult? = null

    val sessions = SessionController(journal, Effects(), { null }, null, { _, _ -> false })

    fun run(locked: Boolean): BootOutcome {
        applied = null
        plan = try {
            filter.apply(decide(locked))
        } catch (e: RuntimeException) {
            null
        }
        val ready = sessions.onStart()
        // Emergency repair does not depend on the revoke or on an earlier stage. The journal stays
        // revoke-pending and the run stays not ready, so nothing is handed back.
        val current = plan
        if (!ready && current != null && applied?.applied?.containsAll(ReadbackPolicyApplier.EMERGENCY_STAGES) != true) {
            attempt { applier.repairEmergency(current) }?.let { applied = it }
        }
        val floor = current?.let { it.emergencyUi + it.neverHide }.orEmpty()
        val disabledFloor = applied?.unrepairable.orEmpty() intersect floor
        return BootOutcome(ready, locked, applied, filter.withheld, disabledFloor, current?.needsAttention == true)
    }

    private inner class Effects : SessionEffects {
        override fun revokeAllRelaxations(): Boolean = attempt {
            val keep = plan?.neverHide
            val inLockTask = port.lockTaskPackages()
            if (inLockTask.any { it in sessionPackages }) port.setLockTaskPackages(inLockTask - sessionPackages)
            if (keep != null) {
                for (name in sessionPackages - keep) {
                    if (port.packageState(name)?.hidden == false) port.setHidden(name, true)
                }
            }
            keep != null && port.lockTaskPackages().none { it in sessionPackages } &&
                (sessionPackages - keep).all { port.packageState(it)?.hidden != false }
        } == true

        override fun open(access: SessionAccess): Boolean = false

        override fun protectedStateCheck(): Boolean {
            val current = plan ?: return false
            val result = attempt { applier.apply(current) } ?: return false
            applied = result
            return result.complete
        }

        override fun readBackPolicy(): Boolean = plan?.let { attempt { applier.readBack(it) } } == true
    }

    private inline fun <T> attempt(call: () -> T): T? = try {
        call()
    } catch (_: RuntimeException) {
        null
    }
}
