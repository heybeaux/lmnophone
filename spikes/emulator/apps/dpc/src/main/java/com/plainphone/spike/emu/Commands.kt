// THROWAWAY SPIKE (PLA-11). adb command hook: am broadcast -a com.plainphone.spike.emu.CMD --es cmd "<line>"
package com.plainphone.spike.emu

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager

object Commands {
    fun run(c: Context, line: String): String {
        val parts = line.trim().split(Regex("\\s+"))
        val cmd = parts[0]
        val a1 = parts.getOrNull(1)
        val a2 = parts.getOrNull(2)
        val a3 = parts.getOrNull(3)
        val dpm = Emu.dpm(c)
        val admin = Emu.admin(c)
        if (cmd != "status" && cmd != "classify-dry" && !Emu.isDeviceOwner(c)) return "NOT_DEVICE_OWNER"
        return when (cmd) {
            "status" -> status(c)
            "restrict-add" -> { dpm.addUserRestriction(admin, a1!!); "OK added $a1" }
            "restrict-clear" -> { dpm.clearUserRestriction(admin, a1!!); "OK cleared $a1" }
            "hide" -> "hide $a1 -> ${dpm.setApplicationHidden(admin, a1!!, true)}"
            "unhide" -> "unhide $a1 -> ${dpm.setApplicationHidden(admin, a1!!, false)}"
            "is-hidden" -> "isApplicationHidden $a1 -> ${dpm.isApplicationHidden(admin, a1!!)}"
            "lt-packages" -> {
                val pkgs = (a1?.split(",").orEmpty() + c.packageName).filter { it.isNotBlank() }.distinct()
                dpm.setLockTaskPackages(admin, pkgs.toTypedArray())
                "OK lockTaskPackages=${dpm.getLockTaskPackages(admin).toList()}"
            }
            "lt-features" -> {
                val flags = a1!!.split("|", ",").sumOf { Emu.FEATURES[it] ?: it.toInt() }
                dpm.setLockTaskFeatures(admin, flags)
                "OK lockTaskFeatures=${Emu.featureNames(dpm.getLockTaskFeatures(admin))} ($flags)"
            }
            "lt-start", "lt-stop" -> {
                Emu.prefs(c).edit().putBoolean("locked", cmd == "lt-start").commit()
                relay(c, Intent().putExtra("op", cmd))
            }
            // launch action=... data=... comp=pkg/cls pkg=... type=... text=... newtask=1 chooser=1 ct=1
            "launch" -> relay(c, Intent().putExtra("op", "launch").putExtra("spec", line.removePrefix("launch").trim()))
            "home-set" -> {
                val f = IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME); addCategory(Intent.CATEGORY_DEFAULT) }
                dpm.addPersistentPreferredActivity(admin, f, ComponentName(c, MainActivity::class.java))
                "OK persistent preferred HOME = emu spike"
            }
            "sms-default" -> { dpm.setDefaultSmsApplication(admin, a1!!); "OK setDefaultSmsApplication($a1)" }
            "can-grant-sensors" -> "canAdminGrantSensorsPermissions=${dpm.canAdminGrantSensorsPermissions()}"
            // grant <pkg> <perm> granted|denied|default
            "grant" -> {
                val state = when (a3) {
                    "granted" -> DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                    "denied" -> DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
                    else -> DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
                }
                val ok = dpm.setPermissionGrantState(admin, a1!!, a2!!, state)
                "setPermissionGrantState($a1,$a2,$a3) -> $ok; " + grantState(c, a1, a2)
            }
            "grant-state" -> grantState(c, a1!!, a2!!)
            "classify" -> Classifier.run(c, apply = true, skipIdentity = false)
            "classify-dry" -> Classifier.run(c, apply = false, skipIdentity = false)
            "classify-noidentity" -> Classifier.run(c, apply = false, skipIdentity = true)
            "floor" -> Floor.compute(c, naive = false).toString()
            "emergency-target" -> "emergencyPackage=${Floor.emergencyPackage(c)}"
            "floor-naive" -> Floor.compute(c, naive = true).toString()
            // PLA-23 V4: the approved list is stored separately; floor-apply REPLACES the lock-task list with
            // approved + system-only floor + self, so a stale entry (seeded with lt-packages) is dropped.
            "approved-set" -> {
                Emu.prefs(c).edit().putString("approved", a1.orEmpty()).commit()
                "OK approved=${approved(c)}"
            }
            "approved" -> "approved=${approved(c)}"
            "floor-apply" -> {
                val f = Floor.compute(c)
                val keep = approved(c) + f.uiSubset + c.packageName
                dpm.setLockTaskPackages(admin, keep.toTypedArray())
                for (p in Floor.all(f)) if (dpm.isApplicationHidden(admin, p)) dpm.setApplicationHidden(admin, p, false)
                "OK floor uiSubset=${f.uiSubset} lockTaskPackages=${dpm.getLockTaskPackages(admin).toList()}"
            }
            "wifi-add" -> Wifi.add(c, a1!!, a2)
            "wifi-list" -> Wifi.list(c)
            "wifi-forget" -> Wifi.forget(c, a1!!.toInt())
            "wifi-forget-current" -> Wifi.forgetCurrent(c)
            "net" -> "wifi=${Emu.wifi} captive=${Emu.captive} validated=${Emu.validated} msg=${Emu.netMessage()}"
            "notify" -> notify(c, parts.drop(1).joinToString(" "))
            "customtabs" -> {
                val svc = c.packageManager.queryIntentServices(
                    Intent("android.support.customtabs.action.CustomTabsService"), PackageManager.MATCH_ALL)
                "CustomTabsService providers=${svc.map { it.serviceInfo.packageName }}"
            }
            "resolve" -> resolve(c, line.removePrefix("resolve").trim())
            "browser-role-request" -> relay(c, Intent().putExtra("op", "browser-role-request"))
            "uid-of" -> "uid($a1)=${runCatching { c.packageManager.getPackageUid(a1!!, PackageManager.MATCH_ALL) }.getOrNull()} " +
                "sharedUid=${runCatching { c.packageManager.getPackagesForUid(c.packageManager.getPackageUid(a1!!, PackageManager.MATCH_ALL))?.toList() }.getOrNull()}"
            else -> "UNKNOWN $cmd"
        }
    }

    fun approved(c: Context): Set<String> =
        Emu.prefs(c).getString("approved", "").orEmpty().split(",").filter { it.isNotBlank() }.toSet()

    /** PLA-23 V5: ops relayed to MainActivity carry this per-process token, so only this adb-only receiver can trigger them. */
    val token: String = java.util.UUID.randomUUID().toString()

    private fun grantState(c: Context, pkg: String, perm: String): String {
        val dpmState = Emu.dpm(c).getPermissionGrantState(Emu.admin(c), pkg, perm)
        val granted = c.packageManager.checkPermission(perm, pkg) == PackageManager.PERMISSION_GRANTED
        return "getPermissionGrantState=$dpmState checkPermission=${if (granted) "GRANTED" else "DENIED"}"
    }

    private fun resolve(c: Context, spec: String): String {
        val i = MainActivity.buildIntent(spec)
        val all = c.packageManager.queryIntentActivities(i, PackageManager.MATCH_ALL).map { it.activityInfo.packageName + "/" + it.activityInfo.name }
        val def = c.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        return "resolve[$spec] default=${def?.packageName}/${def?.name} all=$all"
    }

    private fun notify(c: Context, text: String): String {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("emu", "Spike", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(text.hashCode(), Notification.Builder(c, "emu").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Plainphone spike").setContentText(text).setContentIntent(pi).setAutoCancel(true).build())
        return "OK notified enabled=${nm.areNotificationsEnabled()}"
    }

    private fun relay(c: Context, extras: Intent): String {
        c.startActivity(Intent(c, MainActivity::class.java).putExtras(extras).putExtra("token", token)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        return "OK relayed ${extras.getStringExtra("op")} (result in logcat PPEMU)"
    }

    fun status(c: Context): String {
        val dpm = Emu.dpm(c)
        val admin = Emu.admin(c)
        val am = c.getSystemService(ActivityManager::class.java)
        val sb = StringBuilder("deviceOwner=${Emu.isDeviceOwner(c)} lockTaskModeState=${am.lockTaskModeState}")
        if (Emu.isDeviceOwner(c)) {
            sb.append(" lockTaskPackages=${dpm.getLockTaskPackages(admin).toList()}")
            sb.append(" lockTaskFeatures=${Emu.featureNames(dpm.getLockTaskFeatures(admin))}")
            sb.append(" restrictions=${dpm.getUserRestrictions(admin).keySet().sorted()}")
        }
        sb.append(" locked=${Emu.prefs(c).getBoolean("locked", false)}")
        return sb.toString()
    }
}

@Suppress("DEPRECATION")
object Wifi {
    fun add(c: Context, ssid: String, psk: String?): String {
        val wm = c.getSystemService(WifiManager::class.java)
        val cfg = WifiConfiguration().apply {
            SSID = "\"$ssid\""
            if (psk == null) setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN)
            else { setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK); preSharedKey = "\"$psk\"" }
        }
        val id = wm.addNetwork(cfg)
        val en = if (id >= 0) wm.enableNetwork(id, true) else false
        return "addNetwork($ssid, ${if (psk == null) "open" else "psk"}) -> netId=$id enableNetwork -> $en"
    }

    fun list(c: Context): String {
        val wm = c.getSystemService(WifiManager::class.java)
        val nets = runCatching { wm.configuredNetworks.map { "${it.networkId}:${it.SSID}" } }.getOrElse { listOf("ERR ${it.message}") }
        return "configured=$nets current=${wm.connectionInfo.ssid}/${wm.connectionInfo.networkId}"
    }

    fun forget(c: Context, id: Int) = "removeNetwork($id) -> ${c.getSystemService(WifiManager::class.java).removeNetwork(id)}"

    fun forgetCurrent(c: Context): String {
        val id = c.getSystemService(WifiManager::class.java).connectionInfo.networkId
        return if (id < 0) "no current network" else forget(c, id)
    }
}
