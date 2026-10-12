// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
 * - `org.plainphone.test.DROP --es restriction R [--ei features F] [--ez keepList true]`: clears user
 *   restriction R and the whole lock-task list, as a power cut that lost both writes would; with
 *   `features`, also sets the lock-task feature mask to F (PLA-233: F=16 drops BLOCK_ACTIVITY_START_IN_TASK).
 *   `keepList` keeps the list, so membership survives while the features are lost (PLA-264 finding 1).
 * - `org.plainphone.test.NOTIFY --es component C`: posts a notification whose tap opens activity C, an
 *   unapproved app. Android clears notifications on reboot and the shell cannot attach a tap intent,
 *   so this stands in for an unapproved app's notification right after boot. Works before unlock.
 * - `org.plainphone.test.HOLD --ez hold true|false`: holds (or releases) the checking screen, so the
 *   emergency gate can test its Phone (PLA-245; the real checking screen lasts under a second).
 * - `org.plainphone.test.IN_CALL --ez on true|false`: stands in for an ongoing call in recovery's call
 *   check (PLA-322 finding 1), so the in-call restore path runs without placing a call.
 * - `org.plainphone.test.FEATURE_FAULT --ez on true|false`: on clears the lock-task features and makes the
 *   runtime drop every feature write until off ([FeatureFault], survives reboot), so the features never
 *   read back with KEYGUARD (PLA-344 fallback containment). Off queues a re-apply.
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
                if (!intent.getBooleanExtra("keepList", false)) dpm.setLockTaskPackages(admin, emptyArray())
                if (intent.hasExtra("features")) dpm.setLockTaskFeatures(admin, intent.getIntExtra("features", 0))
            }
            "org.plainphone.test.HOLD" -> PolicyRuntime.get(context).holdChecking(intent.getBooleanExtra("hold", false))
            "org.plainphone.test.IN_CALL" -> PolicyRuntime.get(context).inCallStandIn = intent.getBooleanExtra("on", false).takeIf { it }
            "org.plainphone.test.FEATURE_FAULT" -> if (intent.getBooleanExtra("on", false)) {
                FeatureFault.marker(context).createNewFile()
                dpm.setLockTaskFeatures(admin, 0)
            } else {
                FeatureFault.marker(context).delete()
                PolicyRuntime.get(context).reapply("feature-fault-off")
            }
            "org.plainphone.test.NOTIFY" -> {
                val target = intent.getStringExtra("component")?.let(ComponentName::unflattenFromString) ?: return
                val notifications = context.getSystemService(NotificationManager::class.java)
                notifications.createNotificationChannel(NotificationChannel("test", "Test", NotificationManager.IMPORTANCE_HIGH))
                val tap = PendingIntent.getActivity(
                    context, 0, Intent(Intent.ACTION_MAIN).setComponent(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE,
                )
                notifications.notify(
                    190,
                    Notification.Builder(context, "test").setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("Unapproved app").setContentText("Tap to open ${target.packageName}")
                        .setContentIntent(tap).build(),
                )
            }
            else -> return
        }
        Log.i(PolicyRuntime.TAG, "test hook ${intent.action} lockTask=${dpm.getLockTaskPackages(admin).sorted()} features=${dpm.getLockTaskFeatures(admin)}")
    }
}
