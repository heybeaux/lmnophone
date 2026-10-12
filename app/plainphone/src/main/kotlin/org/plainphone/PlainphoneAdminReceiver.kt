// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import org.plainphone.boot.PolicyRuntime
import org.plainphone.update.SystemUpdatePostpone

/**
 * Device Owner receiver. On a fresh emulator:
 * `adb shell dpm set-device-owner org.plainphone/.PlainphoneAdminReceiver`
 *
 * Sets the postpone update policy as soon as it can (each re-apply sets it again if needed), and passes
 * Android's pending-update report to the runtime (PLA-447).
 */
class PlainphoneAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        SystemUpdatePostpone.forDevice(context).apply()
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        SystemUpdatePostpone.forDevice(context).apply()
    }

    override fun onSystemUpdatePending(context: Context, intent: Intent, receivedTime: Long) {
        PolicyRuntime.get(context).onSystemUpdatePending(receivedTime)
    }
}
