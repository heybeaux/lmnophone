// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

/** Snapshot only. Null nearby means unreadable; an empty list is a successful empty scan. */
data class WifiNetwork(val ssid: String, val strength: Int)
data class WifiFacts(val enabled: Boolean, val connected: Boolean, val ssid: String?, val nearby: List<WifiNetwork>?)
fun interface WifiPort { fun read(): WifiFacts }
data class WifiState(val enabled: Boolean = true, val connected: Boolean = false,
    val network: String? = null, val nearby: List<String>? = null)

/** Pure presentation controller: no Wi-Fi, settings or permission mutations. */
class WifiController(private val port: WifiPort) {
    var state = WifiState()
        private set
    fun refresh() {
        val facts = try { port.read() } catch (_: Exception) { null }
        state = when {
            facts == null -> WifiState()
            !facts.enabled -> WifiState(enabled = false)
            else -> WifiState(true, facts.connected,
                if (facts.connected) name(facts.ssid) else null,
                facts.nearby?.sortedWith(compareByDescending<WifiNetwork> { it.strength }.thenBy { it.ssid })
                    ?.mapNotNull { name(it.ssid) }?.distinct()?.take(10))
        }
    }
    private fun name(raw: String?): String? = raw?.removeSurrounding("\"")
        ?.filterNot { Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt() || it == '\u2028' || it == '\u2029' }
        ?.takeUnless { it.isBlank() || it == "<unknown ssid>" }
}
