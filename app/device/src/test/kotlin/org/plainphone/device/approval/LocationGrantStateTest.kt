// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.junit.Assert.*
import org.junit.Test

class LocationGrantStateTest {
    @Test fun foregroundLocationFixedGrantIsNotAnActualDenial() {
        for (name in listOf("ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION")) {
            assertEquals(PolicyGrant.GRANTED, locationGrantState("android.permission.$name", PolicyGrant.DENIED, true, false))
            assertEquals(PolicyGrant.DENIED, locationGrantState("android.permission.$name", PolicyGrant.DENIED, false, false))
            assertEquals(PolicyGrant.DENIED, locationGrantState("android.permission.$name", PolicyGrant.GRANTED, false, false))
            assertEquals(PolicyGrant.DEFAULT, locationGrantState("android.permission.$name", PolicyGrant.DEFAULT, true, false))
            assertEquals(PolicyGrant.DENIED, locationGrantState("android.permission.$name", PolicyGrant.DENIED, true, true))
        }
        assertEquals(PolicyGrant.DENIED, locationGrantState("android.permission.CAMERA", PolicyGrant.DENIED, true, false))
    }
}
