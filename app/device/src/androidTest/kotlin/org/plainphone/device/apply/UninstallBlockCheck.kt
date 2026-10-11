// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.apply

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import org.plainphone.core.CertDigest
import org.plainphone.core.PackageName
import org.plainphone.device.approval.DpmPermissionGrantPort
import org.plainphone.device.approval.GrantWrite
import org.plainphone.device.approval.PermissionGrantPort
import org.plainphone.device.approval.PermissionGrants
import org.plainphone.device.approval.PolicyGrant
import org.plainphone.policy.ApprovalBinding
import org.plainphone.policy.PermissionChoice
import org.plainphone.policy.PermissionName
import org.plainphone.policy.PermissionWrite

/**
 * On-device check of the [PermissionGrants.write] boundary (PLA-182), with this test APK as a
 * testOnly Device Owner, `check-fixtures/permission-target`'s `target.apk` installed, and its three
 * APKs pushed to `/data/local/tmp/pla182/`. Shell commands run as the `shell` user, as `adb shell` does.
 *
 * `-e phase uninstall-block -e expect <A> -e other <C> -e rotated <B>` (signer digests from build.sh):
 * 1. With uninstall blocked, `pm uninstall` (plain, `--user 0`, `-k --user 0`) is refused and
 *    another signer's install is refused, while hidden and while visible. Control: while hidden,
 *    the `--user 0` forms fail with "not installed for 0" even without a block.
 * 2. A write that adds the block itself, with a replacement attempted through `pm` inside the write:
 *    the replacement is refused, the write is DONE for A, and the block is put back. An existing
 *    block is kept.
 * 3. Control: without the block, the same `pm` replacement succeeds. With a port whose block is not
 *    enforced, a replacement inside the write gets the grant, and the write contains it (DENIED,
 *    read back) and reports REPLACED_CONTAINED.
 * 4. Key rotation: a rotated update installs while uninstall is blocked and keeps the grant state;
 *    inside a write it is contained like any other identity.
 * Every step is asserted. Cleanup restores A, visible, unblocked, DEFAULT, and fails the run if it cannot.
 */
