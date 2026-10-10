// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.PackageName
import kotlin.test.*

class EmergencyFloorTest {
    private val alerts = PackageName("test.alerts")
    private val display = FloorIdentity(alerts, FloorRole.CELL_BROADCAST_DISPLAY, IdentitySource.SYSTEM_ONLY_QUERY)

    @Test fun `trusted cell broadcast display is emergency UI and never hidden`() {
        val floor = EmergencyFloor.compute(listOf(InstalledPackage(alerts, true, 1)), listOf(display))
        assertEquals(setOf(alerts), floor.neverHide)
        assertEquals(setOf(alerts), floor.emergencyUi)
    }

    @Test fun `non system cell broadcast display cannot claim emergency UI`() {
        val floor = EmergencyFloor.compute(listOf(InstalledPackage(alerts, false, 1)), listOf(display))
        assertTrue(floor.neverHide.isEmpty())
        assertTrue(floor.emergencyUi.isEmpty())
    }

    @Test fun `cell broadcast display rejects every wrong identity source`() {
        for (source in IdentitySource.entries - IdentitySource.SYSTEM_ONLY_QUERY) {
            val floor = EmergencyFloor.compute(listOf(InstalledPackage(alerts, true, 1)), listOf(display.copy(source = source)))
            assertTrue(floor.neverHide.isEmpty(), "$source")
            assertTrue(floor.emergencyUi.isEmpty(), "$source")
        }
    }

    @Test fun `cell broadcast receiver alone is never hidden but has no emergency UI`() {
        val floor = EmergencyFloor.compute(listOf(InstalledPackage(alerts, true, 1)),
            listOf(display.copy(role = FloorRole.CELL_BROADCAST_RECEIVER)))
        assertEquals(setOf(alerts), floor.neverHide)
        assertTrue(floor.emergencyUi.isEmpty())
    }
}
