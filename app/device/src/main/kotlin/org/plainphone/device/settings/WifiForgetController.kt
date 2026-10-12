// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

/** Saved configuration identity only; no names, credentials or durable state. */
data class WifiForgetFacts(val connected: Boolean, val connectedId: Int?, val savedIds: Set<Int>) {
    val forgettableId: Int? get() = connectedId?.takeIf { connected && it >= 0 && it in savedIds }
}
interface WifiForgetPort {
    fun read(): WifiForgetFacts
    /** Recheck the current connection immediately before the single removal. */
    fun removeConnected(networkId: Int): Boolean
}
class WifiForgetController(private val port: WifiForgetPort, private val now: () -> Long) {
    private data class Attempt(val id: Int, val remaining: Set<Int>, val started: Long)
    private var attempt: Attempt? = null
    val pending get() = attempt != null
    var networkId: Int? = null
        private set
    var failed = false
        private set
    fun onOpen() { if (!pending) failed = false; refresh() }
    fun forget(expectedId: Int?) {
        if (pending) return
        failed = false
        val before = try { port.read() } catch (_: Exception) { null }
        if (expectedId == null || before?.forgettableId != expectedId) { failed = true; networkId = null; return }
        attempt = Attempt(expectedId, before.savedIds - expectedId, now())
        networkId = null
        val accepted = try { port.removeConnected(expectedId) } catch (_: Exception) { false }
        if (!accepted) { failed = true; attempt = null; return }
        refresh()
    }
    fun refresh() {
        val facts = try { port.read() } catch (_: Exception) { null }
        val current = attempt
        if (current != null) {
            val disconnected = facts != null && (!facts.connected ||
                facts.connectedId?.let { it >= 0 && it != current.id } == true)
            if (disconnected && facts?.savedIds == current.remaining) attempt = null
            else if (facts == null || now() - current.started >= 10_000 || now() < current.started) {
                failed = true; attempt = null
            }
        }
        networkId = if (pending || failed) null else facts?.forgettableId
    }
}
