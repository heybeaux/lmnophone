// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.settings

import android.app.Activity
import android.app.Instrumentation
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import org.plainphone.device.settings.AndroidWifi
import org.plainphone.device.settings.AndroidWifiForget
import org.plainphone.device.settings.AndroidWifiLocation
import org.plainphone.device.settings.WifiForgetController
import org.plainphone.device.settings.WifiLocationController

/** Destructive synthetic-network gate: private API 35 emulator only. Logs no network identifiers. */
@Suppress("DEPRECATION")
class WifiForgetProbe : Instrumentation() {
    override fun onCreate(arguments: Bundle) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
            check(android.os.Build.VERSION.SDK_INT == 35)
            // Host opens the target Activity after instrumentation starts its process.
            Thread.sleep(3_000)
            val app = targetContext
            val wifi = app.getSystemService(WifiManager::class.java)
            val locationPort = AndroidWifiLocation(app)
            val location = WifiLocationController(locationPort, SystemClock::elapsedRealtime)
            AndroidWifi(app).onOpen()
            check(!locationPort.enabled() && !locationPort.owned())
            check(Settings.Secure.getInt(app.contentResolver, Settings.Secure.LOCATION_MODE) == 0)
            result.putString("before", "location_mode=0 owned=false")
            // Add one non-connected synthetic saved configuration as a preservation sentinel.
            val sentinel = WifiConfiguration().apply {
                SSID = "\"Sam\""
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }
            val sentinelId = wifi.addNetwork(sentinel)
            check(sentinelId >= 0)
            // Establish the fixture's exact IDs with Location on, then return to the tap precondition.
            runOnMainSync { check(location.show()) }
            Thread.sleep(2_000)
            val before = wifi.configuredNetworks.map { it.networkId }.toSet()
            check(sentinelId in before && before.size >= 2)
            runOnMainSync { location.leave() }
            Thread.sleep(2_000)
            check(!locationPort.enabled() && !locationPort.owned())
            val port = AndroidWifiForget(app)
            check(port.read().connectedId == null)
            var removedId: Int? = null
            val observed = object : org.plainphone.device.settings.WifiForgetPort {
                override fun read() = port.read().also {
                    result.putString("last_read", "connected=${it.connected} validated=${it.validated} identity_readable=${it.connectedId != null} saved_count=${it.savedIds.size} location_on=${locationPort.enabled()}")
                }
                override fun removeConnected(networkId: Int): Boolean {
                    check(locationPort.enabled())
                    check(networkId != sentinelId)
                    removedId = networkId
                    return port.removeConnected(networkId)
                }
            }
            val controller = WifiForgetController(observed, location, SystemClock::elapsedRealtime)
            runOnMainSync { controller.forget() }
            repeat(120) {
                if (controller.pending) { Thread.sleep(100); runOnMainSync { controller.refresh(); location.tick() } }
            }
            result.putString("controller", "pending=${controller.pending} failed=${controller.failed} removal_attempted=${removedId != null}")
            check(!controller.pending && !controller.failed)
            // Verify the full saved set independently while identity access is available.
            check(!locationPort.enabled() && !locationPort.owned())
            runOnMainSync { check(location.show()) }
            Thread.sleep(2_000)
            val after = wifi.configuredNetworks.map { it.networkId }.toSet()
            result.putString("saved_check", "before_count=${before.size} after_count=${after.size} exact_remaining=${after == before - removedId} sentinel_preserved=${sentinelId in after}")
            check(removedId != null && after == before - removedId!! && sentinelId in after)
            check(!port.read().connected)
            runOnMainSync { location.leave() }
            Thread.sleep(2_000)
            check(!locationPort.enabled() && !locationPort.owned())
            check(Settings.Secure.getInt(app.contentResolver, Settings.Secure.LOCATION_MODE) == 0)
            result.putString("after", "one_connected_id_removed=true other_saved_ids_preserved=true disconnected=true location_mode=0 owned=false")
            // No connected ID: prove a second tap cannot remove the remaining sentinel.
            runOnMainSync { controller.onOpen(); controller.forget() }
            repeat(30) {
                if (controller.pending) { Thread.sleep(100); runOnMainSync { controller.refresh(); location.tick() } }
            }
            check(controller.failed && !controller.pending)
            check(!locationPort.enabled() && !locationPort.owned())
            runOnMainSync { check(location.show()) }
            Thread.sleep(2_000)
            check(wifi.configuredNetworks.map { it.networkId }.toSet() == after)
            runOnMainSync { location.leave() }
            check(!locationPort.enabled() && !locationPort.owned())
            result.putString("no_id", "failed=true removed_nothing=true location_mode=0 owned=false")
            result.putString("outcome", "PASS")
            finish(Activity.RESULT_OK, result)
        } catch (e: Exception) {
            result.putString("outcome", "FAIL ${e.javaClass.simpleName} line=${e.stackTrace.firstOrNull()?.lineNumber}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
