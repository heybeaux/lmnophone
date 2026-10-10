// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import org.plainphone.device.boot.RestrictionFilter

/** Test build: adb and the removal path must keep working (TEAM-POLICY, PLA-190). */
object BuildRestrictions {
    val filter = RestrictionFilter.TEST_BUILD
}
