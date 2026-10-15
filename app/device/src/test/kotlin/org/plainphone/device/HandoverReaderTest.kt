// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.core.PackageName
import org.plainphone.device.authority.DeviceOwnerReader
import org.plainphone.policy.*

class HandoverReaderTest {
    private val home = PackageName("org.plainphone")
    private val phone = PackageName("test.phone")
    private val hidden = PackageName("test.hidden")
    private val plan = PolicyPlan(
        hidden = setOf(hidden), unhide = setOf(home, phone), neverHide = setOf(home, phone),
        emergencyUi = setOf(phone), lockTaskPackages = setOf(home, phone), features = setOf(LockTaskFeature.KEYGUARD),
        restrictions = emptySet(), steps = listOf(ApplyStep(ApplyStage.HIDES), ApplyStep(ApplyStage.LOCK_TASK)),
        expected = ExpectedPolicyState(setOf(hidden), setOf(home, phone), setOf(home, phone),
            setOf(home, phone), setOf(LockTaskFeature.KEYGUARD), null, null),
        sweepMode = SweepMode.USER_APPS_ONLY, refusedDestruction = emptyList(), allowedDestruction = emptyList(),
        classifierConflicts = emptySet(), changedPackages = emptySet(), needsAttention = false, notifyBuildReview = false)
    private val port = FakeDevicePolicyPort(mapOf(home.value to visible(), phone.value to visible(), hidden.value to hiddenApp())).apply {
        lockTask = plan.lockTaskPackages; features = plan.features
    }
    private fun read(owner: () -> Boolean = { true }, decide: () -> PolicyPlan? = { plan },
        route: (PolicyPlan) -> Boolean? = { true }, debugging: (DebuggingKind) -> Int? = { 0 },
        device: DevicePolicyPort = port) = HandoverReader(DeviceOwnerReader(owner), decide, device, route, debugging).read()
    @Test fun allReadsPassWithoutAnyWrites() {
        assertEquals(0, read().needsHelper); assertTrue(port.ops.isEmpty())
    }
    @Test fun usbOnIsFailureAndWirelessMissingIsUnknown() {
        val report = read(debugging = { if (it == DebuggingKind.USB) 1 else null })
        assertEquals(HandoverState.NEEDS_HELPER, report.items[HandoverItem.USB_DEBUGGING])
        assertEquals(HandoverState.COULD_NOT_CHECK, report.items[HandoverItem.WIRELESS_DEBUGGING])
        assertEquals(2, report.needsHelper); assertTrue(port.ops.isEmpty())
    }
    @Test fun lockTaskExtraMissingAndHiddenMismatchFail() {
        port.lockTask += hidden
        assertEquals(HandoverState.NEEDS_HELPER, read().items[HandoverItem.APPROVED_APPS])
        port.lockTask = setOf(home)
        assertEquals(HandoverState.NEEDS_HELPER, read().items[HandoverItem.APPROVED_APPS])
        port.lockTask = plan.lockTaskPackages; port.state[hidden] = visible()
        assertEquals(HandoverState.NEEDS_HELPER, read().items[HandoverItem.APPROVED_APPS])
        assertTrue(port.ops.isEmpty())
    }
    @Test fun absentHiddenPackageDoesNotSilentlyPassReadback() {
        port.state.remove(hidden)
        assertEquals(HandoverState.COULD_NOT_CHECK, read().items[HandoverItem.APPROVED_APPS])
    }
    @Test fun everyUnreadableSourceFailsClosedIndependently() {
        val report = read(owner = { error("owner") }, decide = { error("plan") }, debugging = { error("setting") })
        assertEquals(5, report.needsHelper)
        assertTrue(report.items.values.all { it == HandoverState.COULD_NOT_CHECK })
        assertEquals(HandoverState.COULD_NOT_CHECK, read(route = { error("resolver") }).items[HandoverItem.EMERGENCY])
        val throwing = object : DevicePolicyPort by port {
            override fun lockTaskFeatures(): Set<LockTaskFeature> = error("features")
        }
        assertEquals(HandoverState.COULD_NOT_CHECK, read(device = throwing).items[HandoverItem.APPROVED_APPS])
        assertTrue(port.ops.isEmpty())
    }
    @Test fun hiddenDisabledSuspendedOrMissingFloorNeverPasses() {
        for (state in listOf(hiddenApp(), visible().copy(enabled = false), visible().copy(suspended = true))) {
            port.state[phone] = state
            assertEquals(HandoverState.NEEDS_HELPER, read().items[HandoverItem.EMERGENCY])
        }
        port.state.remove(phone)
        assertEquals(HandoverState.COULD_NOT_CHECK, read().items[HandoverItem.EMERGENCY])
        assertTrue(port.ops.isEmpty())
    }
    @Test fun resolverMismatchAndMissingPlanCannotPass() {
        assertEquals(HandoverState.NEEDS_HELPER, read(route = { false }).items[HandoverItem.EMERGENCY])
        val report = read(decide = { null })
        assertEquals(HandoverState.COULD_NOT_CHECK, report.items[HandoverItem.APPROVED_APPS])
        assertEquals(HandoverState.COULD_NOT_CHECK, report.items[HandoverItem.EMERGENCY])
        assertEquals(HandoverState.OK, report.items[HandoverItem.OWNER])
    }
}
