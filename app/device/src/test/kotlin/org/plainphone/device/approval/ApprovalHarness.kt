// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

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
import org.plainphone.device.FakeDevicePolicyPort
import org.plainphone.device.SerialDispatcher
import org.plainphone.device.hiddenApp
import org.plainphone.device.visible
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
import org.plainphone.policy.PermissionSelection
import org.plainphone.policy.PinResult
import org.plainphone.policy.PinVault
import org.plainphone.policy.PolicyEngine
import org.plainphone.policy.PolicyInput
import org.plainphone.policy.PolicyPlan
import org.plainphone.policy.RuntimePermission
import org.plainphone.policy.SecretEntropy
import org.plainphone.policy.SessionAccess
import org.plainphone.policy.SessionController
import org.plainphone.policy.SessionEffects

/**
 * The real [AppApprovalController] driving the real [DeviceApprovalEffects] over [FakeDevicePolicyPort],
 * everything on one [SerialDispatcher]. In-memory stores, trusted mode, the re-approval route (as in
 * `PermissionWindowCheck`). [target] starts hidden and outside lock task; [sibling] is installed and hidden.
 * [beforeApply] runs just before each [AppApprovalEffects.applyPolicy], to inject a failure there.
 */
class ApprovalHarness : AutoCloseable {
    val target = PackageName("test.target")
    val sibling = PackageName("test.sibling")
    val plainphone = PackageName("org.plainphone")
    val messages = PackageName("test.messages")
    val dialer = PackageName("test.dialer")
    private val signer = CertDigest("ab".repeat(32))
    val identity = ApprovalBinding(target, setOf(signer))
    val siblingIdentity = ApprovalBinding(sibling, setOf(signer))
    val camera = PermissionName("android.permission.CAMERA")
    private val person = TrustedPersonId("holder")
    private val pin get() = CharArray(6) { '1' }

    val port = FakeDevicePolicyPort(mapOf(plainphone.value to visible(), messages.value to visible(),
        dialer.value to visible(), target.value to hiddenApp(), sibling.value to hiddenApp()))
        .apply { lockTask = setOf(plainphone, messages, dialer) }
    val grants = HiddenRefusingGrantPort(port)
    val home = PublishedHomeApps()
    val dispatcher = SerialDispatcher("harness-policy")
    var battery = BatteryState(80, false)
    var beforeApply: (PolicyPlan) -> Unit = {}
    val applies = mutableListOf<Boolean>()

