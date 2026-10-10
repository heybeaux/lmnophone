// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.plainphone.core.PackageName
import org.plainphone.policy.ApplyStage
import org.plainphone.policy.ExpectedPolicyState
import org.plainphone.policy.PolicyPlan

/**
 * device-lockdown, Order of application, steps 2–7. Runs [PolicyPlan.steps] in order and reads each
 * step back against [PolicyPlan.expected] before the next one. A null expected field is outside the
 * plan: it is neither written nor read. The first failed write or readback stops the apply.
 *
 * - [ApplyStage.REVOKE_JOURNAL] belongs to the session controller and is skipped here.
 * - Restrictions are only added, never cleared; the readback requires every expected one.
 * - The floor repairs the emergency UI and every never-hide package the plan unhides (the plan does
 *   not carry the floor's own set separately), and restores the emergency UI's lock-task membership.
 * - The never-hide guard fails a plan that would hide a never-hide package. Hides skip that list
 *   whatever the plan says. [PolicyPlan.allowedDestruction] is a guard result and is never run.
 * - Every real unhide is followed at once by its own flip of Plainphone's flush component to the
 *   opposite of its current state (mitigation B, best effort). A failed flip is retried once, then
 *   reported; the unhide is never undone and the apply continues.
 */
class ReadbackPolicyApplier(private val port: DevicePolicyPort) : PolicyApplier {

    override fun apply(plan: PolicyPlan): ApplyResult {
        val run = Run(plan)
        val applied = mutableListOf<ApplyStage>()
        for (step in plan.steps) {
            if (step.stage == ApplyStage.REVOKE_JOURNAL) continue
            val ok = try {
                run.write(step.stage)
                !step.readbackRequired || run.readBack(step.stage)
            } catch (e: Exception) {
                false
            }
            if (!ok) return run.result(applied, step.stage)
            applied += step.stage
        }
        return run.result(applied, null)
    }

    /** Reads every readback step of [plan] against Android now and writes nothing. A throwing read is false. */
    fun readBack(plan: PolicyPlan): Boolean {
        val run = Run(plan)
        return plan.steps.all { step ->
            !step.readbackRequired || try { run.readBack(step.stage) } catch (e: Exception) { false }
        }
    }

    private inner class Run(private val plan: PolicyPlan) {
        private val expected: ExpectedPolicyState = plan.expected
        private val absent = mutableSetOf<PackageName>()
        private val flipFailures = mutableSetOf<PackageName>()

        fun result(applied: List<ApplyStage>, failed: ApplyStage?) =
            ApplyResult(applied.toList(), failed, absent.toSet(), flipFailures.toSet())

        private val floorPackages get() = plan.emergencyUi + (plan.unhide intersect plan.neverHide)
        private val floorLockTask get() = expected.lockTaskPackages?.let { plan.emergencyUi intersect it }
        private val toHide get() = expected.hidden?.let { plan.hidden - plan.neverHide }

        fun write(stage: ApplyStage) {
            when (stage) {
            ApplyStage.REVOKE_JOURNAL -> Unit
            ApplyStage.RESTRICTIONS -> {
                expected.restrictions?.let { plan.restrictions.forEach(port::addRestriction) }
                expected.systemInputMethodsOnly?.let(port::setSystemInputMethodsOnly)
                expected.systemAccessibilityServicesOnly?.let(port::setSystemAccessibilityServicesOnly)
            }
            ApplyStage.EMERGENCY_FLOOR -> {
                show(floorPackages)
                floorLockTask?.let { want ->
                    val now = port.lockTaskPackages()
                    if (!now.containsAll(want)) port.setLockTaskPackages(now + want)
                }
            }
            ApplyStage.NEVER_HIDE_GUARD -> if (guardHolds()) show(plan.neverHide)
            ApplyStage.HIDES -> {
                toHide?.forEach { name ->
                    if (port.packageState(name)?.hidden == false) port.setHidden(name, true)
                }
                show(plan.unhide - plan.neverHide)
            }
            ApplyStage.LOCK_TASK -> {
                expected.lockTaskPackages?.let { port.setLockTaskPackages(plan.lockTaskPackages) }
                expected.features?.let { port.setLockTaskFeatures(plan.features) }
            }
            ApplyStage.HOME -> expected.home?.let(port::setHome)
            }
        }

        fun readBack(stage: ApplyStage): Boolean = when (stage) {
            ApplyStage.REVOKE_JOURNAL -> true
            ApplyStage.RESTRICTIONS ->
                expected.restrictions.let { it == null || port.restrictions().containsAll(it) } &&
                    expected.systemInputMethodsOnly.let { it == null || port.systemInputMethodsOnly() == it } &&
                    expected.systemAccessibilityServicesOnly.let { it == null || port.systemAccessibilityServicesOnly() == it }
            ApplyStage.EMERGENCY_FLOOR ->
                shown(floorPackages) && floorLockTask.let { it == null || port.lockTaskPackages().containsAll(it) }
            ApplyStage.NEVER_HIDE_GUARD -> guardHolds() && shown(plan.neverHide)
            ApplyStage.HIDES ->
                expected.hidden.let { it == null || it.all { name -> port.packageState(name)?.hidden != false } } &&
                    shown(plan.unhide - plan.neverHide)
            ApplyStage.LOCK_TASK ->
                expected.lockTaskPackages.let { it == null || port.lockTaskPackages() == it } &&
                    expected.features.let { it == null || port.lockTaskFeatures() == it }
            ApplyStage.HOME -> expected.home.let { it == null || port.home() == it }
        }

        private fun guardHolds() = plan.neverHide.none { it in plan.hidden || expected.hidden?.contains(it) == true }

        /** Unsuspends, enables and unhides only what [expected] asks for; each unhide gets its own flip. */
        private fun show(names: Set<PackageName>) {
            for (name in names) {
                val wantShown = name in expected.unhiddenAndEnabled
                val wantUnsuspended = name in expected.unsuspended
                if (!wantShown && !wantUnsuspended) continue
                val now = port.packageState(name)
                if (now == null) { absent += name; continue }
                if (wantUnsuspended && now.suspended) port.unsuspend(name)
                if (wantShown && !now.enabled) port.enable(name)
                if (wantShown && now.hidden) {
                    port.setHidden(name, false)
                    flush(name)
                }
            }
        }

        private fun shown(names: Set<PackageName>) = names.all { name ->
            val now = port.packageState(name) ?: return@all true // reported in absent by show()
            (name !in expected.unhiddenAndEnabled || (!now.hidden && now.enabled)) &&
                (name !in expected.unsuspended || !now.suspended)
        }

        /** One real state change of the flush component, read back, retried once. Never a same-state call. */
        private fun flush(unhidden: PackageName) {
            repeat(2) {
                val before = runCatching { port.flushComponentEnabled() }.getOrNull() ?: run {
                    flipFailures += unhidden
                    return
                }
                val ok = runCatching {
                    port.setFlushComponentEnabled(!before)
                    port.flushComponentEnabled() == !before
                }.getOrDefault(false)
                if (ok) return
            }
            flipFailures += unhidden
        }
    }
}
