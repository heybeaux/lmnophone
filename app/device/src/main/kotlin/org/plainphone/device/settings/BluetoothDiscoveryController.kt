// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

/** Ephemeral radio identities and display names; never persist or log either. */
data class NearbyBluetoothDevice(val id: String, val name: String)
data class BluetoothDiscoveryState(val looking: Boolean = false,
    val devices: List<NearbyBluetoothDevice> = emptyList(), val failed: Boolean = false)
sealed interface BluetoothDiscoveryEvent {
    data class Found(val id: String, val name: String?, val bonded: Boolean) : BluetoothDiscoveryEvent
    data object Finished : BluetoothDiscoveryEvent
    data object Unavailable : BluetoothDiscoveryEvent
}
interface BluetoothDiscoveryPort {
    fun available(): Boolean
    fun start(onEvent: (BluetoothDiscoveryEvent) -> Unit): Boolean
    fun stop()
}

/** All methods/callbacks run on the host's main thread. Generation rejects retired callbacks. */
class BluetoothDiscoveryController(private val port: BluetoothDiscoveryPort,
    private val changed: (BluetoothDiscoveryState) -> Unit = {}) {
    var state = BluetoothDiscoveryState()
        private set
    private var generation = 0
    private var active = false
    private val devices = mutableMapOf<String, NearbyBluetoothDevice>()
    private fun publish(next: BluetoothDiscoveryState) { state = next; changed(next) }
    fun open() {
        close()
        active = true
        val run = generation
        publish(BluetoothDiscoveryState(looking = true))
        try {
            if (!port.available() || !port.start { event ->
                if (active && generation == run) receive(event)
            }) fail()
        } catch (_: Exception) { fail() }
    }
    fun checkAvailable() {
        if (!active) return
        try { if (!port.available()) fail() } catch (_: Exception) { fail() }
    }
    fun close() {
        active = false; generation++
        try { port.stop() } catch (_: Exception) { /* State is still retired. */ }
        devices.clear()
        publish(BluetoothDiscoveryState())
    }
    private fun fail() {
        close()
        publish(BluetoothDiscoveryState(failed = true))
    }
    private fun receive(event: BluetoothDiscoveryEvent) {
        when (event) {
            BluetoothDiscoveryEvent.Unavailable -> fail()
            BluetoothDiscoveryEvent.Finished -> publish(state.copy(looking = false))
            is BluetoothDiscoveryEvent.Found -> {
                if (!state.looking) return
                val name = event.name?.filterNot {
                    Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt() ||
                        it == '\u2028' || it == '\u2029'
                }?.takeUnless { it.isBlank() }
                if (event.bonded || name == null) devices.remove(event.id)
                else devices[event.id] = NearbyBluetoothDevice(event.id, name)
                val sorted = devices.values.sortedWith(
                    compareBy<NearbyBluetoothDevice, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                        .thenBy { it.name }.thenBy { it.id }).take(20)
                // Bound retained observations as well as the displayed list.
                devices.keys.retainAll(sorted.map { it.id }.toSet())
                publish(state.copy(devices = sorted))
            }
        }
    }
}
