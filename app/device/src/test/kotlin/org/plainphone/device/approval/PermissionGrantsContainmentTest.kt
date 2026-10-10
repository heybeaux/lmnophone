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

/**
 * The write-boundary contract of [PermissionGrants.write] (PLA-178 finding 1) and its failure
 * mapping (findings 4 and 5). Several cases come from the Verifier's PLA-178 reproducer.
 *
 * The fake models one installed app per package name. An uninstall and reinstall by another signer
 * starts with fresh permission state and is refused while uninstall is blocked, as Android does
 * (proved on API 35 by `HiddenPermissionCheck -e phase uninstall-block`). An update keeps the
 * permission state; a key-rotation update changes the signer set.
 */
class PermissionGrantsContainmentTest {
    private val app = PackageName("test.app")
    private val a = CertDigest("ab".repeat(32))
    private val b = CertDigest("cd".repeat(32))
    private val rotated = CertDigest("ef".repeat(32))
    private val identity = ApprovalBinding(app, setOf(a))
    private val camera = PermissionName("android.permission.CAMERA")
    private val allowCamera = PermissionWrite(camera, PermissionChoice.ALLOW)

    private class Installed(var signers: Set<CertDigest>) {
        var fixed = PolicyGrant.DEFAULT
        var actual = false
    }

    private open inner class Fake : PermissionGrantPort {
        var installed: Installed? = Installed(setOf(a))
        var blocked: Boolean = false
        /** False models a block that Android does not enforce. */
        var blockHolds = true
        var blockReadFails = false
        var blockWriteWorks = true
        var setWorks = true
        var compensationWorks = true
        var beforeWrite: () -> Unit = {}
        var afterWrite: () -> Unit = {}
        var afterRead: () -> Unit = {}
        val log = mutableListOf<String>()
        val writtenTo = mutableListOf<Set<CertDigest>?>()

        /** Uninstall, then install [signer]'s build; false when Android refuses the uninstall. */
        fun replaceWith(signer: CertDigest): Boolean {
            if (blocked && blockHolds) return false
            installed = Installed(setOf(signer))
            return true
        }
        /** A key-rotation update: same app data and permission state, new signer set. */
        fun rotateTo(signer: CertDigest) { installed!!.signers = setOf(signer) }

        override fun signers(name: PackageName): Set<CertDigest>? { log += "signers"; return installed?.signers }
        override fun policyState(name: PackageName, permission: PermissionName) = installed?.fixed
        override fun granted(name: PackageName, permission: PermissionName): Boolean? {
            val result = installed?.actual; afterRead(); return result
        }
        override fun setPolicyState(name: PackageName, permission: PermissionName, state: PolicyGrant): Boolean {
            log += "set $state"
            beforeWrite(); beforeWrite = {}
            val target = installed ?: return false
            if (!setWorks || (state == PolicyGrant.DENIED && writtenTo.isNotEmpty() && !compensationWorks)) return false
            writtenTo += target.signers
            target.fixed = state
            if (state != PolicyGrant.DEFAULT) target.actual = state == PolicyGrant.GRANTED
            afterWrite(); afterWrite = {}
            return true
        }
        override fun uninstallBlocked(name: PackageName): Boolean? = if (blockReadFails) null else blocked
        override fun setUninstallBlocked(name: PackageName, blocked: Boolean): Boolean {
            log += "block $blocked"
            if (!blockWriteWorks) return false
            this.blocked = blocked
            return true
        }
    }

    @Test fun uninstallIsBlockedBeforeTheIdentityCheckAndRestoredAfter() {
        val fake = Fake()
        assertEquals(GrantWrite.DONE, PermissionGrants(fake).write(identity, allowCamera))
        assertEquals("block true", fake.log.first())
        assertTrue(fake.log.indexOf("block true") < fake.log.indexOf("signers"))
        assertEquals("block false", fake.log.last())
        assertFalse(fake.blocked)
    }

    @Test fun anExistingBlockIsNeverCleared() {
        val fake = Fake().apply { blocked = true }
        assertTrue(PermissionGrants(fake).set(identity, allowCamera))
        assertTrue(fake.blocked)
        assertTrue(fake.log.none { it.startsWith("block") })
    }

