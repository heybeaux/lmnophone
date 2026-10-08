// THROWAWAY SPIKE (PLA-5).
// Safety rule 4: DISALLOW_DEBUGGING_FEATURES is only ever set together with a 5-minute
// automatic lift. Three lift paths: an exact alarm, an in-process handler, and a boot check.
package com.plainphone.spike.dpc

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.UserManager

object DebugTimer {
    private const val KEY = "debug_lift_at"
    const val LIFT_AFTER_MS = 5 * 60 * 1000L
    private val handler = Handler(Looper.getMainLooper())

    fun start(c: Context): String {
        val dpm = Spike.dpm(c)
        if (dpm.getUserRestrictions(Spike.admin(c)).getBoolean(UserManager.DISALLOW_FACTORY_RESET)) {
            return "REFUSED: DISALLOW_FACTORY_RESET is set; never combine with DISALLOW_DEBUGGING_FEATURES"
        }
        val liftAt = System.currentTimeMillis() + LIFT_AFTER_MS
        Spike.prefs(c).edit().putLong(KEY, liftAt).commit()
        c.getSystemService(AlarmManager::class.java)
            .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, liftAt, pending(c))
        handler.postDelayed({ check(c) }, LIFT_AFTER_MS + 1000)
        dpm.addUserRestriction(Spike.admin(c), UserManager.DISALLOW_DEBUGGING_FEATURES)
        Spike.log("DISALLOW_DEBUGGING_FEATURES set; auto-lift at $liftAt")
        return "OK debugging disallowed until $liftAt (epoch ms)"
    }

    /** Lifts the restriction if the deadline passed. Safe to call any time. */
    fun check(c: Context) {
        val liftAt = Spike.prefs(c).getLong(KEY, 0L)
        if (liftAt == 0L) return
        val now = System.currentTimeMillis()
        if (now >= liftAt) {
            lift(c, "deadline passed (late by ${now - liftAt} ms)")
        } else {
            handler.postDelayed({ check(c) }, liftAt - now + 1000)
        }
    }

    fun lift(c: Context, why: String) {
        if (Spike.isDeviceOwner(c)) {
            Spike.dpm(c).clearUserRestriction(Spike.admin(c), UserManager.DISALLOW_DEBUGGING_FEATURES)
        }
        Spike.prefs(c).edit().remove(KEY).commit()
        c.getSystemService(AlarmManager::class.java).cancel(pending(c))
        Spike.log("DISALLOW_DEBUGGING_FEATURES lifted: $why")
    }

    private fun pending(c: Context) = PendingIntent.getBroadcast(
        c, 0, Intent(c, DebugLiftReceiver::class.java).setAction("com.plainphone.spike.LIFT_DEBUG"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class DebugLiftReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Spike.log("DebugLiftReceiver: ${intent.action}")
        DebugTimer.check(context)
    }
}
