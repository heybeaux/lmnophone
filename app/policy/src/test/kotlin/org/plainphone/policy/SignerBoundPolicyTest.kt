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
}
