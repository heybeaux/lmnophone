// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

data class VolumeReading(val level: Int, val min: Int, val max: Int, val adjustable: Boolean = true)
data class VolumeState(val reading: VolumeReading? = null, val failed: Boolean = false)
interface VolumePort {
    fun read(): VolumeReading
    fun write(level: Int)
}

/** Only confirmed values are presented; a failed write attempts to restore its fresh pre-write value. */
class VolumeController(private val port: VolumePort, private val floor: Int) {
    var state = VolumeState()
        private set
    init { refresh() }
    private fun read(): VolumeReading? = try {
        port.read().takeIf { it.min >= 0 && it.max >= maxOf(it.min, floor) && it.level in it.min..it.max }
    } catch (_: Exception) { null }
    fun onOpen() { state = VolumeState(); refresh() }
    fun refresh() { state = VolumeState(read(), state.failed).let { it.copy(failed = it.failed || it.reading == null) } }
    fun setLevel(level: Int) = change { level }
    fun adjust(delta: Int) = change { it.level + delta }
    private fun change(next: (VolumeReading) -> Int) {
        val before = read()
        if (before == null) { state = VolumeState(failed = true); return }
        if (!before.adjustable) { state = VolumeState(before, state.failed); return }
        val wanted = next(before).coerceIn(maxOf(floor, before.min), before.max)
        var success = false
        try {
            port.write(wanted)
            success = read()?.level == wanted
        } catch (_: Exception) { /* Restore even if the write partially took effect. */ }
        if (!success) {
            try { port.write(before.level) } catch (_: Exception) { /* Final read remains authoritative. */ }
        }
        val actual = read()
        state = VolumeState(actual, !success || actual == null || actual.level != wanted)
    }
}
