// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.apply

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import org.plainphone.core.BuildInventory
import org.plainphone.core.NeverHideList
import org.plainphone.core.PackageName
import org.plainphone.device.DevicePolicyPort
import org.plainphone.device.DpmPort
import org.plainphone.device.ReadbackPolicyApplier
import org.plainphone.device.SerialDispatcher
import org.plainphone.device.approval.DeviceApprovalEffects
import org.plainphone.device.approval.DpmPermissionGrantPort
import org.plainphone.device.approval.PublishedHomeApps
import org.plainphone.device.facts.AndroidPackageSystemPort
import org.plainphone.device.facts.LivePolicyInputAssembler
import org.plainphone.device.facts.PackageSystemPort
import org.plainphone.policy.ApprovalBinding
import org.plainphone.policy.PolicyEngine
import org.plainphone.policy.PolicyHealth
import org.plainphone.policy.PolicyPlan
import org.plainphone.policy.PolicyStore
import org.plainphone.policy.Restriction
import org.plainphone.policy.StoredPolicy

/**
 * PLA-293 on an API 35 emulator, with this test APK as a testOnly Device Owner, `uid-a.apk` and `uid-b.apk`
 * (`check-fixtures/uid-target`, one shared UID) installed, `uid-a.apk` pushed to `/data/local/tmp`, and
 * `org.plainphone.permtarget` installed. `-e phase absent`.
 *
 * uid-a is the stand-in minimum-UI package (and so never-hide); uid-b is a second minimum-UI package that
 * shares its UID. permtarget is the window candidate. The real [AndroidPackageSystemPort] answers every
 * absence question; its package lists and floor queries are narrowed to these packages, Plainphone (this
 * APK), the system dialer and the SMS app, so the plan never sweeps the rest of the emulator. The real
 * [LivePolicyInputAssembler], [PolicyEngine] and [DeviceApprovalEffects] over [DpmPort] run on one
 * [SerialDispatcher]. uid-a is removed for user 0 with `pm uninstall -k --user 0` and reinstalled with
 * `pm install -t`. Restriction writes are recorded, not applied (DISALLOW_DEBUGGING_FEATURES would cut
 * adb). Failed lock-task writes and readbacks are injected in a port wrapper; everything else is real.
 */
class AbsentMinimumUiCheck(private val ctx: Context, private val log: (String) -> Unit, private val shell: (String) -> String) {
    private val a = PackageName("org.plainphone.uidtarget.a")
    private val b = PackageName("org.plainphone.uidtarget.b")
    private val target = PackageName("org.plainphone.permtarget")
    private val stray = PackageName("org.plainphone.never.installed")
    private val self = PackageName(ctx.packageName)
    private val admin by lazy { ComponentName(ctx, CheckAdmin::class.java) }
    private val dpm by lazy { ctx.getSystemService(DevicePolicyManager::class.java) }
    private val real = AndroidPackageSystemPort(ctx)
    private val messages = PackageName(real.defaultSms() ?: "com.google.android.apps.messaging")
    private val known = setOfNotNull(self.value, a.value, b.value, target.value, messages.value, real.systemDialer())
    private val faults = FaultPort(NoRestrictionsPort(DpmPort(ctx, admin, ComponentName(ctx, FlushMarker::class.java))))
    private val port: DevicePolicyPort = faults
    private val ui = setOf(messages, a, b)
    private val assembler = LivePolicyInputAssembler(KnownPackages(real, known), object : PolicyStore {
        override fun read() = StoredPolicy(1, emptySet(), PolicyHealth.VALID)
        override fun write(policy: StoredPolicy) = false
    }, listOf(BuildInventory(Build.MODEL, Build.FINGERPRINT, 1, NeverHideList(ui), ui, emptySet())), self, messages)
    private lateinit var effects: DeviceApprovalEffects
    private lateinit var dispatcher: SerialDispatcher
    private lateinit var identity: ApprovalBinding

