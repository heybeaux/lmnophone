// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.BuildInventory
import org.plainphone.core.NeverHideList
import org.plainphone.core.PackageName
import kotlin.test.*

class AbsentMinimumUiTest {
    private val home = PackageName("test.home")
    private val sms = PackageName("test.sms")
    private val dialer = PackageName("test.dialer")
    private val minimum = PackageName("test.minimum")
    private val dependency = PackageName("test.dependency")
    private val target = PackageName("test.target")
    private val inventory = BuildInventory("model", "build", 1,
        NeverHideList(setOf(sms, minimum, dependency)), setOf(sms, minimum), emptySet())

    private fun input() = PolicyInput(
        model = "model", fingerprint = "build", plainphone = home, messages = sms,
        installed = listOf(home, sms, dialer).map { InstalledPackage(it, true, 1) },
        identities = listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)),
        inventory = inventory, previousLockTask = setOf(home, sms, minimum),
    )

    private fun variants(): List<PolicyInput> = PolicyHealth.entries.flatMap { health ->
        listOf(false, true).flatMap { locked ->
            listOf(input(), input().copy(fingerprint = "unknown"), input().copy(inventory = null))
                .map { it.copy(health = health, lockedBoot = locked) }
        }
    }

    @Test fun `confirmed absent minimum UI is removed from every applicable plan and readback`() {
        for (facts in variants()) {
            val plan = PolicyEngine.decide(facts.copy(confirmedAbsentPackages = setOf(minimum)))
            assertFalse(minimum in plan.lockTaskPackages, facts.toString())
            assertFalse(minimum in plan.expected.lockTaskPackages.orEmpty())
            assertTrue(home in plan.lockTaskPackages)
            assertTrue(dialer in plan.lockTaskPackages)
        }
    }

    @Test fun `missing or unreadable package facts alone do not prove absence`() {
        for (facts in variants()) {
            val plan = PolicyEngine.decide(facts)
            // Unknown corrupt baseline deliberately does not inherit minimum UI.
            val inherited = facts.lockedBoot || facts.health != PolicyHealth.BOTH_INVALID ||
                facts.inventory?.fingerprint == facts.fingerprint
            assertEquals(inherited, minimum in plan.lockTaskPackages)
        }
    }

    @Test fun `installed evidence wins contradictory absence including disabled floor members`() {
        for (facts in variants()) {
            // InstalledPackage includes hidden/disabled members: enabled state is not presence.
            val present = facts.copy(installed = facts.installed + InstalledPackage(minimum, true, 1),
                confirmedAbsentPackages = setOf(minimum, dialer, home), previousHidden = setOf(dialer))
            val expected = PolicyEngine.decide(present.copy(confirmedAbsentPackages = emptySet()))
            assertEquals(expected, PolicyEngine.decide(present))
            assertTrue(dialer in expected.unhide)
            assertTrue(dialer in expected.lockTaskPackages)
        }
    }

    @Test fun `absent inventory UI keeps every never hide guard and repair obligation`() {
        for (health in PolicyHealth.entries) for (locked in listOf(false, true)) {
            val requests = DestructiveKind.entries.map { DestructiveRequest(minimum, it) }
            val plan = PolicyEngine.decide(input().copy(health = health, lockedBoot = locked,
                confirmedAbsentPackages = setOf(minimum), requestedDestruction = requests,
                previousHidden = setOf(minimum), classifiedBrowsers = setOf(minimum)))
            assertTrue(minimum in plan.neverHide)
            assertTrue(minimum in plan.unhide)
            assertTrue(minimum in plan.expected.unhiddenAndEnabled)
            assertTrue(minimum in plan.expected.unsuspended)
            assertFalse(minimum in plan.hidden)
            assertEquals(requests, plan.refusedDestruction)
            assertTrue(plan.allowedDestruction.isEmpty())
            assertFalse(minimum in plan.lockTaskPackages)
            assertFalse(dependency in plan.lockTaskPackages)
        }
    }

    @Test fun `fresh supported pass restores installed minimum UI without changing old plan`() {
        for (health in PolicyHealth.entries) {
            val missing = input().copy(health = health, confirmedAbsentPackages = setOf(minimum))
            val before = PolicyEngine.decide(missing)
            val after = PolicyEngine.decide(missing.copy(
                installed = missing.installed + InstalledPackage(minimum, true, 1),
                confirmedAbsentPackages = emptySet(), previousLockTask = before.lockTaskPackages))
            assertFalse(minimum in before.lockTaskPackages)
            assertTrue(minimum in after.lockTaskPackages)
            assertTrue(minimum in after.expected.lockTaskPackages.orEmpty())
            assertTrue(minimum in after.unhide)
        }
    }

    @Test fun `unknown build cannot restore dropped minimum UI from stale inventory`() {
        val missing = input().copy(fingerprint = "unknown", confirmedAbsentPackages = setOf(minimum))
        val before = PolicyEngine.decide(missing)
        val after = PolicyEngine.decide(missing.copy(
            installed = missing.installed + InstalledPackage(minimum, true, 1),
            confirmedAbsentPackages = emptySet(), previousLockTask = before.lockTaskPackages))
        assertFalse(minimum in before.lockTaskPackages)
        assertFalse(minimum in after.lockTaskPackages)
    }

    @Test fun `plan does not retain mutable absence inputs`() {
        val absent = mutableSetOf(minimum)
        val plan = PolicyEngine.decide(input().copy(confirmedAbsentPackages = absent))
        absent.clear()
        assertFalse(minimum in plan.lockTaskPackages)
        assertEquals(plan.lockTaskPackages, plan.expected.lockTaskPackages)
    }

    @Test fun `install between construction and apply does not acquire window UI access`() = installAt(0)
    @Test fun `install between apply and readback does not acquire window UI access`() = installAt(1)
    @Test fun `install between readback and open does not acquire window UI access`() = installAt(2)
    @Test fun `mid window install does not acquire window UI access`() = installAt(3)

    private fun installAt(boundary: Int) {
        val facts = input().copy(confirmedAbsentPackages = setOf(minimum))
        val plan = PolicyEngine.decide(facts)
        val device = WindowModel(facts.previousLockTask, facts.installed.map { it.name }.toSet())
        assertFalse(device.open()) // No verified new apply.
        if (boundary == 0) device.install(minimum)
        device.apply(plan)
        assertFalse(device.open()) // Apply alone is not an acknowledged readback.
        if (boundary == 1) device.install(minimum)
        assertTrue(device.readBack(plan))
        if (boundary == 2) device.install(minimum)
        assertTrue(device.open())
        if (boundary == 3) device.install(minimum)
        assertTrue(device.windowOpen)
        assertFalse(device.hasUiAccess(minimum))
        assertFalse(minimum in plan.expected.lockTaskPackages.orEmpty())
        device.close()
        assertTrue(device.readBack(plan))
        assertFalse(device.hasUiAccess(minimum))
        val fresh = PolicyEngine.decide(facts.copy(
            installed = facts.installed + InstalledPackage(minimum, true, 1),
            confirmedAbsentPackages = emptySet(), previousLockTask = plan.lockTaskPackages))
        device.apply(fresh)
        assertTrue(device.readBack(fresh))
        assertTrue(device.hasUiAccess(minimum))
    }

    @Test fun `remaining unreadable UID still refuses window after reduced list readback`() {
        val facts = input().copy(confirmedAbsentPackages = setOf(minimum))
        val plan = PolicyEngine.decide(facts)
        val device = WindowModel(facts.previousLockTask, facts.installed.map { it.name }.toSet())
        device.apply(plan)
        assertTrue(device.readBack(plan))
        device.unreadable += sms
        assertFalse(device.open())
    }

    @Test fun `failed reduced list readback cannot open a window`() {
        val facts = input().copy(confirmedAbsentPackages = setOf(minimum))
        val plan = PolicyEngine.decide(facts)
        val device = WindowModel(facts.previousLockTask, facts.installed.map { it.name }.toSet())
        device.apply(plan)
        device.applied += minimum // Failed/stale device write.
        assertFalse(device.readBack(plan))
        assertFalse(device.open())
    }

    /** JVM contract model only; real adapter/Android race verification belongs to lane D.
     * Packages use distinct synthetic UIDs. A shared UID is not modeled as isolated UI authority.
     */
    private inner class WindowModel(initial: Set<PackageName>, installed: Set<PackageName>) {
        var applied = initial.toSet()
        private val uids = installed.mapIndexed { index, name -> name to index }.toMap().toMutableMap()
        val unreadable = mutableSetOf<PackageName>()
        private var verified: Set<PackageName>? = null
        var windowOpen = false
            private set
        fun install(name: PackageName) { uids[name] = uids.size }
        fun apply(plan: PolicyPlan) {
            check(!windowOpen)
            applied = plan.lockTaskPackages.toSet()
            verified = null
        }
        fun readBack(plan: PolicyPlan): Boolean {
            val matches = applied == plan.expected.lockTaskPackages
            verified = if (matches) applied.toSet() else null
            return matches
        }
        fun open(): Boolean {
            if (verified != applied || target in applied ||
                applied.any { it in unreadable || uids[it] == null }) return false
            windowOpen = true
            return true
        }
        fun close() { windowOpen = false }
        fun hasUiAccess(name: PackageName) = name in applied && name in uids
    }
}
