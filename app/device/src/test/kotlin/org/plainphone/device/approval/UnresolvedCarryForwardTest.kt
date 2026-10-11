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
 * PLA-276 finding 3. A target left in [DeviceApprovalEffects.unresolvedCleanup] by a failed apply is carried
 * into every later unsuccessful apply: its exclusions are retried and read back again, and it leaves the
 * report only when all three read back closed, or when an apply completes and reads back. A repeated failure
 * of the same plan must not mistake the exposure it left behind for an app that was already active, and a
 * later plan that no longer names the target must not forget it.
 *
 * Setup as in [VerifiedContainmentTest]: [target] was approved, then hidden and taken out of lock task, so
 * the captured plan activates it again; Home publishes and then throws, which fails the apply.
 */
class UnresolvedCarryForwardTest {
    private val h = ApprovalHarness()
    private val target = h.target
    private lateinit var plan: PolicyPlan
    private val listed = mutableSetOf<PackageName>()
    private var publishFails = true
    /** Lock-task writes and hides throw while set. */
    private var portFails = false
    /** Set by the first failed publish: from then on the port fails until a test clears it. */
    private var armPort = true

    private val home = object : HomeAppList {
        override fun publish(names: Set<PackageName>) {
            listed.addAll(names)
            if (publishFails) { if (armPort) portFails = true; error("partly published") }
            listed.retainAll(names)
        }
        override fun remove(name: PackageName) { listed.remove(name) }
        override fun lists(name: PackageName) = name in listed
    }
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
    private val effects = DeviceApprovalEffects(h.dispatcher, port, h.grants, home, h.neverHide) { null }
    private val open = setOf(Exposure.VISIBLE, Exposure.LOCK_TASK)

    @After fun stop() = h.close()

    private fun prepare() {
        h.beforeApply = { plan = it }
        assertEquals(AppApprovalOutcome.APPROVED, h.approve())
        h.port.setHidden(target, true)
        h.port.setLockTaskPackages(h.port.lockTask - target)
    }

    private fun apply(p: PolicyPlan = plan): Boolean = h.dispatcher.run { effects.applyPolicy(p) }
    private fun unresolved() = h.dispatcher.run { effects.unresolvedCleanup }
    private fun state() = "hidden=${h.port.hidden(target.value)} permitted=${target in h.port.lockTask} home=${target in listed}"
    /** The same approval policy without [target]: a later plan that no longer names it. */
    private fun withoutTarget() = plan.copy(unhide = plan.unhide - target, lockTaskPackages = plan.lockTaskPackages - target,
        expected = plan.expected.copy(unhiddenAndEnabled = plan.expected.unhiddenAndEnabled - target,
            unsuspended = plan.expected.unsuspended - target, lockTaskPackages = plan.expected.lockTaskPackages?.minus(target)))

    private fun assertContained() {
        assertTrue("hidden: ${state()}", h.port.hidden(target.value))
        assertFalse("out of lock task: ${state()}", target in h.port.lockTask)
        assertFalse("off Home: ${state()}", target in listed)
    }

    @Test fun aRepeatedFailureOfTheSamePlanKeepsTheReport() {
        prepare()
        assertFalse(apply())
        assertEquals(mapOf(target to open), unresolved())
        repeat(2) {
            assertFalse(apply())
            assertTrue("still exposed: ${state()}", !h.port.hidden(target.value) && target in h.port.lockTask)
            assertEquals(mapOf(target to open), unresolved())
        }
    }

    @Test fun aRepeatedFailureRetriesTheCleanupAndClearsWhatNowReadsBackClosed() {
        prepare()
        assertFalse(apply())
        assertEquals(mapOf(target to open), unresolved())
        portFails = false
        armPort = false
        assertFalse(apply())
        assertContained()
        assertEquals(emptyMap<PackageName, Set<Exposure>>(), unresolved())
    }

    @Test fun aLaterFailedPlanThatOmitsTheTargetStillReportsIt() {
        prepare()
        assertFalse(apply())
        assertEquals(mapOf(target to open), unresolved())
        assertFalse(apply(withoutTarget()))
        assertTrue("still exposed: ${state()}", !h.port.hidden(target.value) && target in h.port.lockTask)
        assertEquals(mapOf(target to open), unresolved())
    }

    @Test fun aLaterFailedPlanThatOmitsTheTargetRetriesItsCleanup() {
        prepare()
        assertFalse(apply())
        portFails = false
        armPort = false
        assertFalse(apply(withoutTarget()))
        assertContained()
        assertEquals(emptyMap<PackageName, Set<Exposure>>(), unresolved())
    }

    @Test fun aSuccessfulApplyStillClearsTheReport() {
        prepare()
        assertFalse(apply())
        assertEquals(setOf(target), unresolved().keys)
        portFails = false
        armPort = false
        publishFails = false
        assertTrue(apply())
        assertEquals(emptyMap<PackageName, Set<Exposure>>(), unresolved())
    }

    @Test fun aSuccessfulPlanThatOmitsTheTargetClearsTheReport() {
        prepare()
        assertFalse(apply())
        portFails = false
        armPort = false
        publishFails = false
        val hides = withoutTarget().let { it.copy(hidden = it.hidden + target, expected = it.expected.copy(hidden = it.expected.hidden?.plus(target))) }
        assertTrue(apply(hides))
        assertContained()
        assertEquals(emptyMap<PackageName, Set<Exposure>>(), unresolved())
    }
}
