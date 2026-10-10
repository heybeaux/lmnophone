// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.telecom.TelecomManager
import android.util.Log

/** Negative control 2, never shipped: the system dialer is taken off the lock-task list after every re-apply. */
object BuildControl {
    val control: NegativeControl = object : NegativeControl {
        override val name = "no-dialer-lock-task"

        override fun afterReapply(context: Context, dpm: DevicePolicyManager, admin: ComponentName) {
            val dialer = context.getSystemService(TelecomManager::class.java)?.systemDialerPackage ?: return
            dpm.setLockTaskPackages(admin, (dpm.getLockTaskPackages(admin).toSet() - dialer).toTypedArray())
            Log.w(PolicyRuntime.TAG, "negative control $name: removed $dialer from lock task")
        }
    }
}
