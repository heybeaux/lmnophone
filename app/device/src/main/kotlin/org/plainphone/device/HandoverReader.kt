// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.plainphone.device.authority.DeviceOwnerReader
import org.plainphone.policy.*

enum class DebuggingKind { USB, WIRELESS }

/** Reads once on the policy dispatcher. No apply, repair, clock, journal or setting writes. */
class HandoverReader(
    private val owner: DeviceOwnerReader,
    private val decide: () -> PolicyPlan?,
    private val port: DevicePolicyPort,
    private val emergencyRoute: (PolicyPlan) -> Boolean?,
    private val debugging: (DebuggingKind) -> Int?,
    /** Preview plans also exempt non-floor system screens; use the actual OS floor there. */
    private val emergencyFloor: (PolicyPlan) -> Set<org.plainphone.core.PackageName>? = { it.neverHide },
) {
    fun read(): HandoverReport {
        // One fresh immutable plan per check, never a cached last successful apply.
        val plan = try { decide() } catch (_: Exception) { null }
        return HandoverCheck.read { item -> when (item) {
            HandoverItem.OWNER -> owner.isDeviceOwner()
            HandoverItem.APPROVED_APPS -> plan?.let(::approvedApps)
            HandoverItem.EMERGENCY -> plan?.let(::emergency)
            HandoverItem.USB_DEBUGGING -> debugging(DebuggingKind.USB)?.let { it == 0 }
            HandoverItem.WIRELESS_DEBUGGING -> debugging(DebuggingKind.WIRELESS)?.let { it == 0 }
        } }
    }
    private fun approvedApps(plan: PolicyPlan): Boolean? {
        val expected = plan.expected
        val wanted = expected.lockTaskPackages ?: return null
        val hidden = expected.hidden ?: return null
        val actual = port.lockTaskPackages()
        // The old apply readback allows packages absent after uninstall. A self-check must not
        // treat missing facts as success; read every required package without short-circuiting.
        val hiddenStates = hidden.map { port.packageHidden(it) ?: return null }
        val shownStates = expected.unhiddenAndEnabled.map { port.packageState(it) ?: return null }
        val readback = ReadbackPolicyApplier(port).readBackOrNull(plan) ?: return null
        return !plan.needsAttention && actual == wanted && hiddenStates.all { it } &&
            shownStates.all { !it.hidden && !it.suspended && it.enabled } && readback
    }
    private fun emergency(plan: PolicyPlan): Boolean? {
        val floor = emergencyFloor(plan) ?: return null
        if (floor.isEmpty() || plan.emergencyUi.isEmpty()) return null
        val states = floor.map { port.packageState(it) ?: return null }
        val route = emergencyRoute(plan) ?: return null
        return !plan.needsAttention && plan.neverHide.containsAll(floor) && states.all { !it.hidden && !it.suspended && it.enabled } && route
    }
}
