// THROWAWAY SPIKE (PLA-22). Not MVP code.
package com.plainphone.spike.bootloop

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

class AdminReceiver : DeviceAdminReceiver()

/**
 * adb shell am broadcast -a com.plainphone.spike.bootloop.CMD -n com.plainphone.spike.bootloop/.CommandReceiver --es cmd 'hide <pkg>'
 * Commands: hide <pkg> | unhide <pkg> | suspend <pkg> | unsuspend <pkg> | state <pkg>
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
