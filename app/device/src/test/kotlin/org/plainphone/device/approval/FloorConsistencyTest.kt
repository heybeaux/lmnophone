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

/**
 * PLA-276 finding 2: the floor-consistency boundary. The live never-hide list is read again before every
 * destructive write (lock-task list, Home removal, hide) and once after the last one. A name it protects
 * at any of those reads is not touched from then on, and every exclusion this call made for it is put back:
 * lock-task membership, Home listing and visibility. Nothing this call did not take away is added, so the
 * rest of the never-hide list is never pushed into lock task or Home.
 *
 * Setup: [target] and [other] are active (visible, in applied lock task, on Home) next to the floor.
 * Never-hide Messages is deliberately not in lock task or on Home, to prove the repair adds only what it removed.
 */
class FloorConsistencyTest {
    private val h = ApprovalHarness()
    private val target = h.target
    private val other = h.sibling
    private val base = h.neverHide()!!
    /** Names the live floor protects on top of [base]; tests promote [target] at a chosen moment. */
    private var promoted = emptySet<PackageName>()
    private var floorReadable = true
    private val live: () -> Set<PackageName>? = { if (floorReadable) base + promoted else null }

    private var onLockTaskWrite: () -> Unit = {}
    private var onHomeRemove: () -> Unit = {}
    private var onHomeRead: () -> Unit = {}
    private var onHide: () -> Unit = {}

    private val port = object : DevicePolicyPort by h.port {
        override fun setLockTaskPackages(packages: Set<PackageName>) {
            h.port.setLockTaskPackages(packages)
            onLockTaskWrite()
        }
        override fun setHidden(name: PackageName, hidden: Boolean) {
            h.port.setHidden(name, hidden)
            if (hidden) onHide()
        }
    }
    private val home = object : HomeAppList by h.home {
        override fun remove(name: PackageName) {
            h.home.remove(name)
            onHomeRemove()
        }
        override fun lists(name: PackageName): Boolean {
            onHomeRead()
            return h.home.lists(name)
        }
    }
    private val effects = DeviceApprovalEffects(h.dispatcher, port, h.grants, home, live) { null }

    init {
        for (name in listOf(target, other)) h.port.setHidden(name, false)
        h.port.lockTask = setOf(h.plainphone, h.dialer, target, other)
        h.home.publish(h.port.lockTask)
        h.port.ops.clear()
    }

    @After fun stop() = h.close()

    private fun contain(names: Set<PackageName> = setOf(target)) = h.dispatcher.run { effects.containFailedActivation(names) }
    private fun promote() { promoted = setOf(target) }
    private fun state(name: PackageName = target) = h.state(name)

    private fun assertKeepsAccess(name: PackageName = target) {
        assertFalse("visible: ${state(name)}", h.port.hidden(name.value))
        assertTrue("in lock task: ${state(name)}", name in h.port.lockTask)
        assertTrue("on Home: ${state(name)}", h.home.lists(name))
    }

    private fun assertContained(name: PackageName) {
        assertTrue("hidden: ${state(name)}", h.port.hidden(name.value))
        assertFalse("out of lock task: ${state(name)}", name in h.port.lockTask)
        assertFalse("off Home: ${state(name)}", h.home.lists(name))
    }

    /** The repair never adds what this call did not remove: Messages stays out, the floor stays in. */
    private fun assertOnlyOwnRemovalsRestored() {
        assertFalse("never-hide Messages not added to lock task", h.messages in h.port.lockTask)
        assertFalse("never-hide Messages not added to Home", h.home.lists(h.messages))
        assertTrue(h.port.lockTask.containsAll(setOf(h.plainphone, h.dialer)))
    }

    @Test fun promotedBeforeTheLockTaskWriteIsNeverRemoved() {
        var reads = 0
        val ports = object : DevicePolicyPort by port {
            override fun lockTaskPackages(): Set<PackageName> {
                if (reads++ == 0) promote()
                return h.port.lockTaskPackages()
            }
        }
        val effects = DeviceApprovalEffects(h.dispatcher, ports, h.grants, home, live) { null }
        assertFalse(h.dispatcher.run { effects.containFailedActivation(setOf(target)) })
        assertKeepsAccess()
        assertTrue("no lock-task write removed it: ${h.port.ops}", h.port.ops.none { it.startsWith("lockTask") && "test.target" !in it })
        assertOnlyOwnRemovalsRestored()
    }

    @Test fun promotedRightAfterTheLockTaskWriteIsPutBack() {
        onLockTaskWrite = { promote() }
        assertFalse(contain())
        assertKeepsAccess()
        assertTrue("never hidden: ${h.port.ops}", "hide test.target" !in h.port.ops)
        assertOnlyOwnRemovalsRestored()
    }