    @Test fun theBlockClosesTheReplacementWindow() {
        val fake = Fake()
        var replaced = true
        fake.beforeWrite = { replaced = fake.replaceWith(b) }
        assertEquals(GrantWrite.DONE, PermissionGrants(fake).write(identity, allowCamera))
        assertFalse("Android refuses the uninstall while blocked", replaced)
        assertEquals(listOf(setOf(a)), fake.writtenTo)
    }

    @Test fun replacementBetweenCheckAndWriteIsContainedWhenTheBlockDoesNotHold() {
        // The Verifier's replacementBetweenCheckAndWriteReceivesGrantDespiteFalseResult, now contained.
        val fake = Fake().apply { blockHolds = false }
        fake.beforeWrite = { fake.replaceWith(b) }
        assertEquals(GrantWrite.REPLACED_CONTAINED, PermissionGrants(fake).write(identity, allowCamera))
        assertEquals(PolicyGrant.DENIED, fake.installed!!.fixed)
        assertEquals(false, fake.installed!!.actual)
        assertEquals(setOf(b), fake.installed!!.signers)
        assertFalse(fake.blocked)
    }

    @Test fun containmentIsDeniedEvenForADenyWrite() {
        val fake = Fake().apply { blockHolds = false }
        fake.beforeWrite = { fake.replaceWith(b) }
        assertFalse(PermissionGrants(fake).set(identity, PermissionWrite(camera, PermissionChoice.DENY)))
        assertEquals(PolicyGrant.DENIED, fake.installed!!.fixed)
        assertEquals(false, fake.installed!!.actual)
    }

    @Test fun failedContainmentIsReportedAsNotContained() {
        val fake = Fake().apply { blockHolds = false; compensationWorks = false }
        fake.beforeWrite = { fake.replaceWith(b) }
        assertEquals(GrantWrite.REPLACED_NOT_CONTAINED, PermissionGrants(fake).write(identity, allowCamera))
        assertFalse(PermissionGrants(fake).set(identity, allowCamera))
    }

    @Test fun replacementDuringTheVerifyReadIsContained() {
        val fake = Fake().apply { blockHolds = false }
        fake.afterWrite = { fake.afterRead = { fake.replaceWith(b); fake.afterRead = {} } }
        assertEquals(GrantWrite.REPLACED_CONTAINED, PermissionGrants(fake).write(identity, allowCamera))
        assertEquals(PolicyGrant.DENIED, fake.installed!!.fixed)
    }

    @Test fun keyRotationDuringTheWriteIsAnotherIdentityAndIsContained() {
        // A v3 rotation is a normal update, so the uninstall block does not stop it, and the
        // permission state carries over. The binding pins exact signers: the rotated app is not
        // the approved one, its grant is fixed DENIED, and it needs a fresh approval.
        val fake = Fake()
        fake.afterWrite = { fake.rotateTo(rotated) }
        assertEquals(GrantWrite.REPLACED_CONTAINED, PermissionGrants(fake).write(identity, allowCamera))
        assertEquals(setOf(rotated), fake.installed!!.signers)
        assertEquals(PolicyGrant.DENIED, fake.installed!!.fixed)
        assertEquals(false, fake.installed!!.actual)
    }

    @Test fun anUnreadableOrRefusedBlockWritesNothing() {
        val unreadable = Fake().apply { blockReadFails = true }
        assertFalse(PermissionGrants(unreadable).set(identity, allowCamera))
        assertTrue(unreadable.writtenTo.isEmpty())
        val refused = Fake().apply { blockWriteWorks = false }
        assertFalse(PermissionGrants(refused).set(identity, allowCamera))
        assertTrue(refused.writtenTo.isEmpty())
        assertFalse(refused.blocked)
    }

    @Test fun aBlockThatDoesNotReadBackWritesNothingAndIsUndone() {
        val fake = object : Fake() {
            override fun uninstallBlocked(name: PackageName): Boolean? = if (log.contains("block true")) false else blocked
        }
        assertFalse(PermissionGrants(fake).set(identity, allowCamera))
        assertTrue(fake.writtenTo.isEmpty())
        assertEquals("block false", fake.log.last())
    }

    @Test fun theBlockIsRestoredWhenTheWriteThrows() {
        val fake = Fake()
        fake.beforeWrite = { throw SecurityException("injected") }
        assertFalse(PermissionGrants(fake).set(identity, allowCamera))
        assertFalse(fake.blocked)
    }

