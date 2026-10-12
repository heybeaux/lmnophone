// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

import org.junit.Assert.*
import org.junit.Test

class TextSizeControllerTest {
    private class Port : TextSizePort {
        var allowed = true
        var value = 1f
        var readFails = false
        var writeFails = false
        var ignored = false
        var substituted: Float? = null
        var failReadAfterWrite = false
        val writes = mutableListOf<Float>()
        override fun canWrite() = allowed
        override fun read(): Float { check(!readFails); return value }
        override fun write(scale: Float): Boolean {
            writes += scale
            if (writeFails) throw SecurityException()
            if (!ignored) value = substituted ?: scale
            if (failReadAfterWrite) readFails = true
            return !ignored
        }
    }
    @Test fun visitsEveryStepBothWaysAndStopsAtEndpoints() {
        val p = Port(); val c = TextSizeController(p)
        for (value in listOf(1.15f, 1.3f, 1.5f, 1.8f, 2f)) {
            c.adjust(true); assertEquals(value, c.state.scale)
        }
        c.adjust(true); assertEquals(5, p.writes.size); assertFalse(c.state.canIncrease)
        for (value in listOf(1.8f, 1.5f, 1.3f, 1.15f, 1f)) {
            c.adjust(false); assertEquals(value, c.state.scale)
        }
        c.adjust(false); assertEquals(10, p.writes.size); assertFalse(c.state.canDecrease)
    }
    @Test fun unlistedValueIsReadUnchangedAndMovesInChosenDirection() {
        for ((current, down, up) in listOf(Triple(1.42f, 1.3f, 1.5f), Triple(.85f, .85f, 1f), Triple(2.2f, 2f, 2.2f))) {
            for (increase in listOf(false, true)) {
                val p = Port().apply { value = current }; val c = TextSizeController(p)
                assertEquals(current, c.state.scale); assertTrue(p.writes.isEmpty())
                c.adjust(increase); assertEquals(if (increase) up else down, c.state.scale)
            }
        }
    }
    @Test fun permissionAbsentOrLostDisablesBothButtonsAndNeverWrites() {
        val p = Port(); val c = TextSizeController(p)
        p.allowed = false; c.refresh(); c.adjust(true)
        assertEquals(1f, c.state.scale); assertTrue(c.state.failed)
        assertFalse(c.state.canDecrease); assertFalse(c.state.canIncrease); assertTrue(p.writes.isEmpty())
        p.allowed = true; c.refresh(); assertFalse(c.state.failed); assertTrue(c.state.canIncrease)
    }
    @Test fun adjustmentUsesFreshValueAndPermission() {
        val p = Port(); val c = TextSizeController(p)
        p.value = 1.42f; c.adjust(true); assertEquals(1.5f, c.state.scale)
        p.allowed = false; c.adjust(false); assertEquals(listOf(1.5f), p.writes)
    }
    @Test fun failedOrIgnoredWriteShowsOnlyReadbackAndError() {
        for (throwing in listOf(false, true)) {
            val p = Port().apply { writeFails = throwing; ignored = !throwing }
            val c = TextSizeController(p); c.adjust(true)
            assertEquals(1f, c.state.scale); assertTrue(c.state.failed)
            c.refresh(); assertTrue(c.state.failed)
        }
    }
    @Test fun differentReadbackNeverShowsRequestedValue() {
        val p = Port().apply { substituted = 1.25f }; val c = TextSizeController(p)
        c.adjust(true); assertEquals(1.25f, c.state.scale); assertTrue(c.state.failed)
    }
    @Test fun failedReadNeverUsesStaleValueOrWrites() {
        val p = Port(); val c = TextSizeController(p)
        p.readFails = true; c.adjust(true)
        assertNull(c.state.scale); assertTrue(c.state.failed); assertTrue(p.writes.isEmpty())
        assertFalse(c.state.canIncrease); assertFalse(c.state.canDecrease)
    }
    @Test fun failedReadAfterWriteNeverShowsOptimisticValue() {
        val p = Port().apply { failReadAfterWrite = true }; val c = TextSizeController(p)
        c.adjust(true); assertNull(c.state.scale); assertTrue(c.state.failed)
    }
    @Test fun invalidValuesAreUnavailable() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f)) {
            val p = Port().apply { this.value = value }; val c = TextSizeController(p)
            c.adjust(true); assertNull(c.state.scale); assertTrue(c.state.failed); assertTrue(p.writes.isEmpty())
        }
    }
}
