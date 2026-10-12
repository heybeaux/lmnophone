// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.settings

import org.junit.Assert.*
import org.junit.Test

class BatteryValuesTest {
    @Test fun percentageUsesScaleAndRejectsMissingOrInvalidLevels() {
        assertEquals(64, batteryPercent(64, 100))
        assertEquals(64, batteryPercent(128, 200))
        assertEquals(0, batteryPercent(0, 100))
        assertEquals(100, batteryPercent(100, 100))
        for ((level, scale) in listOf(-1 to 100, 20 to 0, 20 to -1, 101 to 100)) {
            assertNull(batteryPercent(level, scale))
        }
    }
}
