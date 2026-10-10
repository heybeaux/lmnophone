// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock

/**
 * Direct Boot aware. `LOCKED_BOOT_COMPLETED` runs the locked-boot re-apply (device-protected data
 * only); `BOOT_COMPLETED` and an app update run the full one. Both re-arm the 15-minute alarm.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reason = when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> "locked-boot"
            Intent.ACTION_BOOT_COMPLETED -> "boot"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "updated"
            else -> return
        }
        val pending = goAsync()
        ReapplyAlarm.schedule(context)
        PolicyRuntime.get(context).reapply(reason) { pending.finish() }
    }
}

/**
 * The 15-minute re-apply. Exact, with `setExactAndAllowWhileIdle`: a lost restriction or hide must not
 * wait for an inexact batch, and Doze would otherwise defer it for hours. USE_EXACT_ALARM (API 33+)
 * and SCHEDULE_EXACT_ALARM (API 30–32) cover it; if exact alarms are refused anyway it falls back to
 * `setAndAllowWhileIdle`, which Android may delay. In Doze, allow-while-idle alarms are rate-limited
 * (about one per 9 minutes per app), which a 15-minute period stays under.
 */
class ReapplyAlarm : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        schedule(context)
        PolicyRuntime.get(context).reapply("alarm") { pending.finish() }
    }

    companion object {
        const val PERIOD_MILLIS = 15 * 60 * 1000L

        fun schedule(context: Context) {
            val alarms = context.getSystemService(AlarmManager::class.java)
            val operation = PendingIntent.getBroadcast(
                context, 0, Intent(context, ReapplyAlarm::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val at = SystemClock.elapsedRealtime() + PERIOD_MILLIS
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, operation)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, operation)
            }
        }
    }
}

/**
 * Package installs, removals, updates and enable changes re-run the policy. Context-registered:
 * manifest receivers no longer get these. Not exported: system broadcasts still arrive.
 */
object PackageEvents {
    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val name = intent.data?.schemeSpecificPart
                // Our own flush-component flips arrive as PACKAGE_CHANGED; they never need a re-apply.
                if (name == context.packageName) return
                PolicyRuntime.get(context).onPackageEvent("package:${intent.action?.substringAfterLast('.')}:$name")
            }
        }, filter, Context.RECEIVER_NOT_EXPORTED)
    }
}

/** The no-op component Plainphone flips after each unhide (mitigation B). Never receives anything. */
class FlushMarker : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
