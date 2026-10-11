// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.core.PackageName
import org.plainphone.device.DevicePolicyPort
import org.plainphone.policy.AppApprovalOutcome
import org.plainphone.policy.PolicyPlan

/**
 * The PLA-276 reviewer's probes for findings 2 and 3, copied verbatim from the review evidence (pinned at
 * `3bc7690`). `lowBatteryRetryMustRetryFailedContainment` (finding 1) is in `:policy` and arrives with the
 * PLA-278 merge (PLA-288).
 */
class Pla276ProbeTest {
    @Test fun secondFailedApplyMustKeepUnresolvedExposure() = ApprovalHarness().use { h ->
        lateinit var plan: PolicyPlan
        h.beforeApply = { plan = it }
        assertEquals(AppApprovalOutcome.APPROVED, h.approve())
        h.port.setHidden(h.target, true)
        h.port.setLockTaskPackages(h.port.lockTask - h.target)
        var failCleanup = false
        val listed = mutableSetOf<PackageName>()
        val home = object : HomeAppList {
            override fun publish(names: Set<PackageName>) {
                listed.addAll(names)
                failCleanup = true
                error("publish failed after write")
            }
            override fun remove(name: PackageName) { listed.remove(name) }
            override fun lists(name: PackageName) = name in listed
        }
        val port = object : DevicePolicyPort by h.port {
            override fun setHidden(name: PackageName, hidden: Boolean) {
                if (failCleanup && hidden) error("hide failed")
                h.port.setHidden(name, hidden)
            }
            override fun setLockTaskPackages(packages: Set<PackageName>) {
                if (failCleanup) error("lock-task write failed")
                h.port.setLockTaskPackages(packages)
            }
        }
        val effects = DeviceApprovalEffects(h.dispatcher, port, h.grants, home, h.neverHide) { null }
        h.dispatcher.run {
            assertFalse(effects.applyPolicy(plan))
            assertEquals(setOf(Exposure.VISIBLE, Exposure.LOCK_TASK), effects.unresolvedCleanup[h.target])
            println("first apply: hidden=${h.port.hidden(h.target.value)} permitted=${h.target in h.port.lockTask} home=${h.target in listed} unresolved=${effects.unresolvedCleanup}")
            assertFalse(effects.applyPolicy(plan))
            assertTrue(h.visibleAndPermitted())
            println("second apply: hidden=${h.port.hidden(h.target.value)} permitted=${h.target in h.port.lockTask} home=${h.target in listed} unresolved=${effects.unresolvedCleanup}")
            assertEquals("a second failed apply must not forget open access", setOf(Exposure.VISIBLE, Exposure.LOCK_TASK), effects.unresolvedCleanup[h.target])
        }
    }

    @Test fun floorPromotionAfterSnapshotMustKeepUiAccess() = ApprovalHarness().use { h ->
        val floor = h.neverHide()!!
        var promoted = false
        h.port.setHidden(h.target, false)
        h.port.lockTask = h.port.lockTask + h.target
        h.home.publish(h.port.lockTask)
        val port = object : DevicePolicyPort by h.port {
            override fun lockTaskPackages(): Set<PackageName> {
                promoted = true
                return h.port.lockTaskPackages()
            }
        }
        val effects = DeviceApprovalEffects(h.dispatcher, port, h.grants, h.home,
            { floor + if (promoted) setOf(h.target) else emptySet() }) { null }
        h.dispatcher.run {
            assertFalse(effects.containFailedActivation(setOf(h.target)))
            println("floor changes after snapshot: ${h.state()}")
            assertFalse("the live hide guard preserves visibility", h.port.hidden(h.target.value))
            assertTrue("a package newly protected before the first write must retain UI access", h.target in h.port.lockTask)
            assertTrue(h.home.lists(h.target))
        }
    }
}
