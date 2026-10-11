// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.util.Log
import org.plainphone.MainActivity

/**
 * Starts Home before the first unlock, after the locked-boot re-apply restored the lock-task list, so
 * lock task is on before any other activity can come to the front at unlock (PLA-241 finding 1).
 */
object LockedHome {
    fun start(context: Context) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        if (!dpm.isDeviceOwnerApp(context.packageName)) return
        try {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: RuntimeException) {
            Log.w(PolicyRuntime.TAG, "locked home start failed", e)
        }
    }
}
