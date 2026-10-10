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
import org.plainphone.device.approval.PermissionGrants
import org.plainphone.device.approval.PolicyGrant
import org.plainphone.policy.ApprovalBinding
import org.plainphone.policy.PermissionChoice
import org.plainphone.policy.PermissionChoices
import org.plainphone.policy.PermissionGrantCapability
import org.plainphone.policy.PermissionManifest
import org.plainphone.policy.PermissionName
import org.plainphone.policy.PermissionPlanning
import org.plainphone.policy.PermissionReadback
import org.plainphone.policy.PermissionSelection
import org.plainphone.policy.PermissionVerification
import org.plainphone.policy.PermissionWrite
import org.plainphone.policy.RuntimePermission

/**
 * On-device check of [PermissionGrants] over [DpmPermissionGrantPort] (PLA-86), with this test APK
 * as a testOnly Device Owner and `check-fixtures/permission-target` installed.
 *
 * `-e phase permissions -e expect <sha256>`: first a raw matrix (hidden / visible / visible+suspended
 * x sensor and non-sensor x GRANTED/DENIED). Then the adapter: a binding to another signer writes
 * nothing; on API 35 a write while hidden is refused (Android returns false) and is reported as
 * refused; written while visible, ALLOW and DENY read back, pass the policy's own verify, survive
 * re-hiding (and read back while hidden) and unhiding. Everything is reset to DEFAULT and unhidden.
 */
class HiddenPermissionCheck(private val ctx: Context, private val args: Bundle?, private val log: (String) -> Unit) {
    private val target = PackageName("org.plainphone.permtarget")
    private val camera = PermissionName("android.permission.CAMERA")
    private val mic = PermissionName("android.permission.RECORD_AUDIO")
    private val contacts = PermissionName("android.permission.READ_CONTACTS")
    private val admin by lazy { ComponentName(ctx, CheckAdmin::class.java) }
    private val dpm by lazy { ctx.getSystemService(DevicePolicyManager::class.java) }
    private val port by lazy { DpmPermissionGrantPort(ctx, admin) }
    private val grants by lazy { PermissionGrants(port) }

    fun check() {
        try {
            run()
        } finally {
            runCatching {
                listOf(camera, mic, contacts).forEach { port.setPolicyState(target, it, PolicyGrant.DEFAULT) }
                dpm.setApplicationHidden(admin, target.value, false)
            }.onFailure { note("cleanup failed: $it") }
        }
    }

    private fun run() {
        val expect = CertDigest.parse(args?.getString("expect") ?: fail("missing -e expect <sha256>"))
        expect(dpm.isDeviceOwnerApp(ctx.packageName), "this test APK is the Device Owner")
        expect(dpm.setApplicationHidden(admin, target.value, true), "setApplicationHidden(true) returned true")
        expect(dpm.isApplicationHidden(admin, target.value), "target reads back hidden")

        val signers = port.signers(target)
        note("signers while hidden: $signers")
        expect(signers == setOf(expect), "live signer while hidden equals apksigner's digest")
        val identity = ApprovalBinding(target, setOf(expect))
        expect(grants.read(identity, listOf(camera, mic)) == mapOf(camera to PermissionReadback.DEFAULT, mic to PermissionReadback.DEFAULT),
            "fresh install reads DEFAULT for both, never DENIED")

        matrix()
        val other = ApprovalBinding(target, setOf(CertDigest("ab".repeat(32))))
        expect(!grants.set(other, PermissionWrite(camera, PermissionChoice.ALLOW)), "another signer set writes nothing")
        expect(port.policyState(target, camera) == PolicyGrant.DEFAULT, "camera still DEFAULT after the refused write")

        // API 35 platform rule (matrix above): no grant state can be written while the app is hidden.
        expect(!grants.set(identity, PermissionWrite(camera, PermissionChoice.ALLOW)), "write while hidden is refused, not reported as done")
        expect(port.policyState(target, camera) == PolicyGrant.DEFAULT && port.granted(target, camera) == false, "refused write left CAMERA ungranted")

        expect(dpm.setApplicationHidden(admin, target.value, false), "unhide returned true")
        expect(grants.set(identity, PermissionWrite(camera, PermissionChoice.ALLOW)), "CAMERA=ALLOW written and read back while visible")
        expect(grants.set(identity, PermissionWrite(mic, PermissionChoice.DENY)), "RECORD_AUDIO=DENY written and read back while visible")
        expect(grants.set(identity, PermissionWrite(contacts, PermissionChoice.DENY)), "READ_CONTACTS=DENY written and read back while visible")
        val visible = grants.read(identity, listOf(camera, mic, contacts))
        note("readback while visible: $visible")
        val manifest = PermissionManifest(identity, 1, "check", listOf(
            RuntimePermission(camera, "Camera", emptyList(), PermissionGrantCapability.SUPPORTED),
            RuntimePermission(mic, "Microphone", emptyList(), PermissionGrantCapability.SUPPORTED),
            RuntimePermission(contacts, "Contacts", emptyList(), PermissionGrantCapability.SUPPORTED)))
        val plan = (PermissionChoices.plan(PermissionSelection(manifest,
            mapOf(camera to PermissionChoice.ALLOW, mic to PermissionChoice.DENY, contacts to PermissionChoice.DENY)), manifest) as PermissionPlanning.Ready).plan
        expect(PermissionChoices.verify(plan, manifest, visible) == PermissionVerification.Verified, "policy verify passes on the visible readback")

        expect(dpm.setApplicationHidden(admin, target.value, true), "re-hide returned true")
        val rehidden = grants.read(identity, listOf(camera, mic, contacts))
        note("readback after re-hide: $rehidden")
        expect(rehidden == visible, "fixed states survive hiding and read back while hidden")
        expect(dpm.setApplicationHidden(admin, target.value, false), "final unhide returned true")
        expect(grants.read(identity, listOf(camera, mic, contacts)) == visible, "fixed states survive unhide")
    }

    /** Raw Android answers, hidden and visible, for sensor and non-sensor permissions; reset after. */
    private fun matrix() {
        note("canAdminGrantSensorsPermissions=${dpm.canAdminGrantSensorsPermissions()}")
        for ((hide, suspend) in listOf(true to false, false to false, false to true)) {
            dpm.setApplicationHidden(admin, target.value, hide)
            val notSuspended = dpm.setPackagesSuspended(admin, arrayOf(target.value), suspend)
            note("matrix state hidden=${dpm.isApplicationHidden(admin, target.value)} suspended=${dpm.isPackageSuspended(admin, target.value)} (failed=${notSuspended.toList()})")
            for (p in listOf(camera, mic, contacts)) for (state in listOf(PolicyGrant.GRANTED, PolicyGrant.DENIED)) {
                val set = port.setPolicyState(target, p, state)
                note("matrix hidden=$hide suspended=$suspend ${p.value} set=$state returned=$set policy=${port.policyState(target, p)} granted=${port.granted(target, p)}")
                port.setPolicyState(target, p, PolicyGrant.DEFAULT)
            }
        }
        dpm.setPackagesSuspended(admin, arrayOf(target.value), false)
        dpm.setApplicationHidden(admin, target.value, true)
    }

    private fun expect(ok: Boolean, what: String) {
        if (!ok) fail(what)
        note("ok: $what")
    }
    private fun fail(what: String): Nothing = throw IllegalStateException(what)
    private fun note(line: String) = log("PLA86 $line")
}
