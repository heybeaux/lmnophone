// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.update

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import org.plainphone.core.CertDigest
import org.plainphone.core.PackageName
import org.plainphone.policy.ApprovalBinding
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * On-device check of the Update installer adapter (PLA-167) with this test APK as a testOnly Device
 * Owner. `org.plainphone.updatetarget` (check-fixtures/update-target) stands in for the self package,
 * so the instrumentation process survives each install; v1 is installed before the check, the other
 * builds are served by the host on `10.0.2.2:<port>` and fetched with the real [UpdateDownload].
 *
 * - `-e phase update -e key <sha256 A> -e keyb <sha256 B> -e port <p> -e redirect <r>`: download
 *   limits and redirect refusal, signature-verified facts, a real session through prepare /
 *   finalFacts / commit / status / readback, abandonment right after commit (before `isCommitted()`
 *   turns true), and a "restarted" instance that abandons a leftover session but keeps a committed one.
 * - `-e phase pending1` then `-e phase pending2`: commit v5 and kill this process at once (X4); the
 *   next process abandons at startup and reads the install back.
 */
class UpdateInstallerCheck(private val ctx: Context, private val args: Bundle?, private val log: (String) -> Unit) {
    private val target = PackageName("org.plainphone.updatetarget")
    private val dpm by lazy { ctx.getSystemService(DevicePolicyManager::class.java) }
    private val keyA by lazy { CertDigest.parse(arg("key")) }
    private val pinned by lazy { ApprovalBinding(target, setOf(keyA)) }
    private val statuses = LinkedBlockingQueue<Pair<Int, Int>>()
    private val routed = LinkedBlockingQueue<Pair<Int, Boolean>>()
    private val router = InstallStatusRouter { id, ok -> routed += id to ok }
    private val action = "${ctx.packageName}.UPDATE_STATUS"
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_SESSION_ID, -1)
            val status = intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, InstallStatusRouter.MISSING)
            statuses += id to status
            router.route(intent)
        }
    }
    private val port by lazy {
        AndroidInstallerPort(ctx) { id ->
            PendingIntent.getBroadcast(ctx, id, Intent(action).setPackage(ctx.packageName),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT).intentSender
        }
    }
    private fun staging(name: String) = UpdateStaging(File(ctx.noBackupFilesDir, name))
    private fun installer(staging: UpdateStaging) = StagedUpdateInstaller(port, ArchiveApkInspector(ctx), staging, pinned) { true }

    fun update() {
        ctx.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        try { runUpdate() } finally { ctx.unregisterReceiver(receiver) }
    }

    private fun runUpdate() {
        expect(dpm.isDeviceOwnerApp(ctx.packageName), "this test APK is the Device Owner")
        expect(port.installedVersionCode(target) == 1L, "target v1 installed")
        val main = staging("update-staging")
        val a = installer(main)
        expect(a.abandonOwnSessions(), "startup abandonment with no sessions is ready")

        // Download rules against the host.
        val httpPort = arg("port").toInt()
        fun source(name: String, p: Int = httpPort) = UpdateSource.parse("http://10.0.2.2:$p/$name")!!
        expect(UpdateSource.parse("http://127.0.0.1:$httpPort/v2.apk") == null, "loopback source refused")
        expect(UpdateDownload(main).fetch(source("v3.apk", arg("redirect").toInt())) == null && main.staged() == null,
            "302 from the redirect server refused, nothing staged (host log shows v3 never fetched)")
        expect(UpdateDownload(main, 1_000_000, 120_000_000_000L).fetch(source("v2.apk")) == null && main.staged() == null,
            "41.9 MB body over a 1 MB limit refused, nothing staged")

        // Signature-verified facts from the archive.
        val inspector = ArchiveApkInspector(ctx)
        fun facts(name: String) = UpdateDownload(main).fetch(source(name))?.let { inspector.inspect(it) }
        expect(facts("v6-tampered.apk") == null, "tampered APK (one asset byte flipped after signing): no facts")
        expect(facts("v6-keyb.apk")?.signers == setOf(CertDigest.parse(arg("keyb"))), "key B APK reads signer B")
        expect(facts("v6-otherpkg.apk")?.packageName == PackageName("org.plainphone.updateother"), "other package reads as such")
        expect(main.clear(), "staging cleared")
        expect(a.prepare() == null, "nothing staged: no session")
        expect(port.ownSessions()!!.isEmpty(), "and no session was created")

        // Wrong files never get a session.
        for (bad in listOf("v6-tampered.apk", "v6-keyb.apk", "v6-otherpkg.apk")) {
            expect(UpdateDownload(main).fetch(source(bad)) != null, "$bad staged")
            expect(a.prepare() == null && port.ownSessions()!!.isEmpty(), "$bad refused before any session")
        }

        // A real session: prepare, final facts, substitution, commit, status, readback.
        val staged = UpdateDownload(main).fetch(source("v2.apk"))
        expect(staged != null, "v2 downloaded into private staging (${staged?.length()} bytes)")
        val id = a.prepare() ?: fail("prepare v2")
        val info = ctx.packageManager.packageInstaller.getSessionInfo(id)!!
        expect(info.appPackageName == target.value && !info.isCommitted, "session $id is bound to ${target.value}, uncommitted")
        expect(port.entries(id) == listOf("base.apk"), "session has exactly one entry")
        val f = a.finalFacts() ?: fail("finalFacts v2")
        note("finalFacts: session=${f.sessionId} sha256=${f.staged.binding.sha256} version=${f.staged.binding.versionCode} " +
            "package=${f.staged.identity.packageName.value} signers=${f.staged.identity.signingCerts.map { it.value }} installed=${f.installedVersionCode}")
        expect(f.staged.binding.sha256 == arg("sha2") && f.staged.binding.versionCode == 2L && f.staged.identity == pinned &&
            f.installedVersionCode == 1L, "facts: host sha256 of v2, version 2, pinned identity, installed 1")
        expect(a.prepare() == null, "no second write after the final read")
        expect(UpdateDownload(main).fetch(source("v3.apk")) != null, "a later download replaced the staged file (v3)")
        expect(a.finalFacts() == null, "staged file no longer matches the session: null")
        expectThrows("commit after a failed final read is refused") { a.commit(id) }
        expect(UpdateDownload(main).fetch(source("v2.apk")) != null && a.finalFacts() == f, "same v2 bytes again: same facts")
        expect(port.ownSessions()!!.map { it.id } == listOf(id), "still the one session")

        val t0 = SystemClock.elapsedRealtimeNanos()
        a.commit(id)
        val committedRead = port.ownSessions()!!.firstOrNull { it.id == id }?.committed
        val kept = a.abandonOwnSessions()
        val t1 = SystemClock.elapsedRealtimeNanos()
        note("right after commit: isCommitted=$committedRead, abandonOwnSessions=$kept, ${(t1 - t0) / 1000} us after commit()")
        expectThrows("second commit refused") { a.commit(id) }
        val status = statuses.poll(30, TimeUnit.SECONDS)
        note("status broadcast: $status")
        expect(status == id to android.content.pm.PackageInstaller.STATUS_SUCCESS, "STATUS_SUCCESS for session $id")
        expect(routed.poll(1, TimeUnit.SECONDS) == id to true, "router forwarded success")
        expect(a.installedVersionCode() == 2L, "readback: v2 installed (the session survived abandonment right after commit)")

        // "Restart": a fresh instance abandons a leftover uncommitted session and keeps a committed one.
        val c = installer(staging("update-staging-c"))
        expect(UpdateDownload(staging("update-staging-c")).fetch(source("v4.apk")) != null, "leftover: v4 staged")
        val leftover = c.prepare() ?: fail("prepare leftover")
        expect(UpdateDownload(main).fetch(source("v3.apk")) != null, "v3 staged")
        val committedId = a.prepare() ?: fail("prepare v3")
        expect(a.finalFacts()?.staged?.binding?.versionCode == 3L, "v3 final facts")
        a.commit(committedId)
        val deadline = SystemClock.elapsedRealtime() + 5_000
        var flag = false
        while (!flag && SystemClock.elapsedRealtime() < deadline) {
            flag = port.ownSessions()?.firstOrNull { it.id == committedId }?.committed == true
        }
        note("sessions before restart abandonment: ${port.ownSessions()}")
        val restarted = installer(staging("update-staging-r"))
        expect(flag, "committed session $committedId reads isCommitted=true")
        expect(restarted.abandonOwnSessions(), "restarted instance: abandonment ready")
        val after = port.ownSessions()!!
        note("sessions after restart abandonment: $after")
        expect(after.none { it.id == leftover }, "leftover uncommitted session $leftover abandoned")
        expect(statuses.poll(30, TimeUnit.SECONDS) == committedId to android.content.pm.PackageInstaller.STATUS_SUCCESS,
            "committed session $committedId still finished: STATUS_SUCCESS")
        expect(restarted.installedVersionCode() == 3L, "readback: v3 installed")
        expect(port.ownSessions()!!.isEmpty(), "no own sessions left")
    }

    /** X4: commit and die at once; the install must not depend on this process. */
    fun pending1() {
        expect(dpm.isDeviceOwnerApp(ctx.packageName), "this test APK is the Device Owner")
        val main = staging("update-staging")
        val a = installer(main)
        expect(a.abandonOwnSessions(), "startup abandonment ready")
        val before = a.installedVersionCode()
        val apk = UpdateDownload(main).fetch(UpdateSource.parse("http://10.0.2.2:${arg("port")}/v5.apk")!!) ?: fail("download v5")
        val id = a.prepare() ?: fail("prepare v5")
        expect(a.finalFacts()?.staged?.binding?.versionCode == 5L, "v5 final facts (${apk.length()} bytes), installed $before")
        note("committing session $id, then killing this process")
        a.commit(id)
        Process.killProcess(Process.myPid())
    }

    fun pending2() {
        val a = installer(staging("update-staging"))
        val seen = port.ownSessions()
        note("own sessions at startup: $seen; installed ${a.installedVersionCode()}")
        expect(a.abandonOwnSessions(), "startup abandonment ready")
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (a.installedVersionCode() != 5L && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        expect(a.installedVersionCode() == 5L, "readback after restart: v5 installed")
        expect(port.ownSessions()!!.isEmpty(), "no own sessions left")
    }

    private fun arg(name: String) = args?.getString(name) ?: fail("missing -e $name")
    private fun expectThrows(what: String, block: () -> Unit) {
        val threw = try { block(); false } catch (_: IllegalStateException) { true }
        expect(threw, what)
    }
    private fun expect(ok: Boolean, what: String) {
        if (!ok) fail(what)
        note("ok: $what")
    }
    private fun fail(what: String): Nothing = throw IllegalStateException(what)
    private fun note(line: String) {
        Log.i("PLA167", line)
        log("PLA167 $line")
    }
}
