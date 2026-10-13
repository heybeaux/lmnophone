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
    private fun wifiNetworks() = connectivity.allNetworks.mapNotNull { network ->
        connectivity.getNetworkCapabilities(network)?.takeIf { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }?.let { network.networkHandle to it }
    }
    override fun read(): WifiForgetFacts {
        val saved = wifi.configuredNetworks ?: error("Wi-Fi status unavailable")
        val first = wifiNetworks()
        val info = wifi.connectionInfo
        val networks = wifiNetworks()
        val transport = networks.isNotEmpty()
        val validated = networks.size == 1 && networks.single().second.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        // The connection info belongs to this handle only if no switch happened around reading it (PLA-491).
        val connection = networks.singleOrNull()?.first?.takeIf { first.singleOrNull()?.first == it }
        // An association in progress is not a proven disconnection for the removal readback.
        val connected = transport || info?.supplicantState == SupplicantState.COMPLETED
        val id = if (transport && info?.supplicantState == SupplicantState.COMPLETED)
            info.networkId.takeIf { it >= 0 } else null
        // API 35 may return duplicate rows for one id. Removal is by distinct configuration id.
        return WifiForgetFacts(connected, id, saved.map { it.networkId }.toSet(), validated, connection)
    }
    override fun removeConnected(networkId: Int, connection: Long): Boolean {
        val facts = read()
        if (facts.connection != connection || facts.forgettableId != networkId) return false
        return wifi.removeNetwork(networkId)
    }
}
