// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

/** What fallback containment does after a pass or a lock-task read (PLA-344). */
enum class FallbackStep { CONTAIN, LIFT, NONE }

/**
 * Contain only after the PIN ([userLocked] false), while lock task is not LOCKED and cannot start with
 * KEYGUARD ([mayEnterLockTask] false): Home stays out of lock task then (Keyguard first). Before the PIN
 * the locked-boot plan keeps less, the keyguard is up, and a suspension already applied persists.
 * Lift only while LOCKED with KEYGUARD read back. LOCKED without KEYGUARD is still lock task: nothing changes.
 */
fun fallbackStep(userLocked: Boolean, state: LockTaskState, mayEnterLockTask: Boolean): FallbackStep = when {
    state == LockTaskState.LOCKED -> if (mayEnterLockTask) FallbackStep.LIFT else FallbackStep.NONE
    userLocked || mayEnterLockTask -> FallbackStep.NONE
    else -> FallbackStep.CONTAIN
}
