// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * Negative control 4, never shipped: the cell broadcast alert display is taken off the lock-task list
 * after every re-apply, so lock task blocks its alert screen (PLA-239).
 */
object BuildControl {
    val control: NegativeControl = object : NegativeControl {
        override val name = "no-alert-lock-task"

        override fun afterReapply(context: Context, dpm: DevicePolicyManager, admin: ComponentName) {
            @Suppress("DEPRECATION") // The flags overload needs API 33; minSdk is 30.
            val receivers = context.packageManager.queryBroadcastReceivers(
                Intent(EMERGENCY_CB), PackageManager.MATCH_SYSTEM_ONLY,
            ).map { it.activityInfo.packageName }.toSet()
            dpm.setLockTaskPackages(admin, (dpm.getLockTaskPackages(admin).toSet() - receivers).toTypedArray())
            Log.w(PolicyRuntime.TAG, "negative control $name: removed $receivers from lock task")
        }
    }

    private const val EMERGENCY_CB = "android.provider.action.SMS_EMERGENCY_CB_RECEIVED"
}
