// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.core.*
import org.plainphone.policy.*

class PermissionRollbackTest {
    private val camera = PermissionName("android.permission.CAMERA")
    private val mic = PermissionName("android.permission.RECORD_AUDIO")
    private val identity = ApprovalBinding(PackageName("org.example.fixture"), setOf(CertDigest("a".repeat(64))))
    private val selected = AppPermissionSnapshot(identity, 1, listOf(
        RequestedPermissionGroup("camera", "Camera", listOf(camera)), RequestedPermissionGroup("mic", "Microphone", listOf(mic))))
    private class Port : PermissionGrantPort {
        var signers = setOf(CertDigest("a".repeat(64)))
        val policies = mutableMapOf<PermissionName, PolicyGrant>()
        val grants = mutableMapOf<PermissionName, Boolean>()
        var blocked = false
        var refuse = false
        var writes = 0
        override fun signers(name: PackageName) = signers
        override fun policyState(name: PackageName, permission: PermissionName) = policies[permission]
        override fun granted(name: PackageName, permission: PermissionName) = grants[permission]
        override fun uninstallBlocked(name: PackageName) = blocked
        override fun setUninstallBlocked(name: PackageName, blocked: Boolean): Boolean { this.blocked = blocked; return true }
        override fun setPolicyState(name: PackageName, permission: PermissionName, state: PolicyGrant): Boolean {
            writes++
            if (refuse) return false
            policies[permission] = state
            if (state != PolicyGrant.DEFAULT) grants[permission] = state == PolicyGrant.GRANTED
            return true
        }
    }
    @Test fun restoresActualGrantBeforeClearingDefaultAndPreservesExistingFixedStates() {
        for (original in PolicyGrant.entries) for (runtime in listOf(false, true)) {
            if (original != PolicyGrant.DEFAULT && runtime != (original == PolicyGrant.GRANTED)) continue
            val port = Port().apply {
                policies[camera] = original; grants[camera] = runtime
                policies[mic] = PolicyGrant.DENIED; grants[mic] = false
            }
            val transaction = PermissionGrantAttempts(port)
            assertTrue(transaction.checkpoint(selected))
            assertEquals(GrantWrite.DONE, transaction.write(identity, PermissionWrite(camera,
                if (runtime) PermissionChoice.DENY else PermissionChoice.ALLOW)).result)
            assertTrue(transaction.rollback(selected))
            assertEquals(original, port.policies[camera]); assertEquals(runtime, port.grants[camera])
            assertEquals(PolicyGrant.DENIED, port.policies[mic]); assertFalse(port.grants[mic]!!)
            assertFalse(port.blocked)
        }
    }
    @Test fun unavailableSnapshotWritesNothingAndReplacementNeverReceivesRestoredGrants() {
        val port = Port()
        val transaction = PermissionGrantAttempts(port)
        assertFalse(transaction.checkpoint(selected)); assertEquals(0, port.writes)
        selected.permissions.forEach { port.policies[it] = PolicyGrant.DEFAULT; port.grants[it] = true }
        assertTrue(transaction.checkpoint(selected))
        port.signers = setOf(CertDigest("b".repeat(64)))
        assertFalse(transaction.rollback(selected)); assertEquals(0, port.writes); assertFalse(port.blocked)
    }
    @Test fun restoreRefusalIsReportedAndAnExistingUninstallBlockIsKept() {
        val port = Port().apply { blocked = true }
        selected.permissions.forEach { port.policies[it] = PolicyGrant.DEFAULT; port.grants[it] = false }
        val transaction = PermissionGrantAttempts(port)
        assertTrue(transaction.checkpoint(selected))
        transaction.write(identity, PermissionWrite(camera, PermissionChoice.ALLOW))
        port.refuse = true
        assertFalse(transaction.rollback(selected)); assertTrue(port.blocked)
    }
}
