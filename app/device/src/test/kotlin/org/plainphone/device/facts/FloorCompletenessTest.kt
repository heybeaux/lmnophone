// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.facts

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.policy.FloorResult
import org.plainphone.policy.FloorRole

/** PLA-399 finding 1: an unreadable floor role makes the keep set unknown. */
class FloorCompletenessTest {
    private val port = FakePackageSystemPort().apply {
        systemDialer = "com.android.dialer"
        packages(unsigned("com.android.dialer"))
    }
    private val empty = FloorResult(emptySet(), emptySet(), false)

    @Test fun osFloorReadReportsAPartialRoleFailure() {
        port.failing += "enabledInputMethods"
        val read = osFloorRead(port)
        assertEquals(setOf(FloorRole.KEYBOARD), read.unreadable)
        assertTrue(read.floor.hasSystemDialer)
        assertEquals(read.floor, osFloor(port))
    }

    @Test fun unknownUntilASourceReportsAndFalseWhenItThrows() {
        val completeness = FloorCompleteness()
        assertFalse(completeness.complete)
        completeness.osFloor { OsFloorRead(empty, emptySet()) }
        assertTrue(completeness.complete)
        runCatching { completeness.osFloor { throw IllegalStateException("no floor") } }
        assertFalse(completeness.complete)
    }

    @Test fun anUnreadableRoleIsIncomplete() {
        val completeness = FloorCompleteness()
        completeness.osFloor { OsFloorRead(empty, setOf(FloorRole.EMERGENCY_DIALER)) }
        assertFalse(completeness.complete)
    }

    @Test fun theLaterSourceDecides() {
        val completeness = FloorCompleteness()
        completeness.facts { setOf(FloorRole.KEYBOARD) }
        assertFalse(completeness.complete)
        completeness.facts { emptySet() }
        assertTrue(completeness.complete)
        runCatching { completeness.facts { throw IllegalStateException("no facts") } }
        assertFalse(completeness.complete)
    }
}
