// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings
import org.junit.Assert.*
import org.junit.Test
class VolumeControllerTest {
    private class Port : VolumePort {
        var value = VolumeReading(4, 0, 7)
        val writes = mutableListOf<Int>()
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
            writes += level
            if (throws) throw SecurityException()
            if (partial) { value = value.copy(level = level); partial = false; throw SecurityException() }
            if (!ignores) value = value.copy(level = level)
            if (failReadAfterWrite) { nextReadFails = true; failReadAfterWrite = false }
        }
    }
    @Test fun failuresAreIndependentAndResetOnOpen() {
        val ring = Port(); val media = Port()
        val r = VolumeController(ring, 1); val m = VolumeController(media, 0)
        ring.ignores = true; r.setLevel(6)
        m.setLevel(5)
        assertTrue(r.state.failed); assertFalse(m.state.failed)
        media.ignores = true; m.setLevel(6)
        ring.ignores = false; r.setLevel(5)
        assertFalse(r.state.failed); assertTrue(m.state.failed)
        ring.ignores = true; r.setLevel(6)
        r.onOpen(); m.onOpen()
        assertFalse(r.state.failed); assertFalse(m.state.failed)
    }
    @Test fun refreshReadsOutsideChangesWithoutWritingOrLosingFailure() {
        val p = Port(); val c = VolumeController(p, 1)
        p.ignores = true; c.setLevel(6)
        p.writes.clear(); p.value = p.value.copy(level = 2)
        c.refresh()
        assertEquals(2, c.state.reading?.level); assertTrue(c.state.failed)
        assertTrue(p.writes.isEmpty())
    }
    @Test fun silentOrVibratePreventsEvenStaleControlWritesButMediaStillWorks() {
        val p = Port(); val c = VolumeController(p, 1)
        p.value = p.value.copy(adjustable = false)
        c.adjust(1); c.setLevel(6)
        assertFalse(c.state.reading!!.adjustable)
        assertFalse(c.state.failed); assertTrue(p.writes.isEmpty())
        val media = Port(); VolumeController(media, 0).adjust(1)
        assertEquals(listOf(5), media.writes)
        p.value = p.value.copy(adjustable = true); c.refresh()
        assertTrue(c.state.reading!!.adjustable)
    }
    @Test fun openDoesNotHideAReadFailure() {
        val p = Port(); val c = VolumeController(p, 1)
        p.unreadable = true; c.onOpen()
        assertNull(c.state.reading); assertTrue(c.state.failed)
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
