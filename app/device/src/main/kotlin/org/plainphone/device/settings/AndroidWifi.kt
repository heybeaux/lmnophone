// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager

/**
 * Reads the platform's latest scan; never initiates a scan, joins, forgets, or changes a setting.
 * https://developer.android.com/develop/connectivity/wifi/wifi-scan
 * No SSIDs, BSSIDs, addresses or scan results are logged or persisted.
 */
class AndroidWifi(context: Context) : WifiPort {
    private val app = context.applicationContext
    private val wifi = app.getSystemService(WifiManager::class.java)
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private fun hasPermission() = app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Authorized preview grant, once per page opening; readback decides availability. */
    fun onOpen() {
        if (hasPermission()) return
        try {
            val dpm = app.getSystemService(DevicePolicyManager::class.java)
            if (dpm.isDeviceOwnerApp(app.packageName)) {
                dpm.setPermissionGrantState(ComponentName(app.packageName, "org.plainphone.PlainphoneAdminReceiver"),
                    app.packageName, Manifest.permission.ACCESS_FINE_LOCATION,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            }
        } catch (_: Exception) { /* Read-only fallback remains usable. */ }
    }

    @Suppress("DEPRECATION")
    override fun read(): WifiFacts {
        if (!wifi.isWifiEnabled) return WifiFacts(false, false, null, null)
        // Check every network, not only the default: mobile data or a VPN may be the default.
        val networks = connectivity.allNetworks.mapNotNull { network ->
            connectivity.getNetworkCapabilities(network)?.takeIf {
                it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }?.let { network to it }
        }
        val current = networks.firstOrNull { it.first == connectivity.activeNetwork }
            ?: networks.minByOrNull { it.first.networkHandle }
        val connected = current != null
        val readable = hasPermission() && app.getSystemService(LocationManager::class.java).isLocationEnabled
        val ssid = if (connected && readable) try { wifi.connectionInfo?.ssid } catch (_: Exception) { null } else null
        val nearby = if (readable) try {
            wifi.scanResults.map { WifiNetwork(it.SSID, it.level) }
        } catch (_: Exception) { null } else null
        return WifiFacts(true, connected, ssid, nearby,
            validated = current?.second?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            captivePortal = current?.second?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true,
            connectionId = current?.first?.networkHandle)
    }
}
