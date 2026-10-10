// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.apply

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import java.util.concurrent.CopyOnWriteArrayList
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
import org.plainphone.device.approval.PackageAddedHandler
import org.plainphone.device.approval.PublishedHomeApps
import org.plainphone.policy.AppApprovalController
import org.plainphone.policy.AppApprovalEffects
import org.plainphone.policy.AppApprovalIdSource
import org.plainphone.policy.AppApprovalOutcome
import org.plainphone.policy.AppApprovalState
import org.plainphone.policy.AppApprovalStore
import org.plainphone.policy.ApprovalBinding
import org.plainphone.policy.BatteryState
import org.plainphone.policy.CredentialState
import org.plainphone.policy.CredentialStore
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.ElevationStore
import org.plainphone.policy.FloorIdentity
import org.plainphone.policy.FloorRole
import org.plainphone.policy.IdentitySource
import org.plainphone.policy.InstalledPackage
import org.plainphone.policy.KeyDerivation
import org.plainphone.policy.PermissionChoice
import org.plainphone.policy.PermissionGrantCapability
import org.plainphone.policy.PermissionManifest
import org.plainphone.policy.PermissionName
import org.plainphone.policy.PermissionReadback
import org.plainphone.policy.PermissionSelection
import org.plainphone.policy.PermissionWrite
import org.plainphone.policy.PinResult
import org.plainphone.policy.PinVault
import org.plainphone.policy.PolicyInput
import org.plainphone.policy.PolicyPlan
import org.plainphone.policy.Restriction
import org.plainphone.policy.SecretEntropy
import org.plainphone.policy.SessionAccess
import org.plainphone.policy.SessionController
import org.plainphone.policy.SessionEffects

/**
 * On-device check of the approval permission window (PLA-186), API 35, with this test APK as a
 * testOnly Device Owner and `check-fixtures/permission-target` installed. `-e phase window -e expect <sha256>`.
 *
 * The real [AppApprovalController] (in-memory stores, trusted mode, re-approval route) drives the real
 * [DeviceApprovalEffects] over [DpmPort] and [DpmPermissionGrantPort], everything on one
 * [SerialDispatcher], with the real [PackageAddedHandler] registered. One approval with mixed DENY and
 * ALLOW, then one edit of that approved app. Inside each window, before every grant write, the check
 * reads Android directly: target visible, not in lock task, not on Home. It logs every package
 * broadcast for the target with the step it arrived in.
 *
 * Test-build deviation: PolicyEngine plans carry every user restriction, and
 * DISALLOW_DEBUGGING_FEATURES would cut adb. The port below records restriction writes instead of
 * applying them; every other DPM call is real.
 */
class PermissionWindowCheck(private val ctx: Context, private val args: Bundle?, private val log: (String) -> Unit) {
    private val target = PackageName("org.plainphone.permtarget")
    private val camera = PermissionName("android.permission.CAMERA")
    private val mic = PermissionName("android.permission.RECORD_AUDIO")
    private val contacts = PermissionName("android.permission.READ_CONTACTS")
    private val all = listOf(camera, mic, contacts)
    private val messages = PackageName("com.google.android.apps.messaging")
    private val dialer = PackageName("com.google.android.dialer")
    private val admin by lazy { ComponentName(ctx, CheckAdmin::class.java) }
    private val dpm by lazy { ctx.getSystemService(DevicePolicyManager::class.java) }
    private val person = TrustedPersonId("holder")
    private val pin get() = CharArray(6) { '1' }

