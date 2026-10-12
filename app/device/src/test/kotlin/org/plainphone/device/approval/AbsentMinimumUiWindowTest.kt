// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.core.BuildInventory
import org.plainphone.core.CertDigest
import org.plainphone.core.NeverHideList
import org.plainphone.core.PackageName
import org.plainphone.device.FakeDevicePolicyPort
import org.plainphone.device.ReadbackPolicyApplier
import org.plainphone.device.SerialDispatcher
import org.plainphone.device.facts.FakePackageSystemPort
import org.plainphone.device.facts.LivePolicyInputAssembler
import org.plainphone.device.facts.RawPackage
import org.plainphone.device.hiddenApp
import org.plainphone.device.visible
import org.plainphone.policy.ApprovalBinding
import org.plainphone.policy.PolicyEngine
import org.plainphone.policy.PolicyHealth
import org.plainphone.policy.PolicyPlan
import org.plainphone.policy.PolicyStore
import org.plainphone.policy.StoredPolicy

/**
 * PLA-293: a minimum-UI package removed for the user no longer blocks every permission window. The real
 * [LivePolicyInputAssembler] reads one simulated phone (the same state [FakeDevicePolicyPort] holds),
 * [PolicyEngine] plans from it, and the real [DeviceApprovalEffects] applies, reads back and opens the
 * window on one [SerialDispatcher]. The strict window gate is unchanged: every name left in the applied
 * lock-task list must still resolve to a UID that is not the candidate's.
 */
class AbsentMinimumUiWindowTest {
    private val pp = PackageName("org.plainphone")
    private val messages = PackageName("test.messages")
    private val dialer = PackageName("test.dialer")
    private val minimum = PackageName("test.minimum")
    private val companion = PackageName("test.minimum.companion")
    private val target = PackageName("test.target")
    private val identity = ApprovalBinding(target, setOf(CertDigest("ab".repeat(32))))
    private val system = setOf(messages, dialer, minimum, companion)

    private val port = FakeDevicePolicyPort(mapOf(pp.value to visible(), messages.value to visible(), dialer.value to visible(),
        minimum.value to visible(), companion.value to visible(), target.value to hiddenApp()))
    private val packages = FakePackageSystemPort().apply {
        systemDialer = dialer.value
        inCallUi = setOf(dialer.value)
        // The per-name query reads the simulated phone at the moment it is asked.
        userInstalled = { PackageName(it) in port.state }
    }
    private val store = object : PolicyStore {
        override fun read() = StoredPolicy(1, emptySet(), PolicyHealth.VALID)
        override fun write(policy: StoredPolicy) = false
    }
    private val ui = setOf(messages, minimum, companion)
    private val assembler = LivePolicyInputAssembler(packages,  store,
        listOf(BuildInventory(packages.model, packages.fingerprint, 1, NeverHideList(ui), ui, emptySet())), pp, messages)
    private val dispatcher = SerialDispatcher("pla293-policy")
    private val effects = DeviceApprovalEffects(dispatcher, port, HiddenRefusingGrantPort(port), PublishedHomeApps(),
        { runCatching { pass().neverHide }.getOrNull() }) { null }

    @After fun close() = dispatcher.close()

    /** One fresh policy pass: list the simulated phone, assemble, decide. */
    private fun pass(): PolicyPlan {
        packages.packages(*port.state.map { (name, state) ->
            RawPackage(name.value, name in system, 1, listOf(name.value.toByteArray()), state.enabled)
        }.toTypedArray())
        return PolicyEngine.decide(assembler.assemble().input)
    }
    private fun apply(plan: PolicyPlan) = dispatcher.run { effects.applyPolicy(plan) }
    private fun readBack(plan: PolicyPlan) = dispatcher.run { effects.readBackPolicy(plan) }
    private fun open() = dispatcher.run { effects.openPermissionWindow(identity) }
    private fun closeWindow() = dispatcher.run { effects.closePermissionWindow(identity) }
    private fun remove(name: PackageName) { port.state.remove(name) }
    private fun install(name: PackageName) { port.state[name] = visible() }

    /** The phone as a full apply left it, then [minimum] removed for the user: the stale list still names it. */
    private fun staleList() {
        assertTrue(apply(pass()))
        assertTrue(minimum in port.lockTask)
        remove(minimum)
    }

