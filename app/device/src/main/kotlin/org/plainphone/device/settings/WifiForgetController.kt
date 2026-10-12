// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

/** Saved configuration identity only; no names, credentials or durable state. */
data class WifiForgetFacts(val connected: Boolean, val connectedId: Int?, val savedIds: Set<Int>,
    val validated: Boolean) {
    val forgettableId: Int? get() = connectedId?.takeIf { connected && validated && it >= 0 && it in savedIds }
}
interface WifiForgetPort {
    fun read(): WifiForgetFacts
    /** Recheck the current validated connection immediately before the single removal. */
    fun removeConnected(networkId: Int): Boolean
}
/** Serialized with joining and Location cleanup by WifiJoinSession on the main thread. */
class WifiForgetController(private val port: WifiForgetPort,
    private val location: WifiLocationController, private val now: () -> Long) {
    private data class Attempt(val id: Int, val remaining: Set<Int>, val started: Long)
    private var attempt: Attempt? = null
    private var openingAt: Long? = null
    val pending get() = attempt != null || openingAt != null
    var failed = false
        private set
    fun onOpen() { if (!pending) failed = false }
    fun forget() {
        if (pending) return
        failed = false
        if (!location.show()) { finish(false); return }
        openingAt = now()
        refresh()
    }
    fun refresh() {
        openingAt?.let { started ->
            val before = try { port.read() } catch (_: Exception) { null }
            if (before == null) { finish(false); return }
            val id = before.forgettableId
            if (id == null) {
                // LocationManager can read on before Wi-Fi stops redacting its identity.
                if (now() - started >= 2_000 || now() < started) finish(false)
                return
            }
            openingAt = null
            attempt = Attempt(id, before.savedIds - id, now())
            val accepted = try { port.removeConnected(id) } catch (_: Exception) { false }
            if (!accepted) { finish(false); return }
        }
        val current = attempt ?: return
        val facts = try { port.read() } catch (_: Exception) { null }
        val disconnected = facts != null && (!facts.connected ||
            facts.connectedId?.let { it >= 0 && it != current.id } == true)
        if (disconnected && facts?.savedIds == current.remaining) finish(true)
        else if (facts == null || now() - current.started >= 10_000 || now() < current.started) finish(false)
    }
    fun leave() { if (pending) finish(false) }
    private fun finish(success: Boolean) {
        attempt = null
        openingAt = null
        failed = !success
        location.leave()
    }
}
