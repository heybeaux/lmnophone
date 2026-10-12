// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import android.content.Context
import java.io.File

/**
 * Test build: while the marker file exists, the runtime drops lock-task feature writes, so the features
 * never read back with KEYGUARD (the PLA-307 finding 1 state; TestHook `FEATURE_FAULT`). It survives reboot.
 */
object FeatureFault {
    fun marker(context: Context) = File(context.createDeviceProtectedStorageContext().filesDir, "test-feature-fault")
    fun on(context: Context): Boolean = marker(context).exists()
}
