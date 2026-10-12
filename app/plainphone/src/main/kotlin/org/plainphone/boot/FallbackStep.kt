// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

enum class FallbackStep { CONTAIN, LIFT, NONE }

fun fallbackStep(userLocked: Boolean, state: LockTaskState, mayEnterLockTask: Boolean): FallbackStep = FallbackStep.NONE
