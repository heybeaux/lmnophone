// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.core.*
import org.plainphone.policy.*

class AppPermissionReaderTest {
    private val name = PackageName("org.example.fixture")
    private val permission = PermissionName("android.permission.CAMERA")
    private val cert = CertDigest("a".repeat(64))
    private val snapshot = AppPermissionSnapshot(ApprovalBinding(name, setOf(cert)), 1,
        listOf(RequestedPermissionGroup("camera", "Camera", listOf(permission))))
    private class Port(val cert: CertDigest) : PermissionGrantPort {
        var policy: PolicyGrant? = PolicyGrant.DEFAULT
        var actual: Boolean? = false
        var current = setOf(cert)
        var reads = 0
        var refused = false
        var blocked = false
        var writes = 0
        override fun signers(name: PackageName) = current
        override fun policyState(name: PackageName, permission: PermissionName) = policy.also { reads++ }
        override fun granted(name: PackageName, permission: PermissionName) = actual
        override fun setPolicyState(name: PackageName, permission: PermissionName, state: PolicyGrant): Boolean {
            writes++
            if (refused) return false
            policy = state; actual = state == PolicyGrant.GRANTED; return true
        }
        override fun uninstallBlocked(name: PackageName): Boolean = blocked
        override fun setUninstallBlocked(name: PackageName, blocked: Boolean): Boolean { this.blocked = blocked; return true }
    }
    @Test fun onlyAnActualAndroidAllowRefusalIsRecordedAndReplacementNeverWrites() {
        val port = Port(cert); val attempts = PermissionGrantAttempts(port)
        val allow = PermissionWrite(permission, PermissionChoice.ALLOW)
        assertEquals(PermissionAttempt(GrantWrite.DONE), attempts.write(snapshot.identity, allow))
        port.policy = PolicyGrant.DENIED; port.actual = false; port.refused = true
        assertEquals(PermissionAttempt(GrantWrite.NOT_DONE, true), attempts.write(snapshot.identity, allow))
        port.current = setOf(CertDigest("b".repeat(64)))
        val writes = port.writes
        assertEquals(PermissionAttempt(GrantWrite.NOT_DONE), attempts.write(snapshot.identity, allow))
        assertEquals(writes, port.writes)
    }
    @Test fun readsThroughExactIdentityPermissionGrants() {
        val port = Port(cert)
        val reader = AppPermissionReader({ snapshot }, PermissionGrants(port))
        assertEquals(PermissionReadback.DEFAULT, reader.read(name.value)!!.states[permission])
        port.policy = PolicyGrant.GRANTED; port.actual = true
        assertEquals(PermissionReadback.GRANTED, reader.read(name.value)!!.states[permission])
        port.policy = PolicyGrant.DENIED; port.actual = false
        assertEquals(PermissionReadback.DENIED, reader.read(name.value)!!.states[permission])
        port.actual = null
        assertEquals(PermissionReadback.UNKNOWN, reader.read(name.value)!!.states[permission])
        port.current = setOf(CertDigest("b".repeat(64)))
        assertEquals(PermissionReadback.UNKNOWN, reader.read(name.value)!!.states[permission])
        assertEquals(0, port.writes); assertFalse(port.blocked)
    }
    @Test fun missingOrChangedManifestNeverMeansNoPermissions() {
        assertNull(AppPermissionReader({ null }, PermissionGrants(Port(cert))).read(name.value))
        var reads = 0
        val reader = AppPermissionReader({ if (++reads == 1) snapshot else snapshot.copy(versionCode = 2) }, PermissionGrants(Port(cert)))
        assertNull(reader.read(name.value))
    }
}
