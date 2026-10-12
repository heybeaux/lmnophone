// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

import kotlin.math.roundToInt

data class BrightnessReading(val raw: Int, val automatic: Boolean)
data class BrightnessState(val percent: Int?, val automatic: Boolean = false, val failed: Boolean = false)
interface BrightnessPort {
    fun isOwner(): Boolean
    fun read(): BrightnessReading
    fun write(next: BrightnessReading)
}

/** Synchronous, read-back-only presentation. No optimistic setting values. */
class BrightnessController(private val port: BrightnessPort) {
    var state = BrightnessState(null)
        private set
    init { refresh() }
    fun refresh() {
        state = read()?.let { BrightnessState(percent(it.raw), it.automatic, state.failed) }
            ?: BrightnessState(null, failed = true)
    }
    fun adjust(delta: Int) = change { before ->
        if (before.automatic) before else before.copy(raw = raw(percent(before.raw) + delta))
    }
    fun setPercent(percent: Int) = change { before ->
        if (before.automatic) before else before.copy(raw = raw(percent))
    }
    fun setAutomatic(automatic: Boolean) = change { before ->
        before.copy(automatic = automatic, raw = if (automatic) before.raw else before.raw.coerceAtLeast(MIN_RAW))
    }
    private fun read(): BrightnessReading? = try {
        port.read().takeIf { it.raw in 0..255 }
    } catch (_: Exception) { null }
    private fun change(next: (BrightnessReading) -> BrightnessReading) {
        val before = read()
        if (before == null) { state = BrightnessState(null, failed = true); return }
        val owner = try { port.isOwner() } catch (_: Exception) { false }
        if (!owner) { state = BrightnessState(percent(before.raw), before.automatic, true); return }
        val wanted = next(before)
        var success = false
        try {
            port.write(wanted)
            val actual = read()
            success = actual != null && actual.automatic == wanted.automatic &&
                (wanted.automatic || actual.raw == wanted.raw)
        } catch (_: Exception) { /* Restore below, including a partially applied write. */ }
        if (!success) {
            try { port.write(before) } catch (_: Exception) { /* Read actual state; never invent success. */ }
        }
        val actual = read()
        state = actual?.let { BrightnessState(percent(it.raw), it.automatic, !success) }
            ?: BrightnessState(null, failed = true)
    }
    companion object {
        const val MIN_PERCENT = 10
        const val MIN_RAW = 26
        fun raw(percent: Int) = (percent.coerceIn(MIN_PERCENT, 100) * 255.0 / 100).roundToInt()
        fun percent(raw: Int) = (raw.coerceIn(0, 255) * 100.0 / 255).roundToInt()
    }
}
