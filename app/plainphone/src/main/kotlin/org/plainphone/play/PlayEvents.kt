// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.play

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telephony.TelephonyManager
import org.plainphone.boot.PolicyRuntime
import org.plainphone.policy.SessionEnd

/** Process-lifetime close events. No phone numbers or other call data are read. */
object PlayEvents {
    fun register(context: Context) {
        val runtime = PolicyRuntime.get(context)
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val reason = when {
                    intent.action == Intent.ACTION_SCREEN_OFF -> SessionEnd.SCREEN_OFF
                    intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED &&
                        intent.getStringExtra(TelephonyManager.EXTRA_STATE) == TelephonyManager.EXTRA_STATE_RINGING -> SessionEnd.INCOMING_CALL
                    else -> return
                }
                val pending = goAsync()
                runtime.endPlay(reason) { pending.finish() }
            }
        }, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        }, Context.RECEIVER_EXPORTED)
        val handler = Handler(Looper.getMainLooper())
        val pulse = object : Runnable {
            override fun run() {
                runtime.tickPlay { handler.postDelayed(this, 1000) }
            }
        }
        handler.post(pulse)
    }
}

/** Exact deadline backstop. The engine rechecks its running clock; the alarm grants nothing. */
class PlayDeadline : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        PolicyRuntime.get(context).tickPlay { pending.finish() }
    }
    companion object {
        fun schedule(context: Context, remainingMillis: Long?) {
            val alarms = context.getSystemService(AlarmManager::class.java)
            val operation = PendingIntent.getBroadcast(context, 0, Intent(context, PlayDeadline::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if (remainingMillis == null) { alarms.cancel(operation); return }
            val deadline = SystemClock.elapsedRealtime() + remainingMillis.coerceAtLeast(1)
            if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms())
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, operation)
            else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, operation)
        }
    }
}
