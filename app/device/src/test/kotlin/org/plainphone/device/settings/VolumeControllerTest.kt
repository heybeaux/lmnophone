// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings
import org.junit.Assert.*
import org.junit.Test
class VolumeControllerTest {
    private class Port : VolumePort {
        var value = VolumeReading(4, 0, 7)
        var throws = false
        var ignores = false
        var unreadable = false
        var partial = false
        var failReadAfterWrite = false
        var nextReadFails = false
        override fun read(): VolumeReading {
            if (nextReadFails) { nextReadFails = false; error("read unavailable") }
            check(!unreadable); return value
        }
        override fun write(level: Int) {
            if (throws) throw SecurityException()
            if (partial) { value = value.copy(level = level); partial = false; throw SecurityException() }
            if (!ignores) value = value.copy(level = level)
            if (failReadAfterWrite) { nextReadFails = true; failReadAfterWrite = false }
        }
    }
    @Test fun ringFloorAndMaximum() {
        val p = Port(); val c = VolumeController(p, 1)
        c.setLevel(-10); assertEquals(1, p.value.level); assertEquals(1, c.state.reading?.level)
        c.adjust(-1); assertEquals(1, p.value.level)
        c.setLevel(100); assertEquals(7, p.value.level); assertFalse(c.state.failed)
    }
    @Test fun mediaFloorAndPlatformMinimum() {
        val p = Port(); val c = VolumeController(p, 0)
        c.setLevel(-10); assertEquals(0, p.value.level)
        p.value = VolumeReading(4, 2, 7); c.setLevel(0); assertEquals(2, p.value.level)
    }
    @Test fun thrownWrite() {
        val p = Port(); val c = VolumeController(p, 1); p.throws = true
        c.setLevel(6); assertEquals(4, c.state.reading?.level); assertTrue(c.state.failed)
    }
    @Test fun ignoredWrite() {
        val p = Port(); val c = VolumeController(p, 1); p.ignores = true
        c.setLevel(6); assertEquals(4, c.state.reading?.level); assertTrue(c.state.failed)
    }
    @Test fun partialThrowRestoresPreviousLevel() {
        val p = Port(); val c = VolumeController(p, 1); p.partial = true
        c.setLevel(6); assertEquals(4, p.value.level); assertEquals(4, c.state.reading?.level); assertTrue(c.state.failed)
    }
    @Test fun unreadableIsNotConfirmed() {
        val p = Port(); val c = VolumeController(p, 1); p.unreadable = true
        c.setLevel(6); assertNull(c.state.reading); assertTrue(c.state.failed); assertEquals(4, p.value.level)
    }
    @Test fun missingPostWriteReadbackRestoresTheOriginalVolume() {
        val p = Port(); val c = VolumeController(p, 1); p.failReadAfterWrite = true
        c.setLevel(6); assertEquals(4, p.value.level); assertEquals(4, c.state.reading?.level); assertTrue(c.state.failed)
    }
    @Test fun adjustmentUsesFreshReadAndClearsError() {
        val p = Port(); val c = VolumeController(p, 1); p.ignores = true
        c.setLevel(6); p.ignores = false; p.value = p.value.copy(level = 2)
        c.adjust(1); assertEquals(3, c.state.reading?.level); assertFalse(c.state.failed)
    }
}
