// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.core.PackageName
import org.plainphone.policy.AppApprovalOutcome
import org.plainphone.policy.AppApprovalPhase

/**
 * The reviewer's PLA-313 `:device` probes, verbatim (finding 4, the passing floor-repair check, and
 * `editingHideFailureMustStillRemoveLockTaskAndHome` for findings 1–3, fixed in `:policy` by PLA-314).
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

    @Test fun editingHideFailureMustStillRemoveLockTaskAndHome() {
        val exposed = mutableListOf<String>()
        for (path in listOf("edit", "resume", "restart")) for (throwing in listOf(false, true)) ApprovalHarness().use { h ->
            assertEquals(AppApprovalOutcome.APPROVED, h.approve())
            if (throwing) h.port.throwOnWrite = "packages" else h.port.hidesStick = false
            val initial = runCatching { h.edit() }
            val result = when (path) {
                "resume" -> runCatching { h.resume() }
                "restart" -> runCatching { h.dispatcher.run { h.controller.onStart() } }
                else -> initial
            }
            println("$path throwing=$throwing: ${h.state()} pending=${h.approvals.pending?.phase} result=$result")
            assertEquals(AppApprovalPhase.EDITING, h.approvals.pending?.phase)
            if (h.target in h.port.lockTask || h.home.lists(h.target)) exposed += "$path throwing=$throwing"
        }
        assertTrue("failed hide skipped containment: $exposed", exposed.isEmpty())
    }
}
