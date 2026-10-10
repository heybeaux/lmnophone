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
 * On-device check of [PermissionGrants] over [DpmPermissionGrantPort] (PLA-86, PLA-182), with this
 * test APK as a testOnly Device Owner and `check-fixtures/permission-target` installed.
 *
 * `-e phase permissions -e expect <sha256>`. Every line is asserted; nothing is only logged.
 * First the raw Android matrix (hidden / visible / visible+suspended x sensor and non-sensor x
 * GRANTED/DENIED), each row against the API 35 answers in [expectedRow] with its reset to DEFAULT.
 * Then the adapter: a binding to another signer writes nothing; a write while hidden is refused and
 * reported as refused; written while visible, ALLOW and DENY read back, pass the policy's own
 * verify, survive re-hiding (and read back while hidden) and unhiding, and leave no uninstall block.
 * Cleanup unsuspends and unhides before it resets, checks every step, and fails the run if a step fails.
 */
class HiddenPermissionCheck(private val ctx: Context, private val args: Bundle?, private val log: (String) -> Unit) {
    private val target = PackageName("org.plainphone.permtarget")
    private val camera = PermissionName("android.permission.CAMERA")
    private val mic = PermissionName("android.permission.RECORD_AUDIO")
    private val contacts = PermissionName("android.permission.READ_CONTACTS")
    private val all = listOf(camera, mic, contacts)
    private val admin by lazy { ComponentName(ctx, CheckAdmin::class.java) }
    private val dpm by lazy { ctx.getSystemService(DevicePolicyManager::class.java) }
    private val port by lazy { DpmPermissionGrantPort(ctx, admin) }
    private val grants by lazy { PermissionGrants(port) }

    fun check() {
        val outcome = runCatching { run() }
        val problems = cleanup(ctx, target, all)
        problems.forEach { note("cleanup failed: $it") }
        outcome.getOrThrow()
        if (problems.isNotEmpty()) fail("cleanup failed: ${problems.joinToString()}")
        note("ok: cleanup unsuspended, unhid, unblocked and reset all three to DEFAULT")
    }

    private fun run() {
        val expect = CertDigest.parse(args?.getString("expect") ?: fail("missing -e expect <sha256>"))
        expect(dpm.isDeviceOwnerApp(ctx.packageName), "this test APK is the Device Owner")
        expect(!dpm.isUninstallBlocked(admin, target.value), "target starts without an uninstall block")
        expect(dpm.setApplicationHidden(admin, target.value, true), "setApplicationHidden(true) returned true")
        expect(dpm.isApplicationHidden(admin, target.value), "target reads back hidden")

        val signers = port.signers(target)
        note("signers while hidden: $signers")
        expect(signers == setOf(expect), "live signer while hidden equals apksigner's digest")
        val identity = ApprovalBinding(target, setOf(expect))
        expect(grants.read(identity, all) == all.associateWith { PermissionReadback.DEFAULT },
            "fresh install reads DEFAULT for all three, never DENIED")

        matrix(identity)
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
        expect(!dpm.isUninstallBlocked(admin, target.value), "the writes put back the uninstall block they added (none left)")
        val visible = grants.read(identity, all)
        note("readback while visible: $visible")
        expect(visible == mapOf(camera to PermissionReadback.GRANTED, mic to PermissionReadback.DENIED, contacts to PermissionReadback.DENIED),
            "visible readback is CAMERA=GRANTED, RECORD_AUDIO=DENIED, READ_CONTACTS=DENIED")
        val manifest = PermissionManifest(identity, 1, "check", listOf(
            RuntimePermission(camera, "Camera", emptyList(), PermissionGrantCapability.SUPPORTED),
            RuntimePermission(mic, "Microphone", emptyList(), PermissionGrantCapability.SUPPORTED),
            RuntimePermission(contacts, "Contacts", emptyList(), PermissionGrantCapability.SUPPORTED)))
        val plan = (PermissionChoices.plan(PermissionSelection(manifest,
            mapOf(camera to PermissionChoice.ALLOW, mic to PermissionChoice.DENY, contacts to PermissionChoice.DENY)), manifest) as PermissionPlanning.Ready).plan
        expect(PermissionChoices.verify(plan, manifest, visible) == PermissionVerification.Verified, "policy verify passes on the visible readback")

        expect(dpm.setApplicationHidden(admin, target.value, true), "re-hide returned true")
        val rehidden = grants.read(identity, all)
        note("readback after re-hide: $rehidden")
        expect(rehidden == visible, "fixed states survive hiding and read back while hidden")
        expect(dpm.setApplicationHidden(admin, target.value, false), "final unhide returned true")
        expect(grants.read(identity, all) == visible, "fixed states survive unhide")
    }

