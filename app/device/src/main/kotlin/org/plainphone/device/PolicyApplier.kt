// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.plainphone.core.PackageName
import org.plainphone.policy.ApplyStage
import org.plainphone.policy.PolicyPlan

/** Applies a [PolicyPlan] through DevicePolicyManager in its apply order, reading back each step. */
fun interface PolicyApplier {
    fun apply(plan: PolicyPlan): ApplyResult
}

/**
 * What an apply achieved. [applied] lists only stages that were written and read back (or written,
 * where the step needs no readback). After [failedStage] nothing else ran, so it must never be
 * reported as applied. A returned readback is not proof the state survives a power cut: re-apply at
 * every boot.
 */
data class ApplyResult(
    val applied: List<ApplyStage>,
    val failedStage: ApplyStage?,
    /** Planned packages that are not installed, so there was nothing to show or check. */
    val absent: Set<PackageName>,
    /** Unhides whose mitigation-B flip did not read back after one retry. They stay unhidden. */
    val flipFailures: Set<PackageName>,
    /**
     * Packages the plan needs enabled that are still disabled after one best-effort enable. A Device
     * Owner has no supported call that clears a user-disabled state (PLA-233 finding 3), so this is
     * reported, not failed: hidden and suspended are still applied and read back.
     */
    val unrepairable: Set<PackageName> = emptySet(),
    /** Unhides whose flip did read back: a defined repair of an earlier flip failure for that package. */
    val flipped: Set<PackageName> = emptySet(),
) {
    val complete: Boolean get() = failedStage == null
    /** Show "Plainphone needs attention". */
    val needsAttention: Boolean get() = !complete || flipFailures.isNotEmpty() || unrepairable.isNotEmpty()
}
