// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager

/** DO removal of the live connected configuration only. Location is owned by the shared Wi-Fi controller. */
@Suppress("DEPRECATION")
class AndroidWifiForget(context: Context) : WifiForgetPort {
    private val app = context.applicationContext
    private val wifi = app.getSystemService(WifiManager::class.java)
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    override fun read(): WifiForgetFacts {
        val saved = wifi.configuredNetworks ?: error("Wi-Fi status unavailable")
        val info = wifi.connectionInfo
        val networks = connectivity.allNetworks.mapNotNull {
            val caps = connectivity.getNetworkCapabilities(it)
            caps?.takeIf { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }
        }
        val transport = networks.isNotEmpty()
        val validated = networks.size == 1 && networks.single().hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        // An association in progress is not a proven disconnection for the removal readback.
        val connected = transport || info?.supplicantState == SupplicantState.COMPLETED
        val id = if (transport && info?.supplicantState == SupplicantState.COMPLETED)
            info.networkId.takeIf { it >= 0 } else null
        // API 35 may return duplicate rows for one id. Removal is by distinct configuration id.
        return WifiForgetFacts(connected, id, saved.map { it.networkId }.toSet(), validated)
    }
    override fun removeConnected(networkId: Int): Boolean {
        if (read().forgettableId != networkId) return false
        return wifi.removeNetwork(networkId)
    }
}
