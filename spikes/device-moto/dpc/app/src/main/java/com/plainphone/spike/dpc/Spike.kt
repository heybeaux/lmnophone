// THROWAWAY SPIKE (PLA-5). Not MVP code.
package com.plainphone.spike.dpc

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object Spike {
    const val TAG = "PPSPIKE"

    /** Lock task feature names accepted by the `lt-features` command. */
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

    /** Device-protected storage, so the debug timer can be read before first unlock. */
    fun prefs(c: Context): SharedPreferences =
        c.createDeviceProtectedStorageContext().getSharedPreferences("spike", Context.MODE_PRIVATE)

    fun log(msg: String) {
        Log.i(TAG, msg)
    }

    fun featureNames(flags: Int) =
        FEATURES.filter { flags and it.value != 0 }.keys.joinToString("|").ifEmpty { "NONE" }
}