    @Test fun promotedRightAfterTheHomeRemovalIsPutBack() {
        onHomeRemove = { promote() }
        assertFalse(contain())
        assertKeepsAccess()
        assertTrue("never hidden: ${h.port.ops}", "hide test.target" !in h.port.ops)
        assertOnlyOwnRemovalsRestored()
    }

    @Test fun promotedBetweenTheHomeStageAndTheHideIsNeverHiddenAndIsPutBack() {
        onHomeRead = { promote() }
        assertFalse(contain())
        assertKeepsAccess()
        assertTrue("never hidden: ${h.port.ops}", "hide test.target" !in h.port.ops)
        assertOnlyOwnRemovalsRestored()
    }

    @Test fun promotedRightAfterTheHideIsUnhiddenAndPutBack() {
        onHide = { promote() }
        assertFalse(contain())
        assertKeepsAccess()
        assertOnlyOwnRemovalsRestored()
    }

    @Test fun onlyThePromotedNameIsRestored() {
        onLockTaskWrite = { promote() }
        assertFalse(contain(setOf(target, other)))
        assertKeepsAccess(target)
        assertContained(other)
        assertOnlyOwnRemovalsRestored()
    }

    @Test fun aPromotedNameThisCallNeverListedIsNotAdded() {
        // other is hidden, out of lock task and off Home already; promoting it must not list it anywhere.
        h.port.setHidden(other, true)
        h.port.lockTask = h.port.lockTask - other
        h.home.remove(other)
        onLockTaskWrite = { promoted = setOf(target, other) }
        assertFalse(contain(setOf(target, other)))
        assertKeepsAccess(target)
        assertFalse("other not added to lock task", other in h.port.lockTask)
        assertFalse("other not added to Home", h.home.lists(other))
        assertTrue("other not unhidden: it was hidden before this call", h.port.hidden(other.value))
    }

    @Test fun anUnreadableFloorMidCallStopsFurtherRemovalsWithoutRepair() {
        onLockTaskWrite = { floorReadable = false }
        assertFalse(contain())
        assertFalse("the lock-task removal before it stands", target in h.port.lockTask)
        assertTrue("no Home removal after the floor became unreadable", h.home.lists(target))
        assertTrue("no hide after the floor became unreadable: ${h.port.ops}", "hide test.target" !in h.port.ops)
    }

    @Test fun aFailedApplyThatMeetsAPromotionReportsItAndKeepsAccess() {
        lateinit var plan: org.plainphone.policy.PolicyPlan
        h.beforeApply = { plan = it }
        // Approve with the harness effects (unchanged floor), then replay the plan through these effects
        // with Home publishing failing so containment runs.
        assertEquals(org.plainphone.policy.AppApprovalOutcome.APPROVED, h.approve())
        h.port.setHidden(target, true)
        h.port.setLockTaskPackages(h.port.lockTask - target)
        val failing = object : HomeAppList by home {
            override fun publish(names: Set<PackageName>) { h.home.publish(names); error("publish failed after write") }
        }
        val effects = DeviceApprovalEffects(h.dispatcher, port, h.grants, failing, live) { null }
        onLockTaskWrite = { if (target !in h.port.lockTask) promote() }
        assertFalse(h.dispatcher.run { effects.applyPolicy(plan) })
        assertKeepsAccess()
        assertEquals(mapOf(target to Exposure.entries.toSet()), h.dispatcher.run { effects.unresolvedCleanup })
    }

    @Test fun windowOpenPromotedAfterItsLockTaskRemovalFailsAndPutsMembershipBack() {
        h.port.setHidden(target, true)
        onLockTaskWrite = { promote() }
        assertFalse(h.dispatcher.run { effects.openPermissionWindow(h.identity) })
        assertTrue("in lock task: ${state()}", target in h.port.lockTask)
        assertTrue("on Home: ${state()}", h.home.lists(target))
        assertFalse("no permission write in a failed window", h.dispatcher.run {
            effects.setPermission(h.identity, org.plainphone.policy.PermissionWrite(h.camera, org.plainphone.policy.PermissionChoice.ALLOW))
        })
    }

    @Test fun closePromotedRightAfterItsHideUnhidesAndReturnsFalse() {
        onHide = { promote() }
        assertFalse(h.dispatcher.run { effects.closePermissionWindow(h.identity) })
        assertFalse("visible again: ${state()}", h.port.hidden(target.value))
    }
}
