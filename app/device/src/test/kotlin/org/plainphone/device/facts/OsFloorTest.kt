// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.facts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.core.PackageName

/** The plan-independent repair's floor: OS role holders that Android reports as system (PLA-241 finding 2). */
class OsFloorTest {
    private val port = FakePackageSystemPort().apply {
        systemDialer = "com.android.dialer"
        defaultSms = "com.example.sms"
        activities[FloorIdentityReader.EMERGENCY_DIAL] = setOf("com.android.phone")
        packages(unsigned("com.android.dialer"), unsigned("com.android.phone"), unsigned("com.example.sms", system = false),
            unsigned("com.example.notes"))
    }

    @Test fun keepsSystemRoleHoldersOnly() {
        val floor = osFloor(port)
        assertEquals(setOf(PackageName("com.android.dialer"), PackageName("com.android.phone")), floor.neverHide)
        assertEquals(setOf(PackageName("com.android.dialer"), PackageName("com.android.phone")), floor.emergencyUi)
        assertTrue(floor.hasSystemDialer)
    }
}
