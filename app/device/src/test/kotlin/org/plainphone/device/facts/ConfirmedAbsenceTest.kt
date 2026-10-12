// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.facts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.core.BuildInventory
import org.plainphone.core.NeverHideList
import org.plainphone.core.PackageName
import org.plainphone.policy.PolicyEngine
import org.plainphone.policy.PolicyHealth
import org.plainphone.policy.PolicyStore
import org.plainphone.policy.StoredPolicy

/**
 * PLA-293 required 1: the assembler supplies `PolicyInput.confirmedAbsentPackages` from a positive,
 * per-name "not installed for this user" answer, fresh on every pass. A missing enumeration entry,
 * a throwing query, a hidden, disabled or suspended package, Plainphone and floor names are never absent.
 */
class ConfirmedAbsenceTest {
    private val port = FakePackageSystemPort()
    private val plainphone = PackageName("org.plainphone")
    private val messages = PackageName("com.google.android.apps.messaging")
    private val minimum = "org.example.minimum"
    private val store = object : PolicyStore {
        override fun read() = StoredPolicy(1, emptySet(), PolicyHealth.VALID)
        override fun write(policy: StoredPolicy) = false
    }
    private var inventories = listOf(inventory(minimum))

    private fun inventory(vararg minimumUi: String) = names(*minimumUi, messages.value).let {
        BuildInventory(port.model, port.fingerprint, 1, NeverHideList(it), it, emptySet())
    }
    private fun names(vararg n: String) = n.map(::PackageName).toSet()
    private fun assembler() = LivePolicyInputAssembler(port, store, inventories, plainphone, messages)
    private fun absent(previousLockTask: Set<PackageName> = emptySet()) =
        assembler().assemble(previousLockTask).input.confirmedAbsentPackages

    private val base = listOf(
        raw("com.google.android.dialer", true, 10, "google"),
        raw(messages.value, true, 20, "google"),
        raw(plainphone.value, false, 1, "pp"),
    )

    private fun phone(vararg extra: RawPackage) {
        port.packages(*(base + extra).toTypedArray())
        port.systemDialer = "com.google.android.dialer"
        port.inCallUi = setOf("com.google.android.dialer")
    }

    @Test fun minimumUiNotInstalledForTheUserIsConfirmedAbsent() {
        phone()
        port.userInstalled = { it != minimum }
        assertEquals(names(minimum), absent())
    }

    @Test fun absenceIsAskedPerNameAfterBothEnumerationReads() {
        phone()
        port.userInstalled = { it != minimum }
        absent()
        val lastList = port.calls.lastIndexOf("packages")
        val firstAsk = port.calls.indexOf("installedForUser")
        assertTrue("asked after the second list read: ${port.calls}", firstAsk > lastList)
        assertEquals(1, port.calls.count { it == "installedForUser" })
    }

    @Test fun aMissingEnumerationEntryAloneIsNotAbsence() {
        // Not listed, but the per-name query says installed (for example a hidden app a list missed).
        phone()
        port.userInstalled = { true }
        assertEquals(emptySet<PackageName>(), absent())
    }

    @Test fun aThrowingQueryIsNotAbsence() {
        phone()
        port.userInstalled = { false }
        port.failing += "installedForUser"
        assertEquals(emptySet<PackageName>(), absent())
    }

    @Test fun oneThrowingNameDoesNotHideAnotherConfirmedAbsence() {
        inventories = listOf(inventory(minimum, "org.example.other"))
        phone()
        port.userInstalled = { if (it == "org.example.other") throw SecurityException("unreadable") else it != minimum }
        assertEquals(names(minimum), absent())
    }

    @Test fun hiddenAndSuspendedPackagesAreListedSoNeverAbsent() {
        // RawPackage carries no hidden or suspended flag: the port lists them like any installed package.
        phone(raw(minimum, true, 1, "oem"))
        port.userInstalled = { false } // even a contradicting query cannot make a listed package absent
        assertEquals(emptySet<PackageName>(), absent())
    }

    @Test fun disabledPackageLeftOutOfInstalledIsStillNeverAbsent() {
        phone(RawPackage(minimum, true, 1, listOf("oem".toByteArray()), enabled = false))
        port.userInstalled = { false }
        val input = assembler().assemble().input
        assertFalse(input.installed.any { it.name.value == minimum })
        assertEquals(emptySet<PackageName>(), input.confirmedAbsentPackages)
        assertTrue(PackageName(minimum) in PolicyEngine.decide(input).lockTaskPackages)
    }

    @Test fun listedInOnlyOneOfTheTwoReadsIsNotAbsent() {
        port.reads = mutableListOf(base + raw(minimum, true, 1, "oem"), base)
        port.systemDialer = "com.google.android.dialer"
        port.userInstalled = { false }
        assertEquals(emptySet<PackageName>(), absent())
    }

    @Test fun onlyMinimumUiAndPreviousLockTaskNamesAreAsked() {
        phone()
        port.userInstalled = { false }
        val previous = names("org.example.stale")
        val input = assembler().assemble(previous).input
        assertEquals(names(minimum, "org.example.stale"), input.confirmedAbsentPackages)
        assertEquals(previous, input.previousLockTask)
        assertEquals(2, port.calls.count { it == "installedForUser" })
    }

    @Test fun plainphoneAndFloorNamesAreNeverAbsent() {
        phone()
        port.userInstalled = { false }
        val dialerName = PackageName("com.google.android.dialer")
        val previous = setOf(plainphone, dialerName)
        // Neither listed: Plainphone is never absent, and an OS floor source naming the dialer wins.
        port.reads = mutableListOf(base.filter { it.name != plainphone.value && it.name != dialerName.value })
        assertEquals(names(minimum), absent(previous))
    }

    @Test fun absenceIsFreshEachPassAndNeverReused() {
        phone()
        port.userInstalled = { it != minimum }
        assertEquals(names(minimum), absent())
        phone(raw(minimum, true, 1, "oem"))
        port.userInstalled = { true }
        assertEquals(emptySet<PackageName>(), absent())
        // Gone again, but this pass's query is unreadable: the earlier answer is not reused.
        phone()
        port.failing += "installedForUser"
        assertEquals(emptySet<PackageName>(), absent())
    }

    @Test fun noInventoryAndNoPreviousLockTaskAsksNothing() {
        inventories = emptyList()
        phone()
        port.userInstalled = { false }
        assertEquals(emptySet<PackageName>(), absent())
        assertFalse("installedForUser" in port.calls)
    }

    @Test fun policyDropsTheConfirmedAbsentMinimumUiFromTheLockTaskList() {
        phone()
        port.userInstalled = { it != minimum }
        val plan = PolicyEngine.decide(assembler().assemble().input)
        assertFalse(PackageName(minimum) in plan.lockTaskPackages)
        assertEquals(plan.lockTaskPackages, plan.expected.lockTaskPackages)
        // Never-hide stays: the minimum UI is still protected and reported, not dropped from the floor.
        assertTrue(PackageName(minimum) in plan.neverHide)
    }

    @Test fun unknownBuildDropsAConfirmedAbsentPreviousLockTaskName() {
        inventories = emptyList()
        phone()
        port.userInstalled = { it != minimum }
        val plan = PolicyEngine.decide(assembler().assemble(names(minimum, messages.value)).input)
        assertFalse(PackageName(minimum) in plan.lockTaskPackages)
        assertTrue(messages in plan.lockTaskPackages)
    }
}
