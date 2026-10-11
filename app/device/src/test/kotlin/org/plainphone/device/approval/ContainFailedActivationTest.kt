// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.core.PackageName

/**
 * [DeviceApprovalEffects.containFailedActivation], the device side of the `:policy` port (PLA-234,
 * PLA-237). After an unsuccessful final activation, each target is taken out of lock task and Home and
 * hidden, and all three are read back. True only when every target is verified on all three; the live
 * never-hide list (which holds the emergency UI) is checked first, and a protected name is never touched.
 *
 * Setup: [target] (the candidate) and [sibling] (an already-approved app) are both active: visible, in
 * applied lock task and on Home, next to the floor (Plainphone, never-hide Messages, emergency dialer).
 */
class ContainFailedActivationTest {
    private val h = ApprovalHarness()
    private val target = h.target
    private val sibling = h.sibling
    private val floor = setOf(h.plainphone, h.messages, h.dialer)
    private val listed = mutableSetOf<PackageName>()
    private var removalSticks = true
    private var homeThrows = false
    /** The live never-hide list the adapter reads; it can change after the plan was made. */
    private var liveNeverHide: () -> Set<PackageName>? = h.neverHide

    private val home = object : HomeAppList {
        override fun publish(names: Set<PackageName>) { listed.clear(); listed.addAll(names) }
        override fun remove(name: PackageName) {
            if (homeThrows) error("Home write failed")
            if (removalSticks) listed.remove(name)
        }
        override fun lists(name: PackageName): Boolean = name in listed
    }
    private val effects = DeviceApprovalEffects(h.dispatcher, h.port, h.grants, home, { liveNeverHide() }) { null }

    init {
        for (name in listOf(target, sibling)) h.port.setHidden(name, false)
        h.port.lockTask = floor + target + sibling
        listed += floor + target + sibling
        h.port.ops.clear()
    }

    @After fun stop() = h.close()

    private fun contain(names: Set<PackageName>) = h.dispatcher.run { effects.containFailedActivation(names) }
    private fun hidden(name: PackageName) = h.port.hidden(name.value)
    private fun state(name: PackageName) = "hidden=${hidden(name)} permitted=${name in h.port.lockTask} home=${name in listed}"

    private fun assertContained(name: PackageName) {
        assertTrue("hidden: ${state(name)}", hidden(name))
        assertFalse("out of lock task: ${state(name)}", name in h.port.lockTask)
        assertFalse("off Home: ${state(name)}", name in listed)
    }

    private fun assertActive(name: PackageName) {
        assertFalse("still visible: ${state(name)}", hidden(name))
        assertTrue("still in lock task: ${state(name)}", name in h.port.lockTask)
        assertTrue("still on Home: ${state(name)}", name in listed)
    }

    private fun assertFloorUntouched() = floor.forEach(::assertActive)

    @Test fun allTargetsVerifiedReturnsTrue() {
        assertTrue(contain(setOf(target, sibling)))
        assertContained(target)
        assertContained(sibling)
        assertFloorUntouched()
    }

    @Test fun lockTaskRemovalThatDoesNotStickReturnsFalse() {
        h.port.lockTaskSticks = false
        assertFalse(contain(setOf(target)))
        assertTrue("the failure is real", target in h.port.lockTask)
        assertTrue("hide still attempted and verified", hidden(target))
        assertFalse("Home removal still attempted", target in listed)
        assertFloorUntouched()
    }

    @Test fun homeRemovalThatDoesNotStickReturnsFalse() {
        removalSticks = false
        assertFalse(contain(setOf(target)))
        assertTrue("the failure is real", target in listed)
        assertTrue(hidden(target))
        assertFalse(target in h.port.lockTask)
        assertFloorUntouched()
    }

    @Test fun hideThatDoesNotStickReturnsFalse() {
        h.port.hidesStick = false
        assertFalse(contain(setOf(target)))
        assertFalse("the failure is real", hidden(target))
        assertFalse(target in h.port.lockTask)
        assertFalse(target in listed)
        assertFloorUntouched()
    }

    @Test fun lockTaskRemovalAndHideBothFailingReturnsFalse() {
        h.port.lockTaskSticks = false
        h.port.hidesStick = false
        assertFalse(contain(setOf(target)))
        assertTrue("the failure is real: ${state(target)}", !hidden(target) && target in h.port.lockTask)
        assertFalse("Home removal still attempted", target in listed)
        assertFloorUntouched()
    }

