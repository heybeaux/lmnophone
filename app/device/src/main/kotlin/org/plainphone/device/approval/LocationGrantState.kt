// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.plainphone.policy.PermissionName

/** API 35 DPM includes the foreground app-op in its fixed-state answer. DENIED still proves
 * POLICY_FIXED, but a foreground-only Location grant is reported DENIED while the app is hidden
 * or in the background. Use the actual runtime grant only for these two permissions. Never
 * infer a fixed state from a runtime grant alone, or compensate for a suspended package.
 * Reproduction and Android source: app/tools/pla564/README.md.
 */
internal fun locationGrantState(permission: String, policy: PolicyGrant?, granted: Boolean?, suspended: Boolean): PolicyGrant? =
    if (!suspended && policy == PolicyGrant.DENIED && granted == true && permission in setOf(
        "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION")) PolicyGrant.GRANTED else policy

/** Location's dependencies are applied forwards when granting and backwards when revoking. */
internal fun permissionDependencyOrder(permission: PermissionName, allow: Boolean): Int {
    val rank = when {
        permission.value.contains("BACKGROUND") -> 2
        permission.value == "android.permission.ACCESS_FINE_LOCATION" -> 1
        else -> 0
    }
    return if (allow) rank else -rank
}
