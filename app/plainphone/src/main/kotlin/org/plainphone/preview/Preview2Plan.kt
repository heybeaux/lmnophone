// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.preview

import org.plainphone.core.PackageName
import org.plainphone.policy.ApplyStage
import org.plainphone.policy.ApplyStep
import org.plainphone.policy.EmergencyFloor
import org.plainphone.policy.ExpectedPolicyState
import org.plainphone.policy.FloorIdentity
import org.plainphone.policy.InstalledPackage
import org.plainphone.policy.LockTaskFeature
import org.plainphone.policy.PolicyPlan
import org.plainphone.policy.SweepMode

/**
 * The preview-2 policy (PLA-144): a fixed starter list set by the setup script, with no PIN,
 * approvals or restrictions. It stands in for [org.plainphone.policy.PolicyEngine] until approvals are
 * wired, because the engine's full plan sets every restriction, including DISALLOW_DEBUGGING_FEATURES
 * with DISALLOW_FACTORY_RESET, which test builds must not do (TEAM-POLICY §2).
 *
 * Only launcher-visible apps are hidden, never system components, so a missing inventory can't hide
 * something the phone needs to boot. The emergency floor is always shown and allowed in lock task.
 */
object Preview2Plan {
    val FEATURES: Set<LockTaskFeature> = setOf(
        LockTaskFeature.HOME, LockTaskFeature.KEYGUARD, LockTaskFeature.GLOBAL_ACTIONS, LockTaskFeature.SYSTEM_INFO,
    )

    /**
     * @param launchable packages with a launcher activity, hidden ones included.
     * @param starter the setup script's list. Packages that aren't installed are ignored.
     * @param keepVisible system screens left exactly as they are (Settings, the stock Home): never hidden,
     *   never enabled or unhidden by this plan.
     * @param lockTaskExtras installed system packages the emergency paths need in lock task, such as SOS.
     */
    fun decide(
        plainphone: PackageName,
        installed: List<InstalledPackage>,
        identities: List<FloorIdentity>,
        launchable: Set<PackageName>,
        starter: Set<PackageName>,
        keepVisible: Set<PackageName>,
        lockTaskExtras: Set<PackageName>,
    ): PolicyPlan {
        val floor = EmergencyFloor.compute(installed, identities)
        val present = installed.map { it.name }.toSet()
        val shown = floor.neverHide + (starter intersect present) + plainphone
        val neverHide = shown + keepVisible
        val hidden = launchable - neverHide
        val lockTask = setOf(plainphone) + floor.emergencyUi + (starter intersect present) + (lockTaskExtras intersect present)
        return PolicyPlan(
            hidden = hidden,
            unhide = shown,
            neverHide = neverHide,
            emergencyUi = floor.emergencyUi,
            lockTaskPackages = lockTask,
            features = FEATURES,
            restrictions = emptySet(),
            steps = listOf(ApplyStage.EMERGENCY_FLOOR, ApplyStage.NEVER_HIDE_GUARD, ApplyStage.HIDES,
                ApplyStage.LOCK_TASK, ApplyStage.HOME).map { ApplyStep(it) },
            expected = ExpectedPolicyState(
                hidden = hidden,
                unhiddenAndEnabled = shown,
                unsuspended = shown,
                lockTaskPackages = lockTask,
                features = FEATURES,
                restrictions = null,
                home = plainphone,
                systemInputMethodsOnly = null,
                systemAccessibilityServicesOnly = null,
            ),
            // Informational only: the sweep here is "launcher apps outside the list", not an inventory sweep.
            sweepMode = SweepMode.USER_APPS_ONLY,
            refusedDestruction = emptyList(),
            allowedDestruction = emptyList(),
            classifierConflicts = emptySet(),
            changedPackages = emptySet(),
            needsAttention = !floor.hasSystemDialer,
            notifyBuildReview = false,
        )
    }
}
