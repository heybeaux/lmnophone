// THROWAWAY SPIKE (PLA-38). adb hook: am broadcast -a com.plainphone.spike.grey.CMD --es cmd "<line>". Not MVP code.
package com.plainphone.spike.grey

import android.Manifest
import android.app.PendingIntent
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import java.io.File

class AdminReceiver : DeviceAdminReceiver()

object Grey {
    const val TAG = "PPGREY"
    // Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER_ENABLED / ACCESSIBILITY_DISPLAY_DALTONIZER are @hide.
    const val KEY_ENABLED = "accessibility_display_daltonizer_enabled"
    const val KEY_MODE = "accessibility_display_daltonizer"
    const val MODE_MONOCHROMACY = 0 // AccessibilityManager.DALTONIZER_SIMULATE_MONOCHROMACY

    fun admin(c: Context) = ComponentName(c, AdminReceiver::class.java)
    fun dpm(c: Context): DevicePolicyManager = c.getSystemService(DevicePolicyManager::class.java)
    fun log(msg: String) { Log.i(TAG, msg) }

    fun hasGrant(c: Context) =
        c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun setGrey(c: Context, on: Boolean): String {
        val cr = c.contentResolver
        val a = if (on) Settings.Secure.putInt(cr, KEY_MODE, MODE_MONOCHROMACY) else true
        val b = Settings.Secure.putInt(cr, KEY_ENABLED, if (on) 1 else 0)
        return "putInt mode=$a enabled=$b -> ${state(c)}"
    }

    fun state(c: Context): String {
        val cr = c.contentResolver
        return "$KEY_ENABLED=${Settings.Secure.getString(cr, KEY_ENABLED)} $KEY_MODE=${Settings.Secure.getString(cr, KEY_MODE)}"
    }

    fun status(c: Context): String {
        val pi = c.packageManager.getPackageInfo(c.packageName, 0)
        return "version=${pi.versionName}(${pi.longVersionCode}) deviceOwner=${dpm(c).isDeviceOwnerApp(c.packageName)} " +
            "WRITE_SECURE_SETTINGS=${hasGrant(c)} lockTask=${c.getSystemService(android.app.ActivityManager::class.java).lockTaskModeState} ${state(c)}"
    }

    fun run(c: Context, line: String): String {
        val p = line.trim().split(Regex("\\s+"))
        val a1 = p.getOrNull(1); val a2 = p.getOrNull(2); val a3 = p.getOrNull(3)
        val dpm = dpm(c); val admin = admin(c)
        return when (p[0]) {
            "status" -> status(c)
            "grey-on" -> setGrey(c, true)
            "grey-off" -> setGrey(c, false)
            // put secure|global <key> <value> : what else the grant lets the app write (Q4)
            "put" -> {
                val ok = if (a1 == "global") Settings.Global.putString(c.contentResolver, a2, a3)
                         else Settings.Secure.putString(c.contentResolver, a2, a3)
                "put $a1 $a2=$a3 -> $ok, now ${get(c, a1!!, a2!!)}"
            }
            "get" -> "$a1 $a2=${get(c, a1!!, a2!!)}"
            // Can a Device Owner grant WRITE_SECURE_SETTINGS to itself without adb? (QR-provisioned case)
            "dpm-grant" -> {
                val r = dpm.setPermissionGrantState(admin, c.packageName, Manifest.permission.WRITE_SECURE_SETTINGS,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                "setPermissionGrantState(WRITE_SECURE_SETTINGS, GRANTED) -> $r; hasGrant=${hasGrant(c)}"
            }
            "dpm-secure" -> { dpm.setSecureSetting(admin, a1!!, a2!!); "setSecureSetting $a1=$a2 -> now ${get(c, "secure", a1)}" }
            "restrict-add" -> { dpm.addUserRestriction(admin, a1!!); "OK added $a1" }
            "restrict-clear" -> { dpm.clearUserRestriction(admin, a1!!); "OK cleared $a1" }
            "lt-packages" -> {
                dpm.setLockTaskPackages(admin, arrayOf(c.packageName) + (a1?.split(",").orEmpty().filter { it.isNotBlank() }))
                "OK lockTaskPackages=${dpm.getLockTaskPackages(admin).toList()}"
            }
            "lt-features" -> { dpm.setLockTaskFeatures(admin, a1!!.toInt()); "OK lockTaskFeatures=${dpm.getLockTaskFeatures(admin)}" }
            "home-set" -> {
                val f = IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME); addCategory(Intent.CATEGORY_DEFAULT) }
                dpm.addPersistentPreferredActivity(admin, f, ComponentName(c, MainActivity::class.java))
                "OK persistent preferred HOME"
            }
            "lt-start", "lt-stop" -> {
                c.getSharedPreferences("grey", 0).edit().putBoolean("locked", p[0] == "lt-start").commit()
                c.startActivity(Intent(c, MainActivity::class.java).putExtra("op", p[0]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "OK sent ${p[0]}"
            }
            // self-update from files/update.apk through a PackageInstaller session (Device Owner: no prompt)
            "self-update" -> selfUpdate(c, File(c.filesDir, a1 ?: "update.apk"))
            else -> "UNKNOWN ${p[0]}"
        }
    }

    private fun get(c: Context, ns: String, key: String) =
        if (ns == "global") Settings.Global.getString(c.contentResolver, key) else Settings.Secure.getString(c.contentResolver, key)

    private fun selfUpdate(c: Context, apk: File): String {
        if (!apk.exists()) return "NO FILE $apk"
        val pi = c.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(c.packageName)
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            s.openWrite("base.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; s.fsync(out) }
            val pending = PendingIntent.getBroadcast(c, id, Intent(c, InstallReceiver::class.java),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            s.commit(pending.intentSender)
        }
        return "committed session $id (${apk.length()} bytes)"
    }
}

class CommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val line = intent.getStringExtra("cmd").orEmpty()
        val out = try { Grey.run(context, line) } catch (t: Throwable) { "ERROR ${t.javaClass.simpleName}: ${t.message}" }
        Grey.log("cmd [$line] -> $out")
        resultData = out
    }
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Grey.log("install status=${intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)} msg=${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Grey.log("${intent.action}: ${Grey.status(context)}")
    }
}
