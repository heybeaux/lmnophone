// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

enum class BluetoothAdapterState { On, Off, TurningOn, TurningOff, Absent }
/** Ephemeral display data only; never log or persist device names. */
data class BluetoothFacts(val adapter: BluetoothAdapterState, val paired: List<String?> = emptyList())
fun interface BluetoothPort { fun read(): BluetoothFacts }
data class BluetoothState(val enabled: Boolean = false, val paired: List<String?> = emptyList(),
    val readFailed: Boolean = true)

/** Pure presentation reducer. Null names use the UI's localized unnamed-device label. */
class BluetoothController(private val port: BluetoothPort) {
    var state = BluetoothState()
        private set
    fun clear() { state = BluetoothState() }
    fun refresh() {
        state = try {
            val facts = port.read()
            if (facts.adapter != BluetoothAdapterState.On) BluetoothState(readFailed = false)
            else BluetoothState(true, facts.paired.map { name -> name?.filterNot {
                Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt() ||
                    it == '\u2028' || it == '\u2029'
            }?.takeUnless { it.isBlank() } }
                .sortedWith(compareBy<String?, String>(String.CASE_INSENSITIVE_ORDER) { it ?: "Unnamed device" }
                    .thenBy { it ?: "Unnamed device" }).take(10), false)
        } catch (_: Exception) { BluetoothState() }
    }
}