class UninstallBlockCheck(
    private val ctx: Context,
    private val args: Bundle?,
    private val log: (String) -> Unit,
    private val shell: (String) -> String,
) {
    private val target = PackageName("org.plainphone.permtarget")
    private val camera = PermissionName("android.permission.CAMERA")
    private val all = listOf(camera, PermissionName("android.permission.RECORD_AUDIO"), PermissionName("android.permission.READ_CONTACTS"))
    private val dir = "/data/local/tmp/pla182"
    private val admin by lazy { ComponentName(ctx, CheckAdmin::class.java) }
    private val dpm by lazy { ctx.getSystemService(DevicePolicyManager::class.java) }
    private val port by lazy { DpmPermissionGrantPort(ctx, admin) }
    private val allowCamera = PermissionWrite(camera, PermissionChoice.ALLOW)

    private fun digest(key: String) = CertDigest.parse(args?.getString(key) ?: fail("missing -e $key <sha256>"))
    private val a by lazy { digest("expect") }
    private val c by lazy { digest("other") }
    private val b by lazy { digest("rotated") }
    private val identity by lazy { ApprovalBinding(target, setOf(a)) }

    fun check() {
        val outcome = runCatching { run() }
        val problems = runCatching { restoreA() }.fold({ emptyList() }, { listOf("restore A threw $it") }) +
            HiddenPermissionCheck.cleanup(ctx, target, all)
        problems.forEach { note("cleanup failed: $it") }
        outcome.getOrThrow()
        if (problems.isNotEmpty()) fail("cleanup failed: ${problems.joinToString()}")
        expect(port.signers(target) == setOf(a), "cleanup left A installed, visible, unblocked, DEFAULT")
    }

    private fun run() {
        expect(dpm.isDeviceOwnerApp(ctx.packageName), "this test APK is the Device Owner")
        expect(port.signers(target) == setOf(a), "A is installed")
        expect(!dpm.isUninstallBlocked(admin, target.value), "A starts without an uninstall block")

        // 1. The block holds against pm, hidden and visible.
        dpm.setUninstallBlocked(admin, target.value, true)
        expect(dpm.isUninstallBlocked(admin, target.value), "setUninstallBlocked(true) reads back")
        for (hidden in listOf(true, false)) {
            dpm.setApplicationHidden(admin, target.value, hidden)
            expect(dpm.isApplicationHidden(admin, target.value) == hidden, "target hidden=$hidden")
            for (cmd in listOf("pm uninstall ${target.value}", "pm uninstall --user 0 ${target.value}", "pm uninstall -k --user 0 ${target.value}")) {
                refused(cmd, "hidden=$hidden")
            }
            refused("pm install -r -t $dir/target-other.apk", "hidden=$hidden, another signer")
        }

        // Control: while hidden, `pm uninstall --user 0` does not see the app at all, block or not.
        // Only the plain `pm uninstall` reaches the block there (DELETE_FAILED_OWNER_BLOCKED above).
        dpm.setUninstallBlocked(admin, target.value, false)
        expect(!dpm.isUninstallBlocked(admin, target.value), "block cleared for the control")
        dpm.setApplicationHidden(admin, target.value, true)
        for (cmd in listOf("pm uninstall --user 0 ${target.value}", "pm uninstall -k --user 0 ${target.value}")) {
            val out = sh(cmd)
            expect(out == "Failure [not installed for 0]" && port.signers(target) == setOf(a), "control, hidden and not blocked: `$cmd` -> $out")
        }
        dpm.setApplicationHidden(admin, target.value, false)
        expect(!dpm.isApplicationHidden(admin, target.value), "target visible again")

        // 2. write() under a live replacement attempt; it adds and puts back the block.
        val attempts = mutableListOf<String>()
        val racing = withReplacement { attempts += sh("pm uninstall ${target.value}"); attempts += sh("pm install -t $dir/target-other.apk") }
        val result = PermissionGrants(racing).write(identity, allowCamera)
        expect(attempts.none { it.startsWith("Success") }, "pm replacement inside the write was refused: $attempts")
        expect(result == GrantWrite.DONE, "write is DONE for A while the replacement was refused (got $result)")
        expect(port.signers(target) == setOf(a) && port.policyState(target, camera) == PolicyGrant.GRANTED && port.granted(target, camera) == true,
            "A holds CAMERA GRANTED")
        expect(!dpm.isUninstallBlocked(admin, target.value), "write put back the block it added (none)")
        dpm.setUninstallBlocked(admin, target.value, true)
        expect(PermissionGrants(port).write(identity, PermissionWrite(camera, PermissionChoice.DENY)) == GrantWrite.DONE, "write with an existing block is DONE")
        expect(dpm.isUninstallBlocked(admin, target.value), "write kept the existing block")
        dpm.setUninstallBlocked(admin, target.value, false)

        // 3. Control: an unenforced block lets the replacement in, and the write contains it.
        var fakeBlock = false
        val replacing = withReplacement { replace("target-other.apk", c) }
        val unenforced = object : PermissionGrantPort by replacing {
            override fun uninstallBlocked(name: PackageName): Boolean = fakeBlock
            override fun setUninstallBlocked(name: PackageName, blocked: Boolean): Boolean { fakeBlock = blocked; return true }
        }
        val contained = PermissionGrants(unenforced).write(identity, allowCamera)
        expect(contained == GrantWrite.REPLACED_CONTAINED, "replacement inside the write is REPLACED_CONTAINED (got $contained)")
        expect(port.signers(target) == setOf(c) && port.policyState(target, camera) == PolicyGrant.DENIED && port.granted(target, camera) == false,
            "the replacement C holds CAMERA fixed DENIED, not granted")
        restoreA()

        // 4. Key rotation: an update, so the block does not stop it, and the grant state carries over.
        expect(PermissionGrants(port).set(identity, allowCamera), "A has CAMERA ALLOW before the rotation")
        dpm.setUninstallBlocked(admin, target.value, true)
        val rotatedOut = sh("pm install -r -t $dir/target-rotated.apk")
        expect(rotatedOut.startsWith("Success"), "rotated update installs while uninstall is blocked ($rotatedOut)")
        expect(port.signers(target) == setOf(b), "after the rotation the signer set is B only")
        expect(port.policyState(target, camera) == PolicyGrant.GRANTED && port.granted(target, camera) == true, "the rotated app kept CAMERA GRANTED")
        expect(!PermissionGrants(port).set(identity, PermissionWrite(camera, PermissionChoice.DENY)), "A's binding writes nothing to the rotated app")
        dpm.setUninstallBlocked(admin, target.value, false)
        restoreA()
        val rotating = withReplacement {
            val out = sh("pm install -r -t $dir/target-rotated.apk")
            if (!out.startsWith("Success")) fail("rotated update inside the write: $out")
        }
        val rotation = PermissionGrants(rotating).write(identity, allowCamera)
        expect(rotation == GrantWrite.REPLACED_CONTAINED, "rotation inside the write is REPLACED_CONTAINED (got $rotation)")
        expect(port.signers(target) == setOf(b) && port.policyState(target, camera) == PolicyGrant.DENIED && port.granted(target, camera) == false,
            "the rotated app holds CAMERA fixed DENIED, not granted")
        expect(!dpm.isUninstallBlocked(admin, target.value), "write put back the block it added (none)")
    }

    /** The real port, running [replace] once just before the first grant write. */
    private fun withReplacement(replace: () -> Unit): PermissionGrantPort = object : PermissionGrantPort by port {
        var done = false
        override fun setPolicyState(name: PackageName, permission: PermissionName, state: PolicyGrant): Boolean {
            if (!done) { done = true; replace() }
            return port.setPolicyState(name, permission, state)
        }
    }

    /** Uninstalls whatever is there and installs [apk]; asserts it succeeded with [signer]. */
    private fun replace(apk: String, signer: CertDigest) {
        val gone = sh("pm uninstall ${target.value}")
        val installed = sh("pm install -t $dir/$apk")
        if (!gone.startsWith("Success") || !installed.startsWith("Success") || port.signers(target) != setOf(signer)) {
            fail("replacement with $apk: uninstall=$gone install=$installed")
        }
        note("ok: without an enforced block, pm replaced the app with $apk")
    }

    private fun restoreA() {
        dpm.setUninstallBlocked(admin, target.value, false)
        if (port.signers(target) != setOf(a)) replace("target.apk", a)
    }

    private fun refused(cmd: String, what: String) {
        val out = sh(cmd)
        expect(!out.startsWith("Success") && port.signers(target) == setOf(a), "$what: `$cmd` refused, A still installed ($out)")
    }

    private fun sh(cmd: String): String = shell(cmd).also { note("shell `$cmd` -> $it") }
    private fun expect(ok: Boolean, what: String) {
        if (!ok) fail(what)
        note("ok: $what")
    }
    private fun fail(what: String): Nothing = throw IllegalStateException(what)
    private fun note(line: String) = log("PLA182 $line")
}
