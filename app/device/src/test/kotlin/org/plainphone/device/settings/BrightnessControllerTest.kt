// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

import org.junit.Assert.*
import org.junit.Test

class BrightnessControllerTest {
    private class Port : BrightnessPort {
        var owner = true
        var value = BrightnessReading(128, false)
        var failure = false
        var ignore = false
        val writes = mutableListOf<BrightnessReading>()
        override fun isOwner() = owner
        override fun read() = value
        override fun write(next: BrightnessReading) {
            writes += next
            if (failure) throw SecurityException()
            if (!ignore) value = next
        }
    }
    @Test fun refreshPicksUpOutsideManualChangeWithoutWrite() {
        val p = Port(); val c = BrightnessController(p)
        p.value = BrightnessReading(204, false); c.refresh()
        assertEquals(80, c.state.percent); assertTrue(p.writes.isEmpty())
    }
    @Test fun adjustmentStartsFromCurrentOutsideValue() {
        val p = Port(); val c = BrightnessController(p)
        p.value = BrightnessReading(204, false); c.adjust(-10)
        assertEquals(70, c.state.percent)
    }
    @Test fun mappingRoundsAndClampsToVisibleRange() {
        assertEquals(26, BrightnessController.raw(0))
        assertEquals(26, BrightnessController.raw(10))
        assertEquals(128, BrightnessController.raw(50))
        assertEquals(255, BrightnessController.raw(100))
        assertEquals(255, BrightnessController.raw(Int.MAX_VALUE))
        assertEquals(10, BrightnessController.percent(26))
        assertEquals(100, BrightnessController.percent(255))
    }
    @Test fun repeatedMinusAndSliderCannotCrossFloor() {
        val p = Port(); val c = BrightnessController(p)
        repeat(30) { c.adjust(-10) }
        assertEquals(26, p.value.raw)
        assertEquals(10, c.state.percent)
        c.setPercent(-100)
        assertEquals(26, p.value.raw)
    }
    @Test fun noOwnerMakesNoWriteAndReportsFailure() {
        val p = Port().apply { owner = false }; val c = BrightnessController(p)
        c.setPercent(90)
        assertTrue(c.state.failed)
        assertEquals(50, c.state.percent)
        assertTrue(p.writes.isEmpty())
    }
    @Test fun throwingOrIgnoredWriteNeverReportsRequestedValue() {
        for (ignored in listOf(false, true)) {
            val p = Port().apply { failure = !ignored; ignore = ignored }; val c = BrightnessController(p)
            c.setPercent(90)
            assertTrue(c.state.failed)
            assertEquals(50, c.state.percent)
            assertEquals(BrightnessReading(128, false), p.value)
        }
    }
    @Test fun partialWriteIsRestoredAfterFailure() {
        var value = BrightnessReading(128, false)
        var first = true
        val c = BrightnessController(object : BrightnessPort {
            override fun isOwner() = true
            override fun read() = value
            override fun write(next: BrightnessReading) {
                value = next
                if (first) { first = false; throw IllegalStateException() }
            }
        })
        c.setPercent(90)
        assertTrue(c.state.failed)
        assertEquals(BrightnessReading(128, false), value)
        assertEquals(50, c.state.percent)
    }
    @Test fun autoDisablesManualChangesAndTurningItOffRestoresFloor() {
        val p = Port(); val c = BrightnessController(p)
        c.setAutomatic(true)
        assertTrue(c.state.automatic)
        c.setPercent(10)
        assertEquals(128, p.value.raw)
        p.value = BrightnessReading(1, true)
        c.setAutomatic(false)
        assertEquals(BrightnessReading(26, false), p.value)
        assertFalse(c.state.failed)
    }
    @Test fun failedReadShowsUnavailableRatherThanGuessedBrightness() {
        val c = BrightnessController(object : BrightnessPort {
            override fun isOwner() = true
            override fun read(): BrightnessReading = throw IllegalStateException()
            override fun write(next: BrightnessReading) = error("must not write")
        })
        c.setPercent(60)
        assertNull(c.state.percent)
        assertTrue(c.state.failed)
    }
}
