// THROWAWAY SPIKE (PLA-5).
// Safety rule 3: the in-app way back. Clears every policy this spike can set, then gives up
// Device Owner with clearDeviceOwnerApp(). No wipe.
package com.plainphone.spike.dpc

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager

object Release {
    fun run(c: Context, activity: Activity?): String {
        if (!Spike.isDeviceOwner(c)) return "NOT_DEVICE_OWNER"
        val dpm = Spike.dpm(c)
        val admin = Spike.admin(c)
        val log = mutableListOf<String>()
        runCatching { activity?.stopLockTask() }
        Spike.prefs(c).edit().putBoolean("locked", false).commit()
        DebugTimer.lift(c, "release")
        for (key in dpm.getUserRestrictions(admin).keySet()) {
            dpm.clearUserRestriction(admin, key); log += "cleared $key"
        }
        val pm = c.packageManager
        val all = pm.getInstalledPackages(
            PackageManager.PackageInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong()),
        )
        for (p in all) {
            if (dpm.isApplicationHidden(admin, p.packageName)) {
                dpm.setApplicationHidden(admin, p.packageName, false); log += "unhid ${p.packageName}"
            }
        }
        val suspended = Spike.prefs(c).getStringSet("suspended", emptySet())!!.toTypedArray()
        if (suspended.isNotEmpty()) dpm.setPackagesSuspended(admin, suspended, false)
        dpm.setLockTaskPackages(admin, emptyArray())
        dpm.setLockTaskFeatures(admin, 0)
        dpm.clearPackagePersistentPreferredActivities(admin, c.packageName)
        dpm.setStatusBarDisabled(admin, false)
        dpm.setKeyguardDisabled(admin, false)
        c.stopService(android.content.Intent(c, PackageWatchService::class.java))
        Spike.prefs(c).edit().clear().commit()
        @Suppress("DEPRECATION")
        dpm.clearDeviceOwnerApp(c.packageName)
        val result = "RELEASED deviceOwner=${Spike.isDeviceOwner(c)}; ${log.joinToString("; ")}"
        Spike.log(result)
        return result
    }
}