    @Test fun aThrowingLockTaskWriteReturnsFalseAndTheOtherStepsStillRun() {
        h.port.throwOnWrite = "lockTask"
        assertFalse(contain(setOf(target)))
        assertTrue("the failure is real", target in h.port.lockTask)
        assertTrue(hidden(target))
        assertFalse(target in listed)
    }

    @Test fun aThrowingHomeWriteReturnsFalse() {
        homeThrows = true
        assertFalse(contain(setOf(target)))
        assertTrue("the failure is real", target in listed)
        assertTrue(hidden(target))
        assertFalse(target in h.port.lockTask)
    }

    @Test fun aThrowingHideReturnsFalse() {
        h.port.throwOnWrite = "packages"
        assertFalse(contain(setOf(target)))
        assertFalse("the failure is real", hidden(target))
        assertFalse(target in h.port.lockTask)
        assertFalse(target in listed)
    }

    @Test fun aNeverHidePackageInTheSetIsNeverTouched() {
        assertFalse("a protected target is not contained", contain(setOf(target, h.messages)))
        assertContained(target)
        assertFloorUntouched()
        assertTrue("no hide of Messages: ${h.port.ops}", "hide test.messages" !in h.port.ops)
    }

    @Test fun anEmergencyPackageInTheSetIsNeverTouched() {
        assertFalse(contain(setOf(target, h.dialer)))
        assertContained(target)
        assertFloorUntouched()
        assertTrue("no hide of the dialer: ${h.port.ops}", "hide test.dialer" !in h.port.ops)
    }

    @Test fun aPackageProtectedSinceThePlanIsNeverTouched() {
        // The plan treated the sibling as discretionary; the live list now protects it (say, a new default dialer).
        val before = h.neverHide()!!
        liveNeverHide = { before + sibling }
        assertFalse(contain(setOf(target, sibling)))
        assertContained(target)
        assertActive(sibling)
        assertFloorUntouched()
    }

    @Test fun anUnreadableNeverHideListTouchesNothing() {
        liveNeverHide = { null }
        assertFalse(contain(setOf(target)))
        assertActive(target)
        assertFloorUntouched()
        assertEquals(emptyList<String>(), h.port.ops)
        liveNeverHide = { error("unreadable") }
        assertFalse(contain(setOf(target)))
        assertActive(target)
        assertEquals(emptyList<String>(), h.port.ops)
    }

    /*
     * PLA-278: before its retry and startup gates, policy calls containment with the durable pending target
     * alone, with no activation plan, and possibly with no policy facts to filter protected names out. The
     * live floor guard is then the only guard. This adapter has never been given a plan.
     */
    @Test fun aProtectedSingletonWithNoPlanIsRefusedAndNeverTouched() {
        assertFalse("never-hide Messages", contain(setOf(h.messages)))
        assertFalse("emergency dialer", contain(setOf(h.dialer)))
        assertFloorUntouched()
        assertActive(target)
        assertEquals(emptyList<String>(), h.port.ops)
    }

    @Test fun aSingletonTargetProtectedOnlyByTheLiveListIsRefusedAndNeverTouched() {
        val before = h.neverHide()!!
        liveNeverHide = { before + target }
        assertFalse(contain(setOf(target)))
        assertActive(target)
        assertActive(sibling)
        assertFloorUntouched()
        assertEquals(emptyList<String>(), h.port.ops)
    }

    @Test fun accessOutsideTheSetIsPreserved() {
        assertTrue(contain(setOf(target)))
        assertContained(target)
        assertActive(sibling)
        assertFloorUntouched()
    }

    @Test fun aSecondCallIsIdempotent() {
        assertTrue(contain(setOf(target, sibling)))
        val ops = h.port.ops.toList()
        assertTrue(contain(setOf(target, sibling)))
        assertEquals("no new device writes", ops, h.port.ops)
        assertContained(target)
        assertContained(sibling)
        assertFloorUntouched()
    }

    @Test fun anEmptySetIsTrueAndTouchesNothing() {
        assertTrue(contain(emptySet()))
        assertEquals(emptyList<String>(), h.port.ops)
        assertActive(target)
        assertFloorUntouched()
    }

    @Test fun offTheDispatcherItRefusesAndTouchesNothing() {
        assertFalse(effects.containFailedActivation(setOf(target)))
        assertEquals(emptyList<String>(), h.port.ops)
        assertActive(target)
    }
}
