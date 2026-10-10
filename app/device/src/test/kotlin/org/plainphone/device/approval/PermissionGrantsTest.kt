// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.core.CertDigest
import org.plainphone.core.PackageName
import org.plainphone.policy.ApprovalBinding
import org.plainphone.policy.PermissionChoice
import org.plainphone.policy.PermissionName
import org.plainphone.policy.PermissionReadback
import org.plainphone.policy.PermissionWrite

class PermissionGrantsTest {
    private val app = PackageName("test.app")
    private val signer = CertDigest("ab".repeat(32))
    private val identity = ApprovalBinding(app, setOf(signer))
    private val camera = PermissionName("android.permission.CAMERA")
    private val mic = PermissionName("android.permission.RECORD_AUDIO")

    private inner class Fake : PermissionGrantPort {
        var signers: Set<CertDigest>? = setOf(signer)
        val policy = mutableMapOf<PermissionName, PolicyGrant>()
        val granted = mutableMapOf<PermissionName, Boolean>()
        var setWorks = true
        var grantFollows = true
        var policyReadFails = false
        var afterSet: () -> Unit = {}
        var blocked = false
        val calls = mutableListOf<String>()
        override fun signers(name: PackageName) = signers.takeIf { name == app }
        override fun uninstallBlocked(name: PackageName) = blocked
        override fun setUninstallBlocked(name: PackageName, blocked: Boolean): Boolean { this.blocked = blocked; return true }
        override fun policyState(name: PackageName, permission: PermissionName) =
            if (policyReadFails) null else policy[permission] ?: PolicyGrant.DEFAULT
        override fun setPolicyState(name: PackageName, permission: PermissionName, state: PolicyGrant): Boolean {
            calls += "set $permission $state"
            if (!setWorks) return false
            policy[permission] = state
            if (grantFollows && state != PolicyGrant.DEFAULT) granted[permission] = state == PolicyGrant.GRANTED
            afterSet()
            return true
        }
        override fun granted(name: PackageName, permission: PermissionName) = granted[permission] ?: false
    }

    @Test fun allowAndDenyAreWrittenAsFixedPolicyAndReadBack() {
        val fake = Fake(); val grants = PermissionGrants(fake)
        assertTrue(grants.set(identity, PermissionWrite(camera, PermissionChoice.ALLOW)))
        assertTrue(grants.set(identity, PermissionWrite(mic, PermissionChoice.DENY)))
        assertEquals(mapOf(camera to PermissionReadback.GRANTED, mic to PermissionReadback.DENIED), grants.read(identity, listOf(camera, mic)))
    }

    @Test fun defaultIsNeverReportedAsDenied() {
        val grants = PermissionGrants(Fake())
        assertEquals(mapOf(camera to PermissionReadback.DEFAULT), grants.read(identity, listOf(camera)))
    }

    @Test fun policyAndActualGrantMustAgree() {
        val fake = Fake().apply { grantFollows = false }
        val grants = PermissionGrants(fake)
        assertFalse(grants.set(identity, PermissionWrite(camera, PermissionChoice.ALLOW)))
        assertEquals(PermissionReadback.UNKNOWN, grants.read(identity, listOf(camera))[camera])
        fake.policyReadFails = true
        assertEquals(PermissionReadback.UNKNOWN, grants.read(identity, listOf(mic))[mic])
    }

    @Test fun anotherSignerOrMissingPackageWritesNothingAndReadsUnknown() {
        val fake = Fake(); val grants = PermissionGrants(fake)
        val other = ApprovalBinding(app, setOf(CertDigest("cd".repeat(32))))
        assertFalse(grants.set(other, PermissionWrite(camera, PermissionChoice.ALLOW)))
        assertEquals(mapOf(camera to PermissionReadback.UNKNOWN), grants.read(other, listOf(camera)))
        fake.signers = null
        assertFalse(grants.set(identity, PermissionWrite(camera, PermissionChoice.ALLOW)))
        assertEquals(mapOf(camera to PermissionReadback.UNKNOWN), grants.read(identity, listOf(camera)))
        assertTrue(fake.calls.isEmpty())
    }

    @Test fun signerChangeDuringTheWriteFailsTheWrite() {
        val fake = Fake(); val grants = PermissionGrants(fake)
        fake.afterSet = { fake.signers = setOf(CertDigest("cd".repeat(32))); fake.afterSet = {} }
        assertFalse(grants.set(identity, PermissionWrite(camera, PermissionChoice.ALLOW)))
    }

    @Test fun refusedWriteReportsFalse() {
        val fake = Fake().apply { setWorks = false }
        assertFalse(PermissionGrants(fake).set(identity, PermissionWrite(camera, PermissionChoice.DENY)))
    }
}
