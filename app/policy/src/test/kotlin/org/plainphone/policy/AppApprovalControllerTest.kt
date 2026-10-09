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
    private var crash: String? = null
    private var failure: String? = null
    private var beforeSave: (() -> Unit)? = null
    private fun event(name: String): Boolean {
        events += name
        if (crash == name) error("simulated process death")
        return failure != name
    }
    private val store = object : AppApprovalStore {
        override fun read() = state
        override fun compareAndSet(expected: AppApprovalState, replacement: AppApprovalState, expectedCredentials: CredentialState?): Boolean {
            beforeSave?.invoke()
            val stage = when {
                replacement.pending?.phase == AppApprovalPhase.PREPARING -> "save-preparing"
                replacement.pending?.phase == AppApprovalPhase.COMMITTED -> "save-committed"
                else -> "save-finished"
            }
            if (!event(stage) || state !== expected || (expectedCredentials != null && credentials !== expectedCredentials)) return false
            state = replacement
            event("after-$stage")
            return true
        }
    }
    private var journal: SessionJournal = SessionJournal.Empty
    private val inventory = BuildInventory("model", "build", 1, NeverHideList(setOf(sms, PLAY_PACKAGE)), setOf(sms), setOf(PackageName("test.settings")))
    private val sessions = SessionController(object : JournalStore {
        override fun read() = journal
        override fun compareAndSet(expected: SessionJournal, replacement: SessionJournal): Boolean {
            if (journal != expected) return false
            journal = replacement; return true
        }
    }, object : SessionEffects {
        override fun revokeAllRelaxations() = event("revoke")
        override fun open(access: SessionAccess) = true
        override fun protectedStateCheck() = event("protected")
        override fun readBackPolicy() = event("session-readback")
    }, clock, inventory, vault)
    private var manifest: PermissionManifest? = PermissionManifest(binding, 1, "build", listOf(
        RuntimePermission(camera, "Camera", listOf("Taking photos"), PermissionGrantCapability.SUPPORTED)))
    private var policy = PolicyInput("model", "build", home, sms,
        listOf(home, sms, dialer, PLAY_PACKAGE).map { InstalledPackage(it, true, 1) } + InstalledPackage(app, false, 1),
        listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)), inventory,
        installedIdentities = listOf(binding))
    private var battery = BatteryState(50, false)
    private var permissionReadback = mapOf(camera to PermissionReadback.GRANTED)
    private val effects = object : AppApprovalEffects {
        override fun hideAndReadBack(identity: ApprovalBinding) = event("hide")
        override fun setPermission(identity: ApprovalBinding, write: PermissionWrite) = event("permission")
        override fun readPermissions(identity: ApprovalBinding): Map<PermissionName, PermissionReadback> {
            event("permissions-readback"); return permissionReadback
        }
        override fun applyPolicy(plan: PolicyPlan): Boolean {
            assertNotNull(state!!.apps[app], "unhide before persistent approval")
            assertTrue(app in plan.unhide && app in plan.lockTaskPackages)
            assertFalse(PLAY_PACKAGE in plan.lockTaskPackages)
            return event("unhide")
        }
        override fun readBackPolicy(plan: PolicyPlan) = event("policy-readback")
    }
    private var id = 0
    private fun controller() = AppApprovalController(store, { credentials }, vault, sessions, clock,
        { policy }, { manifest }, { battery }, effects, AppApprovalIdSource { "approval-${++id}" })
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
        for (point in listOf("after-save-preparing", "permission", "permissions-readback", "after-save-committed", "unhide", "policy-readback", "after-save-finished")) {
            state = AppApprovalState(); setup(); val r = request(); val a = auth(r); crash = point
            assertFailsWith<IllegalStateException> { controller.approve(r, a) }
            val committed = state!!.apps.isNotEmpty()
            crash = null; events.clear(); controller = controller()
            val result = controller.onStart()
            assertEquals("revoke", events.first())
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
}
