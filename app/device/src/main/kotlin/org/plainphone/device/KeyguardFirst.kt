// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.plainphone.core.PackageName
import org.plainphone.policy.LockTaskFeature

/**
 * device-lockdown, "Keyguard first" (PLA-264 finding 1). Entering lock task while the lock-task
 * features lack KEYGUARD dismisses even a secure keyguard, and Android does not bring it back when
 * the features return (PLA-242 spike). So Home starts lock task itself, never automatically, and only
 * when this reads true right before the call.
 */
object KeyguardFirst {
    /** True only when the features read back with KEYGUARD and [home] reads back on the lock-task list. A failed read is false. */
    fun mayEnterLockTask(port: DevicePolicyPort, home: PackageName): Boolean = try {
        LockTaskFeature.KEYGUARD in port.lockTaskFeatures() && home in port.lockTaskPackages()
    } catch (e: Exception) {
        false
    }
}
