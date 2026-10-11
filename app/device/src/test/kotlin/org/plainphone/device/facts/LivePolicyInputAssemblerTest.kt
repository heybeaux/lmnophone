// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.facts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.BuildInventory
import org.plainphone.core.NeverHideList
import org.plainphone.core.PackageName
import org.plainphone.device.storage.FilePolicyStore
import org.plainphone.policy.EmergencyFloor
import org.plainphone.policy.FloorRole
import org.plainphone.policy.PolicyHealth
import org.plainphone.policy.PolicyStore
import org.plainphone.policy.StoredPolicy

/**
 * The PolicyInput assembler: live package facts and floor identities, FilePolicyStore health, the live
 * build and its matching inventory. The floor never comes from an inventory or policy file, and
 * contradicting facts confer no system identity.
 */
class LivePolicyInputAssemblerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val port = FakePackageSystemPort()
    private val plainphone = PackageName("org.plainphone")
    private val messages = PackageName("com.google.android.apps.messaging")
    private var store: PolicyStore = FixedStore(PolicyHealth.VALID)
    private var inventories: List<BuildInventory> = emptyList()

    private fun assemble() = LivePolicyInputAssembler(port, store, inventories, plainphone, messages).assemble()
    private fun names(vararg n: String) = n.map(::PackageName).toSet()
    private fun inventory(model: String = port.model, fingerprint: String = port.fingerprint, revision: Int = 1, vararg neverHide: String) =
        BuildInventory(model, fingerprint, revision, NeverHideList(names(*neverHide)), emptySet(), emptySet())

    private class FixedStore(val health: PolicyHealth) : PolicyStore {
        override fun read() = StoredPolicy(1, setOf(PackageName("com.example.fromfile")), health)
        override fun write(policy: StoredPolicy) = false
    }

    private fun phone() {
        port.packages(
            raw("com.google.android.dialer", true, 10, "google"),
            raw("com.android.phone", true, 35, "platform"),
            raw("com.google.android.apps.messaging", true, 20, "google"),
            raw("com.example.notes", false, 3, "keyA"),
            raw("org.plainphone", false, 1, "pp"),
        )
        port.systemDialer = "com.google.android.dialer"
        port.inCallUi = setOf("com.google.android.dialer")
        port.activities["com.android.phone.EmergencyDialer.DIAL"] = setOf("com.android.phone")
        port.defaultSms = "com.google.android.apps.messaging"
    }

    @Test fun assemblesLiveBuildPackagesFloorAndBindings() {
        phone()
        val live = assemble()
        val input = live.input
        assertEquals(port.model, input.model)
        assertEquals(port.fingerprint, input.fingerprint)
        assertEquals(plainphone, input.plainphone)
        assertEquals(messages, input.messages)
        assertEquals(5, input.installed.size)
        assertEquals(5, input.installedIdentities.size)
        assertEquals(5, live.packages.size)
        assertTrue(input.identities.any { it.role == FloorRole.SYSTEM_DIALER })
        val floor = EmergencyFloor.compute(input.installed, input.identities)
        assertTrue(floor.hasSystemDialer)
        assertEquals(names("com.google.android.dialer", "com.android.phone"), floor.emergencyUi)
        assertEquals(names("com.google.android.dialer", "com.android.phone", "com.google.android.apps.messaging"), floor.neverHide)
        assertTrue(live.unreadableFloorRoles.isEmpty())
    }

    @Test fun floorQueriesRunBetweenTheTwoPackageReads() {
        phone()
        assemble()
        val packageCalls = port.calls.withIndex().filter { it.value == "packages" }.map { it.index }
        assertEquals(2, packageCalls.size)
        val floorCalls = port.calls.withIndex().filter { it.value == "systemDialer" || it.value == "systemOnlyActivities" }.map { it.index }
        assertTrue(floorCalls.all { it > packageCalls[0] && it < packageCalls[1] })
    }

    @Test fun healthComesFromThePolicyStore() {
        phone()
        for (health in PolicyHealth.entries) {
            store = FixedStore(health)
            assertEquals(health, assemble().input.health)
        }
    }

    @Test fun healthFromARealFilePolicyStore() {
        phone()
        val fileStore = FilePolicyStore(tmp.newFolder("policy"))
        store = fileStore
        assertEquals(PolicyHealth.BOTH_INVALID, assemble().input.health)
        assertTrue(fileStore.write(StoredPolicy(FilePolicyStore.SCHEMA_VERSION, names("com.example.notes"), PolicyHealth.VALID)))
        assertEquals(PolicyHealth.VALID, assemble().input.health)
    }

    @Test fun theStoredApprovedListIsNotCopiedIntoTheInput() {
        phone()
        assertTrue(assemble().input.approved.isEmpty())
    }

    @Test fun theMatchingInventoryIsChosen() {
        phone()
        val match = inventory(revision = 2)
        inventories = listOf(inventory(fingerprint = "other/build"), inventory(model = "XT9999"), inventory(revision = 1), match)
        assertSame(match, assemble().input.inventory)
    }

    @Test fun noMatchingInventoryMeansUnknownBuild() {
        phone()
        inventories = listOf(inventory(fingerprint = "other/build"), inventory(model = "XT9999"))
        assertNull(assemble().input.inventory)
    }

    @Test fun twoInventoriesForTheSameBuildAndRevisionContradictSoNeitherIsUsed() {
        phone()
        inventories = listOf(inventory(revision = 3), inventory(revision = 3, neverHide = arrayOf("com.example.notes")))
        assertNull(assemble().input.inventory)
    }

    @Test fun theFloorNeverComesFromAnInventoryOrThePolicyFile() {
        phone()
        port.systemDialer = null
        port.inCallUi = emptySet()
        port.activities.clear()
        port.defaultSms = null
        inventories = listOf(inventory(neverHide = arrayOf("com.google.android.dialer", "com.android.phone", "com.example.fromfile")))
        val input = assemble().input
        assertTrue(input.identities.isEmpty())
        val floor = EmergencyFloor.compute(input.installed, input.identities)
        assertTrue(floor.neverHide.isEmpty())
        assertFalse(floor.hasSystemDialer)
    }

    @Test fun theFloorIsTheSameWithOrWithoutAnInventoryOrAHealthyStore() {
        phone()
        val bare = assemble().input.identities
        inventories = listOf(inventory(neverHide = arrayOf("com.example.notes")))
        store = FixedStore(PolicyHealth.BOTH_INVALID)
        assertEquals(bare, assemble().input.identities)
    }

    @Test fun aSystemFlagThatContradictsItselfConfersNoFloorMembership() {
        phone()
        val first = port.reads.single()
        port.reads = mutableListOf(first, first.map { if (it.name == "com.google.android.dialer") raw(it.name, false, 10, "google") else it })
        val input = assemble().input
        val floor = EmergencyFloor.compute(input.installed, input.identities)
        assertFalse(floor.hasSystemDialer)
        assertFalse(PackageName("com.google.android.dialer") in floor.neverHide)
        assertFalse(assemble().packages.single { it.packageName.value == "com.google.android.dialer" }.isSystem)
    }

    @Test fun aNonSystemRoleHolderDoesNotJoinTheFloor() {
        phone()
        port.defaultDialer = "com.example.notes"
        port.defaultSms = "com.example.notes"
        port.activities["android.intent.action.DIAL_EMERGENCY"] = setOf("com.example.notes")
        val input = assemble().input
        val floor = EmergencyFloor.compute(input.installed, input.identities)
        assertFalse(PackageName("com.example.notes") in floor.neverHide)
        assertFalse(PackageName("com.example.notes") in floor.emergencyUi)
    }

    @Test fun aFloorPackageThatIsNotInstalledDoesNotJoin() {
        phone()
        port.systemDialer = "com.android.dialer"
        port.inCallUi = setOf("com.android.dialer")
        val floor = assemble().input.let { EmergencyFloor.compute(it.installed, it.identities) }
        assertFalse(floor.hasSystemDialer)
    }

    @Test fun unreadableFloorRolesAreReported() {
        phone()
        port.failing += "systemDialer"
        assertEquals(setOf(FloorRole.SYSTEM_DIALER, FloorRole.IN_CALL_UI), assemble().unreadableFloorRoles)
    }
}
