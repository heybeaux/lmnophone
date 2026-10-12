// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

interface WifiLocationPort {
    fun enabled(): Boolean
    fun owned(): Boolean
    fun saveOwned(value: Boolean): Boolean
    fun callIdle(): Boolean?
    fun setEnabled(value: Boolean)
}
/** One serialized owner; durable marker is written before the first Location change. */
class WifiLocationController(private val port: WifiLocationPort, private val now: () -> Long) {
    private var started: Long? = null
    private var closing = false
    private var unknownCallSince: Long? = null
    var offFailed = false
        private set
    fun recover() { closing = true; tick() }
    fun show(): Boolean = try {
        if (closing) tick()
        if (closing) false
        else if (port.enabled()) true
        else if (!port.saveOwned(true)) false
        else {
            started = now()
            try { port.setEnabled(true) } catch (_: Exception) { }
            port.enabled().also { if (!it) leave() }
        }
    } catch (_: Exception) { leave(); false }
    fun leave() { started = null; closing = true; tick() }
    fun tick() {
        if (started?.let { now() - it >= 300_000 || now() < it } == true) { started = null; closing = true }
        if (!closing) return
        try {
            if (!port.owned()) { closing = false; unknownCallSince = null; offFailed = false; return }
            if (port.enabled()) {
                when (port.callIdle()) {
                    null -> {
                        val time = now()
                        if (unknownCallSince == null || time < unknownCallSince!!) unknownCallSince = time
                        if (time - unknownCallSince!! >= 60_000) offFailed = true
                        return
                    }
                    false -> { unknownCallSince = null; return }
                    true -> unknownCallSince = null
                }
                try { port.setEnabled(false) } catch (_: Exception) { }
            }
            // Even a thrown write can have taken effect. Keep the marker until confirmed off.
            val stillOn = port.enabled()
            offFailed = stillOn
            if (!stillOn) { unknownCallSince = null; if (port.saveOwned(false)) closing = false }
        } catch (_: Exception) { offFailed = true }
    }
}

enum class WifiSecurity { Open, Personal, Unsupported }
/** Raw identity is transient and separate from the cleaned display label. */
data class WifiChoice(val ssid: String, val name: String, val security: WifiSecurity, val sae: Boolean = false)
fun wifiSecurity(capabilities: String): WifiSecurity {
    val c = capabilities.uppercase()
    if (listOf("WEP", "EAP", "OWE", "WAPI", "SUITE_B", "DPP").any { it in c }) return WifiSecurity.Unsupported
    if (("WPA2" in c || "RSN" in c) && "PSK" in c || "SAE" in c) return WifiSecurity.Personal
    return if (c == "[ESS]" || c == "[ESS][WPS]" || c == "[WPS][ESS]") WifiSecurity.Open else WifiSecurity.Unsupported
}
enum class WifiJoinOutcome { Waiting, Connected, Failed }
interface WifiJoinPort {
    fun start(choice: WifiChoice, password: String): Int?
    fun outcome(networkId: Int): WifiJoinOutcome
    fun cancel(networkId: Int)
    fun complete(networkId: Int)
}
data class WifiJoinState(val choice: WifiChoice? = null, val joining: Boolean = false, val failed: Boolean = false)
class WifiJoinController(private val port: WifiJoinPort, private val now: () -> Long, private val onConnected: () -> Unit) {
    var state = WifiJoinState()
        private set
    private var id: Int? = null
    private var started = 0L
    fun select(choice: WifiChoice) { cancel(); state = WifiJoinState(choice) }
    fun connect(password: String) {
        val choice = state.choice ?: return
        if (state.joining || choice.security == WifiSecurity.Unsupported) return
        id = try { port.start(choice, password) } catch (_: Exception) { null }
        started = now()
        state = state.copy(joining = id != null, failed = id == null)
    }
    fun tick() {
        val network = id ?: return
        val outcome = try { port.outcome(network) } catch (_: Exception) { WifiJoinOutcome.Failed }
        when {
            outcome == WifiJoinOutcome.Connected -> {
                try { port.complete(network) } catch (_: Exception) { }
                id = null; state = WifiJoinState(); onConnected()
            }
            outcome == WifiJoinOutcome.Failed || now() - started >= 30_000 || now() < started -> {
                try { port.cancel(network) } catch (_: Exception) { }
                id = null; state = state.copy(joining = false, failed = true)
            }
        }
    }
    fun cancel() {
        id?.let { try { port.cancel(it) } catch (_: Exception) { } }
        id = null; state = WifiJoinState()
    }
}
