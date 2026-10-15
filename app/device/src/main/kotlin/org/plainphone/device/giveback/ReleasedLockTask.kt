// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.giveback

/**
 * API 34+ reads the resolved policy: absent lock-task policy resolves to GLOBAL_ACTIONS (16).
 * This neutral default is accepted only with no allowed packages AND no locked/pinned task.
 * Source: DevicePolicyManager.getLockTaskFeatures and setLockTaskFeatures API contracts.
 */
internal fun releasedLockTask(packagesEmpty: Boolean, features: Int, noLockedTask: Boolean): Boolean =
    packagesEmpty && noLockedTask && features in setOf(0, 16)
