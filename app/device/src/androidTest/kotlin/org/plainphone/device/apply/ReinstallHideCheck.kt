// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.apply

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import org.plainphone.core.PackageName
import org.plainphone.device.DpmPort

/**
 * PLA-624: why a package that was hidden, uninstalled and installed again stays visible. Runs with
 * this test APK as a testOnly Device Owner and the `play-session/build-fixture.sh` fixture pushed to
 * `/data/local/tmp/pla624/fixture.apk`. Shell commands run as the `shell` user, as `adb shell` does.
 *
 * `-e phase reinstall-hide` records, after every step, the User 0 line of `dumpsys package`, the
 * raw `setApplicationHidden` return value and the `isApplicationHidden` readback:
 * - reinstall: hide, `pm uninstall`, install again, hide again (the PLA-620 failure), then the
 *   [DpmPort.setHidden] path;
 * - control: hide, show, uninstall, install again, hide;
 * - update: hide, `pm install -r` over the hidden package, read back, hide again.
 * Cleanup uninstalls the fixture. Asserts only that the fixture is gone at the end; the lines are the result.
 */
class ReinstallHideCheck(
    private val ctx: Context,
    private val log: (String) -> Unit,
    private val shell: (String) -> String,
) {
    private val fixture = "org.plainphone.playcandidate"
    private val apk = "/data/local/tmp/pla624/fixture.apk"
    private val admin by lazy { ComponentName(ctx, CheckAdmin::class.java) }
    private val flush by lazy { ComponentName(ctx, FlushMarker::class.java) }
    private val dpm by lazy { ctx.getSystemService(DevicePolicyManager::class.java) }
    private val port by lazy { DpmPort(ctx, admin, flush) }

    fun check() {
        try {
            expect(dpm.isDeviceOwnerApp(ctx.packageName), "this test APK is the Device Owner")
            note("api=${Build.VERSION.SDK_INT} fingerprint=${Build.FINGERPRINT}")
            sh("pm uninstall $fixture")
            reinstall()
            control()
            update()
        } finally {
            runCatching { dpm.setApplicationHidden(admin, fixture, false) }
            sh("pm uninstall $fixture")
        }
        expect(!shell("pm list packages -u").lines().contains("package:$fixture"), "fixture removed at the end")
    }

    private fun reinstall() {
        note("== reinstall")
        install("first install")
        hide(true, "hide after first install")
        sh("pm uninstall $fixture"); state("after pm uninstall of the hidden package"); policy()
        install("install again")
        hide(true, "hide after reinstall")
        hide(true, "hide after reinstall, second call")
        port.setHidden(PackageName(fixture), true)
        state("after DpmPort.setHidden(true)")
        policy()
        hide(false, "show (policy reset)")
        hide(true, "hide after the reset")
        hide(false, "show to leave the case")
        sh("pm uninstall $fixture"); state("case end")
    }

    private fun control() {
        note("== control: shown before uninstall")
        install("first install")
        hide(true, "hide")
        hide(false, "show")
        sh("pm uninstall $fixture"); state("after pm uninstall of the shown package")
        install("install again")
        hide(true, "hide after reinstall")
        hide(false, "show to leave the case")
        sh("pm uninstall $fixture"); state("case end")
    }

    private fun update() {
        note("== update over a hidden package")
        install("first install")
        hide(true, "hide")
        install("pm install -r over the hidden package", replace = true)
        hide(true, "hide after the update")
        hide(false, "show to leave the case")
        sh("pm uninstall $fixture"); state("case end")
    }

    private fun install(what: String, replace: Boolean = false) {
        sh("pm install ${if (replace) "-r " else ""}-t $apk")
        state(what)
    }

    private fun hide(hidden: Boolean, what: String) {
        val ret = dpm.setApplicationHidden(admin, fixture, hidden)
        note("$what: setApplicationHidden($hidden) returned $ret")
        state(what)
    }

    private fun state(what: String) {
        val user0 = shell("dumpsys package $fixture").lines().map { it.trim() }
            .filter { it.startsWith("User 0:") || it.startsWith("installed=") || it.contains("pendingUninstall") }
        val readback = runCatching { dpm.isApplicationHidden(admin, fixture) }.getOrElse { "threw ${it.javaClass.simpleName}" }
        note("$what: isApplicationHidden=$readback portHidden=${port.packageHidden(PackageName(fixture))} dumpsys=$user0")
    }

    private fun policy() {
        val dump = shell("dumpsys device_policy").lines()
        val hits = dump.indices.filter { dump[it].contains(fixture) }
        if (hits.isEmpty()) note("dumpsys device_policy: no line names the fixture")
        hits.forEach { i -> note("dumpsys device_policy: " + dump.subList(maxOf(0, i - 2), minOf(dump.size, i + 6)).joinToString(" | ") { it.trim() }) }
    }

    private fun sh(cmd: String): String = shell(cmd).also { note("shell `$cmd` -> ${it.lineSequence().firstOrNull()}") }
    private fun expect(ok: Boolean, what: String) {
        if (!ok) throw IllegalStateException(what)
        note("ok: $what")
    }
    private fun note(line: String) = log("PLA624 $line")
}
