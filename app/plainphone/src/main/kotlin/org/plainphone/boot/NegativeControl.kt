// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * A release-gate negative control (task 4.4): a deliberate break in one emergency route, so the gate
 * can show that it fails. Only the `ctl*` build types install one; debug and release use [None]
 * (`src/noControl`), which changes nothing.
 */
interface NegativeControl {
    val name: String

    /** The intent Home's Phone sends. */
    fun phoneIntent(intent: Intent): Intent = intent

    /** Runs after every re-apply, while Plainphone is Device Owner. */
    fun afterReapply(context: Context, dpm: DevicePolicyManager, admin: ComponentName) {}

    object None : NegativeControl {
        override val name = "none"
    }
}