    fun check() {
        val outcome = runCatching { run() }
        val problems = cleanup()
        problems.forEach { note("cleanup failed: $it") }
        outcome.getOrThrow()
        if (problems.isNotEmpty()) fail("cleanup failed: ${problems.joinToString()}")
        note("ok: cleanup reinstalled uid-a, unhid permtarget, cleared lock task, Home and input-method policy")
    }

    private fun run() {
        note("api=${Build.VERSION.SDK_INT} type=${Build.TYPE} known=${known.sorted()}")
        expect(dpm.isDeviceOwnerApp(ctx.packageName), "this test APK is the Device Owner")
        expect(installed(a) && installed(b), "uid-a and uid-b start installed: ${shell("pm list packages -U org.plainphone.uidtarget")}")
        val sharedUid = port.uid(a)
        expect(sharedUid != null && sharedUid == port.uid(b), "uid-a and uid-b share UID $sharedUid")
        val signers = DpmPermissionGrantPort(ctx, admin).signers(target) ?: fail("permtarget signers unreadable")
        identity = ApprovalBinding(target, signers)
        dispatcher = SerialDispatcher("pla293-policy")
        effects = DeviceApprovalEffects(dispatcher, port, DpmPermissionGrantPort(ctx, admin), PublishedHomeApps(),
            { runCatching { pass().neverHide }.getOrNull() }) { null }
        try {
            neverAbsent()
            staleThenReduced(sharedUid!!)
            strayNullUid()
            failedLockTaskWrite()
            lockTaskNotReadBack()
            for (boundary in Boundary.entries) installAt(boundary)
        } finally {
            dispatcher.close()
        }
    }

    /** Hidden, disabled, suspended and listed packages are never absent; an unknown name is. */
    private fun neverAbsent() {
        fun notAbsent(how: String) {
            val asked = real.installedForUser(a.value)
            val absent = assembler.assemble().input.confirmedAbsentPackages
            expect(asked && a !in absent, "$how uid-a: installedForUser=$asked, confirmedAbsent=${absent.map { it.value }}")
        }
        dpm.setApplicationHidden(admin, a.value, true)
        expect(dpm.isApplicationHidden(admin, a.value), "uid-a hidden by DPM")
        notAbsent("hidden")
        dpm.setApplicationHidden(admin, a.value, false)
        note("disable: ${shell("pm disable-user --user 0 ${a.value}")}")
        notAbsent("disabled")
        note("enable: ${shell("pm enable --user 0 ${a.value}")}")
        expect(dpm.setPackagesSuspended(admin, arrayOf(a.value), true).isEmpty(), "uid-a suspended by DPM")
        notAbsent("suspended")
        dpm.setPackagesSuspended(admin, arrayOf(a.value), false)
        expect(!real.installedForUser(stray.value), "a never-installed name reads not installed (NameNotFound path)")
    }

    private fun staleThenReduced(sharedUid: Int) {
        expect(apply(pass()), "baseline apply with uid-a present")
        expect(a in lockTask() && b in lockTask(), "baseline: uid-a and uid-b in the applied lock-task list ${lockTask().map { it.value }}")
        remove(a)
        expect(!real.installedForUser(a.value), "after pm uninstall -k --user 0: installedForUser(uid-a)=false (FLAG_INSTALLED unset)")
        expect(port.uid(a) == null && port.packageState(a) == null, "uid-a has no UID and no package state for user 0")
        expect(a in lockTask(), "the stale applied list still names uid-a")
        expect(!open(), "window REFUSED with the stale list (uid-a has a null UID)")
        expect(close(), "close after the refused open re-hides permtarget")

        val reduced = pass()
        expect(a !in reduced.lockTaskPackages && b in reduced.lockTaskPackages, "reduced plan drops uid-a, keeps uid-b: ${reduced.lockTaskPackages.map { it.value }.sorted()}")
        expect(a in reduced.neverHide, "uid-a stays never-hide")
        val result = dispatcher.run { ReadbackPolicyApplier(port).apply(reduced) }
        expect(result.complete && !result.needsAttention && result.absent == setOf(a),
            "absent never-hide uid-a: apply complete=${result.complete}, attention=${result.needsAttention}, reported absent=${result.absent.map { it.value }}")
        expect(!installed(a), "uid-a was reported, not repaired: still not installed")
        expect(apply(reduced), "applyPolicy(reduced)")
        expect(readBack(reduced), "readBackPolicy(reduced)")
        expect(lockTask() == reduced.lockTaskPackages, "applied list reads back exactly: ${lockTask().map { it.value }.sorted()}")
        expect(open(), "window OPENS after the reduced-list readback")
        expect(!dpm.isApplicationHidden(admin, target.value) && target !in lockTask(), "in the window: permtarget visible, outside lock task")
        expect(port.uid(b) == sharedUid && b in lockTask(), "uid-b (the absent package's shared UID $sharedUid) remains and resolves")
        expect(close(), "close")
        expect(dpm.isApplicationHidden(admin, target.value), "after close: permtarget hidden")
    }

