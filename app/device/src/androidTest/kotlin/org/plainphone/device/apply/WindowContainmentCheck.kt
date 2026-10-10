// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.apply

import android.app.ActivityManager
import android.app.Instrumentation
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import java.io.File
import org.plainphone.core.BootTime
import org.plainphone.core.BuildInventory
import org.plainphone.core.CertDigest
import org.plainphone.core.ClockReading
import org.plainphone.core.Mode
import org.plainphone.core.NeverHideList
import org.plainphone.core.PackageName
import org.plainphone.core.RunningClock
import org.plainphone.core.RunningTime
import org.plainphone.core.TrustedPersonId
import org.plainphone.device.DevicePolicyPort
import org.plainphone.device.DpmPort
import org.plainphone.device.SerialDispatcher
import org.plainphone.device.approval.DeviceApprovalEffects
import org.plainphone.device.approval.DpmPermissionGrantPort
import org.plainphone.device.approval.HomeAppList
import org.plainphone.device.approval.PublishedHomeApps
import org.plainphone.device.authority.JointAuthorityStore
import org.plainphone.policy.AppApprovalController
import org.plainphone.policy.AppApprovalEffects
import org.plainphone.policy.AppApprovalIdSource
import org.plainphone.policy.AppApprovalOutcome
import org.plainphone.policy.AppApprovalPhase
import org.plainphone.policy.AppApprovalState
import org.plainphone.policy.ApprovalBinding
import org.plainphone.policy.BatteryState
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.FloorIdentity
import org.plainphone.policy.FloorRole
import org.plainphone.policy.IdentitySource
import org.plainphone.policy.InstalledPackage
import org.plainphone.policy.KeyDerivation
import org.plainphone.policy.PermissionChoice
import org.plainphone.policy.PermissionGrantCapability
import org.plainphone.policy.PermissionManifest
import org.plainphone.policy.PermissionName
import org.plainphone.policy.PermissionSelection
import org.plainphone.policy.PermissionWrite
import org.plainphone.policy.PinResult
import org.plainphone.policy.PinVault
import org.plainphone.policy.PolicyEngine
import org.plainphone.policy.PolicyInput
import org.plainphone.policy.PolicyPlan
import org.plainphone.policy.Restriction
import org.plainphone.policy.RuntimePermission
import org.plainphone.policy.SecretEntropy
import org.plainphone.policy.SessionAccess
import org.plainphone.policy.SessionController
import org.plainphone.policy.SessionEffects
import org.plainphone.policy.WaitingState

/**
 * PLA-219 (PLA-218 findings 1 and 4), API 35, this test APK a testOnly Device Owner, fixtures from
 * `check-fixtures/launch-target` and `check-fixtures/uid-target` installed. Phases (`-e phase`):
 *
 * - `uid`: uid-a in applied lock task and visible, uid-b (same UID) hidden. The real adapter's window
 *   for uid-b must refuse without unhiding it; the window for launch-target (own UID) opens (control).
 *   Then leaves uid-a and this APK in lock task and uid-b visible, for the host's launch demonstration.
 * - `pause-preparing` / `pause-editing`: a durable [JointAuthorityStore] in this app's files, the real
 *   controller and adapter over [DpmPort], the ordinary policy applied, and this APK's [CheckHome] in
 *   an active lock task (checked with `getLockTaskModeState`). The approval (or, after one complete
 *   approval, the edit) opens its window; right after the unhide the check logs PAUSED, posts a
 *   notification whose tap opens the target, and blocks. The host tries to enter the target and then
 *   kills this process with `am force-stop`, so no `finally` runs.
 * - `recover` (`-e session ok|fail`, `-e battery ok|low`): a new process with a fresh dispatcher, Home
 *   list and controller on the same store. Logs every effect, session, battery and Home event in order.
 * - `cleanup`: clears lock task, Home, the store, and unhides the fixtures.
 *
 * Restriction writes are recorded, not applied (DISALLOW_DEBUGGING_FEATURES would cut adb), as in
 * [PermissionWindowCheck]. Process death here is `am force-stop`, not a power cut.
 */
