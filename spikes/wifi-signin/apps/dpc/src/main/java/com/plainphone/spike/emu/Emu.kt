// THROWAWAY SPIKE (PLA-11, reused by PLA-18). Shared helpers. Not MVP code.
package com.plainphone.spike.emu

import android.app.Application
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log

object Emu {
    const val TAG = "PPEMU"

    val FEATURES = linkedMapOf(
        "SYSTEM_INFO" to DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO,
        "NOTIFICATIONS" to DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS,
        "HOME" to DevicePolicyManager.LOCK_TASK_FEATURE_HOME,
        "OVERVIEW" to DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW,
        "GLOBAL_ACTIONS" to DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS,
        "KEYGUARD" to DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD,
        "BLOCK" to DevicePolicyManager.LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK,
    )

    fun admin(c: Context) = ComponentName(c, AdminReceiver::class.java)
    fun dpm(c: Context): DevicePolicyManager = c.getSystemService(DevicePolicyManager::class.java)
    fun isDeviceOwner(c: Context) = dpm(c).isDeviceOwnerApp(c.packageName)
    fun prefs(c: Context): SharedPreferences =
        c.createDeviceProtectedStorageContext().getSharedPreferences("emu", Context.MODE_PRIVATE)

    fun log(msg: String) { Log.i(TAG, msg) }

    fun featureNames(flags: Int) =
        FEATURES.filter { flags and it.value != 0 }.keys.joinToString("|").ifEmpty { "NONE" }

    /** Last default-network state, for the Wi-Fi sign-in message. */
    @Volatile var captive = false
    @Volatile var validated = false
    @Volatile var wifi = false

    fun netMessage(): String? = when {
        wifi && captive -> "This Wi-Fi needs a sign-in page, which Plainphone doesn't allow. Use mobile data or another Wi-Fi."
        wifi && !validated -> "This Wi-Fi isn't reaching the internet."
        else -> null
    }
}

class EmuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Signin.revokeOnStart(this, "app-start")
        Signin.register(this)
        val cm = getSystemService(ConnectivityManager::class.java)
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                Emu.captive = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
                Emu.validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                Emu.wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                Emu.log("net wifi=${Emu.wifi} captive=${Emu.captive} validated=${Emu.validated} msg=${Emu.netMessage()}")
            }
            override fun onLost(network: Network) {
                Emu.captive = false; Emu.validated = false; Emu.wifi = false
                Emu.log("net lost")
            }
        })
    }
}

class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) = Emu.log("admin enabled")
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) =
        Emu.log("provisioning complete; deviceOwner=${Emu.isDeviceOwner(context)}")
    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) = Emu.log("lock task entering $pkg")
    override fun onLockTaskModeExiting(context: Context, intent: Intent) = Emu.log("lock task exiting")
}

class CommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val line = intent.getStringExtra("cmd").orEmpty()
        val out = try { Commands.run(context, line) } catch (t: Throwable) { "ERROR ${t.javaClass.simpleName}: ${t.message}" }
        Emu.log("cmd [$line] -> $out")
        resultData = out
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Emu.log("boot ${intent.action} deviceOwner=${Emu.isDeviceOwner(context)} locked=${Emu.prefs(context).getBoolean("locked", false)}")
        Signin.revokeOnStart(context, "boot:${intent.action}")
    }
}
