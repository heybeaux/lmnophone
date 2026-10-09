// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class SignerBoundPolicyTest {
    private val app = PackageName("test.app")
    private val home = PackageName("test.home")
    private val sms = PackageName("test.sms")
    private val signer = CertDigest("ab".repeat(32))
    private val binding = ApprovalBinding(app, setOf(signer))
    private val record = AppApprovalRecord("approval", PermissionSelection(PermissionManifest(binding, 1, "build", emptyList()), emptyMap()))
    private fun input() = PolicyInput("model", "build", home, sms, listOf(InstalledPackage(app, false, 1)), emptyList(),
        BuildInventory("model", "build", 1, NeverHideList(setOf(sms)), setOf(sms), emptySet()),
        approvalState = AppApprovalState(apps = mapOf(app to record)), installedIdentities = listOf(binding))

    @Test fun `package only legacy approvals never open an installed app`() {
        val plan = PolicyEngine.decide(input().copy(approvalState = null, approved = setOf(app)))
        assertTrue(app in plan.hidden); assertFalse(app in plan.lockTaskPackages)
    }
    @Test fun `current exact signer set allows approved app and same signer updates remain approved`() {
        for (version in listOf(1L, 2L)) {
            val plan = PolicyEngine.decide(input().copy(installed = listOf(InstalledPackage(app, false, version))))
            assertTrue(app in plan.unhide && app in plan.lockTaskPackages); assertFalse(app in plan.hidden)
        }
    }
    @Test fun `missing changed partial or ambiguous signing facts reject inherited approval`() {
        for (ids in listOf(emptyList(), listOf(ApprovalBinding(app, setOf(CertDigest("cd".repeat(32))))),
            listOf(ApprovalBinding(app, setOf(signer, CertDigest("cd".repeat(32))))), listOf(binding, binding))) {
            val plan = PolicyEngine.decide(input().copy(installedIdentities = ids))
            assertTrue(app in plan.hidden); assertFalse(app in plan.lockTaskPackages)
        }
    }
    @Test fun `old lock task entries cannot restore a mismatched approved signer at locked or unknown boot`() {
        for (locked in listOf(true, false)) {
            val plan = PolicyEngine.decide(input().copy(inventory = null, lockedBoot = locked,
                installedIdentities = emptyList(), previousLockTask = setOf(app)))
            assertFalse(app in plan.lockTaskPackages)
        }
    }
    @Test fun `candidate data and preparing journal do not confer permanent approval`() {
        val plan = PolicyEngine.decide(input().copy(approvalState = AppApprovalState()))
        assertTrue(app in plan.hidden)
    }
    @Test fun `committed activation journal is excluded from all ordinary policy passes until completed`() {
        val person = TrustedPersonId("holder")
        val receipt = AppApprovalReceipt(record.id, person, mapOf(person to 0L))
        val pending = AppApprovalState(apps = mapOf(app to record), pending = AppApprovalOperation(record, receipt, AppApprovalPhase.COMMITTED), receipts = listOf(receipt))
        for (locked in listOf(true, false)) for (known in listOf(true, false)) {
            val plan = PolicyEngine.decide(input().copy(approvalState = pending, lockedBoot = locked,
                inventory = if (known) input().inventory else null, previousLockTask = setOf(app)))
            assertFalse(app in plan.unhide)
            assertFalse(app in plan.lockTaskPackages)
        }
    }

    @Test fun `persisted committed journal must match both approval payload and receipt`() {
        val person = TrustedPersonId("holder")
        val receipt = AppApprovalReceipt(record.id, person, mapOf(person to 0L))
        val changed = AppApprovalRecord(record.id, PermissionSelection(PermissionManifest(
            ApprovalBinding(app, setOf(CertDigest("cd".repeat(32)))), 1, "build", emptyList()), emptyMap()))
        assertFailsWith<IllegalArgumentException> {
            AppApprovalState(apps = mapOf(app to changed), pending = AppApprovalOperation(record, receipt, AppApprovalPhase.COMMITTED), receipts = listOf(receipt))
        }
        assertFailsWith<IllegalArgumentException> {
            AppApprovalState(apps = mapOf(app to record), pending = AppApprovalOperation(record, receipt, AppApprovalPhase.COMMITTED))
        }
        assertFailsWith<IllegalArgumentException> {
            AppApprovalState(apps = mapOf(app to record), pending = AppApprovalOperation(record, receipt, AppApprovalPhase.COMMITTED),
                receipts = listOf(AppApprovalReceipt(record.id, person, mapOf(person to 9L))))
        }
    }

    @Test fun `signer pruning never removes the required Messages baseline on an unknown build`() {
        val plan = PolicyEngine.decide(input().copy(inventory = null, lockedBoot = true, approvalState = null,
            installed = listOf(InstalledPackage(sms, false, 1)), previousLockTask = setOf(sms)))
        assertTrue(sms in plan.lockTaskPackages)
        assertTrue(sms in plan.unhide)
    }

    @Test fun `inherited ordinary system membership cannot replace approval and signer facts`() {
        val person = TrustedPersonId("holder")
        val preparing = AppApprovalState(pending = AppApprovalOperation(record,
            AppApprovalReceipt(record.id, person, mapOf(person to 0L)), AppApprovalPhase.PREPARING))
        val cases = listOf(
            "missing store" to input().copy(approvalState = null, installedIdentities = emptyList()),
            "empty store" to input().copy(approvalState = AppApprovalState()),
            "preparing" to input().copy(approvalState = preparing),
            "missing signers" to input().copy(installedIdentities = emptyList()),
            "changed signer" to input().copy(installedIdentities = listOf(
                ApprovalBinding(app, setOf(CertDigest("cd".repeat(32)))))),
        )
        for ((name, base) in cases) for (system in listOf(true, false))
            for (locked in listOf(true, false)) for (known in listOf(true, false)) {
                val context = "$name system=$system locked=$locked known=$known"
                val plan = PolicyEngine.decide(base.copy(
                    installed = listOf(InstalledPackage(app, system, 1)),
                    inventory = if (known) base.inventory else null,
                    lockedBoot = locked, previousLockTask = setOf(app)))
                assertFalse(app in plan.lockTaskPackages, context)
                assertFalse(app in plan.unhide, context)
                if (system && (!known || locked)) assertFalse(app in plan.hidden, context)
            }
    }

    @Test fun `validated system approval retains inherited UI while required UI needs no app approval`() {
        val dialer = PackageName("test.dialer")
        val minimum = PackageName("test.minimum")
        val keyboard = PackageName("test.keyboard")
        val required = setOf(home, sms, dialer, minimum)
        val installed = (required + keyboard + app).map { InstalledPackage(it, true, 1) }
        val inventory = BuildInventory("model", "build", 1,
            NeverHideList(required + keyboard), setOf(sms, minimum), emptySet())
        for (known in listOf(true, false)) for (locked in listOf(true, false)) {
            val plan = PolicyEngine.decide(input().copy(installed = installed,
                identities = listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED),
                    FloorIdentity(keyboard, FloorRole.KEYBOARD, IdentitySource.OS_MANAGED)),
                inventory = inventory, fingerprint = if (known) "build" else "ota-build", lockedBoot = locked,
                previousLockTask = required + keyboard + app))
            assertTrue(app in plan.lockTaskPackages, "known=$known locked=$locked")
            assertTrue(setOf(home, sms, dialer).all { it in plan.lockTaskPackages })
            assertTrue(minimum in plan.lockTaskPackages)
            assertTrue(keyboard in plan.unhide)
            assertFalse(keyboard in plan.lockTaskPackages)
            assertFalse(required.any { it in plan.hidden })
        }
    }

    @Test fun `stored minimum UI on unknown build never adds absent previous membership or old never hide`() {
        val minimum = PackageName("test.minimum")
        val oldDependency = PackageName("test.old.dependency")
        val staleInventory = BuildInventory("model", "old-build", 1,
            NeverHideList(setOf(minimum, oldDependency)), setOf(minimum), emptySet())
        for (locked in listOf(true, false)) for (health in PolicyHealth.entries) {
            val plan = PolicyEngine.decide(input().copy(fingerprint = "ota-build", inventory = staleInventory,
                installed = listOf(InstalledPackage(minimum, true, 1), InstalledPackage(oldDependency, true, 1)),
                lockedBoot = locked, health = health, approvalState = null, installedIdentities = emptyList(),
                previousHidden = setOf(oldDependency), previousLockTask = setOf(home, sms)))
            assertFalse(minimum in plan.lockTaskPackages)
            assertFalse(minimum in plan.hidden)
            assertFalse(minimum in plan.unhide)
            assertFalse(oldDependency in plan.neverHide)
            assertTrue(oldDependency in plan.hidden)
            assertFalse(oldDependency in plan.unhide)
            assertEquals(if (locked || health == PolicyHealth.BOTH_INVALID) SweepMode.NONE else SweepMode.USER_APPS_ONLY,
                plan.sweepMode)
        }
    }

}