class WindowContainmentCheck(
    private val instrumentation: Instrumentation,
    private val args: Bundle?,
    private val log: (String) -> Unit,
) {
    private val ctx: Context = instrumentation.targetContext
    private val target = PackageName("org.plainphone.launchtarget")
    private val uidA = PackageName("org.plainphone.uidtarget.a")
    private val uidB = PackageName("org.plainphone.uidtarget.b")
    private val camera = PermissionName("android.permission.CAMERA")
    private val messages = PackageName("com.google.android.apps.messaging")
    private val dialer = PackageName("com.google.android.dialer")
    private val self = PackageName(ctx.packageName)
    private val admin = ComponentName(ctx, CheckAdmin::class.java)
    private val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
    private val am = ctx.getSystemService(ActivityManager::class.java)
    private val person = TrustedPersonId("holder")
    private val pin get() = CharArray(6) { '1' }
    private val dir = File(ctx.filesDir, "pla219-authority")
    private val inventory = BuildInventory("check", "check", 1, NeverHideList(setOf(messages)), setOf(messages), emptySet())
    private val events = mutableListOf<String>()

    private fun expectDigest() = CertDigest.parse(args?.getString("expect") ?: fail("missing -e expect <sha256>"))
    private fun hidden(name: PackageName) = dpm.isApplicationHidden(admin, name.value)
    private fun permitted(name: PackageName) = name.value in dpm.getLockTaskPackages(admin)
    private fun lockMode() = when (am.lockTaskModeState) {
        ActivityManager.LOCK_TASK_MODE_LOCKED -> "LOCKED"
        ActivityManager.LOCK_TASK_MODE_PINNED -> "PINNED"
        else -> "NONE"
    }
    private fun state(name: PackageName = target) = "hidden=${hidden(name)} lockTaskPermitted=${permitted(name)} lockTaskMode=${lockMode()}"
    private fun event(line: String) { events += line; note("event ${events.size}: $line") }

    fun uid() {
        expect(dpm.isDeviceOwnerApp(ctx.packageName), "this test APK is the Device Owner")
        val port = NoRestrictionsPort(DpmPort(ctx, admin, ComponentName(ctx, FlushMarker::class.java)))
        val uids = listOf(uidA, uidB, target).associateWith { port.uid(it) }
        note("uids: ${uids.map { "${it.key.value}=${it.value}" }}")
        expect(uids[uidA] != null && uids[uidA] == uids[uidB] && uids[target] != uids[uidA], "uid-a and uid-b share one UID; launch-target has its own")
        val grantPort = DpmPermissionGrantPort(ctx, admin)
        dpm.setApplicationHidden(admin, uidA.value, false)
        dpm.setApplicationHidden(admin, uidB.value, true)
        dpm.setApplicationHidden(admin, target.value, true)
        dpm.setLockTaskPackages(admin, arrayOf(self.value, uidA.value))
        expect(!hidden(uidA) && permitted(uidA) && hidden(uidB) && !permitted(uidB), "setup: uid-a visible and allowed; uid-b hidden and not listed")
        val dispatcher = SerialDispatcher("pla219-uid")
        try {
            val effects = DeviceApprovalEffects(dispatcher, port, grantPort, PublishedHomeApps(), { setOf(self) }) { null }
            val b = ApprovalBinding(uidB, grantPort.signers(uidB) ?: fail("uid-b signers"))
            val opened = dispatcher.run { effects.openPermissionWindow(b) }
            note("window for uid-b: opened=$opened -> uid-b ${state(uidB)}; uid-a ${state(uidA)}")
            expect(!opened, "the window for uid-b is refused while shared-UID uid-a is allowed")
            expect(hidden(uidB), "uid-b was never unhidden")
            expect(permitted(uidA) && !hidden(uidA), "uid-a keeps its access")
            dispatcher.run { effects.closePermissionWindow(b) }
            val t = ApprovalBinding(target, grantPort.signers(target) ?: fail("launch-target signers"))
            val control = dispatcher.run { effects.openPermissionWindow(t) }
            note("control window for launch-target: opened=$control -> ${state(target)}")
            expect(control && !hidden(target) && !permitted(target), "control: launch-target (own UID) opens, outside lock task")
            expect(dispatcher.run { effects.closePermissionWindow(t) } && hidden(target), "control: closed and hidden")
        } finally {
            dispatcher.close()
        }
        // For the host's demonstration of why the gate exists: uid-b visible but not listed, uid-a listed.
        dpm.setApplicationHidden(admin, uidB.value, false)
        note("demo setup: lock task packages=${dpm.getLockTaskPackages(admin).sorted()} uid-b ${state(uidB)}")
        startLockTask()
    }

    fun pause(editing: Boolean) {
        expect(dpm.isDeviceOwnerApp(ctx.packageName), "this test APK is the Device Owner")
        val identity = ApprovalBinding(target, setOf(expectDigest()))
        dir.deleteRecursively()
        val store = JointAuthorityStore(dir, inventory)
        val vault = vault(store)
        expect(store.initialize(CredentialState(Mode.TRUSTED, mapOf(person to vault.prepareCredential(pin).credential)),
            WaitingState(storageGeneration = "live"), ElevationRecord(), AppApprovalState(removedApps = mapOf(target to identity))),
            "durable store initialized at ${dir.name} with launch-target as a removed (re-approvable) app")
        val w = wire(store, vault, sessionOk = true, battery = BatteryState(80, false), pauseOnOpen = !editing)
        expect(w.dispatcher.run { w.controller.onStart() } == AppApprovalOutcome.READY, "controller onStart READY")
        val ordinary = w.dispatcher.run { PolicyEngine.decide(w.input()) }
        expect(w.dispatcher.run { w.effects.applyPolicy(ordinary) }, "ordinary policy applied: lock task ${dpm.getLockTaskPackages(admin).sorted()}")
        expect(hidden(target) && !permitted(target), "target starts hidden and outside lock task")
        startLockTask()
        val manifest = manifest(identity)
        val request = w.dispatcher.run { w.controller.prepareReapproval(target, person, PermissionSelection(manifest, mapOf(camera to PermissionChoice.ALLOW))) }
            ?: fail("prepareReapproval")
        val auth = (vault.authenticate(person, pin, request.action) as PinResult.Authorized).authority
        val approved = w.dispatcher.run { w.controller.approve(request, auth) }
        if (!editing) fail("approval returned $approved without pausing")
        expect(approved == AppApprovalOutcome.APPROVED, "first approval completes: ${state()}")
        expect(store.approvals.read()?.pending == null, "no pending operation after the approval")
        w.pauseOnOpen = true
        val edit = w.dispatcher.run { w.controller.preparePermissionEdit(target, person, PermissionSelection(manifest, mapOf(camera to PermissionChoice.DENY))) }
            ?: fail("preparePermissionEdit")
        val editAuth = (vault.authenticate(person, pin, edit.action) as PinResult.Authorized).authority
        val edited = w.dispatcher.run { w.controller.editPermissions(edit, edit.approvalId, editAuth) }
        fail("edit returned $edited without pausing")
    }

    fun recover() {
        val sessionOk = args?.getString("session") != "fail"
        val battery = if (args?.getString("battery") == "low") BatteryState(3, false) else BatteryState(80, false)
        note("new process; before any controller call: target ${state()}")
        val store = JointAuthorityStore(dir, inventory)
        val pending = store.approvals.read()?.pending
        note("durable pending operation: ${pending?.phase} for ${pending?.record?.identity?.packageName?.value}")
        expect(pending?.phase in setOf(AppApprovalPhase.PREPARING, AppApprovalPhase.EDITING), "a durable PREPARING or EDITING record survived the kill")
        val w = wire(store, vault(store), sessionOk, battery, pauseOnOpen = false)
        event("controller.onStart (session=${if (sessionOk) "ok" else "fail"}, battery=${battery.percent}%)")
        val outcome = w.dispatcher.run { w.controller.onStart() }
        event("onStart returned $outcome; target ${state()}; Home lists target=${w.home.lists(target)}")
        w.dispatcher.close()
        val firstHide = events.indexOfFirst { it.startsWith("effect hideAndReadBack=true") }
        val firstGate = events.indexOfFirst { it.startsWith("session ") || it.startsWith("battery ") || it.startsWith("home publish") }
        expect(firstHide >= 0 && (firstGate < 0 || firstHide < firstGate), "the target is re-hidden (read back) before any session, battery or Home event")
        expect(hidden(target), "target hidden after recovery")
        val publishedTarget = events.any { it.startsWith("home publish") && target.value in it }
        expect(!publishedTarget || outcome == AppApprovalOutcome.APPROVED || outcome == AppApprovalOutcome.PERMISSIONS_CHANGED, "Home never lists the target unless the operation completed")
        note("recover outcome=$outcome")
    }

    fun cleanup() {
        val problems = mutableListOf<String>()
        fun step(what: String, ok: () -> Boolean) { runCatching(ok).fold({ if (!it) problems += what }, { problems += "$what threw $it" }) }
        step("clear lock task") { dpm.setLockTaskPackages(admin, emptyArray()); dpm.getLockTaskPackages(admin).isEmpty() }
        step("clear Home") { dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName); true }
        step("input methods") { dpm.setPermittedInputMethods(admin, null) }
        step("accessibility") { dpm.setPermittedAccessibilityServices(admin, null) }
        for (p in listOf(target, uidA, uidB)) step("unhide ${p.value}") { dpm.setApplicationHidden(admin, p.value, false); !hidden(p) }
        step("delete store") { dir.deleteRecursively() }
        if (problems.isNotEmpty()) fail("cleanup failed: $problems")
        note("ok: cleanup cleared lock task, Home, input-method policy and the store, and unhid the fixtures")
    }

    private inner class Wired(val dispatcher: SerialDispatcher, val effects: DeviceApprovalEffects, val home: HomeAppList,
        val controller: AppApprovalController, val input: () -> PolicyInput, var pauseOnOpen: Boolean)

    private fun wire(store: JointAuthorityStore, vault: PinVault, sessionOk: Boolean, battery: BatteryState, pauseOnOpen: Boolean): Wired {
        val identity = ApprovalBinding(target, setOf(expectDigest()))
        val dispatcher = SerialDispatcher("pla219-policy")
        val port: DevicePolicyPort = NoRestrictionsPort(DpmPort(ctx, admin, ComponentName(ctx, FlushMarker::class.java)))
        val home = LoggedHome(PublishedHomeApps())
        val input = { PolicyInput("check", "check", self, messages,
            listOf(InstalledPackage(self, false, 1), InstalledPackage(messages, true, 1),
                InstalledPackage(dialer, true, 1), InstalledPackage(target, false, 1)),
            listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)), inventory,
            approvalState = store.approvals.read(), installedIdentities = listOf(identity)) }
        val neverHide = { PolicyEngine.decide(input()).neverHide }
        val grantPort = DpmPermissionGrantPort(ctx, admin)
        val effects = DeviceApprovalEffects(dispatcher, port, grantPort, home, neverHide) { name -> manifest(identity).takeIf { name == target } }
        val sessions = SessionController(store.elevationStore, object : SessionEffects {
            override fun revokeAllRelaxations() = true.also { event("session revokeAllRelaxations") }
            override fun open(access: SessionAccess) = false
            override fun protectedStateCheck() = sessionOk.also { event("session protectedStateCheck=$it") }
            override fun readBackPolicy() = true.also { event("session readBackPolicy") }
        }, clock, inventory, vault) { store.credentials.read()?.mode }
        lateinit var wired: Wired
        val watched = object : AppApprovalEffects by effects {
            override fun hideAndReadBack(identity: ApprovalBinding) = effects.hideAndReadBack(identity).also { event("effect hideAndReadBack=$it -> ${state()}") }
            override fun openPermissionWindow(identity: ApprovalBinding): Boolean {
                val opened = effects.openPermissionWindow(identity)
                event("effect openPermissionWindow=$opened -> ${state()}")
                if (opened && wired.pauseOnOpen) holdOpen(store)
                return opened
            }
            override fun closePermissionWindow(identity: ApprovalBinding) = effects.closePermissionWindow(identity).also { event("effect closePermissionWindow=$it -> ${state()}") }
            override fun applyPolicy(plan: PolicyPlan) = effects.applyPolicy(plan).also { event("effect applyPolicy=$it -> ${state()}") }
        }
        val controller = AppApprovalController(store.approvals, { store.credentials.read() }, vault, sessions, clock, input,
            { name -> manifest(identity).takeIf { name == target } }, { battery.also { event("battery read ${it.percent}%") } },
            watched, AppApprovalIdSource { "approval-${SystemClock.elapsedRealtimeNanos()}" })
        wired = Wired(dispatcher, effects, home, controller, input, pauseOnOpen)
        return wired
    }

    /** Inside the window, right after the unhide: report, post the tap target, and wait to be killed. */
    private fun holdOpen(store: JointAuthorityStore) {
        val phase = store.approvals.read()?.pending?.phase
        note("inside the window: durable phase=$phase; target ${state()}; lock task packages=${dpm.getLockTaskPackages(admin).sorted()}; features=${dpm.getLockTaskFeatures(admin)}")
        expect(lockMode() == "LOCKED", "lock task is active (LOCK_TASK_MODE_LOCKED) while the window is open")
        postNotification()
        note("PAUSED phase=$phase")
        SystemClock.sleep(300_000)
        fail("not killed within 5 minutes")
    }

    private fun postNotification() {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("pla219", "PLA-219 check", NotificationManager.IMPORTANCE_HIGH))
        val open = Intent().setComponent(ComponentName(target.value, "android.app.Activity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val tap = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE)
        nm.notify(219, Notification.Builder(ctx, "pla219").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("PLA219 open target").setContentIntent(tap).build())
        note("notification posted: enabled=${nm.areNotificationsEnabled()} active=${nm.activeNotifications.map { it.id }}")
    }

    private fun startLockTask() {
        val activity = instrumentation.startActivitySync(Intent(ctx, CheckHome::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.runOnMainSync { activity.startLockTask() }
        SystemClock.sleep(1000)
        expect(lockMode() == "LOCKED", "CheckHome started lock task: getLockTaskModeState=LOCKED")
    }

    private val clock = RunningClock {
        val now = SystemClock.elapsedRealtime()
        ClockReading(RunningTime("pla219", now), BootTime(File("/proc/sys/kernel/random/boot_id").readText().trim(), now))
    }
    private var entropy = 0
    private fun vault(store: JointAuthorityStore) = PinVault(store.credentials, KeyDerivation { chars, salt, _ ->
        java.security.MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
    }, SecretEntropy { n -> ByteArray(n) { (++entropy + SystemClock.elapsedRealtimeNanos()).toByte() } }, clock)

    private fun manifest(identity: ApprovalBinding) = PermissionManifest(identity, 1, "check",
        listOf(RuntimePermission(camera, "Camera", emptyList(), PermissionGrantCapability.SUPPORTED)))

    private inner class LoggedHome(private val real: PublishedHomeApps) : HomeAppList by real {
        override fun publish(names: Set<PackageName>) {
            event("home publish ${names.map { it.value }.sorted()}")
            real.publish(names)
        }
    }

    /** Records restriction writes and reports them back; every other call is the real port. */
    private class NoRestrictionsPort(private val real: DevicePolicyPort) : DevicePolicyPort by real {
        val recorded = mutableSetOf<Restriction>()
        override fun restrictions(): Set<Restriction> = recorded.toSet()
        override fun addRestriction(restriction: Restriction) { recorded += restriction }
    }

    private fun expect(ok: Boolean, what: String) {
        if (!ok) fail(what)
        note("ok: $what")
    }
    private fun fail(what: String): Nothing = throw IllegalStateException(what)
    private fun note(line: String) = log("PLA219 $line")
}