    @Test fun aThrowingPortReadsUnknownAndWritesFalse() {
        // Finding 4: a SecurityException from any read is an unavailable value, never an escape.
        val calls = listOf("signers", "policyState", "granted", "uninstallBlocked", "setUninstallBlocked", "setPolicyState")
        for (failing in calls) {
            val fake = object : Fake() {
                override fun signers(name: PackageName) = if (failing == "signers") throw SecurityException() else super.signers(name)
                override fun policyState(name: PackageName, permission: PermissionName) =
                    if (failing == "policyState") throw SecurityException() else super.policyState(name, permission)
                override fun granted(name: PackageName, permission: PermissionName) =
                    if (failing == "granted") throw SecurityException() else super.granted(name, permission)
                override fun uninstallBlocked(name: PackageName) =
                    if (failing == "uninstallBlocked") throw SecurityException() else super.uninstallBlocked(name)
                override fun setUninstallBlocked(name: PackageName, blocked: Boolean) =
                    if (failing == "setUninstallBlocked") throw SecurityException() else super.setUninstallBlocked(name, blocked)
                override fun setPolicyState(name: PackageName, permission: PermissionName, state: PolicyGrant) =
                    if (failing == "setPolicyState") throw SecurityException() else super.setPolicyState(name, permission, state)
            }
            val grants = PermissionGrants(fake)
            assertFalse(failing, grants.set(identity, allowCamera))
            if (failing !in listOf("uninstallBlocked", "setUninstallBlocked", "setPolicyState")) {
                assertEquals(failing, PermissionReadback.UNKNOWN, grants.read(identity, listOf(camera))[camera])
            }
            assertFalse(failing, fake.blocked)
        }
    }

    @Test fun defaultWithAFailedActualReadIsUnknown() {
        // Finding 5, the Verifier's defaultWithFailedActualReadMustBeUnknown.
        val fake = object : Fake() {
            override fun granted(name: PackageName, permission: PermissionName): Boolean? = null
        }
        assertEquals(PermissionReadback.UNKNOWN, PermissionGrants(fake).read(identity, listOf(camera))[camera])
    }

    @Test fun replacementDuringAReadIsUnknown() {
        val fake = Fake().apply { installed!!.fixed = PolicyGrant.GRANTED; installed!!.actual = true }
        fake.afterRead = { fake.rotateTo(b) }
        assertEquals(PermissionReadback.UNKNOWN, PermissionGrants(fake).read(identity, listOf(camera))[camera])
    }

    @Test fun everyFixedAndActualCombination() {
        for (fixed in listOf(null, PolicyGrant.DEFAULT, PolicyGrant.GRANTED, PolicyGrant.DENIED)) {
            for (actual in listOf(null, false, true)) {
                val fake = object : Fake() {
                    override fun policyState(name: PackageName, permission: PermissionName) = fixed
                    override fun granted(name: PackageName, permission: PermissionName) = actual
                }
                val expected = when {
                    fixed == null || actual == null -> PermissionReadback.UNKNOWN
                    fixed == PolicyGrant.DEFAULT -> PermissionReadback.DEFAULT
                    fixed == PolicyGrant.GRANTED && actual -> PermissionReadback.GRANTED
                    fixed == PolicyGrant.DENIED && !actual -> PermissionReadback.DENIED
                    else -> PermissionReadback.UNKNOWN
                }
                assertEquals("fixed=$fixed actual=$actual", expected, PermissionGrants(fake).read(identity, listOf(camera))[camera])
            }
        }
    }

    @Test fun signerSetsMustMatchExactly() {
        for (s in listOf(setOf(a, b), emptySet(), setOf(b), null)) {
            val fake = Fake().apply { installed = s?.let { Installed(it) } }
            assertFalse(PermissionGrants(fake).set(identity, allowCamera))
            assertEquals(PermissionReadback.UNKNOWN, PermissionGrants(fake).read(identity, listOf(camera))[camera])
            assertTrue(fake.writtenTo.isEmpty())
            assertFalse(fake.blocked)
        }
        val fake = Fake()
        assertFalse(PermissionGrants(fake).set(ApprovalBinding(app, setOf(a, b)), allowCamera))
        assertTrue(fake.writtenTo.isEmpty())
    }
}
