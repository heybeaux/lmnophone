// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import org.plainphone.PlainphoneAdminReceiver

/**
 * Test build only: sets up the PLA-190 boot scenarios from adb, which cannot change a Device Owner's
 * policy itself. Shell-only (`android.permission.DUMP`). Neither action triggers a re-apply.
 *
 * - `org.plainphone.test.RELAX --es package P`: adds P to the lock-task list, as an open session would.
 * - `org.plainphone.test.DROP --es restriction R`: clears user restriction R and the whole lock-task
 *   list, as a power cut that lost both writes would.
 */
class TestHook : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(context, PlainphoneAdminReceiver::class.java)
        when (intent.action) {
            "org.plainphone.test.RELAX" -> {
                val name = intent.getStringExtra("package") ?: return
                dpm.setLockTaskPackages(admin, (dpm.getLockTaskPackages(admin).toSet() + name).toTypedArray())
            }
            "org.plainphone.test.DROP" -> {
                val restriction = intent.getStringExtra("restriction") ?: return
                dpm.clearUserRestriction(admin, restriction)
                dpm.setLockTaskPackages(admin, emptyArray())
            }
            else -> return
        }
        Log.i(PolicyRuntime.TAG, "test hook ${intent.action} lockTask=${dpm.getLockTaskPackages(admin).sorted()}")
    }
}
