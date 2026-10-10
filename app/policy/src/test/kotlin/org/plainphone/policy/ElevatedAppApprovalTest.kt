// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class ElevatedAppApprovalTest {
    private val person = TrustedPersonId("self")
    private val home = PackageName("test.home")
    private val sms = PackageName("test.sms")
    private val dialer = PackageName("test.dialer")
    private val a = ApprovalBinding(PackageName("test.a"), setOf(CertDigest("ab".repeat(32))))
    private val b = ApprovalBinding(PackageName("test.b"), setOf(CertDigest("cd".repeat(32))))
    private val camera = PermissionName("android.permission.CAMERA")
    private var now = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
    private val clock = RunningClock { now }
    private var credentials: CredentialState? = null
    private val credentialStore = object : CredentialStore {
        override fun read() = credentials
        override fun compareAndSet(expected: CredentialState, replacement: CredentialState): Boolean {
            if (credentials !== expected) return false
            credentials = replacement; return true
        }
    }
    private val pin get() = CharArray(6) { '1' }
    private var entropy = 0
    private val vault = PinVault(credentialStore, KeyDerivation { chars, salt, _ ->
        java.security.MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
    }, SecretEntropy { n -> ByteArray(n) { (++entropy).toByte() } }, clock)
    private var elevation = ElevationRecord()
    private var approvals = AppApprovalState()
    private var hook: ((String) -> Unit)? = null
    private var failure: String? = null
    private var crash: String? = null
    private val events = mutableListOf<String>()
    private fun event(name: String): Boolean {
        events += name; hook?.invoke(name)
        if (crash == name) error("process death")
        return failure != name
    }
    private val elevationStore = object : ElevationStore() {
        override fun read() = elevation
        override fun compareAndSet(expected: ElevationRecord?, replacement: ElevationRecord, expectedCredentials: CredentialState?): Boolean {
            if (elevation !== expected || (expectedCredentials != null && credentials !== expectedCredentials)) return false
            elevation = replacement; return true
        }
    }
    private val store = object : ElevationApprovalStore {
        override val elevationStore: ElevationStore get() = this@ElevatedAppApprovalTest.elevationStore
        override fun read() = approvals
        override fun compareAndSet(expected: AppApprovalState, replacement: AppApprovalState, expectedCredentials: CredentialState?): Boolean {
            if (approvals !== expected || (expectedCredentials != null && credentials !== expectedCredentials)) return false
            approvals = replacement; event("after-${replacement.pending?.phase}"); return true
        }
        override fun commitElevationApproval(expected: AppApprovalState, replacement: AppApprovalState,
            expectedElevation: ElevationRecord, replacementElevation: ElevationRecord,
            expectedCredentials: CredentialState, finalCheck: () -> Boolean): Boolean {
            assertTrue(replacement.pending!!.record.identity.packageName in hidden, "elevation commit must run hidden")
            if (!event("before-atomic") || !finalCheck()) return false
            if (approvals !== expected || elevation !== expectedElevation || credentials !== expectedCredentials) return false
            // A single durable replacement, simulated without a crash point between the fields.
            approvals = replacement; elevation = replacementElevation
            event("after-atomic"); return true
        }
    }
    private val inventory = BuildInventory("model", "build", 1, NeverHideList(setOf(sms, PLAY_PACKAGE)), setOf(sms), setOf(PackageName("test.settings")))
    private var policy = PolicyInput("model", "build", home, sms,
        listOf(home, sms, dialer, PLAY_PACKAGE).map { InstalledPackage(it, true, 1) } +
            listOf(a, b).map { InstalledPackage(it.packageName, false, 1) },
        listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)), inventory,
        installedIdentities = listOf(a, b))
    private fun input() = policy.copy(approvalState = approvals)
    private var battery = BatteryState(50, false)
    private var physicallyOpen = false
    private val sessionEffects = object : SessionEffects {
        override fun revokeAllRelaxations(): Boolean {
            if (!event("revoke")) return false
            physicallyOpen = false; return true
        }
        override fun open(access: SessionAccess): Boolean { physicallyOpen = true; return true }
        override fun protectedStateCheck() = event("protected")
        override fun readBackPolicy() = event("session-readback")
    }
    private fun manifest(identity: ApprovalBinding) = PermissionManifest(identity, 1, "build", listOf(
        RuntimePermission(camera, "Camera", listOf("Taking photos"), PermissionGrantCapability.SUPPORTED)))
    private var manifests = listOf(a, b).associate { it.packageName to manifest(it) }
    private val hidden = mutableSetOf(a.packageName, b.packageName)
    private val effects = object : AppApprovalEffects {
        override fun hideAndReadBack(identity: ApprovalBinding): Boolean {
            if (!event("hide")) return false
            hidden += identity.packageName; return true
        }
        override fun openPermissionWindow(identity: ApprovalBinding): Boolean {
            assertFalse(identity.packageName in approvals.approvedPackages(policy.installed, policy.installedIdentities))
            hidden -= identity.packageName
            return event("window-open")
        }
        override fun closePermissionWindow(identity: ApprovalBinding): Boolean {
            hidden += identity.packageName
            return event("window-close")
        }
        override fun setPermission(identity: ApprovalBinding, write: PermissionWrite) =
            identity.packageName !in hidden && event("permission")
        override fun readPermissions(identity: ApprovalBinding): Map<PermissionName, PermissionReadback> =
            if (event("permissions-readback")) mapOf(camera to PermissionReadback.GRANTED) else emptyMap()
        override fun applyPolicy(plan: PolicyPlan) = event("apply")
        override fun readBackPolicy(plan: PolicyPlan) = event("policy-readback")
        override fun containFailedActivation(packages: Set<PackageName>) = event("contain")
    }
    private var id = 0
    private val ids = AppApprovalIdSource { "id-${++id}" }
    private fun elevations() = ElevationController(elevationStore, { credentials }, vault, clock,
        { ElevationLimits() }, ::input, { battery }, sessionEffects, ids, store)
    private var elevations = elevations()
    private val sessions = SessionController(elevationStore, sessionEffects, clock, inventory, vault) { credentials?.mode }
    private fun controller() = AppApprovalController(store, { credentials }, vault, sessions, clock,
        ::input, { manifests[it] }, { battery }, effects, ids, elevations)
    private var controller = controller()
    private fun setup(target: ElevationTarget = ElevationTarget.Play): ElevationRequest {
        credentials = CredentialState(Mode.SELF_MANAGED, mapOf(person to vault.prepareCredential(pin).credential))
        assertEquals(ElevationOutcome.IDLE, elevations.onStart())
        assertEquals(AppApprovalOutcome.READY, controller.onStart())
        val request = assertNotNull(elevations.prepare(person, target))
        val auth = assertIs<PinResult.Authorized>(vault.authenticate(person, pin, request.action)).authority
        assertEquals(ElevationOutcome.COUNTING, elevations.request(request, auth))
        advance(600_000)
        assertEquals(ElevationOutcome.STARTED, elevations.start(request.id, target))
        if (target == ElevationTarget.Play) {
            assertTrue(elevations.installed(request.id, a.packageName))
            assertTrue(elevations.installed(request.id, b.packageName))
        }
        events.clear(); return request
    }
    private fun advance(ms: Long) { now = now.copy(proven = now.proven.copy(totalMillis = now.proven.totalMillis + ms),
        boot = now.boot.copy(elapsedMillis = now.boot.elapsedMillis + ms)) }
    private fun request(identity: ApprovalBinding = a) = assertNotNull(controller.prepareElevated(identity.packageName,
        PermissionSelection(manifests.getValue(identity.packageName), mapOf(camera to PermissionChoice.ALLOW))))
    private fun restart() {
        hook = null; failure = null; crash = null
        elevations = elevations(); controller = controller()
        assertEquals(ElevationOutcome.IDLE, elevations.onStart())
        assertTrue(controller.onStart() in setOf(AppApprovalOutcome.READY, AppApprovalOutcome.APPROVED))
    }

    @Test fun `self managed removal keeps debit and data then reapproval needs a new exact elevation`() {
        setup(ElevationTarget.Approve(a))
        assertEquals(AppApprovalOutcome.APPROVED, controller.approveElevated(request()))
        val installed = policy.installed
        val removal = assertNotNull(controller.prepareRemoval(a.packageName, person))
        val authority = assertIs<PinResult.Authorized>(vault.authenticate(person, pin, removal.action)).authority
        assertEquals(AppApprovalOutcome.REMOVED, controller.removeApproved(removal, removal.approvalId, authority))
        assertEquals(1, elevation.state.ledger.debits.size)
        assertEquals(a, approvals.removedApps[a.packageName])
        restart()
        val selection = PermissionSelection(manifest(a), mapOf(camera to PermissionChoice.ALLOW))
        assertNull(controller.prepareReapproval(a.packageName, person, selection))
        assertNull(controller.prepareElevated(a.packageName, selection))
        val next = assertNotNull(elevations.prepare(person, ElevationTarget.Approve(a)))
        val fresh = assertIs<PinResult.Authorized>(vault.authenticate(person, pin, next.action)).authority
        assertEquals(ElevationOutcome.COUNTING, elevations.request(next, fresh))
        advance(600_000)
        assertEquals(ElevationOutcome.STARTED, elevations.start(next.id, ElevationTarget.Approve(a)))
        assertEquals(AppApprovalOutcome.APPROVED, controller.approveElevated(request()))
        assertEquals(2, elevation.state.ledger.debits.size)
        assertFalse(a.packageName in approvals.removedApps)
        assertEquals(installed, policy.installed)
    }

    @Test fun `self managed holder withdraws stuck elevated approval without a new turn`() {
        setup(ElevationTarget.Approve(a))
        failure = "apply"
        assertEquals(AppApprovalOutcome.NOT_READY, controller.approveElevated(request()))
        assertNull(elevation.state.grant)
        val withdrawal = assertNotNull(controller.prepareWithdrawal(person))
        val fresh = assertIs<PinResult.Authorized>(vault.authenticate(person, pin, withdrawal.action)).authority
        failure = null
        assertEquals(AppApprovalOutcome.WITHDRAWN, controller.withdrawPending(withdrawal, withdrawal.approvalId, fresh))
        assertNull(approvals.pending)
        assertFalse(a.packageName in approvals.apps)
        assertEquals(1, elevation.state.ledger.debits.size)
    }

    @Test fun `self managed removal and withdrawal reject changed mode action boot credentials and replay`() {
        for (withdraw in listOf(false, true)) for (bad in listOf("mode", "action", "boot", "generation", "structure", "expiry", "confirmation")) {
            now = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
            elevation = ElevationRecord(); approvals = AppApprovalState()
            elevations = elevations(); controller = controller(); failure = null
            setup(ElevationTarget.Approve(a))
            failure = if (withdraw) "apply" else null
            assertEquals(if (withdraw) AppApprovalOutcome.NOT_READY else AppApprovalOutcome.APPROVED,
                controller.approveElevated(request()))
            failure = null
            val removal = if (withdraw) null else assertNotNull(controller.prepareRemoval(a.packageName, person))
            val withdrawal = if (withdraw) assertNotNull(controller.prepareWithdrawal(person)) else null
            val action = withdrawal?.action ?: removal!!.action
            val fresh = assertIs<PinResult.Authorized>(vault.authenticate(person, pin,
                if (bad == "action") PrivilegedAction("other", "other") else action)).authority
            when (bad) {
                "mode" -> credentials = CredentialState(Mode.TRUSTED, credentials!!.people)
                "boot" -> now = now.copy(boot = BootTime("other", 0))
                "generation" -> credentials = credentials!!.replacing(person, credentials!!.people.getValue(person).copy(generation = 1))
                "structure" -> credentials = CredentialState(Mode.SELF_MANAGED, credentials!!.people, structureGeneration = 1)
                "expiry" -> advance(120_000)
            }
            val before = approvals
            val outcome = if (withdrawal != null) controller.withdrawPending(withdrawal,
                if (bad == "confirmation") "wrong" else withdrawal.approvalId, fresh)
                else controller.removeApproved(removal!!, if (bad == "confirmation") "wrong" else removal.approvalId, fresh)
            assertEquals(AppApprovalOutcome.REFUSED, outcome, "$withdraw $bad")
            assertSame(before, approvals)
        }
    }

    @Test fun `self managed removal crash resumes restrictive direction with one receipt and no refund`() {
        setup(ElevationTarget.Approve(a))
        assertEquals(AppApprovalOutcome.APPROVED, controller.approveElevated(request()))
        val removal = assertNotNull(controller.prepareRemoval(a.packageName, person))
        val fresh = assertIs<PinResult.Authorized>(vault.authenticate(person, pin, removal.action)).authority
        crash = "after-REMOVING"
        assertFailsWith<IllegalStateException> { controller.removeApproved(removal, removal.approvalId, fresh) }
        crash = null
        elevations = elevations(); controller = controller()
        assertEquals(ElevationOutcome.IDLE, elevations.onStart())
        assertEquals(AppApprovalOutcome.REMOVED, controller.onStart())
        assertEquals(AppApprovalOutcome.READY, controller.resume())
        assertEquals(1, approvals.receipts.count { it.kind == AppApprovalReceiptKind.REMOVED })
        assertEquals(1, elevation.state.ledger.debits.size)
        assertEquals(AppApprovalOutcome.REFUSED, controller.removeApproved(removal, removal.approvalId, fresh))
    }

    @Test fun `one Play grant approves two observed apps and closes access without restarting elevation`() {
        val grant = setup(); val deadline = elevation.state.grant!!.deadline
        val first = request(); val second = request(b)
        assertEquals(AppApprovalOutcome.APPROVED, controller.approveElevated(first))
        assertFalse(physicallyOpen); assertNull(elevations.access())
        assertEquals(grant.id, elevation.state.grant!!.id); assertEquals(deadline, elevation.state.grant!!.deadline)
        assertEquals(setOf(a), elevation.state.grant!!.approved)
        assertEquals(AppApprovalOutcome.APPROVED, controller.approveElevated(second))
        assertEquals(setOf(a, b), elevation.state.grant!!.approved)
        assertEquals(setOf(a.packageName, b.packageName), approvals.apps.keys)
        assertEquals(2, approvals.receipts.size); assertEquals(1, elevation.state.ledger.debits.size)
        assertEquals(AppApprovalOutcome.REFUSED, controller.approveElevated(first))
        assertTrue(events.indexOf("revoke") < events.indexOf("permission"))
        assertTrue(events.indexOf("after-atomic") < events.indexOf("apply"))
    }

    @Test fun `Approve grant opens nothing and is exact and single use`() {
        setup(ElevationTarget.Approve(a)); assertFalse(physicallyOpen)
        assertNull(controller.prepareElevated(b.packageName, PermissionSelection(manifest(b), mapOf(camera to PermissionChoice.ALLOW))))
        val first = request(); val stale = request()
        assertEquals(AppApprovalOutcome.APPROVED, controller.approveElevated(first))
        assertNull(elevation.state.grant); assertEquals(1, elevation.state.ledger.debits.size)
        assertEquals(AppApprovalOutcome.REFUSED, controller.approveElevated(stale))
        assertEquals(setOf(a.packageName), approvals.apps.keys)
    }

    @Test fun `caller Play history is never self-managed authority`() {
        setup(ElevationTarget.Settings)
        val access = SessionAccess("fabricated", WindowKind.PLAY, setOf(PLAY_PACKAGE), BootTime("boot", 1_200_000))
        val history = PlaySessionMachine.installed(PlaySessionMachine.begin(access, now.boot)!!,
            access.id, a, now.boot, access).state
        assertNull(controller.prepare(history, a.packageName, person, PermissionSelection(manifest(a), mapOf(camera to PermissionChoice.ALLOW))))
        assertNull(controller.prepareElevated(a.packageName, PermissionSelection(manifest(a), mapOf(camera to PermissionChoice.ALLOW))))
    }

    @Test fun `unobserved package and changed signer cannot use Play grant`() {
        val grant = setup(); val unobserved = ApprovalBinding(PackageName("test.unobserved"), a.signingCerts)
        manifests = manifests + (unobserved.packageName to manifest(unobserved))
        policy = policy.copy(installed = policy.installed + InstalledPackage(unobserved.packageName, false, 1),
            installedIdentities = policy.installedIdentities + unobserved)
        assertNull(controller.prepareElevated(unobserved.packageName, PermissionSelection(manifest(unobserved), mapOf(camera to PermissionChoice.ALLOW))))
        assertEquals(ElevationOutcome.ACCESS_CLOSED, elevations.endPackageAccess(grant.id, SessionEnd.LEFT_PACKAGES))
        assertFalse(elevations.installed(grant.id, unobserved.packageName))
        val r = request()
        policy = policy.copy(installedIdentities = listOf(ApprovalBinding(a.packageName, b.signingCerts), b))
        assertEquals(AppApprovalOutcome.REFUSED, controller.approveElevated(r)); assertTrue(approvals.apps.isEmpty())
    }

    @Test fun `deadline boot rollback credentials mode and signer are rechecked inside atomic commit`() {
        val changes: List<(ElevatedAppApprovalTest) -> Unit> = listOf(
            { it.advance(600_000) },
            { it.now = it.now.copy(boot = BootTime("reboot", 0)) },
            { it.now = it.now.copy(boot = BootTime("boot", 599_999)) },
            { it.credentials = it.credentials!!.replacing(it.person, it.credentials!!.people.getValue(it.person).copy(generation = 1)) },
            { it.credentials = CredentialState(Mode.SELF_MANAGED, it.credentials!!.people, structureGeneration = 1) },
            { it.credentials = CredentialState(Mode.TRUSTED, it.credentials!!.people) },
            { it.policy = it.policy.copy(installedIdentities = listOf(ApprovalBinding(it.a.packageName, it.b.signingCerts), it.b)) },
            { it.battery = BatteryState(1, false) },
        )
        for (change in changes) {
            val f = ElevatedAppApprovalTest(); f.setup(); val r = f.request()
            f.hook = { if (it == "before-atomic") change(f) }
            assertEquals(AppApprovalOutcome.NOT_READY, f.controller.approveElevated(r))
            assertTrue(f.approvals.apps.isEmpty()); assertTrue(f.approvals.receipts.isEmpty())
            assertTrue(f.elevation.state.grant!!.approved.isEmpty()); assertEquals(1, f.elevation.state.ledger.debits.size)
        }
    }

    @Test fun `clock epoch repair leaves original live deadline and debit intact`() {
        setup(); val deadline = elevation.state.grant!!.deadline; val debit = elevation.state.ledger.debits.single()
        val r = request(); now = now.copy(proven = RunningTime("repaired", 0))
        assertEquals(AppApprovalOutcome.APPROVED, controller.approveElevated(r))
        assertEquals(deadline, elevation.state.grant!!.deadline); assertEquals(debit, elevation.state.ledger.debits.single())
    }

    @Test fun `failed preparing effects and atomic write never publish an approval`() {
        for (stage in listOf("hide", "permission", "permissions-readback", "before-atomic")) {
            val f = ElevatedAppApprovalTest(); f.setup(); val r = f.request(); f.failure = stage
            assertEquals(AppApprovalOutcome.NOT_READY, f.controller.approveElevated(r))
            assertTrue(f.approvals.apps.isEmpty()); assertTrue(f.approvals.receipts.isEmpty())
            assertTrue(f.elevation.state.grant!!.approved.isEmpty())
            f.restart(); assertTrue(f.approvals.apps.isEmpty()); assertNull(f.approvals.pending)
            assertNull(f.elevation.state.grant); assertEquals(1, f.elevation.state.ledger.debits.size)
            assertEquals(AppApprovalOutcome.REFUSED, f.controller.approveElevated(r))
        }
    }

    @Test fun `crash on either side of atomic write recovers without partial consumption or a second receipt`() {
        for (target in listOf(ElevationTarget.Play, ElevationTarget.Approve(a))) {
            for (stage in listOf("before-atomic", "after-atomic")) {
                val f = ElevatedAppApprovalTest(); f.setup(target); val r = f.request(); f.crash = stage
                assertFailsWith<IllegalStateException> { f.controller.approveElevated(r) }
                val committed = stage == "after-atomic"
                assertEquals(committed, f.approvals.apps.isNotEmpty())
                if (committed) {
                    if (target == ElevationTarget.Play) assertEquals(setOf(f.a), f.elevation.state.grant!!.approved)
                    else assertNull(f.elevation.state.grant)
                }
                f.restart(); assertEquals(committed, f.approvals.apps.isNotEmpty())
                assertEquals(if (committed) 1 else 0, f.approvals.receipts.size)
                assertEquals(1, f.elevation.state.ledger.debits.size); assertNull(f.elevation.state.grant)
                assertEquals(AppApprovalOutcome.REFUSED, f.controller.approveElevated(r))
            }
        }
    }

    @Test fun `failed committed readback resumes without consuming again`() {
        setup(); val r = request(); failure = "policy-readback"
        assertEquals(AppApprovalOutcome.NOT_READY, controller.approveElevated(r))
        assertEquals(AppApprovalPhase.COMMITTED, approvals.pending!!.phase)
        assertEquals(setOf(a), elevation.state.grant!!.approved)
        failure = null; assertEquals(AppApprovalOutcome.APPROVED, controller.resume())
        assertEquals(1, events.count { it == "after-atomic" }); assertEquals(1, approvals.receipts.size)
    }

    @Test fun `stale approval or elevation revision cannot cross final CAS`() {
        for (mutateElevation in listOf(false, true)) {
            val f = ElevatedAppApprovalTest(); f.setup(); val r = f.request()
            f.hook = { if (it == "before-atomic") {
                if (mutateElevation) f.elevation = f.elevation.replacing(f.elevation.state)
                else f.approvals = f.approvals.replacing()
            } }
            assertEquals(AppApprovalOutcome.NOT_READY, f.controller.approveElevated(r))
            assertTrue(f.approvals.apps.isEmpty()); assertTrue(f.elevation.state.grant!!.approved.isEmpty())
        }
    }

    @Test fun `pending accommodation is exact and unavailable to ordinary tick or a foreign operation`() {
        for (foreign in listOf(false, true)) {
            val f = ElevatedAppApprovalTest(); f.setup(); val r = f.request()
            f.hook = { if (it == "before-atomic") {
                if (foreign) {
                    val record = AppApprovalRecord("foreign", PermissionSelection(f.manifest(f.b), mapOf(f.camera to PermissionChoice.ALLOW)))
                    f.approvals = AppApprovalState(f.approvals.revision, pending = AppApprovalOperation(record,
                        AppApprovalReceipt(record.id, f.person, mapOf(f.person to 0L)), AppApprovalPhase.PREPARING))
                } else {
                    assertEquals(ElevationOutcome.LAPSED, f.elevations.tick())
                }
            } }
            assertEquals(AppApprovalOutcome.NOT_READY, f.controller.approveElevated(r))
            assertTrue(f.approvals.apps.isEmpty()); assertTrue(f.approvals.receipts.isEmpty())
        }
    }

    @Test fun `restart invalidates prepared request even if persisted grant data is copied back`() {
        setup(); val r = request(); val saved = elevation
        restart(); elevation = saved
        assertEquals(AppApprovalOutcome.REFUSED, controller.approveElevated(r))
        assertNull(controller.prepareElevated(a.packageName, PermissionSelection(manifest(a), mapOf(camera to PermissionChoice.ALLOW))))
        assertTrue(approvals.apps.isEmpty())
    }

    @Test fun `deadline remains strict after returning from Play or a failed permission effect`() {
        val grant = setup()
        assertEquals(ElevationOutcome.ACCESS_CLOSED, elevations.endPackageAccess(grant.id, SessionEnd.INCOMING_CALL))
        advance(599_999); val r = request(); advance(1)
        assertEquals(AppApprovalOutcome.REFUSED, controller.approveElevated(r))
        assertTrue(approvals.apps.isEmpty()); assertEquals(1, elevation.state.ledger.debits.size)
    }
}
