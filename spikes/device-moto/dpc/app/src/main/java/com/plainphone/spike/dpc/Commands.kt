// THROWAWAY SPIKE (PLA-5).
package com.plainphone.spike.dpc

import android.app.ActivityManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.UserManager
import java.io.File

object Commands {
    private const val HIDDEN = "hidden"
    private const val SUSPENDED = "suspended"

    fun run(c: Context, line: String): String {
        val parts = line.trim().split(Regex("\\s+"))
        val cmd = parts[0]
        val arg = parts.getOrNull(1)
        val dpm = Spike.dpm(c)
        val admin = Spike.admin(c)
        if (cmd != "status" && !Spike.isDeviceOwner(c)) return "NOT_DEVICE_OWNER"
        return when (cmd) {
            "status" -> status(c)
            "restrict-add" -> restrictAdd(c, arg!!)
            "restrict-clear" -> { dpm.clearUserRestriction(admin, arg!!); "OK cleared $arg" }
            "hide" -> track(c, HIDDEN, arg!!, true).let { "hide $arg -> ${dpm.setApplicationHidden(admin, arg, true)}" }
            "unhide" -> track(c, HIDDEN, arg!!, false).let { "unhide $arg -> ${dpm.setApplicationHidden(admin, arg, false)}" }
            "is-hidden" -> "isApplicationHidden $arg -> ${dpm.isApplicationHidden(admin, arg!!)}"
            "suspend" -> track(c, SUSPENDED, arg!!, true).let {
                "suspend $arg -> refused=${dpm.setPackagesSuspended(admin, arrayOf(arg), true).toList()}"
            }
            "unsuspend" -> track(c, SUSPENDED, arg!!, false).let {
                "unsuspend $arg -> refused=${dpm.setPackagesSuspended(admin, arrayOf(arg), false).toList()}"
            }
            "lt-packages" -> {
                val pkgs = (arg?.split(",").orEmpty() + c.packageName).filter { it.isNotBlank() }.distinct()
                dpm.setLockTaskPackages(admin, pkgs.toTypedArray())
                "OK lockTaskPackages=${dpm.getLockTaskPackages(admin).toList()}"
            }
            "lt-features" -> {
                val flags = arg!!.split("|", ",").sumOf { Spike.FEATURES[it] ?: it.toInt() }
                dpm.setLockTaskFeatures(admin, flags)
                "OK lockTaskFeatures=${Spike.featureNames(dpm.getLockTaskFeatures(admin))} ($flags)"
            }
            "lt-start", "lt-stop" -> {
                Spike.prefs(c).edit().putBoolean("locked", cmd == "lt-start").commit()
                relay(c, Intent().putExtra("op", cmd))
            }
            // launch action=... data=... comp=pkg/cls type=... text=... newtask=1 chooser=1
            "launch" -> relay(c, Intent().putExtra("op", "launch").putExtra("spec", line.removePrefix("launch").trim()))
            "home-set" -> {
                val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME); addCategory(Intent.CATEGORY_DEFAULT)
                }
                dpm.addPersistentPreferredActivity(admin, filter, ComponentName(c, MainActivity::class.java))
                "OK persistent preferred HOME = spike"
            }
            "home-clear" -> { dpm.clearPackagePersistentPreferredActivities(admin, c.packageName); "OK home cleared" }
            "debug-off-5min" -> DebugTimer.start(c)
            "debug-lift" -> { DebugTimer.lift(c, "manual"); "OK lifted" }
            "watch-on" -> {
                Spike.prefs(c).edit().putString("watch_allow", arg.orEmpty()).commit()
                c.startForegroundService(Intent(c, PackageWatchService::class.java))
                "OK watching; allow=${arg.orEmpty()}"
            }
            "watch-off" -> { c.stopService(Intent(c, PackageWatchService::class.java)); "OK watch stopped" }
            "install" -> install(c, arg!!)
            "grant-notifications" -> dpm.setPermissionGrantState(
                admin, c.packageName, android.Manifest.permission.POST_NOTIFICATIONS,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
            ).let { "grant POST_NOTIFICATIONS -> $it" }
            // grant <pkg> <permission>  (B-Q1: pre-grant so prompts never show under BLOCK)
            "grant" -> {
                val perm = parts.getOrNull(2)!!
                val ok = dpm.setPermissionGrantState(admin, arg!!, perm, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                val granted = c.packageManager.checkPermission(perm, arg) == PackageManager.PERMISSION_GRANTED
                "grant $arg $perm -> $ok state=${dpm.getPermissionGrantState(admin, arg, perm)} granted=$granted"
            }
            "statusbar-disable" -> "setStatusBarDisabled(${arg == "1"}) -> ${dpm.setStatusBarDisabled(admin, arg == "1")}"
            "crash" -> relay(c, Intent().putExtra("op", "crash"))
            "release" -> Release.run(c, null)
            else -> "UNKNOWN $cmd"
        }
    }

    private fun restrictAdd(c: Context, key: String): String {
        val dpm = Spike.dpm(c)
        val current = dpm.getUserRestrictions(Spike.admin(c))
        // Safety rule 4.
        if (key == UserManager.DISALLOW_DEBUGGING_FEATURES) return "REFUSED: use debug-off-5min"
        if (key == UserManager.DISALLOW_FACTORY_RESET &&
            current.getBoolean(UserManager.DISALLOW_DEBUGGING_FEATURES)
        ) return "REFUSED: DISALLOW_DEBUGGING_FEATURES is set"
        dpm.addUserRestriction(Spike.admin(c), key)
        return "OK added $key"
    }

    private fun track(c: Context, set: String, pkg: String, add: Boolean) {
        val p = Spike.prefs(c)
        val s = p.getStringSet(set, emptySet())!!.toMutableSet()
        if (add) s.add(pkg) else s.remove(pkg)
        p.edit().putStringSet(set, s).commit()
    }

    private fun relay(c: Context, extras: Intent): String {
        c.startActivity(
            Intent(c, MainActivity::class.java).putExtras(extras).putExtra("token", Spike.relayToken)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        return "OK relayed ${extras.getStringExtra("op")} to MainActivity (result in logcat PPSPIKE)"
    }

    fun status(c: Context): String {
        val dpm = Spike.dpm(c)
        val admin = Spike.admin(c)
        val am = c.getSystemService(ActivityManager::class.java)
        val sb = StringBuilder()
        sb.append("deviceOwner=${Spike.isDeviceOwner(c)}")
        sb.append(" lockTaskModeState=${am.lockTaskModeState}")
        if (Spike.isDeviceOwner(c)) {
            sb.append(" lockTaskPackages=${dpm.getLockTaskPackages(admin).toList()}")
            sb.append(" lockTaskFeatures=${Spike.featureNames(dpm.getLockTaskFeatures(admin))}")
            sb.append(" restrictions=${dpm.getUserRestrictions(admin).keySet().sorted()}")
            val hidden = Spike.prefs(c).getStringSet(HIDDEN, emptySet())!!.sorted()
            sb.append(" hidden=${hidden.map { "$it:${dpm.isApplicationHidden(admin, it)}" }}")
            sb.append(" suspended=${Spike.prefs(c).getStringSet(SUSPENDED, emptySet())!!.sorted()}")
        }
        sb.append(" locked=${Spike.prefs(c).getBoolean("locked", false)}")
        sb.append(" debugLiftAt=${Spike.prefs(c).getLong("debug_lift_at", 0)}")
        return sb.toString()
    }

    /** S2: Device Owner silent install of an APK the test pushed into files/. */
    private fun install(c: Context, name: String): String {
        val apk = File(c.filesDir, name)
        if (!apk.exists()) return "NO_FILE ${apk.path}"
        val pi = c.packageManager.packageInstaller
        val t0 = System.currentTimeMillis()
        val id = try {
            pi.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        } catch (e: SecurityException) {
            return "createSession SecurityException: ${e.message}"
        }
        pi.openSession(id).use { s ->
            s.openWrite("base.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; s.fsync(out) }
            val sender = PendingIntent.getBroadcast(
                c, id, Intent(c, InstallResultReceiver::class.java).putExtra("t0", t0),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ).intentSender
            s.commit(sender)
        }
        return "OK session $id committed (result in logcat PPSPIKE)"
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)
        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val pkg = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        val ms = System.currentTimeMillis() - intent.getLongExtra("t0", 0)
        val v = runCatching { context.packageManager.getPackageInfo(pkg ?: "", 0).longVersionCode }.getOrNull()
        Spike.log("install result pkg=$pkg status=$status msg=$msg versionCode=$v after ${ms}ms")
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) Spike.log("install needs user action (unexpected for DO)")
    }
}

