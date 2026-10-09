// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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
import org.plainphone.policy.AppApprovalController
import org.plainphone.policy.AppApprovalEffects
import org.plainphone.policy.AppApprovalIdSource
import org.plainphone.policy.AppApprovalOutcome
import org.plainphone.policy.AppApprovalPhase
import org.plainphone.policy.ApprovalBinding
import org.plainphone.policy.BatteryState
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationController
import org.plainphone.policy.ElevationLimits
import org.plainphone.policy.ElevationOutcome
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.ElevationTarget
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
import org.plainphone.policy.SecretEntropy
import org.plainphone.policy.SessionAccess
import org.plainphone.policy.SessionController
import org.plainphone.policy.SessionEffects
import org.plainphone.policy.SessionJournal
import org.plainphone.policy.WaitingState

/**
 * The real elevation, session and approval controllers over the real file store. Android effects
 * are fakes here; the hidden-package permission writes and their readbacks need a device check.
 */
class ElevatedApprovalOnDiskTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dir: File by lazy { tmp.newFolder("authority") }
    private val person = TrustedPersonId("self")
    private val home = PackageName("test.home")
    private val sms = PackageName("test.sms")
    private val dialer = PackageName("test.dialer")
    private val play = PackageName("com.android.vending")
    private val a = ApprovalBinding(PackageName("test.a"), setOf(CertDigest("ab".repeat(32))))
    private val b = ApprovalBinding(PackageName("test.b"), setOf(CertDigest("cd".repeat(32))))
    private val camera = PermissionName("android.permission.CAMERA")
    private var now = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
    private val clock = RunningClock { now }
    private val inventory = BuildInventory("model", "build", 1, NeverHideList(setOf(sms, play)), setOf(sms), setOf(PackageName("test.settings")))
    private var faults = StorageFaults { _, _ -> }
    private lateinit var store: JointAuthorityStore
    private fun open() = JointAuthorityStore(dir, { point, d -> faults.at(point, d) }, inventory)

    private val pin get() = CharArray(6) { '1' }
    private var entropy = 0
    private fun vault() = PinVault(store.credentials, KeyDerivation { chars, salt, _ ->
        java.security.MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
    }, SecretEntropy { n -> ByteArray(n) { (++entropy).toByte() } }, clock)
    private lateinit var vault: PinVault

    private val events = mutableListOf<String>()
    private var crash: String? = null
    private fun event(name: String): Boolean {
        events += name
        if (crash == name) throw SimulatedCrash()
        return true
    }
    private val policy = PolicyInput("model", "build", home, sms,
        listOf(home, sms, dialer, play).map { InstalledPackage(it, true, 1) } +
            listOf(a, b).map { InstalledPackage(it.packageName, false, 1) },
        listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)), inventory,
        installedIdentities = listOf(a, b))
    private fun input() = policy.copy(approvalState = store.approvals.read())
    private val sessionEffects = object : SessionEffects {
        override fun revokeAllRelaxations() = event("revoke")
        override fun open(access: SessionAccess) = event("open")
        override fun protectedStateCheck() = true
        override fun readBackPolicy() = true
    }
    private fun manifest(identity: ApprovalBinding) = PermissionManifest(identity, 1, "build", listOf(
        RuntimePermissionFor(camera)))
    private val effects = object : AppApprovalEffects {
        override fun hideAndReadBack(identity: ApprovalBinding) = event("hide")
        override fun setPermission(identity: ApprovalBinding, write: PermissionWrite) = event("permission")
        override fun readPermissions(identity: ApprovalBinding): Map<PermissionName, PermissionReadback> =
            mapOf(camera to PermissionReadback.GRANTED).also { event("permissions-readback") }
        override fun applyPolicy(plan: PolicyPlan) = event("apply")
        override fun readBackPolicy(plan: PolicyPlan) = event("policy-readback")
    }
    private var id = 0
    private val ids = AppApprovalIdSource { "id-${++id}" }
    private lateinit var elevations: ElevationController
    private lateinit var approvals: AppApprovalController

    /** One process: a fresh store object, vault and controllers, started in the documented order. */
    private fun boot() {
        store = open(); vault = vault()
        elevations = ElevationController(store.elevationStore, { store.credentials.read() }, vault, clock,
            { ElevationLimits() }, ::input, { BatteryState(50, false) }, sessionEffects, ids, store.approvals)
        val sessions = SessionController(store.elevationStore, sessionEffects, clock, inventory, vault) { store.credentials.read()?.mode }
        approvals = AppApprovalController(store.approvals, { store.credentials.read() }, vault, sessions, clock,
            ::input, { name -> listOf(a, b).firstOrNull { it.packageName == name }?.let(::manifest) },
            { BatteryState(50, false) }, effects, ids, elevations)
        assertEquals(ElevationOutcome.IDLE, elevations.onStart())
        assertTrue(approvals.onStart() in setOf(AppApprovalOutcome.READY, AppApprovalOutcome.APPROVED))
    }

    private fun enroll() {
        store = open(); vault = vault()
        val credential = vault.prepareCredential(pin).credential
        assertTrue(store.initialize(CredentialState(Mode.SELF_MANAGED, mapOf(person to credential)),
            WaitingState(storageGeneration = "live"), ElevationRecord()))
        boot()
    }

    private fun grant(target: ElevationTarget): String {
        val request = checkNotNull(elevations.prepare(person, target))
        val auth = (vault.authenticate(person, pin, request.action) as PinResult.Authorized).authority
        assertEquals(ElevationOutcome.COUNTING, elevations.request(request, auth))
        advance(600_000)
        assertEquals(ElevationOutcome.STARTED, elevations.start(request.id, target))
        if (target == ElevationTarget.Play) {
            assertTrue(elevations.installed(request.id, a.packageName))
            assertTrue(elevations.installed(request.id, b.packageName))
        }
        events.clear()
        return request.id
    }
    private fun advance(ms: Long) {
        now = ClockReading(now.proven.copy(totalMillis = now.proven.totalMillis + ms), now.boot.copy(elapsedMillis = now.boot.elapsedMillis + ms))
    }
    private fun request(identity: ApprovalBinding) = checkNotNull(approvals.prepareElevated(identity.packageName,
        PermissionSelection(manifest(identity), mapOf(camera to PermissionChoice.ALLOW))))

    @Test fun playGrantApprovesTwoObservedAppsDurablyWithOneDebit() {
        enroll()
        val grantId = grant(ElevationTarget.Play)
        val deadline = store.elevationStore.read()!!.state.grant!!.deadline
        val first = request(a); val second = request(b)
        assertEquals(AppApprovalOutcome.APPROVED, approvals.approveElevated(first))
        assertEquals(AppApprovalOutcome.APPROVED, approvals.approveElevated(second))
        assertEquals(AppApprovalOutcome.REFUSED, approvals.approveElevated(first))
        val disk = open()
        val elevation = disk.elevationStore.read()!!
        assertEquals(grantId, elevation.state.grant!!.id)
        assertEquals(deadline, elevation.state.grant!!.deadline)
        assertEquals(setOf(a, b), elevation.state.grant!!.approved)
        assertEquals(SessionJournal.Empty, elevation.journal)
        assertEquals(1, elevation.state.ledger.debits.size)
        assertEquals(setOf(a.packageName, b.packageName), disk.approvals.read()!!.apps.keys)
        assertEquals(2, disk.approvals.read()!!.receipts.size)
        assertTrue(events.indexOf("revoke") < events.indexOf("permission"))
    }

    @Test fun approveGrantIsExactAndSingleUseAcrossReopen() {
        enroll()
        grant(ElevationTarget.Approve(a))
        val first = request(a); val stale = request(a)
        assertEquals(AppApprovalOutcome.APPROVED, approvals.approveElevated(first))
        assertNull(open().elevationStore.read()!!.state.grant)
        assertEquals(1, open().elevationStore.read()!!.state.ledger.debits.size)
        assertEquals(AppApprovalOutcome.REFUSED, approvals.approveElevated(stale))
        boot()
        assertNull(approvals.prepareElevated(b.packageName, PermissionSelection(manifest(b), mapOf(camera to PermissionChoice.ALLOW))))
        assertEquals(setOf(a.packageName), store.approvals.read()!!.apps.keys)
    }

    @Test fun restartRevokesTheGrantFirstButKeepsTheDebitAndApprovedApps() {
        enroll()
        grant(ElevationTarget.Play)
        assertEquals(AppApprovalOutcome.APPROVED, approvals.approveElevated(request(a)))
        val debits = store.elevationStore.read()!!.state.ledger.debits
        events.clear()
        boot()
        assertEquals("revoke", events.first())
        val after = store.elevationStore.read()!!
        assertNull(after.state.grant)
        assertEquals(debits, after.state.ledger.debits)
        assertEquals(setOf(a.packageName), store.approvals.read()!!.apps.keys)
        assertNull(approvals.prepareElevated(b.packageName, PermissionSelection(manifest(b), mapOf(camera to PermissionChoice.ALLOW))))
    }

    /** Crash at every write point of every transaction the approval makes, then recover. */
    @Test fun crashAnywhereNeverLeavesConsumptionWithoutApprovalOrTheReverse() {
        var transactions = 0
        var crashAt: Pair<Int, WritePoint>? = null
        faults = StorageFaults { at, _ ->
            if (at == WritePoint.BEFORE_COPY_A) transactions++
            if (crashAt == transactions to at) throw SimulatedCrash()
        }
        enroll(); grant(ElevationTarget.Approve(a)); val r = request(a)
        val first = transactions + 1
        assertEquals(AppApprovalOutcome.APPROVED, approvals.approveElevated(r))
        val last = transactions
        assertTrue(last > first)
        var sawBothSides = setOf<Boolean>()
        for (k in first..last) for (point in WritePoint.entries) {
            dir.deleteRecursively(); id = 0; transactions = 0; crashAt = null
            enroll(); grant(ElevationTarget.Approve(a)); val again = request(a)
            crashAt = transactions + (k - first + 1) to point
            try { approvals.approveElevated(again) } catch (_: SimulatedCrash) { }
            crashAt = null
            val disk = open()
            val approval = disk.approvals.read()!!
            val consumed = disk.elevationStore.read()!!.state.grant == null
            val committed = a.packageName in approval.apps && approval.pending?.phase != AppApprovalPhase.PREPARING
            assertEquals("transaction $k at $point", consumed, committed)
            sawBothSides = sawBothSides + consumed
            boot()
            // Recovery never consumes again, never restores the grant and never adds a debit.
            assertNull(store.elevationStore.read()!!.state.grant)
            assertEquals(1, store.elevationStore.read()!!.state.ledger.debits.size)
            assertEquals(committed, a.packageName in store.approvals.read()!!.apps)
        }
        assertEquals(setOf(true, false), sawBothSides)
    }
}

private fun RuntimePermissionFor(name: PermissionName) =
    org.plainphone.policy.RuntimePermission(name, "Camera", listOf("Taking photos"), PermissionGrantCapability.SUPPORTED)
