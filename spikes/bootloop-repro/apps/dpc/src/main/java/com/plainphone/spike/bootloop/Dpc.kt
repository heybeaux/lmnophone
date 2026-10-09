// THROWAWAY SPIKE (PLA-22). Not MVP code.
package com.plainphone.spike.bootloop

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

class AdminReceiver : DeviceAdminReceiver()

class FlushToggle : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {}
}

/** Flip our no-op component with SYNCHRONOUS so PackageManager writes package-restrictions (incl. hidden flags) before returning. */
fun flush(context: Context): String {
    val pm = context.packageManager
    val c = ComponentName(context, FlushToggle::class.java)
    val next = if (pm.getComponentEnabledSetting(c) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    val t0 = System.nanoTime()
    pm.setComponentEnabledSetting(c, next, PackageManager.DONT_KILL_APP or PackageManager.SYNCHRONOUS)
    return "flushed in ${(System.nanoTime() - t0) / 1_000_000} ms"
}

/**
 * adb shell am broadcast -a com.plainphone.spike.bootloop.CMD -n com.plainphone.spike.bootloop/.CommandReceiver --es cmd 'hide <pkg>'
 * Commands: hide <pkg> | unhide <pkg> | unhideflush <pkg> | suspend <pkg> | unsuspend <pkg> | state <pkg>
 */
class CommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(context, AdminReceiver::class.java)
        val parts = (intent.getStringExtra("cmd") ?: "").trim().split(Regex("\\s+"))
        val pkg = parts.getOrNull(1) ?: ""
        val out = try {
            when (parts[0]) {
                "hide" -> "hide $pkg -> ${dpm.setApplicationHidden(admin, pkg, true)}"
                "unhide" -> "unhide $pkg -> ${dpm.setApplicationHidden(admin, pkg, false)}"
                "unhideflush" -> "unhide $pkg -> ${dpm.setApplicationHidden(admin, pkg, false)}; ${flush(context)}"
                "suspend" -> "suspend $pkg -> failed=${dpm.setPackagesSuspended(admin, arrayOf(pkg), true).toList()}"
                "unsuspend" -> "unsuspend $pkg -> failed=${dpm.setPackagesSuspended(admin, arrayOf(pkg), false).toList()}"
                "state" -> "state $pkg hidden=${dpm.isApplicationHidden(admin, pkg)} suspended=${runCatching { dpm.isPackageSuspended(admin, pkg) }.getOrElse { it.toString() }}"
                else -> "unknown: ${parts[0]}"
            }
        } catch (t: Throwable) { "ERROR ${t}" }
        Log.i("PPBOOT", out)
        resultData = out
    }
}
