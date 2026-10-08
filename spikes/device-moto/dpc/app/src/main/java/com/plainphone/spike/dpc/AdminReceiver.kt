// THROWAWAY SPIKE (PLA-5).
package com.plainphone.spike.dpc

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Spike.log("admin enabled; deviceOwner=${Spike.isDeviceOwner(context)}")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Spike.log("admin disabled")
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        Spike.log("lock task entering: $pkg")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        Spike.log("lock task exiting")
    }
}
