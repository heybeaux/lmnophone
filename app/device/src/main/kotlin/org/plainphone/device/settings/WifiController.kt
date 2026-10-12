// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

/** Snapshot only. Null nearby means unreadable; an empty list is a successful empty scan. */
data class WifiNetwork(val ssid: String, val strength: Int)
data class WifiFacts(val enabled: Boolean, val connected: Boolean, val ssid: String?, val nearby: List<WifiNetwork>?,
    val validated: Boolean? = null, val captivePortal: Boolean = false,
    val connectionId: Long? = null)
fun interface WifiPort { fun read(): WifiFacts }
enum class WifiInternetStatus { SignInRequired, NoInternet }
data class WifiState(val enabled: Boolean = true, val connected: Boolean = false,
    val network: String? = null, val nearby: List<String>? = null,
    val readFailed: Boolean = false, val internetStatus: WifiInternetStatus? = null)

/** Pure presentation controller: no Wi-Fi, settings or permission mutations. */
class WifiController(private val port: WifiPort,
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private var unvalidatedSince: Long? = null
    private var observedConnection: Long? = null
    fun onOpen() { unvalidatedSince = null; observedConnection = null }
    var state = WifiState()
        private set
    fun refresh() {
        val facts = try { port.read() } catch (_: Exception) { null }
        val now = elapsedMillis()
        val waiting = facts != null && facts.enabled && facts.connected &&
            facts.validated == false && !facts.captivePortal
        if (!waiting || observedConnection != facts?.connectionId) unvalidatedSince = null
        observedConnection = facts?.connectionId
        if (waiting && (unvalidatedSince == null || now < unvalidatedSince!!)) unvalidatedSince = now
        val internet = when {
            facts == null || !facts.enabled || !facts.connected -> null
            facts.captivePortal -> WifiInternetStatus.SignInRequired
            waiting && now - unvalidatedSince!! >= 10_000 -> WifiInternetStatus.NoInternet
            else -> null
        }
        state = when {
            facts == null -> WifiState(readFailed = true)
            !facts.enabled -> WifiState(enabled = false)
            else -> WifiState(true, facts.connected,
                if (facts.connected) name(facts.ssid) else null,
                facts.nearby?.sortedWith(compareByDescending<WifiNetwork> { it.strength }.thenBy { it.ssid })
                    ?.mapNotNull { name(it.ssid) }?.distinct()?.take(10), internetStatus = internet)
        }
    }
    private fun name(raw: String?): String? = raw?.removeSurrounding("\"")
        ?.filterNot { Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt() || it == '\u2028' || it == '\u2029' }
        ?.takeUnless { it.isBlank() || it == "<unknown ssid>" }
}
