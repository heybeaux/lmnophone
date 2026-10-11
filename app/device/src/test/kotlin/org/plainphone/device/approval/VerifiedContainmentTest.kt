// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.core.PackageName
import org.plainphone.device.DevicePolicyPort
import org.plainphone.policy.AppApprovalOutcome
import org.plainphone.policy.PolicyPlan

/**
 * PLA-229 finding 3. When an apply fails, containment reads back all three exclusions for each name it
 * contains: hidden, out of lock task and off Home. Whatever it cannot verify is reported in
 * [DeviceApprovalEffects.unresolvedCleanup]; a failed apply alone is not proof of containment.
 *
 * Setup: [target] was approved, then hidden and taken out of lock task behind Plainphone's back, so the
 * captured approval plan activates it again. Home publishes the names and then throws, which fails the
 * apply after the candidate was unhidden and put in lock task.
 */
class VerifiedContainmentTest {
    private val h = ApprovalHarness()
    private val target = h.target
    private lateinit var plan: PolicyPlan
    private val listed = mutableSetOf<PackageName>()
    private var publishFails = true
    private var removalSticks = true
    private var homeReadable = true
    private var removalReads = 0
    private var portFails = false

    private val home = object : HomeAppList {
        override fun publish(names: Set<PackageName>) {
            listed.addAll(names)
            if (publishFails) { portFails = armPort; error("partly published") }
            listed.retainAll(names)
        }
        override fun remove(name: PackageName) { if (removalSticks) listed.remove(name) }
        override fun lists(name: PackageName): Boolean? {
            removalReads++
            return if (homeReadable) name in listed else null
        }
    }
    /** When set, a failed publish also makes lock-task writes and hides throw from then on. */
    private var armPort = false
    private val port = object : DevicePolicyPort by h.port {
        override fun setLockTaskPackages(packages: Set<PackageName>) {
            if (portFails) error("lock-task write failed")
            h.port.setLockTaskPackages(packages)
        }
        override fun setHidden(name: PackageName, hidden: Boolean) {
            if (portFails && hidden) error("hide failed")
            h.port.setHidden(name, hidden)
        }
    }
    /** Names the live never-hide list protects on top of the harness facts (facts changed since the plan). */
    private var protectedSincePlan = emptySet<PackageName>()
    private val effects = DeviceApprovalEffects(h.dispatcher, port, h.grants, home, { h.neverHide()?.plus(protectedSincePlan) }) { null }

    @After fun stop() = h.close()

    private fun prepare() {
        h.beforeApply = { plan = it }
        assertEquals(AppApprovalOutcome.APPROVED, h.approve())
        h.port.setHidden(target, true)
        h.port.setLockTaskPackages(h.port.lockTask - target)
    }

    private fun apply(): Boolean = h.dispatcher.run { effects.applyPolicy(plan) }
    private fun unresolved() = h.dispatcher.run { effects.unresolvedCleanup }
    private fun state() = "hidden=${h.port.hidden(target.value)} permitted=${target in h.port.lockTask} home=${target in listed} reads=$removalReads"

    private fun assertFloorUntouched() {
        assertTrue("floor in lock task", h.port.lockTask.containsAll(setOf(h.plainphone, h.messages, h.dialer)))
        assertFalse("dialer visible", h.port.hidden(h.dialer.value))
        assertTrue("floor never reported", unresolved().keys.none { it in setOf(h.plainphone, h.messages, h.dialer) })
    }

    @Test fun verifiedContainmentReadsBackHomeAndReportsNothingUnresolved() {
        prepare()
        assertFalse(apply())
        assertTrue("Home removal read back: ${state()}", removalReads > 0)
        assertTrue(h.port.hidden(target.value))
        assertFalse(target in h.port.lockTask)
        assertFalse(target in listed)
        assertEquals(emptyMap<PackageName, Set<Exposure>>(), unresolved())
        assertFloorUntouched()
    }

    @Test fun aHomeRemovalThatDoesNotStickIsReported() {
        prepare()
        removalSticks = false
        assertFalse(apply())
        assertTrue("Home removal read back: ${state()}", removalReads > 0)
        assertTrue("still hidden and out of lock task: ${state()}", h.port.hidden(target.value) && target !in h.port.lockTask)
        assertEquals(mapOf(target to setOf(Exposure.HOME)), unresolved())
        assertFloorUntouched()
    }

    @Test fun anUnreadableHomeIsReported() {
        prepare()
        homeReadable = false
        assertFalse(apply())
        assertEquals(mapOf(target to setOf(Exposure.HOME)), unresolved())
    }

    @Test fun lockTaskRemovalAndHideBothFailingAreReported() {
        prepare()
        armPort = true
        assertFalse(apply())
        assertFalse("the failure is real: ${state()}", h.port.hidden(target.value))
        assertTrue("the failure is real: ${state()}", target in h.port.lockTask)
        assertFalse("Home removal still verified", target in listed)
        assertEquals(mapOf(target to setOf(Exposure.VISIBLE, Exposure.LOCK_TASK)), unresolved())
        assertFloorUntouched()
    }

    @Test fun aNameProtectedSinceThePlanIsNotTouchedAndIsReported() {
        prepare()
        protectedSincePlan = setOf(target)
        val ops = h.port.ops.size
        assertFalse(apply())
        assertTrue("left as the apply made it: ${state()}", !h.port.hidden(target.value) && target in h.port.lockTask)
        assertTrue("containment wrote nothing for it: ${h.port.ops.drop(ops)}", h.port.ops.drop(ops).none { it == "hide test.target" })
        assertEquals(mapOf(target to Exposure.entries.toSet()), unresolved())
        assertFloorUntouched()
    }

    @Test fun aLaterCompleteApplyClearsTheReport() {
        prepare()
        removalSticks = false
        assertFalse(apply())
        assertEquals(setOf(target), unresolved().keys)
        publishFails = false
        assertTrue(apply())
        assertEquals(emptyMap<PackageName, Set<Exposure>>(), unresolved())
        assertTrue(target in listed && !h.port.hidden(target.value) && target in h.port.lockTask)
    }
}
