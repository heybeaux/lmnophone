// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

/** Values are confirmed system settings, never optimistic requests. */
data class TextSizeState(val scale: Float? = null, val canDecrease: Boolean = false,
    val canIncrease: Boolean = false, val failed: Boolean = false)
interface TextSizePort {
    fun canWrite(): Boolean
    fun read(): Float
    fun write(scale: Float): Boolean
}

class TextSizeController(private val port: TextSizePort) {
    private var changeFailed = false
    var state = TextSizeState()
        private set
    init { refresh() }
    fun onOpen() { changeFailed = false; refresh() }
    fun refresh() { publish(read(), allowed()) }
    fun adjust(increase: Boolean) {
        val before = read()
        val writable = allowed()
        if (before == null || !writable) { publish(before, writable); return }
        val wanted = next(before, increase)
        if (wanted == null) { publish(before, writable); return }
        val written = try { port.write(wanted) } catch (_: Exception) { false }
        val actual = read()
        changeFailed = !written || actual != wanted
        publish(actual, allowed())
    }
    private fun allowed() = try { port.canWrite() } catch (_: Exception) { false }
    private fun read() = try { port.read().takeIf { it.isFinite() && it > 0f } } catch (_: Exception) { null }
    private fun publish(scale: Float?, writable: Boolean) {
        state = TextSizeState(scale,
            writable && scale != null && next(scale, false) != null,
            writable && scale != null && next(scale, true) != null,
            changeFailed || !writable || scale == null)
    }
    private fun next(scale: Float, increase: Boolean): Float? =
        if (increase) steps.firstOrNull { it > scale } else steps.lastOrNull { it < scale }
    private val steps get() = listOf(1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f)
}
