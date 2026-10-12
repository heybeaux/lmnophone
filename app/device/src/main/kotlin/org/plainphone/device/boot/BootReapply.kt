// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.boot

import org.plainphone.core.PackageName
import org.plainphone.device.ApplyResult
import org.plainphone.device.DevicePolicyPort
import org.plainphone.device.ReadbackPolicyApplier
import org.plainphone.policy.ApplyStage
import org.plainphone.policy.ApplyStep
import org.plainphone.policy.ExpectedPolicyState
import org.plainphone.policy.FloorResult
import org.plainphone.policy.JournalStore
import org.plainphone.policy.LockTaskFeature
import org.plainphone.policy.PolicyPlan
import org.plainphone.policy.Restriction
import org.plainphone.policy.SessionAccess
import org.plainphone.policy.SessionController
import org.plainphone.policy.SessionEffects
import org.plainphone.policy.SweepMode

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
    /** The saved flip-failure record could not be read ([FlipFailures.unknown]). */
    val flipRecordUnknown: Boolean = false,
    /**
     * What fallback containment never suspends: the lock-task UI list, the never-hide dependencies and
     * the emergency floor of this pass's plan (the OS-only floor without one), plus Home.
     */
    val keep: Set<PackageName> = emptySet(),
) {
    /** Show "Plainphone needs attention" (Home still draws when [ready]). */
    val needsAttention: Boolean get() =
        planNeedsAttention || apply?.needsAttention != false || disabledFloor.isNotEmpty() || flipRecordUnknown
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
 * guard (a failed revoke, a failed stage, or a throw from the journal, the revoke or onStart),
 * [ReadbackPolicyApplier.repairEmergency] still runs them. When no plan could be built (facts or
 * decide threw), it runs a plan-independent one ([fallbackPlan]): the compiled restrictions and
 * features and the floor from OS sources only ([floor]). It unhides and re-adds lock-task membership
 * and never hides. During a committing turn-off, or when that cannot be read, it repairs only the floor.
 *
 * Unresolved flip failures are kept in [flipFailures] (PLA-241 finding 4). A save that is not
 * acknowledged (false or a throw) is retried on every later pass until one is (PLA-264 finding 2); an
 * unreadable record keeps attention until acknowledged (finding 3), and a newly read unknown
 * state is saved the same way (PLA-307 finding 3). Notice storage never stops a
 * repair. Enabled state is read for every expected package after the final readback (finding 5).
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
    /** The emergency floor from OS-managed sources only, for [fallbackPlan]. Null or a throw: no floor packages. */
    private val floor: () -> FloorResult? = { null },
    /** Plainphone's own package: kept in lock task by [fallbackPlan] so Home can still enter it. */
    private val home: PackageName? = null,
    /** Whether a turn-off is committing. A throw counts as yes: the fallback then repairs only the floor. */
    private val turnOffCommitting: () -> Boolean = { false },
    private val flipFailures: FlipFailureStore = InMemoryFlipFailureStore(),
) {
    private val applier = ReadbackPolicyApplier(port, onFailure)
    private var plan: PolicyPlan? = null
    private var applied: ApplyResult? = null
    private val runFlipFailures = mutableSetOf<PackageName>()
    private val runFlipped = mutableSetOf<PackageName>()
    /** The flip-failure state this process knows; null until the store is first read. */
    private var unresolved: FlipFailures? = null
    /** [unresolved] has not been acknowledged durable by [flipFailures] yet. */
    private var unsaved = false

    val sessions = SessionController(journal, Effects(), { null }, null, { _, _ -> false })

    fun run(locked: Boolean): BootOutcome {
        applied = null
        runFlipFailures.clear()
        runFlipped.clear()
        plan = attempt { filter.apply(decide(locked)) }
        val ready = attempt { sessions.onStart() } == true
        // Emergency repair does not depend on the revoke, on an earlier stage or on a plan. The journal
        // stays revoke-pending and the run stays not ready, so nothing is handed back.
        val repairPlan = plan ?: fallbackPlan()
        if (!ready && applied?.applied?.containsAll(ReadbackPolicyApplier.EMERGENCY_STAGES) != true) {
            attempt { applier.repairEmergency(repairPlan) }?.let(::record)
        }
        val failures = rememberFlipFailures()
        val floorPackages = repairPlan.emergencyUi + repairPlan.neverHide
        val nowDisabled = attempt { applier.disabled(repairPlan) }.orEmpty()
        val disabledFloor = (applied?.unrepairable.orEmpty() + nowDisabled) intersect floorPackages
        return BootOutcome(
            ready, locked, applied?.copy(flipFailures = failures.names), filter.withheld, disabledFloor,
            plan?.needsAttention ?: true, failures.unknown,
            repairPlan.lockTaskPackages + repairPlan.neverHide + repairPlan.emergencyUi + listOfNotNull(home),
        )
    }

    /** The trusted person saw the flip-failure notice: clear it (the wiring slice calls this). */
    fun acknowledgeFlipFailures() {
        unresolved = FlipFailures.NONE
        save(FlipFailures.NONE)
    }

    /**
     * Earlier unresolved failures plus this run's, minus packages whose flip read back this run. An
     * unreadable store is unknown. Saved when it changed, when an earlier save was not acknowledged, or
     * when this process first reads unknown: a read that failed only this once must not let a restart
     * read an empty record and drop the attention (PLA-307 finding 3).
     *
     * Durable once a save succeeds. Until then the running process keeps the record in memory, shows
     * attention and retries the save on every pass. Known limit (copy-test spec, "The attention
     * record"): if every save fails and the process dies first, the record is lost. Emergency repair
     * never waits for it.
     */
    private fun rememberFlipFailures(): FlipFailures {
        val firstRead = unresolved == null
        val earlier = unresolved ?: attempt { flipFailures.read() } ?: FlipFailures.UNKNOWN
        val now = earlier.copy(names = earlier.names - runFlipped + runFlipFailures)
        unresolved = now
        if (now != earlier || unsaved || (firstRead && now.unknown)) save(now)
        return now
    }

    private fun save(state: FlipFailures) {
        unsaved = attempt { flipFailures.save(state) } != true
    }

    private fun record(result: ApplyResult) {
        applied = result
        runFlipFailures += result.flipFailures
        runFlipped += result.flipped
    }

    /** Restrictions, floor and guard without the policy engine: fixed sets and OS-sourced floor only. */
    private fun fallbackPlan(): PolicyPlan {
        val emergency = attempt { floor() }
        val emergencyUi = emergency?.emergencyUi.orEmpty()
        val neverHide = emergency?.neverHide.orEmpty() + emergencyUi
        val lockdown = attempt { !turnOffCommitting() } == true
        val restrictions = if (lockdown) Restriction.entries.toSet() else emptySet()
        val features = LockTaskFeature.entries.toSet()
        return filter.apply(PolicyPlan(
            hidden = emptySet(), unhide = neverHide, neverHide = neverHide, emergencyUi = emergencyUi,
            lockTaskPackages = emptySet(), features = if (lockdown) features else emptySet(), restrictions = restrictions,
            steps = ReadbackPolicyApplier.EMERGENCY_STAGES.map { ApplyStep(it) },
            expected = ExpectedPolicyState(
                hidden = null, unhiddenAndEnabled = neverHide, unsuspended = neverHide,
                lockTaskPackages = if (lockdown) emergencyUi + listOfNotNull(home) else null,
                features = if (lockdown) features else null,
                restrictions = if (lockdown) restrictions else null,
                home = home.takeIf { lockdown },
                systemInputMethodsOnly = null, systemAccessibilityServicesOnly = null,
            ),
            sweepMode = SweepMode.NONE, refusedDestruction = emptyList(), allowedDestruction = emptyList(),
            classifierConflicts = emptySet(), changedPackages = emptySet(), needsAttention = true, notifyBuildReview = false,
        ))
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
            record(result)
            return result.complete
        }

        override fun readBackPolicy(): Boolean = plan?.let { attempt { applier.readBack(it) } } == true
    }

    /** Any throw except an [Error] is a failed step (a SecurityException, an IOException from Java). */
    private inline fun <T> attempt(call: () -> T): T? = try {
        call()
    } catch (e: Exception) {
        null
    }
}
