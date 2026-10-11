// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.facts

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import java.io.File
import org.plainphone.core.CertDigest
import org.plainphone.core.PackageName
import org.plainphone.device.apply.CheckAdmin
import org.plainphone.device.storage.FilePolicyStore
import org.plainphone.policy.ApprovalBinding
import org.plainphone.policy.EmergencyFloor
import org.plainphone.policy.FloorRole

/**
 * On-device check of [LivePolicyInputAssembler] over [AndroidPackageSystemPort] (PLA-132), with this
 * test APK as a testOnly Device Owner and the `check-fixtures/signer-target` APK installed.
 *
 * - `-e phase facts -e expect <sha256>`: list the floor and signer digests; the target's live digest
 *   equals the one apksigner printed; the target's emergency filters keep it off the floor; a hidden
 *   user app and a hidden system emergency dialer are both still listed.
 * - `-e phase rotated -e before <sha256> -e expect <sha256>`: after `adb install -r target-v2.apk`
 *   (key B, rotated from A), the signer set is {B}, so a binding to {A} no longer matches.
 *
 * Everything hidden is unhidden at the end. Lines go to logcat (tag PLA132) and `am instrument`.
 */
class PackageFactsCheck(private val ctx: Context, private val args: Bundle?, private val log: (String) -> Unit) {
    private val admin by lazy { ComponentName(ctx, CheckAdmin::class.java) }
    private val dpm by lazy { ctx.getSystemService(DevicePolicyManager::class.java) }
    private val pm by lazy { ctx.packageManager }
    private val policyDir by lazy { File(ctx.filesDir, "pla132-policy") }
    private val target = PackageName("org.plainphone.testtarget")
    private val safetyhub = PackageName("com.google.android.apps.safetyhub")
    private val dialer = PackageName("com.google.android.dialer")
    private val phone = PackageName("com.android.phone")

    private fun assemble() = LivePolicyInputAssembler(AndroidPackageSystemPort(ctx), FilePolicyStore(policyDir), emptyList(),
        PackageName(ctx.packageName), PackageName("com.google.android.apps.messaging")).assemble()

    private fun digest(name: String) = CertDigest.parse(args?.getString(name) ?: throw IllegalStateException("missing -e $name"))

    fun facts() {
        try {
            runFacts()
        } finally {
            for (p in listOf(target, safetyhub)) runCatching { dpm.setApplicationHidden(admin, p.value, false) }
            policyDir.deleteRecursively()
            note("cleanup: hidden left=${listOf(target, safetyhub).filter { dpm.isApplicationHidden(admin, it.value) }.map { it.value }}")
        }
    }