    private fun manifestOf(name: PackageName) = PermissionManifest(ApprovalBinding(name, setOf(signer)), 1, "build",
        listOf(RuntimePermission(camera, "Camera", emptyList(), PermissionGrantCapability.SUPPORTED)))
    private val inventory = BuildInventory("model", "build", 1, NeverHideList(setOf(messages)), setOf(messages), emptySet())
    var approvals = AppApprovalState(removedApps = mapOf(target to identity, sibling to siblingIdentity))
    private val input = { PolicyInput("model", "build", plainphone, messages,
        listOf(InstalledPackage(plainphone, false, 1), InstalledPackage(messages, true, 1), InstalledPackage(dialer, true, 1),
            InstalledPackage(target, false, 1), InstalledPackage(sibling, false, 1)),
        listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)), inventory,
        approvalState = approvals, installedIdentities = listOf(identity, siblingIdentity)) }
    val neverHide: () -> Set<PackageName>? = { PolicyEngine.decide(input()).neverHide }
    val effects = DeviceApprovalEffects(dispatcher, port, grants, home, neverHide) { name -> manifestOf(name).takeIf { name == target || name == sibling } }

    private var now = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
    private var credentials: CredentialState? = null
    private val clock = RunningClock { now }
    private var entropy = 0
    private val vault = PinVault(object : CredentialStore {
        override fun read() = credentials
        override fun compareAndSet(expected: CredentialState, replacement: CredentialState): Boolean {
            if (credentials !== expected) return false
            credentials = replacement; return true
        }
    }, KeyDerivation { chars, salt, _ ->
        java.security.MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
    }, SecretEntropy { n -> ByteArray(n) { (++entropy).toByte() } }, clock)
    private var combined = ElevationRecord()
    private val sessions = SessionController(object : ElevationStore() {
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
    private val store = object : AppApprovalStore {
        override fun read() = approvals
        override fun compareAndSet(expected: AppApprovalState, replacement: AppApprovalState, expectedCredentials: CredentialState?): Boolean {
            if (approvals !== expected) return false
            approvals = replacement; return true
        }
    }
    private val watched = object : AppApprovalEffects by effects {
        override fun applyPolicy(plan: PolicyPlan): Boolean {
            beforeApply(plan)
            return effects.applyPolicy(plan).also { applies += it }
        }
    }
    private var id = 0
    val controller = AppApprovalController(store, { credentials }, vault, sessions, clock, input,
        { name -> manifestOf(name).takeIf { name == target || name == sibling } }, { battery },
        watched, AppApprovalIdSource { "approval-${++id}" })

    init {
        credentials = CredentialState(Mode.TRUSTED, mapOf(person to vault.prepareCredential(pin).credential))
        check(dispatcher.run { controller.onStart() } == AppApprovalOutcome.READY)
    }

    /** One re-approval of [name] with CAMERA allowed, as one dispatcher task. */
    fun approve(name: PackageName = target): AppApprovalOutcome = dispatcher.run {
        val request = controller.prepareReapproval(name, person, PermissionSelection(manifestOf(name), mapOf(camera to PermissionChoice.ALLOW)))
            ?: error("prepareReapproval refused")
        val auth = (vault.authenticate(person, pin, request.action) as PinResult.Authorized).authority
        controller.approve(request, auth)
    }

    /** One permission edit of approved [name] to CAMERA denied, as one dispatcher task. */
    fun edit(name: PackageName = target): AppApprovalOutcome = dispatcher.run {
        val request = controller.preparePermissionEdit(name, person, PermissionSelection(manifestOf(name), mapOf(camera to PermissionChoice.DENY)))
            ?: error("preparePermissionEdit refused")
        val auth = (vault.authenticate(person, pin, request.action) as PinResult.Authorized).authority
        controller.editPermissions(request, request.approvalId, auth)
    }

    fun resume(): AppApprovalOutcome = dispatcher.run { controller.resume() }
    fun visibleAndPermitted(name: PackageName = target) = !port.hidden(name.value) && name in port.lockTask
    fun state(name: PackageName = target) = "hidden=${port.hidden(name.value)} permitted=${name in port.lockTask} home=${home.lists(name)}"

    override fun close() = dispatcher.close()
}

/** API 35: grant writes are refused while the app is hidden or suspended; reads work either way. */
class HiddenRefusingGrantPort(private val port: FakeDevicePolicyPort) : PermissionGrantPort {
    var signers: Set<CertDigest>? = setOf(CertDigest("ab".repeat(32)))
    private val policy = mutableMapOf<Pair<PackageName, PermissionName>, PolicyGrant>()
    private val blocked = mutableSetOf<PackageName>()
    override fun signers(name: PackageName) = signers
    override fun policyState(name: PackageName, permission: PermissionName) = policy[name to permission] ?: PolicyGrant.DEFAULT
    override fun setPolicyState(name: PackageName, permission: PermissionName, state: PolicyGrant): Boolean {
        val now = port.state[name] ?: return false
        if (now.hidden || now.suspended) return false
        policy[name to permission] = state
        return true
    }
    override fun granted(name: PackageName, permission: PermissionName) = policy[name to permission] == PolicyGrant.GRANTED
    override fun uninstallBlocked(name: PackageName) = name in blocked
    override fun setUninstallBlocked(name: PackageName, blocked: Boolean): Boolean {
        if (blocked) this.blocked += name else this.blocked -= name
        return true
    }
}