    /** A remaining name with a null UID still refuses: the gate is not filtered. */
    private fun strayNullUid() {
        dpm.setLockTaskPackages(admin, (lockTask() + stray).map { it.value }.toTypedArray())
        expect(stray in lockTask() && port.uid(stray) == null, "stray never-installed name in the applied list, null UID")
        expect(!open(), "window REFUSED: a remaining null-UID name")
        expect(close(), "close")
        expect(apply(pass()), "re-apply drops the stray")
    }

    private fun stale() {
        if (!installed(a)) install(a)
        expect(apply(pass()) && a in lockTask(), "uid-a present and applied")
        remove(a)
    }

    private fun failedLockTaskWrite() {
        stale()
        faults.lockTaskThrows = true
        val ok = try { apply(pass()) } finally { faults.lockTaskThrows = false }
        expect(!ok, "lock-task write throws (injected): applyPolicy false")
        expect(a in lockTask(), "stale uid-a still applied")
        expect(!open(), "window REFUSED after a failed lock-task write")
        expect(close(), "close")
    }

    private fun lockTaskNotReadBack() {
        faults.lockTaskIgnored = true
        val plan = pass()
        val ok = try { apply(plan) } finally { faults.lockTaskIgnored = false }
        expect(!ok && !readBack(plan), "lock-task write ignored (injected): apply and readback false")
        expect(!open(), "window REFUSED when the list did not read back")
        expect(close(), "close")
    }

    private enum class Boundary { PLAN_BUILT, APPLIED, READ_BACK, IN_WINDOW }

    private fun installAt(boundary: Boundary) {
        stale()
        val reduced = pass()
        expect(a !in reduced.lockTaskPackages, "[$boundary] plan built without uid-a")
        fun at(b: Boundary) {
            if (b == boundary) install(a)
            if (b != Boundary.PLAN_BUILT) expect(a !in lockTask(), "[$boundary] at $b: uid-a (installed=${installed(a)}) not in lock task")
        }
        at(Boundary.PLAN_BUILT)
        expect(apply(reduced), "[$boundary] apply")
        at(Boundary.APPLIED)
        expect(readBack(reduced), "[$boundary] readback")
        at(Boundary.READ_BACK)
        expect(open(), "[$boundary] window opens")
        at(Boundary.IN_WINDOW)
        expect(close(), "[$boundary] close")
        expect(installed(a) && a !in lockTask(), "[$boundary] after close: uid-a installed, still no lock task")
        val fresh = pass()
        expect(a in fresh.lockTaskPackages && apply(fresh) && a in lockTask(), "[$boundary] fresh pass after close restores uid-a")
    }