    private fun runFacts() {
        header()
        val expected = digest("expect")

        // 1. The floor, from OS-managed identities.
        val live = assemble()
        val input = live.input
        note("packages listed=${live.packages.size} installed entries=${input.installed.size} bindings=${input.installedIdentities.size} health=${input.health} inventory=${input.inventory}")
        for (role in FloorRole.entries) {
            note("floor ${role.name} (${role.source}): ${input.identities.filter { it.role == role }.map { it.name.value }.sorted()}")
        }
        note("unreadable floor roles=${live.unreadableFloorRoles}")
        val floor = EmergencyFloor.compute(input.installed, input.identities)
        note("EmergencyFloor neverHide=${floor.neverHide.map { it.value }.sorted()}")
        note("EmergencyFloor emergencyUi=${floor.emergencyUi.map { it.value }.sorted()} hasSystemDialer=${floor.hasSystemDialer}")
        expect("no floor source refused", live.unreadableFloorRoles.isEmpty())
        expect("system dialer is ${dialer.value} and in the floor", floor.hasSystemDialer && dialer in floor.emergencyUi)
        expect("${phone.value} is an emergency dialer in the UI subset", phone in floor.emergencyUi)
        expect("every identity carries its role's source", input.identities.all { it.source == it.role.source })

        // 2. Signer digests.
        for (p in listOf(target, dialer, phone, PackageName(ctx.packageName))) {
            val f = live.packages.single { it.packageName == p }
            note("signers ${p.value} system=${f.isSystem} http=${f.handlesProbeHttp} https=${f.handlesProbeHttps}: ${f.signingCerts.map { it.value }}")
        }
        val targetFacts = live.packages.single { it.packageName == target }
        expect("target live digest equals apksigner's ${expected.value}", targetFacts.signingCerts == setOf(expected))
        expect("target binding is the same signer set", input.installedIdentities.single { it.packageName == target } == ApprovalBinding(target, setOf(expected)))
        expect("target is a user app that answers both probes", !targetFacts.isSystem && targetFacts.handlesProbeHttp && targetFacts.handlesProbeHttps)

        // 3. Untrusted app advertising emergency filters: only MATCH_SYSTEM_ONLY keeps it out.
        val naive = pm.queryIntentActivities(Intent(FloorIdentityReader.DIAL_EMERGENCY), PackageManager.ResolveInfoFlags.of(0))
            .map { it.activityInfo.packageName }.toSet()
        note("control: DIAL_EMERGENCY without MATCH_SYSTEM_ONLY resolves to ${naive.sorted()}")
        expect("control: the target does declare the emergency filter", target.value in naive)
        expect("target is not a floor identity", input.identities.none { it.name == target })
        expect("target is not in the floor", target !in floor.neverHide && target !in floor.emergencyUi)

        // 4. A hidden user app is still listed, with the same digest.
        dpm.setApplicationHidden(admin, target.value, true)
        expect("target hidden (DPM readback)", dpm.isApplicationHidden(admin, target.value))
        expect("control: a plain getPackageInfo no longer sees the hidden target", !visibleWithoutFlag(target))
        val hiddenLive = assemble()
        val hiddenTarget = hiddenLive.packages.singleOrNull { it.packageName == target }
        note("hidden target: facts=${hiddenTarget?.signingCerts?.map { it.value }} installed=${hiddenLive.input.installed.filter { it.name == target }}")
        expect("hidden target still listed with the same digest", hiddenTarget?.signingCerts == setOf(expected))
        expect("hidden target still has its binding", hiddenLive.input.installedIdentities.count { it.packageName == target } == 1)

        // 5. A hidden system emergency dialer is still found by the system-only query, so the floor unhides it.
        dpm.setApplicationHidden(admin, safetyhub.value, true)
        expect("safetyhub hidden (DPM readback)", dpm.isApplicationHidden(admin, safetyhub.value))
        expect("control: a plain getPackageInfo no longer sees hidden safetyhub", !visibleWithoutFlag(safetyhub))
        val withHiddenSystem = assemble().input
        val floor2 = EmergencyFloor.compute(withHiddenSystem.installed, withHiddenSystem.identities)
        note("hidden safetyhub: emergency dialers=${withHiddenSystem.identities.filter { it.role == FloorRole.EMERGENCY_DIALER }.map { it.name.value }.sorted()}")
        expect("hidden safetyhub is still an emergency dialer in the floor UI subset", safetyhub in floor2.emergencyUi && safetyhub in floor2.neverHide)
    }

    fun rotated() {
        header()
        val before = digest("before")
        val expected = digest("expect")
        val live = assemble()
        val facts = live.packages.single { it.packageName == target }
        val version = live.input.installed.single { it.name == target }.versionCode
        note("rotated target versionCode=$version signers=${facts.signingCerts.map { it.value }}")
        val history = pm.getPackageInfo(target.value, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            .signingInfo?.signingCertificateHistory?.map { CertDigests.sha256(it.toByteArray()).value }
        note("control: signingCertificateHistory (not used) = $history")
        expect("version 2 installed", version == 2L)
        expect("live signer set is {${expected.value}} (apksigner, minSdk 33+ signer)", facts.signingCerts == setOf(expected))
        expect("signer set changed from {${before.value}}", facts.signingCerts != setOf(before))
        val old = ApprovalBinding(target, setOf(before))
        expect("the binding to the old signer no longer matches", !old.matches(target, facts.signingCerts))
        expect("the live binding is the new signer set only",
            live.input.installedIdentities.filter { it.packageName == target } == listOf(ApprovalBinding(target, setOf(expected))))
    }

    private fun header() {
        note("api=${Build.VERSION.SDK_INT} model=${Build.MODEL} fingerprint=${Build.FINGERPRINT}")
        expect("this APK is Device Owner", dpm.isDeviceOwnerApp(ctx.packageName))
    }

    private fun visibleWithoutFlag(p: PackageName) =
        runCatching { pm.getPackageInfo(p.value, PackageManager.PackageInfoFlags.of(0)) }.isSuccess

    private fun expect(what: String, ok: Boolean) {
        note((if (ok) "ok   " else "FAIL ") + what)
        if (!ok) throw IllegalStateException(what)
    }

    private fun note(line: String) {
        Log.i("PLA132", line)
        log(line)
    }
}
