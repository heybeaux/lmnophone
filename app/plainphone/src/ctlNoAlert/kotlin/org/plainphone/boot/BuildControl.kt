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
 * Negative control 3, never shipped: the cell broadcast alert display is switched off after every
 * re-apply. A Device Owner cannot disable another app's component, so this hides the system packages
 * that receive emergency broadcasts, which takes their alert screen with them.
 */
object BuildControl {
    val control: NegativeControl = object : NegativeControl {
        override val name = "no-alert"

        override fun afterReapply(context: Context, dpm: DevicePolicyManager, admin: ComponentName) {
            @Suppress("DEPRECATION") // The flags overload needs API 33; minSdk is 30.
            val receivers = context.packageManager.queryBroadcastReceivers(
                Intent(EMERGENCY_CB), PackageManager.MATCH_SYSTEM_ONLY,
            ).map { it.activityInfo.packageName }.toSet()
            hiding = receivers
            for (name in receivers) {
                val hidden = dpm.setApplicationHidden(admin, name, true)
                Log.w(PolicyRuntime.TAG, "negative control ${this.name}: hide $name -> $hidden")
            }
        }

        override fun ignoresPackageEvent(packageName: String?) = packageName in hiding
    }

    @Volatile private var hiding: Set<String> = emptySet()

    private const val EMERGENCY_CB = "android.provider.action.SMS_EMERGENCY_CB_RECEIVED"
}