    @Test fun windowOpensAfterTheReducedListReadsBackAndWasRefusedBefore() {
        staleList()
        assertFalse("a stale absent name has no UID: the strict gate refuses", open())
        assertTrue(closeWindow())

        val reduced = pass()
        assertFalse(minimum in reduced.lockTaskPackages)
        assertTrue(apply(reduced))
        assertTrue(readBack(reduced))
        assertEquals(reduced.lockTaskPackages, port.lockTask)
        assertTrue(open())
        assertFalse(target in port.lockTask)
        assertTrue(closeWindow())
        assertTrue(port.hidden(target.value))
    }

    private enum class Boundary { PLAN_BUILT, APPLIED, READ_BACK, IN_WINDOW }

    @Test fun anInstallAtEachBoundaryGivesNoUiAccessUntilCloseAndAFreshPass() {
        for (boundary in Boundary.entries) {
            val t = AbsentMinimumUiWindowTest()
            try { t.installAt(boundary) } finally { t.close() }
        }
    }

    private fun installAt(boundary: Boundary) {
        staleList()
        val reduced = pass()
        fun at(b: Boundary) {
            if (b == boundary) install(minimum)
            assertFalse("$boundary: no lock-task access at $b", minimum in port.lockTask)
        }
        at(Boundary.PLAN_BUILT)
        assertTrue("$boundary", apply(reduced))
        at(Boundary.APPLIED)
        assertTrue("$boundary", readBack(reduced))
        at(Boundary.READ_BACK)
        assertTrue("$boundary", open())
        at(Boundary.IN_WINDOW)
        assertTrue("$boundary", closeWindow())
        assertFalse("$boundary: closing never restores access", minimum in port.lockTask)

        val fresh = pass()
        assertTrue("$boundary: installed again, so back in the plan", minimum in fresh.lockTaskPackages)
        assertTrue(apply(fresh))
        assertTrue("$boundary: the fresh pass restores it", minimum in port.lockTask)
    }

    @Test fun aFailedLockTaskWriteRefusesTheWindow() {
        staleList()
        port.throwOnWrite = "lockTask"
        assertFalse(apply(pass()))
        assertTrue(minimum in port.lockTask)
        assertFalse(open())
    }

    @Test fun aLockTaskListThatDoesNotReadBackRefusesTheWindow() {
        staleList()
        port.lockTaskSticks = false
        val reduced = pass()
        assertFalse(apply(reduced))
        assertFalse(readBack(reduced))
        assertFalse(open())
    }

    @Test fun aRemainingNameWithANullUidStillRefuses() {
        staleList()
        assertTrue(apply(pass()))
        port.uidAbsent += companion
        assertFalse(open())
    }

    @Test fun aRemainingNameWithAnUnreadableUidStillRefuses() {
        staleList()
        assertTrue(apply(pass()))
        port.uidUnreadable += companion
        assertFalse(open())
    }

    @Test fun anUnreadableAbsenceQueryKeepsTheNameSoTheWindowStaysRefused() {
        staleList()
        packages.failing += "installedForUser"
        val plan = pass()
        assertTrue(minimum in plan.lockTaskPackages)
        apply(plan)
        assertTrue(minimum in port.lockTask)
        assertFalse(open())
    }

    @Test fun aRemainingPackageThatSharedTheAbsentOnesUidStillResolvesAndTheWindowOpens() {
        port.uids[minimum] = 10_500
        port.uids[companion] = 10_500
        staleList()
        assertTrue(apply(pass()))
        assertTrue(companion in port.lockTask)
        assertTrue(open())
        assertTrue(closeWindow())
    }

    @Test fun aRemainingPackageSharingTheCandidatesUidStillRefuses() {
        port.uids[target] = 10_600
        port.uids[companion] = 10_600
        staleList()
        assertTrue(apply(pass()))
        assertFalse(open())
    }

    @Test fun anAbsentNeverHidePackageReadsBackAndIsReportedNotRepaired() {
        staleList()
        val reduced = pass()
        assertTrue(minimum in reduced.neverHide)
        port.ops.clear()
        val result = ReadbackPolicyApplier(port).apply(reduced)
        assertTrue(result.complete)
        assertFalse(result.needsAttention)
        assertEquals(setOf(minimum), result.absent)
        assertTrue(port.ops.none { minimum.value in it.split(' ', ',') })
        assertTrue(readBack(reduced))
    }
}