    @Volatile private var step = "setup"
    private val broadcasts = CopyOnWriteArrayList<String>()
    private val handled = CopyOnWriteArrayList<String>()
    private fun wall() = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())

    fun check() {
        val outcome = runCatching { run() }
        val problems = HiddenPermissionCheck.cleanup(ctx, target, all) + resetDevice()
        problems.forEach { note("cleanup failed: $it") }
        outcome.getOrThrow()
        if (problems.isNotEmpty()) fail("cleanup failed: ${problems.joinToString()}")
        note("ok: cleanup unhid, unblocked, reset permissions, cleared lock task, Home and input-method policy")
    }

    private fun run() {
        val expect = CertDigest.parse(args?.getString("expect") ?: fail("missing -e expect <sha256>"))
        expect(dpm.isDeviceOwnerApp(ctx.packageName), "this test APK is the Device Owner")
        val identity = ApprovalBinding(target, setOf(expect))
        @Suppress("DEPRECATION")
        val version = ctx.packageManager.getPackageInfo(target.value, PackageManager.MATCH_UNINSTALLED_PACKAGES).longVersionCode
        val grantPort = DpmPermissionGrantPort(ctx, admin)
        expect(grantPort.signers(target) == setOf(expect), "live signer equals apksigner's digest")
        val manifest = PermissionManifest(identity, version, "check", listOf(
            RuntimePermission(camera, "Camera"), RuntimePermission(mic, "Microphone"), RuntimePermission(contacts, "Contacts")))

        val dispatcher = SerialDispatcher("pla186-policy")
        val port = NoRestrictionsPort(DpmPort(ctx, admin, ComponentName(ctx, FlushMarker::class.java)))
        val home = PublishedHomeApps()
        val inventory = BuildInventory("check", "check", 1, NeverHideList(setOf(messages)), setOf(messages), emptySet())
        var approvals = AppApprovalState(removedApps = mapOf(target to identity))
        val input = { PolicyInput("check", "check", PackageName(ctx.packageName), messages,
            listOf(InstalledPackage(PackageName(ctx.packageName), false, 1), InstalledPackage(messages, true, 1),
                InstalledPackage(dialer, true, 1), InstalledPackage(target, false, version)),
            listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)), inventory,
            approvalState = approvals, installedIdentities = listOf(identity)) }
        val neverHide = { org.plainphone.policy.PolicyEngine.decide(input()).neverHide }
        val effects = DeviceApprovalEffects(dispatcher, port, grantPort, home, neverHide) { name -> manifest.takeIf { name == target } }
        val handler = PackageAddedHandler(dispatcher, port, { name ->
            (name in approvals.approvedPackages(input().installed, input().installedIdentities)).also {
                handled += step
                note("handler: ACTION_PACKAGE_ADDED ${name.value} handled on the dispatcher during [$step] at ${wall()}, mayShow=$it")
            }
        }, neverHide)
        val receivers = listOf(handler.register(ctx), listen())

        try {
            // The device starts as an approved-elsewhere state: target hidden, outside lock task and Home.
            expect(dispatcher.run { effects.hideAndReadBack(identity) }, "hideAndReadBack: target starts hidden")
            expect(dispatcher.run { effects.readPermissions(identity) } == all.associateWith { PermissionReadback.DEFAULT }, "hidden read: all DEFAULT")

            var now = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
            var credentials: CredentialState? = null
            val credentialStore = object : CredentialStore {
                override fun read() = credentials
                override fun compareAndSet(expected: CredentialState, replacement: CredentialState): Boolean {
                    if (credentials !== expected) return false
                    credentials = replacement; return true
                }
            }
            val clock = RunningClock { now }
            var entropy = 0
            val vault = PinVault(credentialStore, KeyDerivation { chars, salt, _ ->
                java.security.MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
            }, SecretEntropy { n -> ByteArray(n) { (++entropy).toByte() } }, clock)
            credentials = CredentialState(Mode.TRUSTED, mapOf(person to vault.prepareCredential(pin).credential))
            var combined = ElevationRecord()
            val sessions = SessionController(object : ElevationStore() {
                override fun read() = combined
                override fun compareAndSet(expected: ElevationRecord?, replacement: ElevationRecord, expectedCredentials: CredentialState?): Boolean {
                    if (combined !== expected) return false
                    combined = replacement; return true
                }
            }, object : SessionEffects {
                override fun revokeAllRelaxations() = true
                override fun open(access: SessionAccess) = false
                override fun protectedStateCheck() = true
                override fun readBackPolicy() = true
            }, clock, inventory, vault) { credentials?.mode }
            val store = object : AppApprovalStore {
                override fun read() = approvals
                override fun compareAndSet(expected: AppApprovalState, replacement: AppApprovalState, expectedCredentials: CredentialState?): Boolean {
                    if (approvals !== expected) return false
                    note("store: phase ${expected.pending?.phase ?: "none"} -> ${replacement.pending?.phase ?: "none"}")
                    approvals = replacement; return true
                }
            }
            var id = 0
            val controller = AppApprovalController(store, { credentials }, vault, sessions, clock, input,
                { name -> manifest.takeIf { name == target } }, { BatteryState(80, false) },
                Watched(effects, identity, port, home), AppApprovalIdSource { "approval-${++id}" })
            expect(dispatcher.run { controller.onStart() } == AppApprovalOutcome.READY, "controller onStart READY")

            step = "approval"
            val approve = mapOf(camera to PermissionChoice.ALLOW, mic to PermissionChoice.DENY, contacts to PermissionChoice.DENY)
            val request = dispatcher.run { controller.prepareReapproval(target, person, PermissionSelection(manifest, approve)) } ?: fail("prepareReapproval")
            val auth = (vault.authenticate(person, pin, request.action) as PinResult.Authorized).authority
            val approved = dispatcher.run { controller.approve(request, auth) }
            settle(dispatcher)
            expect(approved == AppApprovalOutcome.APPROVED, "approval outcome APPROVED (was $approved)")
            after(identity, approve, port, home, grantPort)

            step = "edit"
            val edit = mapOf(camera to PermissionChoice.DENY, mic to PermissionChoice.ALLOW, contacts to PermissionChoice.DENY)
            val editRequest = dispatcher.run { controller.preparePermissionEdit(target, person, PermissionSelection(manifest, edit)) } ?: fail("preparePermissionEdit")
            val editAuth = (vault.authenticate(person, pin, editRequest.action) as PinResult.Authorized).authority
            val edited = dispatcher.run { controller.editPermissions(editRequest, editRequest.approvalId, editAuth) }
            settle(dispatcher)
            expect(edited == AppApprovalOutcome.PERMISSIONS_CHANGED, "edit outcome PERMISSIONS_CHANGED (was $edited)")
            after(identity, edit, port, home, grantPort)
            note("restriction writes recorded, not applied (test build): ${port.recorded.map { it.name }.sorted()}")
        } finally {
            step = "cleanup"
            receivers.forEach { runCatching { ctx.unregisterReceiver(it) } }
            dispatcher.close()
        }
        broadcasts.forEach { note("broadcast received: $it") }
        expect(broadcasts.count { it.startsWith("android.intent.action.PACKAGE_ADDED") } >= 4,
            "ACTION_PACKAGE_ADDED received for each unhide (two windows, two committed applies)")
        expect(handled.isNotEmpty() && handled.none { "window open" in it }, "the package-added handler never ran inside a window: $handled")
    }

    /** After a completed approval or edit: visible, in lock task, on Home, choices read back exactly. */
    private fun after(identity: ApprovalBinding, choices: Map<PermissionName, PermissionChoice>, port: DevicePolicyPort,
        home: PublishedHomeApps, grantPort: DpmPermissionGrantPort) {
        val want = choices.mapValues { if (it.value == PermissionChoice.ALLOW) PermissionReadback.GRANTED else PermissionReadback.DENIED }
        expect(!dpm.isApplicationHidden(admin, target.value), "[$step] committed: target visible")
        expect(target in dpm.getLockTaskPackages(admin).map(::PackageName), "[$step] committed: target back in lock task")
        expect(home.lists(target), "[$step] committed: target back on Home")
        expect(org.plainphone.device.approval.PermissionGrants(grantPort).read(identity, all) == want, "[$step] committed readback $want")
        expect(!dpm.isUninstallBlocked(admin, target.value), "[$step] no uninstall block left")
        expect(port.packageState(target)?.suspended == false, "[$step] never suspended")
    }

    /** Logs each effect call with its result, and checks Android directly inside the window. */
    private inner class Watched(private val e: DeviceApprovalEffects, private val identity: ApprovalBinding,
        private val port: DevicePolicyPort, private val home: PublishedHomeApps) : AppApprovalEffects {
        private fun androidState() = "hidden=${dpm.isApplicationHidden(admin, target.value)} lockTask=${target.value in dpm.getLockTaskPackages(admin)} home=${home.lists(target)}"
        override fun hideAndReadBack(identity: ApprovalBinding) = e.hideAndReadBack(identity).also { note("effect hideAndReadBack=$it -> ${androidState()} at ${wall()}") }
        override fun openPermissionWindow(identity: ApprovalBinding): Boolean {
            step = step.substringBefore(":") + ": window open"
            note("window [$step] opening at ${wall()}")
            return e.openPermissionWindow(identity).also { note("effect openPermissionWindow=$it -> ${androidState()}") }
        }
        override fun closePermissionWindow(identity: ApprovalBinding): Boolean = e.closePermissionWindow(identity).also {
            note("effect closePermissionWindow=$it -> ${androidState()} at ${wall()}")
            step = step.substringBefore(":") + ": after window"
        }
        override fun setPermission(identity: ApprovalBinding, write: PermissionWrite): Boolean {
            expect(!dpm.isApplicationHidden(admin, target.value) && target.value !in dpm.getLockTaskPackages(admin) && home.lists(target) == false,
                "[$step] before ${write.choice} ${write.permission.value}: visible, outside lock task and Home")
            return e.setPermission(identity, write).also { note("effect setPermission ${write.choice} ${write.permission.value}=$it") }
        }
        override fun readPermissions(identity: ApprovalBinding) = e.readPermissions(identity).also {
            note("effect readPermissions (hidden=${dpm.isApplicationHidden(admin, target.value)}) = $it")
        }
        override fun applyPolicy(plan: PolicyPlan) = e.applyPolicy(plan).also { note("effect applyPolicy=$it -> ${androidState()} at ${wall()}") }
        override fun readBackPolicy(plan: PolicyPlan) = e.readBackPolicy(plan).also { note("effect readBackPolicy=$it") }
    }

    /** Every package broadcast for the target, on its own thread so delivery does not wait for the dispatcher. */
    private fun listen(): BroadcastReceiver {
        val thread = HandlerThread("pla186-broadcasts").apply { start() }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.data?.schemeSpecificPart != target.value) return
                val extras = intent.extras?.keySet()?.sorted()?.filter { it.startsWith("android.intent.extra.") && it != "android.intent.extra.UID" }
                    ?.joinToString { "${it.removePrefix("android.intent.extra.")}=${intent.extras?.get(it)}" }
                broadcasts += "${intent.action} at ${wall()} during [$step] $extras"
            }
        }
        val filter = IntentFilter().apply {
            listOf(Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REMOVED, Intent.ACTION_PACKAGE_CHANGED,
                Intent.ACTION_PACKAGE_REPLACED, Intent.ACTION_PACKAGE_FULLY_REMOVED, Intent.ACTION_PACKAGE_RESTARTED,
                Intent.ACTION_PACKAGE_DATA_CLEARED).forEach(::addAction)
            addDataScheme("package")
        }
        ctx.registerReceiver(receiver, filter, null, Handler(thread.looper), Context.RECEIVER_NOT_EXPORTED)
        return receiver
    }

    /** Lets queued broadcasts reach the dispatcher, then drains it. */
    private fun settle(dispatcher: SerialDispatcher) {
        SystemClock.sleep(1500)
        dispatcher.run { }
    }

    private fun resetDevice(): List<String> {
        val problems = mutableListOf<String>()
        fun step(what: String, ok: () -> Boolean) { runCatching(ok).fold({ if (!it) problems += what }, { problems += "$what threw $it" }) }
        step("clear lock task") { dpm.setLockTaskPackages(admin, emptyArray()); dpm.getLockTaskPackages(admin).isEmpty() }
        step("clear Home") { dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName); true }
        step("input methods") { dpm.setPermittedInputMethods(admin, null) }
        step("accessibility") { dpm.setPermittedAccessibilityServices(admin, null) }
        return problems
    }

    /** Records restriction writes and reports them back; every other call is the real port. */
    private class NoRestrictionsPort(private val real: DevicePolicyPort) : DevicePolicyPort by real {
        val recorded = mutableSetOf<Restriction>()
        override fun restrictions(): Set<Restriction> = recorded.toSet()
        override fun addRestriction(restriction: Restriction) { recorded += restriction }
    }

    @Suppress("FunctionName")
    private fun RuntimePermission(name: PermissionName, label: String) =
        org.plainphone.policy.RuntimePermission(name, label, emptyList(), PermissionGrantCapability.SUPPORTED)

    private fun expect(ok: Boolean, what: String) {
        if (!ok) fail(what)
        note("ok: $what")
    }
    private fun fail(what: String): Nothing = throw IllegalStateException(what)
    private fun note(line: String) = log("PLA186 $line")
}
