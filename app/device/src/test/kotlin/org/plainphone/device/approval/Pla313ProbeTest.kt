// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.core.PackageName

/**
 * The reviewer's PLA-313 `:device` probes, verbatim (finding 4, and the passing floor-repair check).
 * `editingHideFailureMustStillRemoveLockTaskAndHome` (findings 1–3) is `:policy` and lands with that fix.
 */
class Pla313ProbeTest {
    private fun unreadableHome(open: Boolean) = ApprovalHarness().use { h ->
        val floor = h.neverHide()!!
        var promoted = false
        var firstRead = true
        h.port.setHidden(h.target, false)
        h.port.lockTask = h.port.lockTask + h.target
        h.home.publish(h.port.lockTask - h.target)
        val home = object : HomeAppList by h.home {
            override fun lists(name: PackageName): Boolean? {
                if (name == h.target && firstRead) { firstRead = false; return null }
                return h.home.lists(name)
            }
            override fun remove(name: PackageName) {
                h.home.remove(name)
                promoted = true
            }
        }
        val effects = DeviceApprovalEffects(h.dispatcher, h.port, h.grants, home,
            { floor + if (promoted) setOf(h.target) else emptySet() }) { null }
        assertFalse(h.dispatcher.run {
            if (open) effects.openPermissionWindow(h.identity) else effects.containFailedActivation(setOf(h.target))
        })
        println("open=$open: ${h.state()}")
        assertFalse("repair must not invent Home membership from an unreadable prior state", h.home.lists(h.target))
    }
    @Test fun containmentMustNotRelistAnAbsentHomeEntry() = unreadableHome(false)
    @Test fun windowOpenMustNotRelistAnAbsentHomeEntry() = unreadableHome(true)

    @Test fun aFailedFloorRepairReturnsFalse() = ApprovalHarness().use { h ->
        h.port.setHidden(h.target, false)
        h.port.lockTask += h.target
        h.home.publish(h.port.lockTask)
        val floor = h.neverHide()!!
        var promoted = false
        val home = object : HomeAppList by h.home {
            override fun remove(name: PackageName) { h.home.remove(name); promoted = true }
            override fun relist(name: PackageName) { error("repair unavailable") }
        }
        val effects = DeviceApprovalEffects(h.dispatcher, h.port, h.grants, home,
            { floor + if (promoted) setOf(h.target) else emptySet() }) { null }
        assertFalse(h.dispatcher.run { effects.containFailedActivation(setOf(h.target)) })
        assertFalse(h.home.lists(h.target))
        assertTrue(h.target in h.port.lockTask)
    }
}