    private fun pass(): PolicyPlan = PolicyEngine.decide(assembler.assemble().input)
    private fun apply(plan: PolicyPlan) = dispatcher.run { effects.applyPolicy(plan) }
    private fun readBack(plan: PolicyPlan) = dispatcher.run { effects.readBackPolicy(plan) }
    private fun open() = dispatcher.run { effects.openPermissionWindow(identity) }
    private fun close() = dispatcher.run { effects.closePermissionWindow(identity) }
    private fun lockTask() = dpm.getLockTaskPackages(admin).map(::PackageName).toSet()
    private fun installed(name: PackageName) = real.installedForUser(name.value)
    private fun remove(name: PackageName) = note("remove ${name.value}: ${shell("pm uninstall -k --user 0 ${name.value}")}")
    private fun install(name: PackageName) {
        note("install ${name.value}: ${shell("pm install -t /data/local/tmp/uid-a.apk")}")
        expect(installed(name), "${name.value} installed again")
    }

    private fun cleanup(): List<String> {
        val problems = mutableListOf<String>()
        fun step(what: String, ok: () -> Boolean) { runCatching(ok).fold({ if (!it) problems += what }, { problems += "$what threw $it" }) }
        step("reinstall uid-a") { installed(a) || shell("pm install -t /data/local/tmp/uid-a.apk").contains("Success") }
        problems += HiddenPermissionCheck.cleanup(ctx, target, emptyList())
        for (name in listOf(a, b)) step("unhide ${name.value}") { dpm.setApplicationHidden(admin, name.value, false); !dpm.isApplicationHidden(admin, name.value) }
        step("clear lock task") { dpm.setLockTaskPackages(admin, emptyArray()); dpm.getLockTaskPackages(admin).isEmpty() }
        step("clear Home") { dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName); true }
        step("input methods") { dpm.setPermittedInputMethods(admin, null) }
        step("accessibility") { dpm.setPermittedAccessibilityServices(admin, null) }
        return problems
    }

    /** The real port's answers, with lists and floor queries narrowed to [known]. Absence is asked for real. */
    private class KnownPackages(private val real: PackageSystemPort, private val known: Set<String>) : PackageSystemPort by real {
        override fun packages() = real.packages().filter { it.name in known }
        override fun browsableHandlers(url: String) = real.browsableHandlers(url) intersect known
        override fun systemDialer() = real.systemDialer()?.takeIf { it in known }
        override fun defaultDialer() = real.defaultDialer()?.takeIf { it in known }
        override fun defaultSms() = real.defaultSms()?.takeIf { it in known }
        override fun webViewProvider() = real.webViewProvider()?.takeIf { it in known }
        override fun enabledInputMethods() = real.enabledInputMethods() intersect known
        override fun systemInCallUiPackages() = real.systemInCallUiPackages() intersect known
        override fun systemOnlyActivities(action: String, data: String?) = real.systemOnlyActivities(action, data) intersect known
        override fun systemOnlyReceivers(action: String) = real.systemOnlyReceivers(action) intersect known
    }

    /** Records restriction writes and reports them back; every other call is the real port. */
    private class NoRestrictionsPort(private val real: DevicePolicyPort) : DevicePolicyPort by real {
        private val recorded = mutableSetOf<Restriction>()
        override fun restrictions(): Set<Restriction> = recorded.toSet()
        override fun addRestriction(restriction: Restriction) { recorded += restriction }
    }

    /** Injected lock-task write failures: a throw, or a write that does not take effect. */
    private class FaultPort(private val real: DevicePolicyPort) : DevicePolicyPort by real {
        @Volatile var lockTaskThrows = false
        @Volatile var lockTaskIgnored = false
        override fun setLockTaskPackages(packages: Set<PackageName>) {
            if (lockTaskThrows) throw IllegalStateException("injected lock-task write failure")
            if (!lockTaskIgnored) real.setLockTaskPackages(packages)
        }
    }

    private fun expect(ok: Boolean, what: String) {
        if (!ok) fail(what)
        note("ok: $what")
    }
    private fun fail(what: String): Nothing = throw IllegalStateException(what)
    private fun note(line: String) = log("PLA293 $line")
}
