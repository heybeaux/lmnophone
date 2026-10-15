// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.giveback

import org.junit.Assert.*
import org.junit.Test

class ReleasedLockTaskTest {
    @Test fun releaseRequiresNoPackagesNoLockedTaskAndOnlyNeutralFeatures() {
        for (flags in listOf(0, 16)) assertTrue(releasedLockTask(true, flags, true))
        for (flags in listOf(1, 2, 8, 17, 32, 64, 127)) assertFalse(releasedLockTask(true, flags, true))
        assertFalse(releasedLockTask(false, 16, true))
        assertFalse(releasedLockTask(true, 16, false))
    }
}
