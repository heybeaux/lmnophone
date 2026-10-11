// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ReapplyTimingsTest {
    @Test fun nestedAndRepeatedMeasurementsAccumulateAndResetBetweenPasses() {
        var now = 0L
        val timing = ReapplyTimings { now }
        val result = timing.measure("input") {
            now += 2
            timing.measure("osFloor") { now += 3 }
            now += 5
            42
        }
        timing.measure("osFloor") { now += 7 }
        assertEquals(42, result)
        assertEquals("osFloor=10,input=10", timing.summary())
        timing.reset()
        assertEquals("", timing.summary())
    }

    @Test fun throwingWorkKeepsItsExceptionAndElapsedTime() {
        var now = 0L
        val timing = ReapplyTimings { now }
        val failure = IllegalStateException("test")
        try {
            timing.measure("write.EMERGENCY_FLOOR") { now = 11; throw failure }
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
        assertEquals("write.EMERGENCY_FLOOR=11", timing.summary())
    }
}