    /** API 35 answer for one matrix write: (returned, fixed state, actually granted). */
    private fun expectedRow(hidden: Boolean, suspended: Boolean, p: PermissionName, state: PolicyGrant): Triple<Boolean, PolicyGrant, Boolean> = when {
        hidden -> Triple(false, PolicyGrant.DEFAULT, false)
        // Suspended sensor permissions read back fixed DENIED; a GRANTED write is still actually granted.
        suspended && p != contacts -> Triple(true, PolicyGrant.DENIED, state == PolicyGrant.GRANTED)
        else -> Triple(true, state, state == PolicyGrant.GRANTED)
    }

    /** The raw Android matrix, every row and every reset asserted; ends hidden, not suspended. */
    private fun matrix(identity: ApprovalBinding) {
        expect(dpm.canAdminGrantSensorsPermissions(), "canAdminGrantSensorsPermissions is true")
        val mismatches = mutableListOf<String>()
        for ((hide, suspend) in listOf(true to false, false to false, false to true)) {
            dpm.setApplicationHidden(admin, target.value, hide)
            val failed = dpm.setPackagesSuspended(admin, arrayOf(target.value), suspend)
            expect(failed.isEmpty() && dpm.isApplicationHidden(admin, target.value) == hide && dpm.isPackageSuspended(admin, target.value) == suspend,
                "matrix setup hidden=$hide suspended=$suspend")
            for (p in all) for (state in listOf(PolicyGrant.GRANTED, PolicyGrant.DENIED)) {
                val row = "hidden=$hide suspended=$suspend ${p.value} set=$state"
                val actual = Triple(port.setPolicyState(target, p, state), port.policyState(target, p), port.granted(target, p))
                val readback = grants.read(identity, listOf(p))[p]
                val reset = port.setPolicyState(target, p, PolicyGrant.DEFAULT)
                val afterReset = port.policyState(target, p)
                val line = "$row returned=${actual.first} policy=${actual.second} granted=${actual.third} adapter=$readback reset=$reset resetPolicy=$afterReset"
                val expected = expectedRow(hide, suspend, p, state)
                // The adapter must never confirm a write whose fixed state and actual grant disagree.
                val adapterOk = readback == when {
                    expected.second == PolicyGrant.GRANTED && expected.third -> PermissionReadback.GRANTED
                    expected.second == PolicyGrant.DENIED && !expected.third -> PermissionReadback.DENIED
                    expected.second == PolicyGrant.DEFAULT -> PermissionReadback.DEFAULT
                    else -> PermissionReadback.UNKNOWN
                }
                val ok = actual == expected && adapterOk && reset == !hide && afterReset == PolicyGrant.DEFAULT
                note("${if (ok) "ok" else "MISMATCH"}: matrix $line")
                if (!ok) mismatches += "$line expected=$expected"
            }
        }
        expect(mismatches.isEmpty(), "matrix matches the API 35 answers (${mismatches.joinToString("; ")})")
        expect(dpm.setPackagesSuspended(admin, arrayOf(target.value), false).isEmpty() && !dpm.isPackageSuspended(admin, target.value), "matrix unsuspended")
        expect(dpm.setApplicationHidden(admin, target.value, true) && dpm.isApplicationHidden(admin, target.value), "matrix ends hidden")
    }

    private fun expect(ok: Boolean, what: String) {
        if (!ok) fail(what)
        note("ok: $what")
    }
    private fun fail(what: String): Nothing = throw IllegalStateException(what)
    private fun note(line: String) = log("PLA86 $line")

    companion object {
        /**
         * Unsuspends, unhides and unblocks [target] first (Android refuses grant writes while hidden),
         * then resets [permissions] to DEFAULT. Returns each step that failed or threw.
         */
        fun cleanup(ctx: Context, target: PackageName, permissions: List<PermissionName>): List<String> {
            val admin = ComponentName(ctx, CheckAdmin::class.java)
            val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
            val port = DpmPermissionGrantPort(ctx, admin)
            val problems = mutableListOf<String>()
            fun step(what: String, ok: () -> Boolean) {
                runCatching(ok).fold({ if (!it) problems += what }, { problems += "$what threw $it" })
            }
            step("unsuspend") { dpm.setPackagesSuspended(admin, arrayOf(target.value), false).isEmpty() && !dpm.isPackageSuspended(admin, target.value) }
            step("unhide") { dpm.setApplicationHidden(admin, target.value, false); !dpm.isApplicationHidden(admin, target.value) }
            step("unblock uninstall") { port.setUninstallBlocked(target, false) }
            for (p in permissions) step("reset ${p.value}") { port.setPolicyState(target, p, PolicyGrant.DEFAULT) && port.policyState(target, p) == PolicyGrant.DEFAULT }
            return problems
        }
    }
}
