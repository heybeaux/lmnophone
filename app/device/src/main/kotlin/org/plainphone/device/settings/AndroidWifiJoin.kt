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
import android.net.wifi.SupplicantState
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telecom.TelecomManager

/** Device-protected ownership only. No network identity or credential is persisted here. */
class AndroidWifiLocation(context: Context) : WifiLocationPort {
    private val app = context.applicationContext
    private val prefs = app.createDeviceProtectedStorageContext().getSharedPreferences("wifi_location", Context.MODE_PRIVATE)
    private val dpm = app.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(app.packageName, "org.plainphone.PlainphoneAdminReceiver")
    override fun enabled() = app.getSystemService(LocationManager::class.java).isLocationEnabled
    override fun owned() = prefs.getBoolean("owned", false)
    override fun saveOwned(value: Boolean) = prefs.edit().putBoolean("owned", value).commit()
    override fun setEnabled(value: Boolean) { dpm.setLocationEnabled(admin, value) }
    @Suppress("DEPRECATION")
    override fun callIdle(): Boolean? = try {
        if (app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            dpm.setPermissionGrantState(admin, app.packageName, Manifest.permission.READ_PHONE_STATE,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
        }
        if (app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) null
        else !app.getSystemService(TelecomManager::class.java).isInCall
    } catch (_: Exception) { null }
}

/** DO legacy Wi-Fi join; existing saved configurations are selected, never rewritten. */
@Suppress("DEPRECATION")
class AndroidWifiJoin(context: Context) : WifiJoinPort {
    private val app = context.applicationContext
    private val wifi = app.getSystemService(WifiManager::class.java)
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private var created: Int? = null
    private var previous: Int? = null
    fun choices(): List<WifiChoice> = try {
        if (app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) emptyList()
        else wifi.scanResults.sortedByDescending { it.level }.mapNotNull { scan ->
            val raw = scan.SSID
            val name = raw.filterNot { Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt() || it == '\u2028' || it == '\u2029' }
            if (name.isBlank() || raw == WifiManager.UNKNOWN_SSID) null
            else WifiChoice(raw, name, wifiSecurity(scan.capabilities), "SAE" in scan.capabilities && "PSK" !in scan.capabilities)
        }.distinctBy { it.ssid }.take(10)
    } catch (_: Exception) { emptyList() }
    override fun start(choice: WifiChoice, password: String): Int? {
        if (!wifi.isWifiEnabled || choice.security == WifiSecurity.Unsupported) return null
        if (app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        // Recheck live scan security before accepting stale UI input.
        if (choices().none { it == choice }) return null
        previous = wifi.connectionInfo?.networkId?.takeIf { it >= 0 }
        val existing = wifi.configuredNetworks.firstOrNull { it.SSID?.removeSurrounding("\"") == choice.ssid }
        if (existing != null) {
            val keys = existing.allowedKeyManagement
            val compatible = when (choice.security) {
                WifiSecurity.Open -> keys.get(WifiConfiguration.KeyMgmt.NONE) && existing.wepKeys.all { it == null }
                WifiSecurity.Personal -> if (choice.sae) keys.get(WifiConfiguration.KeyMgmt.SAE)
                    else keys.get(WifiConfiguration.KeyMgmt.WPA_PSK) || keys.get(WifiConfiguration.KeyMgmt.SAE)
                WifiSecurity.Unsupported -> false
            }
            if (!compatible) return null
        }
        val id = if (existing != null) existing.networkId else {
            val config = WifiConfiguration().apply {
                SSID = quoteWifiValue(choice.ssid)
                when (choice.security) {
                    WifiSecurity.Open -> allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    WifiSecurity.Personal -> {
                        if (choice.sae) { setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE) }
                        else allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                        preSharedKey = if (!choice.sae && password.length == 64 && password.all { it in "0123456789abcdefABCDEF" }) password else quoteWifiValue(password)
                    }
                    WifiSecurity.Unsupported -> return null
                }
            }
            wifi.addNetwork(config).also { if (it >= 0) created = it }
        }
        if (id < 0) return null
        try { if (wifi.enableNetwork(id, true)) return id } catch (_: Exception) { }
        cancel(id)
        return null
    }
    override fun outcome(networkId: Int): WifiJoinOutcome {
        if (!wifi.isWifiEnabled) return WifiJoinOutcome.Failed
        val info = wifi.connectionInfo
        val connected = connectivity.allNetworks.any { connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
        return if (connected && info.networkId == networkId && info.supplicantState == SupplicantState.COMPLETED) WifiJoinOutcome.Connected else WifiJoinOutcome.Waiting
    }
    override fun cancel(networkId: Int) {
        if (outcome(networkId) == WifiJoinOutcome.Connected) { complete(networkId); return }
        if (created == networkId) { wifi.removeNetwork(networkId); created = null }
        previous?.takeIf { it != networkId }?.let { wifi.enableNetwork(it, true) }
        previous = null
    }
    override fun complete(networkId: Int) { created = null; previous = null }
}

/** Process-wide cleanup survives Activity pause, Home, and calls. Restart reads the durable marker. */
class WifiJoinSession private constructor(context: Context) {
    val source = AndroidWifi(context)
    val locationPort = AndroidWifiLocation(context)
    val location = WifiLocationController(locationPort, SystemClock::elapsedRealtime)
    val forget = WifiForgetController(AndroidWifiForget(context), location, SystemClock::elapsedRealtime)
    val joinPort = AndroidWifiJoin(context)
    private var connectedName: String? = null
    private var connectedHandle: Long? = null
    val join = WifiJoinController(joinPort, SystemClock::elapsedRealtime) {
        // Name was selected from Android; keep it only for this confirmed, current connection.
        val facts = try { source.read() } catch (_: Exception) { null }
        connectedName = facts?.ssid?.removeSurrounding("\"")
        connectedHandle = facts?.connectionId
        location.leave()
    }
    private val handler = Handler(Looper.getMainLooper())
    private val cleanup = object : Runnable {
        override fun run() {
            join.tick(); forget.refresh(); location.tick()
            if (try { locationPort.owned() } catch (_: Exception) { true } || join.state.joining || forget.pending) handler.postDelayed(this, 1_000)
        }
    }
    init { location.recover(); schedule() }
    fun schedule() { handler.removeCallbacks(cleanup); handler.post(cleanup) }
    fun leave() { forget.leave(); join.cancel(); location.leave(); schedule() }
    fun facts(): WifiFacts {
        val facts = source.read()
        if (!facts.connected || facts.connectionId != connectedHandle) { connectedName = null; connectedHandle = null }
        return if (facts.ssid == null && facts.connectionId != null && facts.connectionId == connectedHandle) facts.copy(ssid = connectedName) else facts
    }
    companion object {
        @Volatile private var instance: WifiJoinSession? = null
        fun get(context: Context): WifiJoinSession = instance ?: synchronized(this) {
            instance ?: WifiJoinSession(context.applicationContext).also { instance = it }
        }
    }
}
