// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.plainphone.core.PackageName
import org.plainphone.device.ApplyResult
import org.plainphone.device.boot.BootOutcome
import org.plainphone.policy.ApplyStage
import org.plainphone.ui.home.HomeNotice

/** PLA-232 finding 8: the attention signal reaches HomeUiState; it never decides whether Home shows. */
class HomeAttentionTest {
    private val stages = listOf(ApplyStage.RESTRICTIONS, ApplyStage.EMERGENCY_FLOOR, ApplyStage.NEVER_HIDE_GUARD,
        ApplyStage.HIDES, ApplyStage.LOCK_TASK, ApplyStage.HOME)
    private fun applied(flipFailures: Set<PackageName> = emptySet()) = ApplyResult(stages, null, emptySet(), flipFailures)

    @Test fun corruptPolicyShowsTheNotice() {
        val outcome = BootOutcome(true, false, applied(), emptySet(), planNeedsAttention = true)
        assertEquals(HomeNotice.NeedsAttention, homeNotice(outcome))
    }

    @Test fun aFailedFlipShowsTheNotice() {
        val outcome = BootOutcome(true, false, applied(setOf(PackageName("com.android.dialer"))), emptySet())
        assertEquals(HomeNotice.NeedsAttention, homeNotice(outcome))
    }

    @Test fun aDisabledFloorPackageShowsTheNotice() {
        val outcome = BootOutcome(true, false, applied(), emptySet(), disabledFloor = setOf(PackageName("com.android.dialer")))
        assertEquals(HomeNotice.NeedsAttention, homeNotice(outcome))
    }

    @Test fun aCleanRunShowsNoNotice() {
        assertNull(homeNotice(BootOutcome(true, false, applied(), emptySet())))
    }
}
