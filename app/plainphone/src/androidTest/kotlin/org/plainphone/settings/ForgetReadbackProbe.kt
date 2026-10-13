// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.settings

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import org.plainphone.device.settings.AndroidWifi
import org.plainphone.device.settings.AndroidWifiForget
import org.plainphone.device.settings.AndroidWifiLocation
import org.plainphone.device.settings.WifiLocationController

/**
 * Read-only PLA-488 question: can the Device Owner read saved Wi-Fi IDs while Location is off?
 * Private API 35 emulator only. Reports counts and booleans, never network names or IDs.
 */
class ForgetReadbackProbe : Instrumentation() {
    override fun onCreate(arguments: Bundle) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
            check(android.os.Build.VERSION.SDK_INT == 35)
            val app = targetContext
            val locationPort = AndroidWifiLocation(app)
            val location = WifiLocationController(locationPort, SystemClock::elapsedRealtime)
            AndroidWifi(app).onOpen()
            check(Settings.Secure.getInt(app.contentResolver, Settings.Secure.LOCATION_MODE) == 0)
            val port = AndroidWifiForget(app)
            fun read(label: String) = port.read().also {
                result.putString(label, "location_on=${locationPort.enabled()} connected=${it.connected} " +
                    "identity_readable=${it.connectedId != null} saved_count=${it.savedIds.size}")
            }
            val off = read("location_off")
            runOnMainSync { check(location.show()) }
            Thread.sleep(2_000)
            val on = read("location_on")
            runOnMainSync { location.leave() }
            Thread.sleep(2_000)
            check(!locationPort.enabled() && !locationPort.owned())
            result.putString("same_saved_set", "${off.savedIds == on.savedIds}")
            result.putString("outcome", "PASS")
            finish(Activity.RESULT_OK, result)
        } catch (e: Exception) {
            result.putString("outcome", "FAIL ${e.javaClass.simpleName} line=${e.stackTrace.firstOrNull()?.lineNumber}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
