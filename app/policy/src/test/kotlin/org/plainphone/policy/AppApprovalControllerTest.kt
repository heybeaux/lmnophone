// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class AppApprovalControllerTest {
    private val person = TrustedPersonId("holder")
    private val other = TrustedPersonId("other")
    private val app = PackageName("test.added")
    private val home = PackageName("test.home")
    private val sms = PackageName("test.sms")
    private val dialer = PackageName("test.dialer")
    private val signer = CertDigest("ab".repeat(32))
    private val binding = ApprovalBinding(app, setOf(signer))
    private val camera = PermissionName("android.permission.CAMERA")
    private val pin get() = CharArray(6) { '1' }
    private var now: ClockReading? = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
    private val clock = RunningClock { now }
    private var credentials: CredentialState? = null
    private val credentialStore = object : CredentialStore {
        override fun read() = credentials
        override fun compareAndSet(expected: CredentialState, replacement: CredentialState): Boolean {
            if (credentials !== expected) return false
            credentials = replacement; return true
        }
    }
    private var entropy = 0
    private val vault = PinVault(credentialStore, KeyDerivation { chars, salt, _ ->
        java.security.MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
    }, SecretEntropy { size -> ByteArray(size) { (++entropy).toByte() } }, clock)
    private var state: AppApprovalState? = AppApprovalState()
    private val events = mutableListOf<String>()
    private class SimulatedProcessDeath : Error()
    private var crash: String? = null
    private var failure: String? = null
    private var beforeSave: (() -> Unit)? = null
    private var atEvent: ((String) -> Unit)? = null
    private fun event(name: String): Boolean {
        events += name
        atEvent?.invoke(name)
        if (crash == name) throw SimulatedProcessDeath()
        return failure != name
    }
    private val store = object : AppApprovalStore {
        override fun read() = state
        override fun compareAndSet(expected: AppApprovalState, replacement: AppApprovalState, expectedCredentials: CredentialState?): Boolean {
            beforeSave?.invoke()
            val stage = when {
                replacement.pending?.phase == AppApprovalPhase.EDITING -> "save-editing"
                replacement.pending?.phase == AppApprovalPhase.PREPARING -> "save-preparing"
                replacement.pending?.phase == AppApprovalPhase.COMMITTED -> "save-committed"
                replacement.pending?.phase == AppApprovalPhase.WITHDRAWING -> "save-withdrawing"
                replacement.pending?.phase == AppApprovalPhase.REMOVING -> "save-removing"
                else -> "save-finished"
            }
            if (!event(stage) || state !== expected || (expectedCredentials != null && credentials !== expectedCredentials)) return false
            state = replacement
            event("after-$stage")
            return true
        }
    }
    private val existingDebit = ElevationDebit("previous-elevation", RunningTime("epoch", 0))
    private var combined = ElevationRecord(state = ElevationState(ElevationLedger(listOf(existingDebit))))
    private val journal: SessionJournal get() = combined.journal
    private val inventory = BuildInventory("model", "build", 1, NeverHideList(setOf(sms, PLAY_PACKAGE)), setOf(sms), setOf(PackageName("test.settings")))
    private val sessions = SessionController(object : ElevationStore() {
        override fun read() = combined
        override fun compareAndSet(expected: ElevationRecord?, replacement: ElevationRecord, expectedCredentials: CredentialState?): Boolean {
            if (combined !== expected || (expectedCredentials != null && credentials !== expectedCredentials)) return false
            combined = replacement; return true
        }
    }, object : SessionEffects {
        override fun revokeAllRelaxations() = event("revoke")
        override fun open(access: SessionAccess) = true
        override fun protectedStateCheck() = event("protected")
        override fun readBackPolicy() = event("session-readback")
    }, clock, inventory, vault) { credentials?.mode }
    private var manifest: PermissionManifest? = PermissionManifest(binding, 1, "build", listOf(
        RuntimePermission(camera, "Camera", listOf("Taking photos"), PermissionGrantCapability.SUPPORTED)))
    private var policy = PolicyInput("model", "build", home, sms,
        listOf(home, sms, dialer, PLAY_PACKAGE).map { InstalledPackage(it, true, 1) } + InstalledPackage(app, false, 1),
        listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)), inventory,
        installedIdentities = listOf(binding))
    private var inputAvailable = true
    private var pla313InputThrows = false
    private var battery = BatteryState(50, false)
    private var permissionReadback = mapOf(camera to PermissionReadback.GRANTED)
    private var hidden = true
    private var appliedLockTask = emptySet<PackageName>()
    private var appliedHome = emptySet<PackageName>()
    private val containmentRequests = mutableListOf<Set<PackageName>>()
    private var containmentWorks = true
    private var poweredOff = false
    private var modelPermissions = false
    private var actualPermissions = emptyMap<PermissionName, PermissionReadback>()
    private fun assertWindowExcluded() {
        assertFalse(app in appliedLockTask)
        assertFalse(app in state!!.approvedPackages(policy.installed, policy.installedIdentities))
    }
    private val effects = object : AppApprovalEffects {
        override fun hideAndReadBack(identity: ApprovalBinding): Boolean {
            if (poweredOff) throw SimulatedProcessDeath()
            if (!event("hide")) return false
            hidden = true
            return true
        }
        override fun openPermissionWindow(identity: ApprovalBinding): Boolean {
            assertTrue(hidden)
            if (!event("window-exclude")) return false
            appliedLockTask = appliedLockTask - identity.packageName
            assertWindowExcluded()
            hidden = false
            if (!event("window-unhide")) return false
            return event("window-visible-readback")
        }
        override fun closePermissionWindow(identity: ApprovalBinding): Boolean {
            if (poweredOff) throw SimulatedProcessDeath()
            if (!event("window-rehide")) return false
            hidden = true
            return event("window-hidden-readback")
        }
        override fun setPermission(identity: ApprovalBinding, write: PermissionWrite): Boolean {
            if (hidden) return false // API 35 refuses both grants and denials while hidden.
            assertWindowExcluded()
            if (!event("permission")) return false
            actualPermissions = actualPermissions + (write.permission to
                if (write.choice == PermissionChoice.ALLOW) PermissionReadback.GRANTED else PermissionReadback.DENIED)
            return event("write-${write.choice}-${write.permission.value}")
        }
        override fun readPermissions(identity: ApprovalBinding): Map<PermissionName, PermissionReadback> {
            if (!hidden && state!!.pending?.phase in setOf(AppApprovalPhase.PREPARING, AppApprovalPhase.EDITING)) assertWindowExcluded()
            if (!event("permissions-readback")) return emptyMap()
            event(if (hidden) "permissions-hidden" else "permissions-visible")
            return if (modelPermissions) actualPermissions else permissionReadback
        }
        override fun applyPolicy(plan: PolicyPlan): Boolean {
            assertFalse(PLAY_PACKAGE in plan.lockTaskPackages)
            if (app !in plan.unhide) {
                if (!event("restore-policy")) return false
                hidden = true; appliedLockTask = plan.lockTaskPackages
                return true
            }
            assertNotNull(state!!.apps[app], "unhide before persistent approval")
            assertTrue(app in plan.lockTaskPackages)
            if (!event("unhide")) return false
            hidden = false; appliedLockTask = plan.lockTaskPackages; appliedHome = plan.unhide.intersect(plan.lockTaskPackages)
            return true
        }
        override fun readBackPolicy(plan: PolicyPlan) = event("policy-readback")
        override fun containFailedActivation(packages: Set<PackageName>): Boolean {
            containmentRequests += packages
            if (!event("contain") || !containmentWorks) return false
            appliedLockTask = appliedLockTask - packages
            appliedHome = appliedHome - packages
            if (app in packages) hidden = true
            return event("contain-readback")
        }
    }
    private var id = 0
    private fun controller() = AppApprovalController(store, { credentials }, vault, sessions, clock,
        { if (pla313InputThrows) error("facts unavailable"); policy.takeIf { inputAvailable } }, { manifest }, { battery }, effects, AppApprovalIdSource { "approval-${++id}" })
    private var controller = controller()
    private lateinit var play: PlaySessionState
    private fun setup() {
        credentials = CredentialState(Mode.TRUSTED, mapOf(person to vault.prepareCredential(pin).credential, other to vault.prepareCredential(pin).credential))
        assertEquals(AppApprovalOutcome.READY, controller.onStart())
        val a = assertIs<PinResult.Authorized>(vault.authenticate(person, pin, PrivilegedAction("session:PLAY", "play"))).authority
        assertEquals(SessionOutcome.OPENED, sessions.start("play", WindowKind.PLAY, battery, a))
        val access = sessions.access()!!
        play = PlaySessionMachine.installed(PlaySessionMachine.begin(access, now!!.boot)!!, "play", binding, now!!.boot, access).state
        events.clear()
    }
    private fun request(selected: Map<PermissionName, PermissionChoice> = mapOf(camera to PermissionChoice.ALLOW)) =
        assertNotNull(controller.prepare(play, app, person, PermissionSelection(manifest!!, selected)))
    private fun auth(r: AppApprovalRequest, who: TrustedPersonId = person) =
        assertIs<PinResult.Authorized>(vault.authenticate(who, pin, r.action)).authority

    @Test fun `Add an app closes Play applies and checks permissions saves approval then unhides and verifies`() {
        setup(); val r = request()
        assertEquals(AppApprovalOutcome.APPROVED, controller.approve(r, auth(r)))
        assertNull(sessions.access())
        assertEquals(SessionJournal.Empty, journal)
        assertEquals(listOf(existingDebit), combined.state.ledger.debits)
        assertNull(combined.state.grant)
        assertEquals(binding, state!!.apps.getValue(app).identity)
        assertEquals(mapOf(camera to PermissionChoice.ALLOW), state!!.apps.getValue(app).permissions.choices)
        assertNull(state!!.pending)
        assertEquals(setOf(person, other), state!!.receipts.single().recipientGenerations.keys)
        assertEquals(person, state!!.receipts.single().actor)
        assertTrue(events.indexOf("revoke") < events.indexOf("permission"))
        assertTrue(events.indexOf("permissions-readback") < events.indexOf("save-committed"))
        assertTrue(events.indexOf("save-committed") < events.indexOf("unhide"))
        assertTrue(events.indexOf("policy-readback") < events.indexOf("save-finished"))
    }

    @Test fun `candidate alone incomplete permissions wrong person and wrong action grant nothing`() {
        setup()
        assertNull(controller.prepare(play, PackageName("test.absent"), person, PermissionSelection(manifest!!, mapOf(camera to PermissionChoice.ALLOW))))
        assertNull(controller.prepare(play, app, person, PermissionSelection(manifest!!, emptyMap())))
        val r = request()
        assertEquals(AppApprovalOutcome.REFUSED, controller.approve(r, auth(r, other)))
        val next = request()
        val unrelated = assertIs<PinResult.Authorized>(vault.authenticate(person, pin, PrivilegedAction("other", "x"))).authority
        assertEquals(AppApprovalOutcome.REFUSED, controller.approve(next, unrelated))
        assertTrue(state!!.apps.isEmpty()); assertFalse("permission" in events)
    }

    @Test fun `expired PIN authority cannot begin a durable approval`() {
        setup(); val r = request(); val a = auth(r)
        now = ClockReading(RunningTime("epoch", 120_000), BootTime("boot", 120_000))
        assertEquals(AppApprovalOutcome.REFUSED, controller.approve(r, a))
        assertNull(state!!.pending); assertTrue(state!!.apps.isEmpty())
    }

    @Test fun `self managed mode cannot use supervised PIN approval`() {
        setup(); credentials = CredentialState(Mode.SELF_MANAGED, mapOf(person to credentials!!.people.getValue(person)))
        assertNull(controller.prepare(play, app, person, PermissionSelection(manifest!!, mapOf(camera to PermissionChoice.ALLOW))))
    }

    @Test fun `browser protected session only unknown build and locked boot candidates are refused`() {
        setup(); val original = policy
        for (p in listOf(original.copy(classifiedBrowsers = setOf(app)), original.copy(inventory = null),
            original.copy(lockedBoot = true), original.copy(health = PolicyHealth.BOTH_INVALID),
            original.copy(inventory = BuildInventory("model", "build", 1, NeverHideList(setOf(sms, app)), setOf(sms), setOf(app))))) {
            policy = p
            assertNull(controller.prepare(play, app, person, PermissionSelection(manifest!!, mapOf(camera to PermissionChoice.ALLOW))))
        }
    }

    @Test fun `battery and package changes before approval do not write or unhide`() {
        setup(); val r = request(); val a = auth(r)
        battery = BatteryState(4, false)
        assertEquals(AppApprovalOutcome.LOW_BATTERY, controller.approve(r, a))
        assertNull(state!!.pending)
        battery = BatteryState(50, false)
        val next = request(); val nextAuth = auth(next)
        manifest = PermissionManifest(ApprovalBinding(app, setOf(CertDigest("cd".repeat(32)))), 1, "build", manifest!!.permissions)
        assertEquals(AppApprovalOutcome.REFUSED, controller.approve(next, nextAuth))
        assertTrue(state!!.apps.isEmpty()); assertFalse("unhide" in events)
    }

    @Test fun `permission failure or missing readback keeps the app unapproved`() {
        for (point in listOf("permission", "readback")) {
            setup(); val r = request(); val a = auth(r)
            if (point == "permission") failure = point else permissionReadback = emptyMap()
            assertEquals(AppApprovalOutcome.NOT_READY, controller.approve(r, a))
            assertTrue(state!!.apps.isEmpty()); assertFalse("unhide" in events)
            failure = null; permissionReadback = mapOf(camera to PermissionReadback.GRANTED)
            assertEquals(AppApprovalOutcome.READY, controller.onStart())
            assertNull(state!!.pending)
        }
    }

    @Test fun `commit CAS failure preserves pending unapproved work and never unhides`() {
        setup(); val r = request(); failure = "save-committed"
        assertEquals(AppApprovalOutcome.NOT_READY, controller.approve(r, auth(r)))
        assertTrue(state!!.apps.isEmpty()); assertTrue(state!!.receipts.isEmpty())
        assertFalse("unhide" in events)
        failure = null
        assertEquals(AppApprovalOutcome.READY, controller.onStart())
        assertNull(state!!.pending)
    }

    @Test fun `activation and completion failures retain a retryable approval without duplicate receipt`() {
        for (point in listOf("unhide", "policy-readback", "save-finished")) {
            setup(); val r = request(); failure = point
            assertEquals(AppApprovalOutcome.NOT_READY, controller.approve(r, auth(r)))
            assertEquals(1, state!!.apps.size); assertEquals(1, state!!.receipts.size)
            failure = null
            assertEquals(AppApprovalOutcome.APPROVED, controller.resume())
            assertNull(state!!.pending); assertEquals(1, state!!.receipts.size)
            state = AppApprovalState()
        }
    }

    @Test fun `startup revokes first and abandons uncommitted work but completes committed work`() {
        for (point in listOf("revoke", "protected", "session-readback", "save-preparing", "after-save-preparing", "hide", "permission", "permissions-readback", "save-committed", "after-save-committed", "unhide", "policy-readback", "save-finished", "after-save-finished")) {
            state = AppApprovalState(); setup(); val r = request(); val a = auth(r); crash = point
            assertFailsWith<SimulatedProcessDeath> { controller.approve(r, a) }
            val recoverContain = state!!.pending?.phase == AppApprovalPhase.COMMITTED
            val recoverHide = state!!.pending?.phase == AppApprovalPhase.PREPARING
            val committed = state!!.apps.isNotEmpty()
            crash = null; events.clear(); controller = controller()
            val result = controller.onStart()
            assertEquals(if (recoverHide || recoverContain) "contain" else "revoke", events.first())
            assertTrue(result == AppApprovalOutcome.READY || result == AppApprovalOutcome.APPROVED)
            assertNull(state!!.pending)
            assertEquals(committed, state!!.apps.isNotEmpty())
            assertEquals(if (committed) 1 else 0, state!!.receipts.size)
            assertEquals(AppApprovalOutcome.REFUSED, controller.approve(r, a))
        }
    }

    @Test fun `reentrant credential change at irreversible save rejects approval`() {
        setup(); val r = request(); val a = auth(r)
        beforeSave = {
            if (state!!.pending?.phase == AppApprovalPhase.PREPARING) {
                val old = credentials!!
                credentials = old.replacing(person, old.people.getValue(person).copy(generation = 1))
            }
        }
        assertEquals(AppApprovalOutcome.NOT_READY, controller.approve(r, a))
        assertTrue(state!!.apps.isEmpty()); assertFalse("unhide" in events)
    }

    @Test fun `committed stale package or failed revocation cannot resume visibility`() {
        setup(); val r = request(); failure = "unhide"
        assertEquals(AppApprovalOutcome.NOT_READY, controller.approve(r, auth(r)))
        failure = null
        manifest = PermissionManifest(binding, 2, "build", manifest!!.permissions)
        events.clear()
        assertEquals(AppApprovalOutcome.NOT_READY, controller.resume())
        assertFalse("unhide" in events)
        failure = "revoke"; controller = controller()
        assertEquals(AppApprovalOutcome.NOT_READY, controller.onStart())
        assertEquals(AppApprovalOutcome.NOT_READY, controller.resume())
    }

    @Test fun `records and receipt collections cannot be changed by callers`() {
        setup(); val r = request()
        assertEquals(AppApprovalOutcome.APPROVED, controller.approve(r, auth(r)))
        assertFailsWith<UnsupportedOperationException> { (state!!.apps as MutableMap).clear() }
        assertFailsWith<UnsupportedOperationException> { (state!!.receipts as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (state!!.receipts.single().recipientGenerations as MutableMap).clear() }
        assertEquals(AppApprovalOutcome.REFUSED, controller.approve(r, auth(r)))
    }
    @Test fun `manifest or classification changed after permission writes prevents irreversible approval`() {
        for (change in listOf("manifest", "classifier", "clock", "battery")) {
            state = AppApprovalState(); setup(); val r = request(); val a = auth(r)
            val originalManifest = manifest; val originalPolicy = policy; val originalClock = now
            atEvent = { step -> if (step == "permissions-readback") {
                when (change) {
                    "manifest" -> manifest = PermissionManifest(binding, 2, "build", manifest!!.permissions)
                    "classifier" -> policy = policy.copy(classifiedBrowsers = setOf(app))
                    "clock" -> now = null
                    "battery" -> battery = BatteryState(4, false)
                }
            } }
            assertEquals(AppApprovalOutcome.NOT_READY, controller.approve(r, a))
            assertTrue(state!!.apps.isEmpty()); assertFalse("unhide" in events)
            atEvent = null; manifest = originalManifest; policy = originalPolicy; now = originalClock; battery = BatteryState(50, false)
            assertEquals(AppApprovalOutcome.READY, controller.onStart())
        }
    }

    @Test fun `durable approval cannot be published by ordinary policy while activation is incomplete`() {
        setup(); val r = request(); failure = "unhide"
        assertEquals(AppApprovalOutcome.NOT_READY, controller.approve(r, auth(r)))
        val normal = PolicyEngine.decide(policy.copy(approvalState = state))
        assertFalse(app in normal.unhide); assertFalse(app in normal.lockTaskPackages)
        failure = null
        assertEquals(AppApprovalOutcome.APPROVED, controller.resume())
        assertTrue(app in PolicyEngine.decide(policy.copy(approvalState = state)).lockTaskPackages)
    }

    private fun stuckApproval() {
        state = AppApprovalState(); failure = null; crash = null; beforeSave = null; atEvent = null
        controller = controller(); setup()
        val r = request(); failure = "unhide"
        assertEquals(AppApprovalOutcome.NOT_READY, controller.approve(r, auth(r)))
        failure = null; events.clear()
    }
    private fun withdrawalAuth(r: AppApprovalWithdrawalRequest, who: TrustedPersonId = person) =
        assertIs<PinResult.Authorized>(vault.authenticate(who, pin, r.action)).authority

    @Test fun `fresh PIN withdrawal releases stale committed approval without approving changed facts`() {
        val originalManifest = manifest!!; val originalPolicy = policy
        for (change in listOf("version", "signer", "missing manifest", "uninstalled", "unknown build")) {
            manifest = originalManifest; policy = originalPolicy; stuckApproval()
            when (change) {
                "version" -> manifest = PermissionManifest(binding, 2, "build", originalManifest.permissions)
                "signer" -> {
                    val changed = ApprovalBinding(app, setOf(CertDigest("cd".repeat(32))))
                    manifest = PermissionManifest(changed, 1, "build", originalManifest.permissions)
                    policy = policy.copy(installedIdentities = listOf(changed))
                }
                "missing manifest" -> manifest = null
                "uninstalled" -> { manifest = null; policy = policy.copy(installed = policy.installed.filterNot { it.name == app }, installedIdentities = emptyList()) }
                "unknown build" -> policy = policy.copy(fingerprint = "ota-build")
            }
            val r = assertNotNull(controller.prepareWithdrawal(other))
            val a = withdrawalAuth(r, other)
            assertEquals(AppApprovalOutcome.WITHDRAWN, controller.withdrawPending(r, r.approvalId, a), change)
            assertNull(state!!.pending); assertFalse(app in state!!.apps)
            assertEquals(listOf(AppApprovalReceiptKind.APPROVED, AppApprovalReceiptKind.WITHDRAWN), state!!.receipts.map { it.kind })
            val receipt = state!!.receipts.last()
            assertEquals(r.approvalId, receipt.approvalId); assertEquals(other, receipt.actor)
            assertEquals(setOf(person, other), receipt.recipientGenerations.keys)
            assertEquals("contain", events.first()); assertFalse("unhide" in events)
            assertFalse("permission" in events); assertFalse("permissions-readback" in events)
            assertEquals(AppApprovalOutcome.READY, controller.resume())
            assertEquals(2, state!!.receipts.size)
        }
    }

    @Test fun `withdrawal requires exact confirmation and fresh person action generation and boot`() {
        val initialClock = now
        for (bad in listOf("confirmation", "person", "action", "expiry", "generation", "structure", "reboot")) {
            now = initialClock; stuckApproval()
            val r = assertNotNull(controller.prepareWithdrawal(person))
            val a = when (bad) {
                "person" -> withdrawalAuth(r, other)
                "action" -> assertIs<PinResult.Authorized>(vault.authenticate(person, pin, PrivilegedAction("other", "x"))).authority
                else -> withdrawalAuth(r)
            }
            when (bad) {
                "expiry" -> now = ClockReading(RunningTime("epoch", 120_000), BootTime("boot", 120_000))
                "generation" -> credentials = credentials!!.replacing(person, credentials!!.people.getValue(person).copy(generation = 1))
                "structure" -> credentials = CredentialState(Mode.TRUSTED, credentials!!.people, structureGeneration = 1)
                "reboot" -> now = ClockReading(RunningTime("epoch", 1), BootTime("next-boot", 0))
            }
            assertEquals(AppApprovalOutcome.REFUSED, controller.withdrawPending(r,
                if (bad == "confirmation") "another-approval" else r.approvalId, a), bad)
            assertEquals(AppApprovalPhase.COMMITTED, state!!.pending!!.phase)
            assertEquals(1, state!!.receipts.size); assertTrue(events.isEmpty())
        }
    }

    @Test fun `withdrawal is limited to current pending committed record and is single use`() {
        setup(); assertNull(controller.prepareWithdrawal(person))
        val approval = request(); failure = "permission"
        assertEquals(AppApprovalOutcome.NOT_READY, controller.approve(approval, auth(approval)))
        assertNull(controller.prepareWithdrawal(person))
        stuckApproval(); val r = assertNotNull(controller.prepareWithdrawal(person)); val a = withdrawalAuth(r)
        assertEquals(AppApprovalOutcome.WITHDRAWN, controller.withdrawPending(r, r.approvalId, a))
        assertEquals(AppApprovalOutcome.REFUSED, controller.withdrawPending(r, r.approvalId, a))
        assertNull(controller.prepareWithdrawal(person))
        // Existing candidate data still needs a newly reviewed request and fresh PIN after withdrawal.
        val freshRequest = request()
        assertEquals(AppApprovalOutcome.APPROVED, controller.approve(freshRequest, auth(freshRequest)))
        assertEquals(3, state!!.receipts.size)
    }

    @Test fun `withdrawal intent and completion crashes replay removal and preserve receipts`() {
        val originalManifest = manifest!!
        for (point in listOf("revoke", "protected", "session-readback", "save-withdrawing", "after-save-withdrawing",
            "restore-policy", "policy-readback", "save-finished", "after-save-finished")) {
            manifest = originalManifest; stuckApproval()
            manifest = PermissionManifest(binding, 2, "build", originalManifest.permissions)
            val r = assertNotNull(controller.prepareWithdrawal(person)); val a = withdrawalAuth(r)
            crash = point
            assertFailsWith<SimulatedProcessDeath>(point) { controller.withdrawPending(r, r.approvalId, a) }
            val saved = state!!.pending?.phase == AppApprovalPhase.WITHDRAWING || state!!.pending == null
            crash = null; events.clear(); controller = controller()
            val result = controller.onStart()
            assertEquals(if (point == "after-save-finished") "revoke" else "contain", events.first())
            assertFalse("unhide" in events)
            if (saved) {
                assertTrue(result in setOf(AppApprovalOutcome.READY, AppApprovalOutcome.WITHDRAWN))
                assertNull(state!!.pending); assertFalse(app in state!!.apps); assertEquals(2, state!!.receipts.size)
            } else {
                assertEquals(AppApprovalOutcome.NOT_READY, result)
                assertEquals(AppApprovalPhase.COMMITTED, state!!.pending!!.phase); assertEquals(1, state!!.receipts.size)
            }
            assertEquals(AppApprovalOutcome.REFUSED, controller.withdrawPending(r, r.approvalId, a))
        }
    }

    @Test fun `failed withdrawal effects or save never release slot and retry queues one receipt`() {
        for (point in listOf("restore-policy", "policy-readback", "save-finished")) {
            stuckApproval(); val r = assertNotNull(controller.prepareWithdrawal(person)); failure = point
            assertEquals(AppApprovalOutcome.NOT_READY, controller.withdrawPending(r, r.approvalId, withdrawalAuth(r)))
            assertEquals(AppApprovalPhase.WITHDRAWING, state!!.pending!!.phase); assertEquals(1, state!!.receipts.size)
            assertFalse(app in PolicyEngine.decide(policy.copy(approvalState = state)).lockTaskPackages)
            failure = null; battery = BatteryState(1, false)
            assertEquals(AppApprovalOutcome.WITHDRAWN, controller.resume())
            assertEquals(2, state!!.receipts.size)
            battery = BatteryState(50, false)
        }
    }

    @Test fun `withdrawal credential CAS race fails before durable authority and visibility`() {
        stuckApproval(); val r = assertNotNull(controller.prepareWithdrawal(person)); val a = withdrawalAuth(r)
        beforeSave = { credentials = credentials!!.replacing(person, credentials!!.people.getValue(person).copy(generation = 1)) }
        assertEquals(AppApprovalOutcome.NOT_READY, controller.withdrawPending(r, r.approvalId, a))
        assertEquals(AppApprovalPhase.COMMITTED, state!!.pending!!.phase)
        assertFalse("restore-policy" in events); assertEquals(1, state!!.receipts.size)
    }

    @Test fun `unsafe cleanup facts keep withdrawal journal until complete safe readback`() {
        val originalPolicy = policy
        for (bad in listOf("locked", "floor", "unknown system", "changed during readback")) {
            policy = originalPolicy; stuckApproval(); val r = assertNotNull(controller.prepareWithdrawal(person))
            when (bad) {
                "locked" -> policy = policy.copy(lockedBoot = true)
                "floor" -> policy = policy.copy(identities = policy.identities + FloorIdentity(app, FloorRole.IN_CALL_UI, IdentitySource.OS_MANAGED),
                    installed = policy.installed.map { if (it.name == app) it.copy(isSystem = true) else it })
                "unknown system" -> policy = policy.copy(fingerprint = "ota-build", installed = policy.installed.map { if (it.name == app) it.copy(isSystem = true) else it })
                "changed during readback" -> atEvent = { if (it == "policy-readback") policy = policy.copy(lockedBoot = true) }
            }
            assertEquals(AppApprovalOutcome.NOT_READY, controller.withdrawPending(r, r.approvalId, withdrawalAuth(r)), bad)
            assertEquals(if (bad == "floor") AppApprovalPhase.COMMITTED else AppApprovalPhase.WITHDRAWING, state!!.pending!!.phase)
            assertTrue(app in state!!.apps); assertFalse("unhide" in events)
            atEvent = null; policy = originalPolicy
            if (bad == "floor") {
                val retry = assertNotNull(controller.prepareWithdrawal(person))
                assertEquals(AppApprovalOutcome.WITHDRAWN, controller.withdrawPending(retry, retry.approvalId, withdrawalAuth(retry)))
            } else assertEquals(AppApprovalOutcome.WITHDRAWN, controller.resume())
        }
    }

    @Test fun `package replacement during cleanup invalidates readback even with identical policy sets`() {
        for (change in listOf("version", "signer")) {
            stuckApproval(); val r = assertNotNull(controller.prepareWithdrawal(person))
            atEvent = { if (it == "policy-readback") {
                policy = if (change == "version") policy.copy(installed = policy.installed.map {
                    if (it.name == app) it.copy(versionCode = it.versionCode + 1) else it
                }) else policy.copy(installedIdentities = listOf(ApprovalBinding(app, setOf(CertDigest("cd".repeat(32))))))
            } }
            assertEquals(AppApprovalOutcome.NOT_READY, controller.withdrawPending(r, r.approvalId, withdrawalAuth(r)))
            assertEquals(AppApprovalPhase.WITHDRAWING, state!!.pending!!.phase)
            atEvent = null
            assertEquals(AppApprovalOutcome.WITHDRAWN, controller.resume())
            policy = policy.copy(installed = policy.installed.map { if (it.name == app) it.copy(versionCode = 1) else it },
                installedIdentities = listOf(binding))
        }
    }

    @Test fun `saved withdrawal survives credential replacement and retains its original receipt recipients`() {
        stuckApproval(); val r = assertNotNull(controller.prepareWithdrawal(person)); failure = "restore-policy"
        assertEquals(AppApprovalOutcome.NOT_READY, controller.withdrawPending(r, r.approvalId, withdrawalAuth(r)))
        credentials = CredentialState(Mode.SELF_MANAGED, mapOf(other to credentials!!.people.getValue(other).copy(generation = 9)), structureGeneration = 1)
        failure = null; now = ClockReading(RunningTime("new-epoch", 0), BootTime("reboot", 0))
        controller = controller()
        assertEquals(AppApprovalOutcome.WITHDRAWN, controller.onStart())
        assertEquals(mapOf(person to 0L, other to 0L), state!!.receipts.last().recipientGenerations)
        assertEquals(person, state!!.receipts.last().actor)
    }

    @Test fun `invalid withdrawal journals and mismatched receipt types are rejected`() {
        stuckApproval(); val op = state!!.pending!!
        assertFailsWith<IllegalArgumentException> { AppApprovalOperation(op.record, op.receipt, AppApprovalPhase.WITHDRAWING) }
        val wrong = AppApprovalReceipt("withdrawal", person, mapOf(person to 0L), AppApprovalReceiptKind.WITHDRAWN, "another-approval")
        assertFailsWith<IllegalArgumentException> { AppApprovalOperation(op.record, op.receipt, AppApprovalPhase.WITHDRAWING, wrong) }
        val valid = AppApprovalReceipt("withdrawal", person, mapOf(person to 0L), AppApprovalReceiptKind.WITHDRAWN, op.record.id)
        assertFailsWith<IllegalArgumentException> { AppApprovalOperation(op.record, op.receipt, AppApprovalPhase.COMMITTED, valid) }
        assertFailsWith<IllegalArgumentException> {
            AppApprovalState(apps = state!!.apps, pending = AppApprovalOperation(op.record, op.receipt, AppApprovalPhase.WITHDRAWING, valid),
                receipts = state!!.receipts + valid)
        }
    }

    private fun activeApproval(acknowledge: Boolean = false) {
        stuckApproval()
        assertEquals(AppApprovalOutcome.APPROVED, controller.resume())
        if (acknowledge) state = state!!.replacing(receipts = emptyList())
        events.clear()
    }
    private fun removalAuth(r: AppRemovalRequest, who: TrustedPersonId = person) =
        assertIs<PinResult.Authorized>(vault.authenticate(who, pin, r.action)).authority

    @Test fun `self managed holder removes active app immediately with fresh PIN and no elevation`() {
        activeApproval(true)
        credentials = CredentialState(Mode.SELF_MANAGED,
            mapOf(person to credentials!!.people.getValue(person)), structureGeneration = 1)
        controller = controller()
        assertEquals(AppApprovalOutcome.READY, controller.onStart())
        val ledger = combined.state.ledger
        val installed = policy.installed
        val request = assertNotNull(controller.prepareRemoval(app, person),
            "Copy-change self-managed removal must be available without an elevation")
        battery = BatteryState(1, false)
        assertEquals(AppApprovalOutcome.REMOVED,
            controller.removeApproved(request, request.approvalId, removalAuth(request)))
        assertNull(state!!.pending)
        assertFalse(app in state!!.apps)
        assertEquals(binding, state!!.removedApps[app])
        assertEquals(installed, policy.installed)
        assertEquals(ledger.debits, combined.state.ledger.debits)
        assertNull(combined.state.grant)
        assertTrue(app in PolicyEngine.decide(policy.copy(approvalState = state)).hidden)
    }

    @Test fun `audit characterizes self managed direct permission edit and PIN reapproval as unavailable`() {
        activeApproval(true)
        credentials = CredentialState(Mode.SELF_MANAGED,
            mapOf(person to credentials!!.people.getValue(person)), structureGeneration = 1)
        controller = controller()
        assertEquals(AppApprovalOutcome.READY, controller.onStart())
        val selection = PermissionSelection(manifest!!, mapOf(camera to PermissionChoice.DENY))
        assertNull(controller.preparePermissionEdit(app, person, selection))
        state = state!!.replacing(apps = emptyMap(), removedApps = mapOf(app to binding))
        assertNull(controller.prepareReapproval(app, person, selection))
        // This records current entry points, not approval of a new self-managed permission rule.
        // A hidden app's reapproval is required to use the existing Approve elevation path.
    }

    @Test fun `other holder removes active app and reapproves after restart without reinstall`() {
        for (ack in listOf(false, true)) {
            now = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
            activeApproval(ack)
            val installed = policy.installed
            val r = assertNotNull(controller.prepareRemoval(app, other))
            battery = BatteryState(1, false)
            assertEquals(AppApprovalOutcome.REMOVED, controller.removeApproved(r, r.approvalId, removalAuth(r, other)))
            assertNull(state!!.pending); assertFalse(app in state!!.apps)
            assertEquals(binding, state!!.removedApps[app])
            val removal = state!!.receipts.last()
            assertEquals(AppApprovalReceiptKind.REMOVED, removal.kind); assertEquals(r.approvalId, removal.approvalId)
            assertEquals(other, removal.actor); assertEquals(setOf(person, other), removal.recipientGenerations.keys)
            assertEquals(if (ack) 1 else 2, state!!.receipts.size)
            assertEquals(installed, policy.installed)
            assertEquals("revoke", events.first()); assertFalse("permission" in events); assertFalse("unhide" in events)
            assertTrue(app in PolicyEngine.decide(policy.copy(approvalState = state)).hidden)
            now = ClockReading(RunningTime("next-epoch", 0), BootTime("next-boot", 0))
            controller = controller(); assertEquals(AppApprovalOutcome.READY, controller.onStart())
            battery = BatteryState(50, false)
            val next = assertNotNull(controller.prepareReapproval(app, person,
                PermissionSelection(manifest!!, mapOf(camera to PermissionChoice.DENY))))
            permissionReadback = mapOf(camera to PermissionReadback.DENIED)
            assertEquals(AppApprovalOutcome.APPROVED, controller.approve(next, auth(next)))
            assertFalse(app in state!!.removedApps)
            assertTrue(app in PolicyEngine.decide(policy.copy(approvalState = state)).lockTaskPackages)
            assertEquals(installed, policy.installed)
            permissionReadback = mapOf(camera to PermissionReadback.GRANTED)
        }
    }

    @Test fun `active removal requires fresh exact person action generation structure and boot`() {
        val initialClock = now
        for (bad in listOf("confirmation", "person", "action", "expiry", "generation", "structure", "reboot", "mode")) {
            now = initialClock; activeApproval()
            val r = assertNotNull(controller.prepareRemoval(app, person))
            val a = when (bad) {
                "person" -> removalAuth(r, other)
                "action" -> assertIs<PinResult.Authorized>(vault.authenticate(person, pin, PrivilegedAction("other", "x"))).authority
                else -> removalAuth(r)
            }
            when (bad) {
                "expiry" -> now = ClockReading(RunningTime("epoch", 120_000), BootTime("boot", 120_000))
                "generation" -> credentials = credentials!!.replacing(person, credentials!!.people.getValue(person).copy(generation = 1))
                "structure" -> credentials = CredentialState(Mode.TRUSTED, credentials!!.people, structureGeneration = 1)
                "reboot" -> now = ClockReading(RunningTime("epoch", 1), BootTime("next-boot", 0))
                "mode" -> credentials = CredentialState(Mode.SELF_MANAGED, mapOf(person to credentials!!.people.getValue(person)))
            }
            assertEquals(AppApprovalOutcome.REFUSED, controller.removeApproved(r,
                if (bad == "confirmation") "another-approval" else r.approvalId, a), bad)
            assertNull(state!!.pending); assertTrue(app in state!!.apps); assertTrue(state!!.removedApps.isEmpty())
            assertTrue(events.isEmpty())
        }
    }

    @Test fun `removal request cannot target a replacement approval or occupy another live slot`() {
        activeApproval()
        val stale = assertNotNull(controller.prepareRemoval(app, person)); val staleAuth = removalAuth(stale)
        val current = assertNotNull(controller.prepareRemoval(app, other)); failure = "restore-policy"
        assertEquals(AppApprovalOutcome.NOT_READY, controller.removeApproved(current, current.approvalId, removalAuth(current, other)))
        assertNull(controller.prepareRemoval(app, person)); assertNull(controller.prepareWithdrawal(person))
        assertNull(controller.prepareReapproval(app, person, PermissionSelection(manifest!!, mapOf(camera to PermissionChoice.ALLOW))))
        failure = null; assertEquals(AppApprovalOutcome.REMOVED, controller.resume())
        val reapprove = assertNotNull(controller.prepareReapproval(app, person, PermissionSelection(manifest!!, mapOf(camera to PermissionChoice.ALLOW))))
        assertEquals(AppApprovalOutcome.APPROVED, controller.approve(reapprove, auth(reapprove)))
        assertEquals(AppApprovalOutcome.REFUSED, controller.removeApproved(stale, stale.approvalId, staleAuth))
        assertTrue(app in state!!.apps)
    }

    @Test fun `active removal crash replay preserves acknowledged history and finishes only saved intent`() {
        for (point in listOf("revoke", "protected", "session-readback", "save-removing", "after-save-removing",
            "restore-policy", "policy-readback", "save-finished", "after-save-finished")) {
            activeApproval(true)
            val r = assertNotNull(controller.prepareRemoval(app, person)); val a = removalAuth(r)
            crash = point
            assertFailsWith<SimulatedProcessDeath>(point) { controller.removeApproved(r, r.approvalId, a) }
            val saved = state!!.pending?.phase == AppApprovalPhase.REMOVING || app !in state!!.apps
            val recovering = state!!.pending != null
            crash = null; events.clear(); controller = controller()
            val result = controller.onStart()
            assertEquals(if (recovering) "contain" else "revoke", events.first()); assertFalse("unhide" in events)
            assertTrue(result in setOf(AppApprovalOutcome.READY, AppApprovalOutcome.REMOVED))
            assertNull(state!!.pending); assertEquals(!saved, app in state!!.apps)
            assertEquals(saved, app in state!!.removedApps)
            assertEquals(if (saved) 1 else 0, state!!.receipts.size)
            assertEquals(AppApprovalOutcome.REFUSED, controller.removeApproved(r, r.approvalId, a))
        }
    }

    @Test fun `failed active removal effects and writes do not publish removal or duplicate receipts`() {
        for (point in listOf("save-removing", "restore-policy", "policy-readback", "save-finished")) {
            activeApproval(true); val r = assertNotNull(controller.prepareRemoval(app, person)); failure = point
            assertEquals(AppApprovalOutcome.NOT_READY, controller.removeApproved(r, r.approvalId, removalAuth(r)))
            assertTrue(app in state!!.apps); assertTrue(state!!.removedApps.isEmpty()); assertTrue(state!!.receipts.isEmpty())
            if (point != "save-removing") {
                assertFalse(app in PolicyEngine.decide(policy.copy(approvalState = state)).lockTaskPackages)
                failure = null; assertEquals(AppApprovalOutcome.REMOVED, controller.resume())
                assertEquals(AppApprovalOutcome.READY, controller.resume()); assertEquals(1, state!!.receipts.size)
            } else assertNull(state!!.pending)
        }
    }

    @Test fun `active removal credential CAS cannot save authority across credential changes`() {
        activeApproval(true); val r = assertNotNull(controller.prepareRemoval(app, person)); val a = removalAuth(r)
        beforeSave = { credentials = credentials!!.replacing(person, credentials!!.people.getValue(person).copy(generation = 1)) }
        assertEquals(AppApprovalOutcome.NOT_READY, controller.removeApproved(r, r.approvalId, a))
        assertNull(state!!.pending); assertTrue(app in state!!.apps)
        assertFalse("restore-policy" in events)
    }

    @Test fun `removed marker is not approval and never authorizes a different signer or incomplete choices`() {
        activeApproval(); val r = assertNotNull(controller.prepareRemoval(app, person))
        assertEquals(AppApprovalOutcome.REMOVED, controller.removeApproved(r, r.approvalId, removalAuth(r)))
        assertNull(controller.prepareReapproval(app, person, PermissionSelection(manifest!!, emptyMap())))
        assertNull(controller.prepareReapproval(PackageName("test.other"), person, PermissionSelection(manifest!!, mapOf(camera to PermissionChoice.ALLOW))))
        val changed = ApprovalBinding(app, setOf(CertDigest("cd".repeat(32))))
        manifest = PermissionManifest(changed, 1, "build", manifest!!.permissions)
        policy = policy.copy(installedIdentities = listOf(changed))
        assertNull(controller.prepareReapproval(app, person, PermissionSelection(manifest!!, mapOf(camera to PermissionChoice.ALLOW))))
        assertFalse(app in PolicyEngine.decide(policy.copy(approvalState = state)).lockTaskPackages)
    }

    @Test fun `removing journal allows absent approval receipt but never fabricates or accepts wrong type`() {
        activeApproval(true); val record = state!!.apps.getValue(app)
        val receipt = AppApprovalReceipt("remove", other, mapOf(person to 0L, other to 0L), AppApprovalReceiptKind.REMOVED, record.id)
        val op = AppApprovalOperation(record, null, AppApprovalPhase.REMOVING, receipt)
        assertNotNull(AppApprovalState(apps = state!!.apps, pending = op))
        assertFailsWith<IllegalArgumentException> { AppApprovalOperation(record, null, AppApprovalPhase.COMMITTED) }
        assertFailsWith<IllegalArgumentException> { AppApprovalOperation(record, null, AppApprovalPhase.REMOVING) }
        val wrong = AppApprovalReceipt("remove", other, mapOf(other to 0L), AppApprovalReceiptKind.WITHDRAWN, record.id)
        assertFailsWith<IllegalArgumentException> { AppApprovalOperation(record, null, AppApprovalPhase.REMOVING, wrong) }
        assertFailsWith<IllegalArgumentException> { AppApprovalState(apps = state!!.apps, removedApps = mapOf(app to binding)) }
        assertFailsWith<UnsupportedOperationException> { (AppApprovalState(removedApps = mapOf(app to binding)).removedApps as MutableMap).clear() }
    }


    private fun editRequest(who: TrustedPersonId = person, choice: PermissionChoice = PermissionChoice.DENY) =
        assertNotNull(controller.preparePermissionEdit(app, who, PermissionSelection(manifest!!, mapOf(camera to choice))))
    private fun editAuth(r: AppPermissionEditRequest, who: TrustedPersonId = person) =
        assertIs<PinResult.Authorized>(vault.authenticate(who, pin, r.action)).authority

    @Test fun `either holder edits permissions immediately with a fresh PIN and last edit stands`() {
        activeApproval(true)
        for ((who, choice) in listOf(other to PermissionChoice.DENY, person to PermissionChoice.ALLOW)) {
            val before = state!!.apps.getValue(app)
            val r = editRequest(who, choice)
            permissionReadback = mapOf(camera to if (choice == PermissionChoice.ALLOW) PermissionReadback.GRANTED else PermissionReadback.DENIED)
            events.clear()
            assertEquals(AppApprovalOutcome.PERMISSIONS_CHANGED, controller.editPermissions(r, r.approvalId, editAuth(r, who)))
            val after = state!!.apps.getValue(app)
            assertNotEquals(before.id, after.id)
            assertEquals(mapOf(camera to choice), after.permissions.choices)
            assertNull(state!!.pending); assertTrue(state!!.removedApps.isEmpty())
            val receipt = state!!.receipts.last()
            assertEquals(AppApprovalReceiptKind.PERMISSIONS_CHANGED, receipt.kind)
            assertEquals(before.id, receipt.approvalId); assertEquals(after.id, receipt.id)
            assertEquals(who, receipt.actor); assertEquals(setOf(person, other), receipt.recipientGenerations.keys)
            assertTrue(events.indexOf("revoke") < events.indexOf("save-editing"))
            assertTrue(events.indexOf("save-editing") < events.indexOf("hide"))
            assertTrue(events.indexOf("hide") < events.indexOf("permission"))
            assertTrue(events.indexOf("permissions-readback") < events.indexOf("save-committed"))
            assertTrue(events.indexOf("save-committed") < events.indexOf("unhide"))
            assertEquals(AppApprovalOutcome.REFUSED, controller.editPermissions(r, r.approvalId, editAuth(r, who)))
        }
        assertEquals(2, state!!.receipts.size) // acknowledged original approval is not recreated
    }

    @Test fun `permission edit requires exact confirmation fresh person action generation structure and boot`() {
        val initialClock = now
        for (bad in listOf("confirmation", "person", "action", "expiry", "generation", "structure", "reboot", "mode")) {
            now = initialClock; activeApproval()
            val before = state!!.apps.getValue(app)
            val r = editRequest()
            val a = when (bad) {
                "person" -> editAuth(r, other)
                "action" -> assertIs<PinResult.Authorized>(vault.authenticate(person, pin, PrivilegedAction("other", "x"))).authority
                else -> editAuth(r)
            }
            when (bad) {
                "expiry" -> now = ClockReading(RunningTime("epoch", 120_000), BootTime("boot", 120_000))
                "generation" -> credentials = credentials!!.replacing(person, credentials!!.people.getValue(person).copy(generation = 1))
                "structure" -> credentials = CredentialState(Mode.TRUSTED, credentials!!.people, structureGeneration = 1)
                "reboot" -> now = ClockReading(RunningTime("epoch", 1), BootTime("next", 0))
                "mode" -> credentials = CredentialState(Mode.SELF_MANAGED, mapOf(person to credentials!!.people.getValue(person)))
            }
            assertEquals(AppApprovalOutcome.REFUSED, controller.editPermissions(r, if (bad == "confirmation") "wrong" else r.approvalId, a), bad)
            assertSame(before, state!!.apps[app]); assertNull(state!!.pending); assertTrue(events.isEmpty())
        }
    }

    @Test fun `permission edit rejects missing approval incomplete choices replacement signer and stale requests`() {
        setup()
        val selection = PermissionSelection(manifest!!, mapOf(camera to PermissionChoice.DENY))
        assertNull(controller.preparePermissionEdit(app, person, selection))
        activeApproval()
        assertNull(controller.preparePermissionEdit(app, person, PermissionSelection(manifest!!, emptyMap())))
        val stale = editRequest(); val staleAuth = editAuth(stale)
        val current = editRequest(other)
        permissionReadback = mapOf(camera to PermissionReadback.DENIED)
        assertEquals(AppApprovalOutcome.PERMISSIONS_CHANGED, controller.editPermissions(current, current.approvalId, editAuth(current, other)))
        assertEquals(AppApprovalOutcome.REFUSED, controller.editPermissions(stale, stale.approvalId, staleAuth))
        val changed = ApprovalBinding(app, setOf(CertDigest("cd".repeat(32))))
        manifest = PermissionManifest(changed, 1, "build", manifest!!.permissions)
        policy = policy.copy(installedIdentities = listOf(changed))
        assertNull(controller.preparePermissionEdit(app, person, PermissionSelection(manifest!!, mapOf(camera to PermissionChoice.ALLOW))))
    }

    @Test fun `permission edit crash replay only resumes durable direction and never duplicates receipt`() {
        for (point in listOf("revoke", "protected", "session-readback", "save-editing", "after-save-editing", "hide", "permission",
            "permissions-readback", "save-committed", "after-save-committed", "unhide", "policy-readback", "save-finished", "after-save-finished")) {
            permissionReadback = mapOf(camera to PermissionReadback.GRANTED)
            activeApproval(true)
            val oldId = state!!.apps.getValue(app).id
            val r = editRequest(); val a = editAuth(r)
            permissionReadback = mapOf(camera to PermissionReadback.DENIED); crash = point
            assertFailsWith<SimulatedProcessDeath>(point) { controller.editPermissions(r, r.approvalId, a) }
            val durable = state!!.apps.getValue(app).id != oldId
            if (state!!.pending != null) assertFalse(app in PolicyEngine.decide(policy.copy(approvalState = state)).lockTaskPackages)
            val recoverContain = state!!.pending?.phase == AppApprovalPhase.COMMITTED
            val recoverHide = state!!.pending?.phase == AppApprovalPhase.EDITING
            crash = null; events.clear(); controller = controller()
            assertEquals(if (durable && state!!.pending != null) AppApprovalOutcome.PERMISSIONS_CHANGED else AppApprovalOutcome.READY,
                controller.onStart(), point)
            assertEquals(if (recoverHide || recoverContain) "contain" else "revoke", events.first()); assertNull(state!!.pending)
            assertEquals(if (durable) PermissionChoice.DENY else PermissionChoice.ALLOW, state!!.apps.getValue(app).permissions.choices[camera])
            assertEquals(if (durable) 1 else 0, state!!.receipts.size)
            assertEquals(AppApprovalOutcome.REFUSED, controller.editPermissions(r, r.approvalId, a))
        }
    }

    @Test fun `failed permission edit effects readbacks and writes hold pending without false completion`() {
        for (point in listOf("save-editing", "hide", "permission", "save-committed", "unhide", "policy-readback", "save-finished", "bad-permissions")) {
            permissionReadback = mapOf(camera to PermissionReadback.GRANTED); activeApproval(true)
            val oldId = state!!.apps.getValue(app).id; val r = editRequest()
            if (point != "bad-permissions") permissionReadback = mapOf(camera to PermissionReadback.DENIED)
            failure = point
            assertEquals(AppApprovalOutcome.NOT_READY, controller.editPermissions(r, r.approvalId, editAuth(r)), point)
            if (point == "save-editing") {
                assertEquals(oldId, state!!.apps.getValue(app).id); assertNull(state!!.pending); assertTrue(state!!.receipts.isEmpty())
            } else {
                assertNotNull(state!!.pending); assertEquals(1, state!!.receipts.size)
                assertFalse(app in PolicyEngine.decide(policy.copy(approvalState = state)).lockTaskPackages)
                failure = null; permissionReadback = mapOf(camera to PermissionReadback.DENIED)
                assertEquals(AppApprovalOutcome.PERMISSIONS_CHANGED, controller.resume())
                assertEquals(AppApprovalOutcome.READY, controller.resume()); assertEquals(1, state!!.receipts.size)
            }
        }
    }

    @Test fun `permission edit checks credential CAS and live facts before saving and before each write`() {
        activeApproval(true); val r = editRequest(); val a = editAuth(r)
        beforeSave = { credentials = credentials!!.replacing(person, credentials!!.people.getValue(person).copy(generation = 1)) }
        assertEquals(AppApprovalOutcome.NOT_READY, controller.editPermissions(r, r.approvalId, a))
        assertNull(state!!.pending); assertFalse("hide" in events)
        beforeSave = null
        activeApproval(true); val next = editRequest()
        atEvent = { if (it == "hide") manifest = PermissionManifest(binding, 2, "build", manifest!!.permissions) }
        assertEquals(AppApprovalOutcome.NOT_READY, controller.editPermissions(next, next.approvalId, editAuth(next)))
        assertEquals(AppApprovalPhase.EDITING, state!!.pending!!.phase)
        assertFalse("permission" in events); assertFalse("unhide" in events)
    }

    @Test fun `stuck permission edit can be withdrawn with a new PIN instead of blocking every later action`() {
        for (point in listOf("hide", "unhide")) {
            permissionReadback = mapOf(camera to PermissionReadback.GRANTED); activeApproval(true)
            val r = editRequest(); permissionReadback = mapOf(camera to PermissionReadback.DENIED); failure = point
            assertEquals(AppApprovalOutcome.NOT_READY, controller.editPermissions(r, r.approvalId, editAuth(r)))
            assertNull(controller.preparePermissionEdit(app, person, PermissionSelection(manifest!!, mapOf(camera to PermissionChoice.DENY))))
            val withdrawal = assertNotNull(controller.prepareWithdrawal(other))
            failure = null; events.clear()
            assertEquals(AppApprovalOutcome.WITHDRAWN, controller.withdrawPending(withdrawal, withdrawal.approvalId, withdrawalAuth(withdrawal, other)))
            assertRestrictiveEditWithdrawal()
            assertEquals(listOf(AppApprovalReceiptKind.PERMISSIONS_CHANGED, AppApprovalReceiptKind.WITHDRAWN), state!!.receipts.map { it.kind })
        }
    }

    @Test fun `permission edit journals reject mismatched kinds and preserve fixed recipients after restart`() {
        activeApproval(true); val r = editRequest(); failure = "hide"
        assertEquals(AppApprovalOutcome.NOT_READY, controller.editPermissions(r, r.approvalId, editAuth(r)))
        val op = state!!.pending!!
        assertFailsWith<IllegalArgumentException> { AppApprovalOperation(op.record, null, AppApprovalPhase.EDITING) }
        val wrong = AppApprovalReceipt(op.record.id, person, mapOf(person to 0L))
        assertFailsWith<IllegalArgumentException> { AppApprovalOperation(op.record, wrong, AppApprovalPhase.EDITING) }
        assertFailsWith<IllegalArgumentException> { AppApprovalOperation(op.record, op.receipt, AppApprovalPhase.PREPARING) }
        credentials = CredentialState(Mode.SELF_MANAGED, mapOf(other to credentials!!.people.getValue(other).copy(generation = 9)), structureGeneration = 1)
        now = ClockReading(RunningTime("new", 0), BootTime("new", 0)); failure = null
        permissionReadback = mapOf(camera to PermissionReadback.DENIED)
        controller = controller(); assertEquals(AppApprovalOutcome.PERMISSIONS_CHANGED, controller.onStart())
        assertEquals(mapOf(person to 0L, other to 0L), state!!.receipts.single().recipientGenerations)
    }


    @Test fun `withdrawing a permission edit preserves reapproval provenance across restart`() {
        activeApproval(true); val r = editRequest(); failure = "hide"
        assertEquals(AppApprovalOutcome.NOT_READY, controller.editPermissions(r, r.approvalId, editAuth(r)))
        val withdrawal = assertNotNull(controller.prepareWithdrawal(person)); failure = null
        modelPermissions = true
        assertEquals(AppApprovalOutcome.WITHDRAWN, controller.withdrawPending(withdrawal, withdrawal.approvalId, withdrawalAuth(withdrawal)))
        assertFalse(app in state!!.apps)
        assertEquals(binding, state!!.removedApps[app])
        controller = controller(); assertEquals(AppApprovalOutcome.READY, controller.onStart())
        assertFalse(app in state!!.approvedPackages(policy.installed, policy.installedIdentities))
        val selection = PermissionSelection(manifest!!, mapOf(camera to PermissionChoice.DENY))
        assertNotNull(controller.prepareReapproval(app, other, selection))
        assertNull(controller.preparePermissionEdit(app, other, selection))
    }

    @Test fun `active removal after permission edit preserves both action receipts`() {
        activeApproval(true); val r = editRequest(); permissionReadback = mapOf(camera to PermissionReadback.DENIED)
        assertEquals(AppApprovalOutcome.PERMISSIONS_CHANGED, controller.editPermissions(r, r.approvalId, editAuth(r)))
        val removal = assertNotNull(controller.prepareRemoval(app, other))
        assertEquals(AppApprovalOutcome.REMOVED, controller.removeApproved(removal, removal.approvalId, removalAuth(removal, other)))
        assertEquals(listOf(AppApprovalReceiptKind.PERMISSIONS_CHANGED, AppApprovalReceiptKind.REMOVED), state!!.receipts.map { it.kind })
        assertEquals(binding, state!!.removedApps[app])
    }

    @Test fun `permission edit holds on new build facts battery and partial multi permission writes`() {
        activeApproval(true)
        val microphone = PermissionName("android.permission.RECORD_AUDIO")
        manifest = PermissionManifest(binding, 2, "build", manifest!!.permissions + RuntimePermission(microphone, "Microphone",
            listOf("Recording"), PermissionGrantCapability.SUPPORTED))
        policy = policy.copy(installed = policy.installed.map { if (it.name == app) it.copy(versionCode = 2) else it })
        val selection = PermissionSelection(manifest!!, mapOf(camera to PermissionChoice.DENY, microphone to PermissionChoice.ALLOW))
        val r = assertNotNull(controller.preparePermissionEdit(app, person, selection)); val a = editAuth(r)
        battery = BatteryState(1, false)
        assertEquals(AppApprovalOutcome.LOW_BATTERY, controller.editPermissions(r, r.approvalId, a))
        assertNull(state!!.pending); assertTrue(events.isEmpty())
        battery = BatteryState(50, false)
        val next = assertNotNull(controller.preparePermissionEdit(app, person, selection))
        atEvent = { if (it == "permission") policy = policy.copy(fingerprint = "ota") }
        assertEquals(AppApprovalOutcome.NOT_READY, controller.editPermissions(next, next.approvalId, editAuth(next)))
        assertEquals(1, events.count { it == "permission" }); assertFalse("unhide" in events)
        atEvent = null; policy = policy.copy(fingerprint = "build")
        permissionReadback = mapOf(camera to PermissionReadback.DENIED, microphone to PermissionReadback.GRANTED)
        assertEquals(AppApprovalOutcome.PERMISSIONS_CHANGED, controller.resume())
        assertEquals(selection.choices, state!!.apps.getValue(app).permissions.choices)
    }

    private val microphone = PermissionName("android.permission.RECORD_AUDIO")
    private val contacts = PermissionName("android.permission.READ_CONTACTS")
    private fun windowAction(edit: Boolean): () -> AppApprovalOutcome {
        if (edit) activeApproval(true) else setup()
        manifest = PermissionManifest(binding, 1, "build", listOf(
            RuntimePermission(camera, "Camera", listOf("Photos"), PermissionGrantCapability.SUPPORTED),
            RuntimePermission(microphone, "Microphone", listOf("Recording"), PermissionGrantCapability.SUPPORTED),
            RuntimePermission(contacts, "Contacts", listOf("Contacts"), PermissionGrantCapability.SUPPORTED)))
        // Deliberately interleave ALLOW, DENY, ALLOW in manifest order.
        val choices = linkedMapOf(camera to PermissionChoice.ALLOW, microphone to PermissionChoice.DENY,
            contacts to PermissionChoice.ALLOW)
        modelPermissions = true
        actualPermissions = mapOf(camera to PermissionReadback.DENIED, microphone to PermissionReadback.DENIED,
            contacts to PermissionReadback.DENIED)
        val action: () -> AppApprovalOutcome = if (edit) {
            val r = assertNotNull(controller.preparePermissionEdit(app, person, PermissionSelection(manifest!!, choices)))
            val a = editAuth(r)
            val run = { controller.editPermissions(r, r.approvalId, a) }
            run
        } else {
            val r = request(choices); val a = auth(r)
            val run = { controller.approve(r, a) }
            run
        }
        events.clear()
        containmentRequests.clear()
        return action
    }

    @Test fun `permission window writes denials first and commits only after hidden verification`() {
        for (edit in listOf(false, true)) {
            val f = AppApprovalControllerTest(); val action = f.windowAction(edit)
            assertEquals(edit, f.app in f.appliedLockTask)
            f.atEvent = { if (it == "save-committed") assertTrue(f.hidden) }
            assertEquals(if (edit) AppApprovalOutcome.PERMISSIONS_CHANGED else AppApprovalOutcome.APPROVED, action())
            val order = listOf("hide", "window-exclude", "window-unhide", "window-visible-readback",
                "write-DENY-${f.microphone.value}", "write-ALLOW-${f.camera.value}", "write-ALLOW-${f.contacts.value}",
                "permissions-visible", "window-rehide", "window-hidden-readback", "permissions-hidden", "save-committed", "unhide")
            val positions = order.map { name -> f.events.indexOf(name).also { assertTrue(it >= 0, "$name: ${f.events}") } }
            assertEquals(positions.sorted(), positions, f.events.toString())
            assertFalse(f.hidden); assertTrue(f.app in f.appliedLockTask)
        }
    }

    @Test fun `permission window refused writes readbacks and gates always rehide without commit`() {
        for (edit in listOf(false, true)) for (point in listOf("window-exclude", "window-unhide", "window-visible-readback",
            "permission", "permissions-readback", "plan", "battery", "hidden-readback", "save-committed")) {
            val f = AppApprovalControllerTest(); val action = f.windowAction(edit)
            when (point) {
                "plan" -> f.atEvent = { if (it == "window-unhide") f.policy = f.policy.copy(fingerprint = "changed") }
                "battery" -> f.atEvent = { if (it == "permissions-visible") f.battery = BatteryState(1, false) }
                "hidden-readback" -> f.atEvent = { if (it == "window-hidden-readback") f.actualPermissions = emptyMap() }
                else -> f.failure = point
            }
            assertEquals(AppApprovalOutcome.NOT_READY, action(), "$edit $point")
            assertTrue(f.hidden, "$edit $point left app visible")
            assertFalse("after-save-committed" in f.events)
            assertTrue("window-rehide" in f.events)
            assertFalse(f.app in f.state!!.approvedPackages(f.policy.installed, f.policy.installedIdentities))
        }
    }

    @Test fun `failed close retains pending work and next start hides before low battery`() {
        for (edit in listOf(false, true)) for (point in listOf("window-rehide", "window-hidden-readback")) {
            val f = AppApprovalControllerTest(); val action = f.windowAction(edit); f.failure = point
            assertEquals(AppApprovalOutcome.NOT_READY, action())
            assertFalse("after-save-committed" in f.events)
            f.failure = null; f.battery = BatteryState(1, false); f.events.clear(); f.controller = f.controller()
            assertEquals(if (edit) AppApprovalOutcome.LOW_BATTERY else AppApprovalOutcome.READY, f.controller.onStart())
            assertEquals("contain", f.events.first()); assertTrue(f.hidden)
        }
    }

    @Test fun `battery dropping after initial hide never opens the permission window`() {
        for (edit in listOf(false, true)) {
            val f = AppApprovalControllerTest(); val action = f.windowAction(edit)
            f.atEvent = { if (it == "hide") f.battery = BatteryState(1, false) }
            assertEquals(if (edit) AppApprovalOutcome.LOW_BATTERY else AppApprovalOutcome.NOT_READY, action())
            assertTrue(f.hidden); assertFalse("window-unhide" in f.events)
        }
    }

    @Test fun `power loss after unhide and each write recovers hidden before any gate`() {
        for (edit in listOf(false, true)) for (point in listOf("window-unhide", "write-DENY-${microphone.value}",
            "write-ALLOW-${camera.value}", "write-ALLOW-${contacts.value}")) for (gate in listOf("battery", "session", "plan")) {
            val f = AppApprovalControllerTest(); val action = f.windowAction(edit)
            f.atEvent = { if (it == point) { f.poweredOff = true; throw SimulatedProcessDeath() } }
            assertFailsWith<SimulatedProcessDeath> { action() }
            assertFalse(f.hidden, point)
            assertEquals(if (edit) AppApprovalPhase.EDITING else AppApprovalPhase.PREPARING, f.state!!.pending!!.phase)
            assertFalse("after-save-committed" in f.events)
            f.poweredOff = false; f.atEvent = null; f.events.clear()
            when (gate) {
                "battery" -> f.battery = BatteryState(1, false)
                "session" -> f.failure = "revoke"
                "plan" -> f.policy = f.policy.copy(lockedBoot = true)
            }
            f.controller = f.controller()
            val result = f.controller.onStart()
            assertTrue(result in setOf(AppApprovalOutcome.READY, AppApprovalOutcome.LOW_BATTERY, AppApprovalOutcome.NOT_READY))
            assertEquals("contain", f.events.first(), "$edit $point $gate")
            assertTrue(f.hidden); assertFalse("after-save-committed" in f.events)
            assertFalse(f.app in f.state!!.approvedPackages(f.policy.installed, f.policy.installedIdentities))
            if (!edit) assertTrue(f.state!!.apps.isEmpty())
        }
    }

    @Test fun `recovered edit rewrites every permission before it commits`() {
        val f = AppApprovalControllerTest(); val action = f.windowAction(true)
        f.atEvent = { if (it == "write-DENY-${f.microphone.value}") { f.poweredOff = true; throw SimulatedProcessDeath() } }
        assertFailsWith<SimulatedProcessDeath> { action() }
        f.poweredOff = false; f.atEvent = null; f.events.clear(); f.controller = f.controller()
        assertEquals(AppApprovalOutcome.PERMISSIONS_CHANGED, f.controller.onStart())
        assertEquals("contain", f.events.first())
        assertEquals(3, f.events.count { it.startsWith("write-") })
        assertTrue(f.events.indexOf("permissions-hidden") < f.events.indexOf("save-committed"))
        assertEquals(1, f.state!!.receipts.size)
    }


    private fun assertContained() {
        assertEquals(setOf(app), containmentRequests.last())
        assertTrue(hidden)
        assertFalse(app in appliedLockTask)
        assertFalse(app in appliedHome)
        assertEquals(AppApprovalPhase.COMMITTED, state!!.pending!!.phase)
        assertFalse(app in state!!.approvedPackages(policy.installed, policy.installedIdentities))
    }

    @Test fun `failed final readback contains approval and edit and resume retries full activation`() {
        for (edit in listOf(false, true)) {
            val f = AppApprovalControllerTest(); val action = f.windowAction(edit)
            f.failure = "policy-readback"
            assertEquals(AppApprovalOutcome.NOT_READY, action())
            f.assertContained()
            assertTrue(f.events.indexOf("policy-readback") < f.events.lastIndexOf("contain"))
            val receipts = f.state!!.receipts
            f.failure = null; f.events.clear()
            assertEquals(if (edit) AppApprovalOutcome.PERMISSIONS_CHANGED else AppApprovalOutcome.APPROVED, f.controller.resume())
            assertTrue("unhide" in f.events && "policy-readback" in f.events)
            assertTrue(f.events.indexOf("contain-readback") < f.events.indexOf("unhide"))
            assertNull(f.state!!.pending); assertEquals(receipts, f.state!!.receipts)
            assertFalse(f.hidden); assertTrue(f.app in f.appliedLockTask && f.app in f.appliedHome)
        }
    }

    @Test fun `apply false and apply or readback exceptions contain both activation flows`() {
        for (edit in listOf(false, true)) for (point in listOf("apply-false", "apply-throws", "readback-throws")) {
            val f = AppApprovalControllerTest(); val action = f.windowAction(edit)
            if (point == "apply-false") f.failure = "unhide"
            else f.atEvent = { if (it == if (point == "apply-throws") "unhide" else "policy-readback") {
                // The adapter may have made partial changes before throwing.
                f.hidden = false; f.appliedLockTask += f.app; f.appliedHome += f.app
                throw IllegalStateException("adapter failure")
            } }
            assertEquals(AppApprovalOutcome.NOT_READY, action(), "$edit $point")
            f.assertContained()
        }
    }

    @Test fun `post apply permission mismatch and invalid plan contain both activation flows`() {
        for (edit in listOf(false, true)) for (point in listOf("permissions", "plan")) {
            val f = AppApprovalControllerTest(); val action = f.windowAction(edit)
            f.atEvent = { if (it == "policy-readback") {
                if (point == "permissions") f.actualPermissions = emptyMap()
                else f.policy = f.policy.copy(lockedBoot = true)
            } }
            assertEquals(AppApprovalOutcome.NOT_READY, action(), "$edit $point")
            f.assertContained()
        }
    }

    @Test fun `post apply permission and final CAS exceptions also contain activation`() {
        for (edit in listOf(false, true)) for (point in listOf("permissions-visible", "save-finished")) {
            val f = AppApprovalControllerTest(); val action = f.windowAction(edit)
            f.atEvent = { if (it == point && "policy-readback" in f.events) throw IllegalStateException("post-apply failure") }
            assertEquals(AppApprovalOutcome.NOT_READY, action())
            f.assertContained()
        }
    }

    @Test fun `final CAS failure contains both flows and retains committed retry`() {
        for (edit in listOf(false, true)) {
            val f = AppApprovalControllerTest(); val action = f.windowAction(edit)
            f.failure = "save-finished"
            assertEquals(AppApprovalOutcome.NOT_READY, action())
            f.assertContained()
            assertTrue(f.events.indexOf("save-finished") < f.events.lastIndexOf("contain"))
            f.failure = null
            assertEquals(if (edit) AppApprovalOutcome.PERMISSIONS_CHANGED else AppApprovalOutcome.APPROVED, f.controller.resume())
        }
    }

    @Test fun `failed or throwing containment never reports completion or verified containment`() {
        for (edit in listOf(false, true)) for (point in listOf("false", "throw", "readback")) {
            val f = AppApprovalControllerTest(); val action = f.windowAction(edit)
            f.failure = "policy-readback"
            f.atEvent = { event ->
                if (event == "policy-readback" && point == "false") f.containmentWorks = false
                if (event == "contain" && "policy-readback" in f.events) {
                    if (point == "throw") throw IllegalStateException("containment failure")
                    if (point == "readback") f.failure = "contain-readback"
                }
            }
            assertEquals(AppApprovalOutcome.NOT_READY, action())
            assertEquals(setOf(f.app), f.containmentRequests.last())
            assertEquals(AppApprovalPhase.COMMITTED, f.state!!.pending!!.phase)
            assertFalse("save-finished" in f.events)
            if (point != "readback") {
                assertFalse(f.hidden); assertTrue(f.app in f.appliedLockTask && f.app in f.appliedHome)
            }
        }
    }

    @Test fun `containment targets all discretionary activation packages and preserves floor access`() {
        for (edit in listOf(false, true)) {
            val f = AppApprovalControllerTest(); val action = f.windowAction(edit)
            val extra = PackageName("test.existing")
            val identity = ApprovalBinding(extra, setOf(f.signer))
            val record = AppApprovalRecord("existing", PermissionSelection(PermissionManifest(identity, 1, "build", emptyList()), emptyMap()))
            f.state = f.state!!.replacing(apps = f.state!!.apps + (extra to record))
            f.policy = f.policy.copy(installed = f.policy.installed + InstalledPackage(extra, false, 1),
                installedIdentities = f.policy.installedIdentities + identity)
            f.failure = "policy-readback"
            assertEquals(AppApprovalOutcome.NOT_READY, action())
            assertEquals(setOf(f.app, extra), f.containmentRequests.last())
            assertTrue(f.containmentRequests.last().intersect(setOf(f.home, f.sms, f.dialer, PLAY_PACKAGE)).isEmpty())
            assertTrue(setOf(f.home, f.sms, f.dialer).all { it in f.appliedLockTask && it in f.appliedHome })
            assertFalse(extra in f.appliedLockTask || extra in f.appliedHome)
        }
    }

    @Test fun `successful activation and pre apply failure only contain the pending target`() {
        for (edit in listOf(false, true)) {
            val f = AppApprovalControllerTest(); val action = f.windowAction(edit)
            assertEquals(if (edit) AppApprovalOutcome.PERMISSIONS_CHANGED else AppApprovalOutcome.APPROVED, action())
            assertTrue(f.containmentRequests.isNotEmpty())
            assertTrue(f.containmentRequests.all { it == setOf(f.app) })
            val blocked = AppApprovalControllerTest(); val blockedAction = blocked.windowAction(edit)
            blocked.failure = "save-committed"
            assertEquals(AppApprovalOutcome.NOT_READY, blockedAction())
            assertTrue(blocked.containmentRequests.all { it == setOf(blocked.app) })
        }
    }

    private fun failedActivationAndContainment(edit: Boolean) {
        val action = windowAction(edit)
        failure = "policy-readback"
        // Fail cleanup only after activation has exposed the app.
        atEvent = { if (it == "policy-readback") containmentWorks = false }
        assertEquals(AppApprovalOutcome.NOT_READY, action())
        assertEquals(AppApprovalPhase.COMMITTED, state!!.pending!!.phase)
        assertFalse(hidden); assertTrue(app in appliedLockTask && app in appliedHome)
        assertEquals(setOf(app), containmentRequests.last())
        atEvent = null; failure = null; containmentWorks = true
        events.clear(); containmentRequests.clear()
    }

    private fun retryContainment(edit: Boolean, gate: String, restart: Boolean) {
        failedActivationAndContainment(edit)
        val pending = state!!.pending
        val receipts = state!!.receipts
        val permissions = actualPermissions
        when (gate) {
            "battery" -> battery = BatteryState(4, false)
            "permissions" -> actualPermissions = emptyMap()
            "plan" -> inputAvailable = false
            "session" -> failure = "revoke"
        }
        val result = if (restart) { controller = controller(); controller.onStart() } else controller.resume()
        assertEquals(if (gate == "battery") AppApprovalOutcome.LOW_BATTERY else AppApprovalOutcome.NOT_READY, result)
        assertTrue(containmentRequests.isNotEmpty(), "cleanup skipped: edit=$edit gate=$gate restart=$restart")
        assertTrue(containmentRequests.all { it == setOf(app) })
        assertEquals(listOf("contain", "contain-readback"), events.take(2))
        assertTrue(hidden); assertFalse(app in appliedLockTask || app in appliedHome)
        assertSame(pending, state!!.pending); assertEquals(receipts, state!!.receipts)
        assertFalse("unhide" in events)
        battery = BatteryState(50, false); actualPermissions = permissions; inputAvailable = true; failure = null
        assertEquals(if (edit) AppApprovalOutcome.PERMISSIONS_CHANGED else AppApprovalOutcome.APPROVED,
            if (gate == "session") controller.onStart() else controller.resume())
        assertNull(state!!.pending); assertEquals(receipts, state!!.receipts)
        assertFalse(hidden); assertTrue(app in appliedLockTask && app in appliedHome)
    }

    @Test fun `approval retry contains before battery gate`() = retryContainment(false, "battery", false)

    @Test fun `approval retry contains before permissions gate`() = retryContainment(false, "permissions", false)

    @Test fun `approval retry contains before plan gate`() = retryContainment(false, "plan", false)

    @Test fun `approval restart contains before battery gate`() = retryContainment(false, "battery", true)

    @Test fun `approval restart contains before permissions gate`() = retryContainment(false, "permissions", true)

    @Test fun `approval restart contains before plan gate`() = retryContainment(false, "plan", true)

    @Test fun `approval restart contains before session gate`() = retryContainment(false, "session", true)

    @Test fun `edit retry contains before battery gate`() = retryContainment(true, "battery", false)

    @Test fun `edit retry contains before permissions gate`() = retryContainment(true, "permissions", false)

    @Test fun `edit retry contains before plan gate`() = retryContainment(true, "plan", false)

    @Test fun `edit restart contains before battery gate`() = retryContainment(true, "battery", true)

    @Test fun `edit restart contains before permissions gate`() = retryContainment(true, "permissions", true)

    @Test fun `edit restart contains before plan gate`() = retryContainment(true, "plan", true)

    @Test fun `edit restart contains before session gate`() = retryContainment(true, "session", true)

    @Test fun `unverified recovery cleanup retains journal and never activates`() {
        for (edit in listOf(false, true)) for (restart in listOf(false, true)) for (point in listOf("false", "throw", "readback")) {
            val f = AppApprovalControllerTest(); f.failedActivationAndContainment(edit)
            when (point) {
                "false" -> f.containmentWorks = false
                "throw" -> f.atEvent = { if (it == "contain") error("cleanup unavailable") }
                else -> f.failure = "contain-readback"
            }
            f.battery = BatteryState(4, false)
            val pending = f.state!!.pending
            assertEquals(AppApprovalOutcome.NOT_READY,
                if (restart) { f.controller = f.controller(); f.controller.onStart() } else f.controller.resume())
            assertSame(pending, f.state!!.pending); assertFalse("unhide" in f.events)
        }
    }

    @Test fun `low battery committed recovery preserves unrelated approved access`() {
        for (edit in listOf(false, true)) {
            val f = AppApprovalControllerTest(); val action = f.windowAction(edit)
            val extra = PackageName("test.existing")
            val identity = ApprovalBinding(extra, setOf(f.signer))
            val record = AppApprovalRecord("existing", PermissionSelection(PermissionManifest(identity, 1, "build", emptyList()), emptyMap()))
            f.state = f.state!!.replacing(apps = f.state!!.apps + (extra to record))
            f.policy = f.policy.copy(installed = f.policy.installed + InstalledPackage(extra, false, 1),
                installedIdentities = f.policy.installedIdentities + identity)
            f.appliedLockTask += extra; f.appliedHome += extra
            f.atEvent = { if (it == "after-save-committed") f.battery = BatteryState(4, false) }
            assertEquals(AppApprovalOutcome.LOW_BATTERY, action())
            assertEquals(AppApprovalPhase.COMMITTED, f.state!!.pending!!.phase)
            assertTrue(f.containmentRequests.isNotEmpty())
            assertTrue(f.containmentRequests.all { it == setOf(f.app) })
            assertTrue(extra in f.appliedLockTask && extra in f.appliedHome)
            assertFalse("unhide" in f.events)
        }
    }

    @Test fun `editing recovery revokes lock task and Home before low battery or missing plan`() {
        for (gate in listOf("battery", "plan")) for (restart in listOf(false, true)) {
            val f = AppApprovalControllerTest(); val action = f.windowAction(true)
            f.failure = "hide"
            assertEquals(AppApprovalOutcome.NOT_READY, action())
            assertEquals(AppApprovalPhase.EDITING, f.state!!.pending!!.phase)
            f.failure = null; f.events.clear(); f.containmentRequests.clear()
            if (gate == "battery") f.battery = BatteryState(4, false) else f.inputAvailable = false
            assertEquals(if (gate == "battery") AppApprovalOutcome.LOW_BATTERY else AppApprovalOutcome.NOT_READY,
                if (restart) { f.controller = f.controller(); f.controller.onStart() } else f.controller.resume())
            assertTrue(f.containmentRequests.isNotEmpty())
            assertTrue(f.hidden); assertFalse(f.app in f.appliedLockTask || f.app in f.appliedHome)
            assertEquals(AppApprovalPhase.EDITING, f.state!!.pending!!.phase)
        }
    }


    @Test fun `recovery never supplies a target that became protected to containment`() {
        for (edit in listOf(false, true)) for (kind in listOf("floor", "inventory", "home", "messages")) {
            val f = AppApprovalControllerTest(); f.failedActivationAndContainment(edit)
            f.policy = when (kind) {
                "floor" -> f.policy.copy(installed = f.policy.installed.map { if (it.name == f.app) it.copy(isSystem = true) else it },
                    identities = f.policy.identities + FloorIdentity(f.app, FloorRole.EMERGENCY_DIALER, IdentitySource.SYSTEM_ONLY_QUERY))
                "inventory" -> f.policy.copy(inventory = BuildInventory("model", "build", 1,
                    NeverHideList(setOf(f.sms, f.app)), setOf(f.sms), emptySet()))
                "home" -> f.policy.copy(plainphone = f.app)
                else -> f.policy.copy(messages = f.app)
            }
            f.battery = BatteryState(4, false)
            assertEquals(AppApprovalOutcome.NOT_READY, f.controller.resume())
            assertTrue(f.containmentRequests.isEmpty()); assertFalse(f.hidden)
            assertEquals(AppApprovalPhase.COMMITTED, f.state!!.pending!!.phase)
        }
    }

    @Test fun `earlier containment of other approved apps is not undone at low battery`() {
        for (edit in listOf(false, true)) {
            val f = AppApprovalControllerTest(); val action = f.windowAction(edit)
            val extra = PackageName("test.existing")
            val identity = ApprovalBinding(extra, setOf(f.signer))
            val record = AppApprovalRecord("existing", PermissionSelection(PermissionManifest(identity, 1, "build", emptyList()), emptyMap()))
            f.state = f.state!!.replacing(apps = f.state!!.apps + (extra to record))
            f.policy = f.policy.copy(installed = f.policy.installed + InstalledPackage(extra, false, 1),
                installedIdentities = f.policy.installedIdentities + identity)
            f.failure = "policy-readback"
            assertEquals(AppApprovalOutcome.NOT_READY, action())
            assertEquals(setOf(f.app, extra), f.containmentRequests.last())
            f.failure = null; f.battery = BatteryState(4, false); f.events.clear(); f.containmentRequests.clear()
            assertEquals(AppApprovalOutcome.LOW_BATTERY, f.controller.resume())
            assertEquals(listOf(setOf(f.app)), f.containmentRequests)
            assertFalse(extra in f.appliedLockTask || extra in f.appliedHome); assertFalse("unhide" in f.events)
            f.battery = BatteryState(50, false)
            assertEquals(if (edit) AppApprovalOutcome.PERMISSIONS_CHANGED else AppApprovalOutcome.APPROVED, f.controller.resume())
            assertTrue(extra in f.appliedLockTask && extra in f.appliedHome)
        }
    }


    @Test fun pla313ThrowingFactsMustStillContain() {
        val skipped = mutableListOf<Boolean>()
        for (restart in listOf(false, true)) {
            val f = AppApprovalControllerTest(); f.failedActivationAndContainment(false)
            f.pla313InputThrows = true
            val result = if (restart) f.controller().onStart() else f.controller.resume()
            assertEquals(AppApprovalOutcome.NOT_READY, result)
            println("throwing facts restart=$restart hidden=${f.hidden} lock=${f.app in f.appliedLockTask} home=${f.app in f.appliedHome} containment=${f.containmentRequests}")
            if (f.containmentRequests.isEmpty()) skipped += restart
        }
        assertTrue(skipped.isEmpty(), "facts exception skipped containment: restart=$skipped")
    }

    @Test fun pla313WithdrawalMustNotDropPendingContainment() {
        failedActivationAndContainment(false)
        val r = assertNotNull(controller.prepareWithdrawal(person)); val a = withdrawalAuth(r)
        inputAvailable = false
        assertEquals(AppApprovalOutcome.NOT_READY, controller.withdrawPending(r, r.approvalId, a))
        assertEquals(AppApprovalPhase.WITHDRAWING, state!!.pending!!.phase)
        println("withdrawal hidden=$hidden lock=${app in appliedLockTask} home=${app in appliedHome} containment=$containmentRequests")
        inputAvailable = true; failure = "revoke"
        assertEquals(AppApprovalOutcome.NOT_READY, controller().onStart())
        println("withdrawal restart hidden=$hidden lock=${app in appliedLockTask} home=${app in appliedHome} containment=$containmentRequests")
        assertFalse(app in appliedLockTask, "withdrawal must preserve pending containment across missing facts and restart")
    }

    @Test fun `editing hide failure still attempts independent containment on edit retry and start`() {
        val failures = mutableListOf<String>()
        for (entry in listOf("edit", "retry", "start")) for (throwing in listOf(false, true)) {
            val f = AppApprovalControllerTest(); val action = f.windowAction(true)
            f.failure = "hide"
            f.atEvent = { if (it == "hide" && throwing) error("hide unavailable") }
            val first = runCatching { action() }
            if (entry != "edit") {
                f.hidden = false; f.appliedLockTask += f.app; f.appliedHome += f.app
                f.events.clear(); f.containmentRequests.clear()
            }
            val result = when (entry) {
                "retry" -> runCatching { f.controller.resume() }
                "start" -> runCatching { f.controller().onStart() }
                else -> first
            }
            try {
                assertEquals(AppApprovalOutcome.NOT_READY, result.getOrNull())
                assertEquals(AppApprovalPhase.EDITING, f.state!!.pending!!.phase)
                assertTrue(f.containmentRequests.isNotEmpty())
                assertFalse(f.app in f.appliedLockTask || f.app in f.appliedHome)
            } catch (e: AssertionError) { failures += "$entry throwing=$throwing: ${e.message}" }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    // Private recovery seams are invoked to exercise their own entry guard, independently of resume.
    private fun recoverySeam(name: String): AppApprovalOutcome {
        val method = AppApprovalController::class.java.getDeclaredMethod(name,
            AppApprovalState::class.java, AppApprovalOperation::class.java)
        method.isAccessible = true
        return method.invoke(controller, state!!, state!!.pending!!) as AppApprovalOutcome
    }

    @Test fun `pending target containment matrix across entries phases and failed recovery gates`() {
        val failures = mutableListOf<String>()
        var cases = 0
        for (entry in listOf("resume", "finishPermissionEdit", "onStart", "withdraw"))
            for (phase in listOf(AppApprovalPhase.COMMITTED, AppApprovalPhase.EDITING, AppApprovalPhase.WITHDRAWING))
                for (fault in listOf("facts-null", "facts-throw", "hide", "revoke", "plan", "battery")) {
                    val f = AppApprovalControllerTest(); f.failedActivationAndContainment(true)
                    val op = f.state!!.pending!!
                    val receipt = AppApprovalReceipt("matrix-withdraw", f.person, mapOf(f.person to 0L),
                        AppApprovalReceiptKind.WITHDRAWN, op.record.id)
                    f.state = f.state!!.replacing(pending = AppApprovalOperation(op.record, op.receipt, phase,
                        receipt.takeIf { phase == AppApprovalPhase.WITHDRAWING }))
                    val extra = f.addUnrelatedApprovedApp()
                    val request = if (entry == "withdraw" && phase != AppApprovalPhase.WITHDRAWING)
                        assertNotNull(f.controller.prepareWithdrawal(f.person)) else null
                    val auth = request?.let { f.withdrawalAuth(it) }
                    when (fault) {
                        "facts-null" -> f.inputAvailable = false
                        "facts-throw" -> f.pla313InputThrows = true
                        "hide" -> f.failure = "hide"
                        "revoke" -> f.failure = "revoke"
                        "plan" -> f.policy = f.policy.copy(lockedBoot = true)
                        "battery" -> f.battery = BatteryState(4, false)
                    }
                    // A non-sticking target hide is part of containment in every phase.
                    // The fake still removes independent lock-task/Home access before reporting failure.
                    if (fault == "hide") f.atEvent = { if (it == "contain-readback") f.failure = "contain-readback" }
                    val receipts = f.state!!.receipts
                    f.events.clear(); f.containmentRequests.clear()
                    val result = runCatching {
                        when (entry) {
                            "onStart" -> f.controller().onStart()
                            "finishPermissionEdit" -> f.recoverySeam("finishPermissionEdit")
                            "withdraw" -> if (request != null) f.controller.withdrawPending(request, request.approvalId, auth!!)
                                else f.recoverySeam("finishWithdrawal") // already-authorized durable withdrawal
                            else -> f.controller.resume()
                        }
                    }
                    try {
                        assertTrue(result.getOrNull() in setOf(AppApprovalOutcome.NOT_READY, AppApprovalOutcome.LOW_BATTERY))
                        assertEquals("contain", f.events.firstOrNull())
                        assertTrue(f.containmentRequests.isNotEmpty())
                        assertTrue(f.containmentRequests.all { it == setOf(f.app) })
                        assertNotNull(f.state!!.pending)
                        assertEquals(receipts, f.state!!.receipts)
                        assertTrue(extra in f.appliedLockTask && extra in f.appliedHome)
                        assertTrue(extra in f.state!!.apps)
                    } catch (e: AssertionError) { failures += "$entry/$phase/$fault: ${e.message}" }
                    cases++
                }
        assertEquals(72, cases)
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun addUnrelatedApprovedApp(): PackageName {
        val extra = PackageName("test.unrelated")
        val identity = ApprovalBinding(extra, setOf(signer))
        val extraManifest = PermissionManifest(identity, 1, "build", emptyList())
        val record = AppApprovalRecord("unrelated-approval", PermissionSelection(extraManifest, emptyMap()))
        state = state!!.replacing(apps = state!!.apps + (extra to record))
        policy = policy.copy(installed = policy.installed + InstalledPackage(extra, false, 1),
            installedIdentities = policy.installedIdentities + identity)
        appliedLockTask += extra; appliedHome += extra
        return extra
    }

    private fun assertRestrictiveEditWithdrawal() {
        assertNull(state!!.pending)
        assertFalse(app in state!!.apps)
        assertEquals(binding, state!!.removedApps[app])
        assertTrue(hidden)
        assertFalse(app in appliedLockTask || app in appliedHome)
        assertFalse(app in state!!.approvedPackages(policy.installed, policy.installedIdentities))
        assertEquals(AppApprovalReceiptKind.WITHDRAWN, state!!.receipts.last().kind)
        assertFalse(events.any { it == "unhide" || it == "window-unhide" || it.startsWith("write-") })
    }

    @Test fun `withdrawn edit completes restrictively only after verified cleanup`() {
        for (fault in listOf("facts", "session", "battery", "permissions", "readback", "save")) {
            val f = AppApprovalControllerTest(); f.failedActivationAndContainment(true)
            val r = assertNotNull(f.controller.prepareWithdrawal(f.person)); val a = f.withdrawalAuth(r)
            val receipts = f.state!!.receipts
            // Inject after durable authorization so every failure retains WITHDRAWING.
            f.atEvent = { event ->
                if (event == "after-save-withdrawing") when (fault) {
                    "facts" -> f.inputAvailable = false
                    "session" -> f.failure = "revoke"
                    // Battery and permissions do not gate cleanup. Fail readback first,
                    // then recover while these conditions remain present.
                    "battery" -> { f.battery = BatteryState(4, false); f.failure = "policy-readback" }
                    "permissions", "readback" -> f.failure = "policy-readback"
                    "save" -> f.failure = "save-finished"
                }
                if (fault == "permissions" && event == "permissions-readback") error("permissions unavailable")
            }
            assertEquals(AppApprovalOutcome.NOT_READY, f.controller.withdrawPending(r, r.approvalId, a), fault)
            assertEquals(AppApprovalPhase.WITHDRAWING, f.state!!.pending!!.phase)
            assertEquals(receipts, f.state!!.receipts)
            val intent = f.state!!.pending!!.withdrawalReceipt
            assertTrue(f.hidden)
            assertFalse(f.app in f.appliedLockTask || f.app in f.appliedHome, fault)
            assertFalse(f.events.any { it == "unhide" || it == "window-unhide" || it.startsWith("write-") }, fault)
            f.inputAvailable = true; f.failure = null
            assertEquals(AppApprovalOutcome.WITHDRAWN, f.controller().onStart(), fault)
            f.assertRestrictiveEditWithdrawal()
            assertEquals(receipts + assertNotNull(intent), f.state!!.receipts)
        }
    }

    @Test fun `withdrawing a loosened permission edit never writes ALLOW or exposes the app`() {
        activeApproval(true)
        val deny = editRequest()
        permissionReadback = mapOf(camera to PermissionReadback.DENIED)
        assertEquals(AppApprovalOutcome.PERMISSIONS_CHANGED, controller.editPermissions(deny, deny.approvalId, editAuth(deny)))
        assertEquals(PermissionChoice.DENY, state!!.apps.getValue(app).permissions.choices[camera])
        val allow = editRequest(choice = PermissionChoice.ALLOW)
        failure = "hide"
        assertEquals(AppApprovalOutcome.NOT_READY, controller.editPermissions(allow, allow.approvalId, editAuth(allow)))
        assertEquals(PermissionChoice.ALLOW, state!!.pending!!.record.permissions.choices[camera])
        val withdrawal = assertNotNull(controller.prepareWithdrawal(other))
        failure = null; events.clear()
        assertTrue(hidden)
        atEvent = { assertTrue(hidden, "app visible at $it") }
        assertEquals(AppApprovalOutcome.WITHDRAWN,
            controller.withdrawPending(withdrawal, withdrawal.approvalId, withdrawalAuth(withdrawal, other)))
        assertRestrictiveEditWithdrawal()
        assertEquals(PermissionReadback.DENIED, actualPermissions[camera])
        assertFalse(events.any { it.startsWith("write-ALLOW") })
    }

    @Test fun `low battery and unavailable permissions do not hold restrictive edit withdrawal`() {
        activeApproval(true); val edit = editRequest(); failure = "hide"
        assertEquals(AppApprovalOutcome.NOT_READY, controller.editPermissions(edit, edit.approvalId, editAuth(edit)))
        val withdrawal = assertNotNull(controller.prepareWithdrawal(person))
        failure = null; battery = BatteryState(4, false); events.clear()
        atEvent = { if (it == "permissions-readback") error("permissions unavailable") }
        assertEquals(AppApprovalOutcome.WITHDRAWN,
            controller.withdrawPending(withdrawal, withdrawal.approvalId, withdrawalAuth(withdrawal)))
        assertRestrictiveEditWithdrawal()
        assertFalse("permissions-readback" in events)
    }


    @Test fun `unverified containment retains every phase even before controller initialization`() {
        for (phase in AppApprovalPhase.entries) for (entry in listOf("resume", "start"))
            for (fault in listOf("false", "throw", "readback")) {
                val f = AppApprovalControllerTest(); f.failedActivationAndContainment(phase == AppApprovalPhase.EDITING)
                val op = f.state!!.pending!!
                val finalKind = when (phase) {
                    AppApprovalPhase.WITHDRAWING -> AppApprovalReceiptKind.WITHDRAWN
                    AppApprovalPhase.REMOVING -> AppApprovalReceiptKind.REMOVED
                    else -> null
                }
                val receipt = finalKind?.let { AppApprovalReceipt("final-receipt", f.person,
                    mapOf(f.person to 0L), it, op.record.id) }
                f.state = f.state!!.replacing(
                    apps = if (phase == AppApprovalPhase.PREPARING) emptyMap() else f.state!!.apps,
                    receipts = if (phase == AppApprovalPhase.PREPARING) emptyList() else f.state!!.receipts,
                    pending = AppApprovalOperation(op.record, op.receipt, phase, receipt))
                val extra = f.addUnrelatedApprovedApp()
                val before = f.state
                when (fault) {
                    "false" -> f.containmentWorks = false
                    "throw" -> f.atEvent = { if (it == "contain") error("containment unavailable") }
                    "readback" -> f.failure = "contain-readback"
                }
                f.events.clear(); f.containmentRequests.clear()
                val cold = f.controller()
                assertEquals(AppApprovalOutcome.NOT_READY, if (entry == "resume") cold.resume() else cold.onStart())
                assertEquals("contain", f.events.first(), "$phase/$entry/$fault")
                assertEquals(listOf(setOf(f.app)), f.containmentRequests)
                assertSame(before, f.state)
                assertTrue(extra in f.appliedLockTask && extra in f.appliedHome)
                assertFalse("revoke" in f.events || "unhide" in f.events)
            }
    }

}
