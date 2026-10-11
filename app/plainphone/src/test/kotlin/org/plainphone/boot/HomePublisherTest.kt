// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.plainphone.core.PackageName
import org.plainphone.device.ApplyResult
import org.plainphone.device.boot.BootOutcome
import org.plainphone.policy.ApplyStage
import org.plainphone.ui.home.HomeNotice

/**
 * PLA-266 finding 2: Home re-reads its app list after every ready re-apply, not only when the notice
 * changes. Two healthy setup runs with different starter lists both end with notice=null, so a refresh
 * keyed on the notice alone never fired the second time.
 */
class HomePublisherTest {
    private val stages = listOf(ApplyStage.EMERGENCY_FLOOR, ApplyStage.NEVER_HIDE_GUARD, ApplyStage.HIDES,
        ApplyStage.LOCK_TASK, ApplyStage.HOME)
    private fun ready() = BootOutcome(true, false, ApplyResult(stages, null, emptySet(), emptySet()), emptySet())
    private fun notReady() = BootOutcome(false, false, ApplyResult(stages.take(2), ApplyStage.HIDES, emptySet(), emptySet()), emptySet())

    @Test fun twoHealthyRunsWithTheSameNoticeEachRefreshHome() {
        val home = HomePublisher()
        // Run 1: the starter list without Maps. Run 2: Maps added back. Same notice both times.
        home.afterRun(locked = false, ready())
        val first = home.refresh.value
        home.afterRun(locked = false, ready())
        val second = home.refresh.value

        assertNull(first.notice)
        assertNull(second.notice)
        assertNotEquals(first, second)
        assertEquals(HomeGate.READY, home.gate.value)
    }

    @Test fun aLockedRunNeitherOpensNorRefreshesHome() {
        val home = HomePublisher()
        val before = home.refresh.value
        home.afterRun(locked = true, ready())
        assertEquals(HomeGate.CHECKING, home.gate.value)
        assertEquals(before, home.refresh.value)
    }

    @Test fun aFailedRunClosesHomeAndKeepsTheNotice() {
        val home = HomePublisher()
        home.afterRun(locked = false, ready())
        val ok = home.refresh.value
        home.afterRun(locked = false, notReady())
        assertEquals(HomeGate.NOT_READY, home.gate.value)
        assertEquals(ok.revision, home.refresh.value.revision)
    }

    @Test fun aThrownRunShowsTheNoticeAndClosesHome() {
        val home = HomePublisher()
        home.afterRun(locked = false, null)
        assertEquals(HomeGate.NOT_READY, home.gate.value)
        assertEquals(HomeNotice.NeedsAttention, home.refresh.value.notice)
    }

    @Test fun anAttentionRunStillRefreshesHome() {
        val home = HomePublisher()
        home.afterRun(locked = false, ready())
        val first = home.refresh.value
        val flipFailed = BootOutcome(true, false, ApplyResult(stages, null, emptySet(), setOf(PackageName("com.android.dialer"))), emptySet())
        home.afterRun(locked = false, flipFailed)
        assertEquals(HomeNotice.NeedsAttention, home.refresh.value.notice)
        assertNotEquals(first.revision, home.refresh.value.revision)
    }

    @Test fun notDeviceOwnerClosesHome() {
        val home = HomePublisher()
        home.afterRun(locked = false, ready())
        home.notOwner()
        assertEquals(HomeGate.NOT_READY, home.gate.value)
    }
}
