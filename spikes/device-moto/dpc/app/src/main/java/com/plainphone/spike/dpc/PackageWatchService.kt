// THROWAWAY SPIKE (PLA-5). S7: hide any newly installed package that is not allowed,
// and log how long the gap was.
package com.plainphone.spike.dpc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock

class PackageWatchService : Service() {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val tRecv = System.currentTimeMillis()
            val pkg = intent.data?.schemeSpecificPart ?: return
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            val allow = Spike.prefs(c).getString("watch_allow", "")!!.split(",").toSet() + c.packageName
            val firstInstall = runCatching { c.packageManager.getPackageInfo(pkg, 0).firstInstallTime }.getOrDefault(0L)
            if (replacing || pkg in allow) {
                Spike.log("watch: $pkg added (replacing=$replacing) allowed, left visible")
                return
            }
            val t0 = SystemClock.elapsedRealtime()
            val ok = Spike.dpm(c).setApplicationHidden(Spike.admin(c), pkg, true)
            val tHidden = System.currentTimeMillis()
            Spike.prefs(c).edit().putStringSet(
                "hidden", Spike.prefs(c).getStringSet("hidden", emptySet())!! + pkg,
            ).commit()
            Spike.log(
                "watch: hid $pkg ok=$ok firstInstallTime=$firstInstall broadcastReceived=$tRecv hiddenAt=$tHidden " +
                    "installToBroadcastMs=${tRecv - firstInstall} installToHiddenMs=${tHidden - firstInstall} " +
                    "hideCallMs=${SystemClock.elapsedRealtime() - t0}",
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("watch", "Spike watch", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "watch").setContentTitle("Plainphone spike: watching installs")
            .setSmallIcon(android.R.drawable.ic_menu_view).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package"); priority = 999 },
            RECEIVER_EXPORTED,
        )
        Spike.log("watch service started")
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        Spike.log("watch service stopped")
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
}
