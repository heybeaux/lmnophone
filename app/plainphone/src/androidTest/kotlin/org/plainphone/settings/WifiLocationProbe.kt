// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.settings

import android.app.Activity
import android.app.Instrumentation
import android.app.admin.DevicePolicyManager
import android.content.pm.PackageManager
import android.net.*
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Throwaway probe: owned API 35 emulator only; no setting writes or name logging. */
class WifiLocationProbe : Instrumentation() {
    override fun onCreate(arguments: Bundle) { super.onCreate(arguments); start() }
    @Suppress("DEPRECATION")
    override fun onStart() {
        val result = Bundle()
        try {
            check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
            check(android.os.Build.VERSION.SDK_INT == 35)
            val app = targetContext
            // Host starts Home after instrumentation kills the prior target process.
            Thread.sleep(3_000)
            check(app.getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(app.packageName))
            check(app.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_GRANTED)
            result.putString("locationMode", Settings.Secure.getString(app.contentResolver, Settings.Secure.LOCATION_MODE))
            val wifi = app.getSystemService(WifiManager::class.java)
            fun named(raw: String?) = raw != null && raw.removeSurrounding("\"").isNotBlank() && raw.removeSurrounding("\"") != WifiManager.UNKNOWN_SSID
            result.putString("connectionInfo", try {
                val info = wifi.connectionInfo
                "returned=true infoPresent=${info != null} named=${named(info?.ssid)} unknownMarker=${info?.ssid == WifiManager.UNKNOWN_SSID}"
            } catch (e: Exception) { "threw=${e.javaClass.simpleName}" })
            result.putString("scanResults", try {
                val scan = wifi.scanResults
                "returned=true count=${scan.size} namedCount=${scan.count { named(it.SSID) }}"
            } catch (e: Exception) { "threw=${e.javaClass.simpleName}" })
            val cm = app.getSystemService(ConnectivityManager::class.java)
            val latch = CountDownLatch(1)
            val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val info = caps.transportInfo as? WifiInfo
                    result.putString("transportInfo", "callback=true wifiInfo=${info != null} named=${named(info?.ssid)} unknownMarker=${info?.ssid == WifiManager.UNKNOWN_SSID} validated=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)} captive=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)}")
                    latch.countDown()
                }
            }
            cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback)
            try { check(latch.await(10, TimeUnit.SECONDS)) } finally { cm.unregisterNetworkCallback(callback) }
            result.putString("outcome", "PASS")
            finish(Activity.RESULT_OK, result)
        } catch (e: Exception) {
            result.putString("outcome", "FAIL ${e.javaClass.simpleName}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
