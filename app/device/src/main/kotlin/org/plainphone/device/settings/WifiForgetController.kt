// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

/**
 * Saved configuration identity only; no names, credentials or durable state. [connection] is the live
 * Wi-Fi network handle, readable with Location off, so a replacement connection is never mistaken for the asked one.
 */
data class WifiForgetFacts(val connected: Boolean, val connectedId: Int?, val savedIds: Set<Int>,
    val validated: Boolean, val connection: Long?) {
    val forgettableId: Int? get() = connectedId?.takeIf { connected && validated && it >= 0 && it in savedIds }
}
/** What the last Forget did, when it is known from Android's readback or by construction. */
enum class WifiForgetResult { Removed, NotRemoved }
interface WifiForgetPort {
    fun read(): WifiForgetFacts
    /** Recheck the confirmed connection and its configuration immediately before the single removal. */
    fun removeConnected(networkId: Int, connection: Long): Boolean
}
/** Serialized with joining and Location cleanup by WifiJoinSession on the main thread. */
class WifiForgetController(private val port: WifiForgetPort,
    private val location: WifiLocationController, private val now: () -> Long) {
    private data class Attempt(val id: Int, val remaining: Set<Int>, val started: Long)
    private var attempt: Attempt? = null
    private var openingAt: Long? = null
    private var confirmed: Long? = null
    /** A settled outcome stays until the Wi-Fi page has shown it (PLA-491). */
    private var shown = false
    val pending get() = attempt != null || openingAt != null
    var failed = false
        private set
    var result: WifiForgetResult? = null
        private set
    fun onOpen() { if (!pending && shown) { failed = false; result = null } }
    /** The Wi-Fi page is showing the current outcome. */
    fun presented() { if (!pending) shown = true }
    /** Forget only [connection], the one the question was asked about. */
    fun forget(connection: Long) {
        if (pending) return
        failed = false
        result = null
        shown = false
        confirmed = connection
        if (!location.show()) { finish(false); return }
        openingAt = now()
        refresh()
    }
    fun refresh() {
        openingAt?.let { started ->
            val before = try { port.read() } catch (_: Exception) { null }
            if (before == null) { finish(false); return }
            // Another connection, even to the same saved network or with the same name: nothing is removed.
            val connection = before.connection
            if (connection == null || connection != confirmed) { end(WifiForgetResult.NotRemoved); return }
            val id = before.forgettableId
            if (id == null) {
                // LocationManager can read on before Wi-Fi stops redacting its identity.
                if (now() - started >= 2_000 || now() < started) finish(false)
                return
            }
            openingAt = null
            attempt = Attempt(id, before.savedIds - id, now())
            val accepted = try { port.removeConnected(id, connection) } catch (_: Exception) { false }
            if (!accepted) { finish(false); return }
        }
        val current = attempt ?: return
        val facts = try { port.read() } catch (_: Exception) { null }
        val disconnected = facts != null && (!facts.connected ||
            facts.connectedId?.let { it >= 0 && it != current.id } == true)
        if (disconnected && facts?.savedIds == current.remaining) finish(true)
        else if (facts == null || now() - current.started >= 10_000 || now() < current.started) finish(false)
    }
    /**
     * Before the removal, leaving ends the attempt: nothing was removed. After it, the readback keeps
     * its own bounded window, because with Location off Android hides the saved list (PLA-488).
     */
    fun leave() { if (openingAt != null) end(WifiForgetResult.NotRemoved) }
    /** Ended before any removal: nothing was removed. */
    private fun end(outcome: WifiForgetResult) {
        openingAt = null
        confirmed = null
        shown = false
        failed = false
        result = outcome
        location.leave()
    }
    private fun finish(success: Boolean) {
        attempt = null
        openingAt = null
        confirmed = null
        shown = false
        failed = !success
        result = if (success) WifiForgetResult.Removed else null
        location.leave()
    }
}
