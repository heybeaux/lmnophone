// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class ElevationControllerTest {
    private val person = TrustedPersonId("self")
    private val home = PackageName("test.home")
    private val sms = PackageName("test.sms")
    private val dialer = PackageName("test.dialer")
    private val settings = PackageName("test.settings")
    private val app = PackageName("test.app")
    private val identity = ApprovalBinding(app, setOf(CertDigest("ab".repeat(32))))
    private val pin get() = CharArray(6) { '1' }
    private var now: ClockReading? = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
    private var checkpointHook: (() -> Unit)? = null
    private val clock = RunningClock { checkpointHook?.invoke(); now }
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
    }, SecretEntropy { n -> ByteArray(n) { (++entropy).toByte() } }, clock)
    private var record: ElevationRecord? = ElevationRecord()
    private var limits = ElevationLimits()
    private var battery = BatteryState(50, false)
    private val inventory = BuildInventory("model", "build", 1, NeverHideList(setOf(sms, PLAY_PACKAGE)), setOf(sms), setOf(settings))
    private var policy: PolicyInput? = PolicyInput("model", "build", home, sms,
        listOf(home, sms, dialer, PLAY_PACKAGE, settings).map { InstalledPackage(it, true, 1) } + InstalledPackage(app, false, 1),
        listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)), inventory,
        installedIdentities = listOf(identity))
    private val events = mutableListOf<String>()
    private var crash: String? = null
    private var failure: String? = null
    private var hook: ((String) -> Unit)? = null
    private fun event(name: String): Boolean {
        events += name; hook?.invoke(name)
        if (crash == name) error("simulated process death")
        return failure != name
    }
    private val store = object : ElevationStore {
        override fun read() = record
        override fun compareAndSet(expected: ElevationRecord?, replacement: ElevationRecord, expectedCredentials: CredentialState?): Boolean {
            val stage = when {
                replacement.journal is SessionJournal.Active -> "save-${(replacement.journal as SessionJournal.Active).phase}"
                replacement.state.countdown != null -> "save-countdown"
                else -> "save-clear"
            }
            if (!event(stage) || record !== expected || (expectedCredentials != null && credentials !== expectedCredentials)) return false
            record = replacement; event("after-$stage"); return true
        }
    }
    private var physicallyOpen = false
    private val effects = object : SessionEffects {
        override fun revokeAllRelaxations(): Boolean {
            if (!event("revoke")) return false
            physicallyOpen = false; return true
        }
        override fun open(access: SessionAccess): Boolean {
            assertNotNull(record!!.state.grant)
            assertEquals(access.id, record!!.state.ledger.debits.last().id)
            assertEquals(SessionPhase.OPENING, (record!!.journal as SessionJournal.Active).phase)
            if (!event("open")) return false
            physicallyOpen = true; event("after-open"); return true
        }
        override fun protectedStateCheck() = event("protected")
        override fun readBackPolicy() = event("readback")
    }
    private var id = 0
    private fun controller() = ElevationController(store, { credentials }, vault, clock, { limits }, { policy },
        { battery }, effects, AppApprovalIdSource { "elevation-${++id}" })
    private var controller = controller()
    private fun setup() {
        credentials = CredentialState(Mode.SELF_MANAGED, mapOf(person to vault.prepareCredential(pin).credential))
        assertEquals(ElevationOutcome.IDLE, controller.onStart()); events.clear()
    }
    private fun advance(ms: Long) { now = now!!.copy(proven = now!!.proven.copy(totalMillis = now!!.proven.totalMillis + ms),
        boot = now!!.boot.copy(elapsedMillis = now!!.boot.elapsedMillis + ms)) }
    private fun prepare(target: ElevationTarget = ElevationTarget.Play) = assertNotNull(controller.prepare(person, target))
    private fun auth(r: ElevationRequest) = assertIs<PinResult.Authorized>(vault.authenticate(person, pin, r.action)).authority
    private fun request(target: ElevationTarget = ElevationTarget.Play): ElevationRequest {
        val r = prepare(target); assertEquals(ElevationOutcome.COUNTING, controller.request(r, auth(r))); return r
    }
    private fun start(r: ElevationRequest): ElevationOutcome = controller.start(r.id, r.target)

    @Test fun `PIN is consumed for the exact countdown at request and no PIN is needed at Start`() {
        setup(); val r = prepare(); val a = auth(r)
        assertEquals(ElevationOutcome.COUNTING, controller.request(r, a))
        assertFalse(vault.consumeFor(a, r.action, person)); assertFalse(physicallyOpen)
        assertEquals(ElevationOutcome.REFUSED, start(r)); advance(600_000)
        assertEquals(ElevationOutcome.REFUSED, controller.start(r.id, ElevationTarget.Settings))
        assertEquals(ElevationOutcome.STARTED, start(r)); assertTrue(physicallyOpen)
        assertEquals(1, record!!.state.ledger.debits.size)
        assertEquals(ElevationOutcome.REFUSED, start(r)); assertEquals(1, record!!.state.ledger.debits.size)
        assertNotNull(controller.access()); assertEquals(1_200_000, controller.access()!!.deadline.elapsedMillis)
    }
    @Test fun `request refuses wrong action stale credentials and replays`() {
        setup(); val r = prepare(); val wrong = assertIs<PinResult.Authorized>(vault.authenticate(person, pin, PrivilegedAction("wrong", r.id))).authority
        assertEquals(ElevationOutcome.REFUSED, controller.request(r, wrong)); assertNull(record!!.state.countdown)
        val replacement = prepare(); val a = auth(replacement)
        credentials = credentials!!.replacing(person, credentials!!.people.getValue(person).copy(generation = 1))
        assertEquals(ElevationOutcome.REFUSED, controller.request(replacement, a))
        val good = request(); assertEquals(ElevationOutcome.REFUSED, controller.request(good, auth(good)))
    }
    @Test fun `credential CAS failure cannot create a countdown or reuse consumed PIN`() {
        setup(); val r = prepare(); val a = auth(r)
        hook = { if (it == "save-countdown") credentials = credentials!!.replacing(person, credentials!!.people.getValue(person)) }
        assertEquals(ElevationOutcome.NOT_READY, controller.request(r, a)); hook = null
        assertNull(record!!.state.countdown); assertFalse(vault.consumeFor(a, r.action, person))
    }
    @Test fun `countdown cancellation lapse limits and epoch repair do not debit early`() {
        setup(); val cancelled = request(); assertEquals(ElevationOutcome.ENDED, controller.cancel(cancelled.id))
        val lapse = request(); advance(900_000); assertEquals(ElevationOutcome.LAPSED, start(lapse))
        assertTrue(record!!.state.ledger.debits.isEmpty())
        val r = request(); advance(600_000); limits = ElevationLimits(dailyLimit = 0)
        assertEquals(ElevationOutcome.LIMIT_REACHED, start(r)); assertTrue(record!!.state.ledger.debits.isEmpty())
        limits = ElevationLimits(countdownMillis = 1_200_000)
        assertEquals(ElevationOutcome.COUNTING, controller.tick()); advance(600_000)
        now = now!!.copy(proven = RunningTime("repaired", 0))
        assertEquals(ElevationOutcome.STARTED, start(r)); assertEquals("repaired", record!!.state.ledger.debits.single().at.epoch)
    }
    @Test fun `Start rejects live mode inventory battery operation and credential changes`() {
        for (change in listOf("mode", "generation", "structure", "inventory", "locked", "battery", "clock")) {
            val f = ElevationControllerTest(); f.setup(); val r = f.request(); f.advance(600_000)
            when (change) {
                "mode" -> f.credentials = CredentialState(Mode.TRUSTED, f.credentials!!.people)
                "generation" -> f.credentials = f.credentials!!.replacing(f.person, f.credentials!!.people.getValue(f.person).copy(generation = 1))
                "structure" -> f.credentials = CredentialState(Mode.SELF_MANAGED, f.credentials!!.people, structureGeneration = 1)
                "inventory" -> f.policy = f.policy!!.copy(fingerprint = "new")
                "locked" -> f.policy = f.policy!!.copy(lockedBoot = true)
                "battery" -> f.battery = BatteryState(4, false)
                "clock" -> f.now = null
            }
            assertTrue(f.start(r) != ElevationOutcome.STARTED, change); assertFalse(f.physicallyOpen, change)
            assertTrue(f.record!!.state.ledger.debits.isEmpty(), change)
        }
    }
    @Test fun `Start persists debit grant journal together before opening and holds failed effects`() {
        for (point in listOf("save-OPENING", "open", "readback", "save-OPEN")) {
            val f = ElevationControllerTest(); f.setup(); val r = f.request(); f.advance(600_000); f.failure = point
            assertEquals(ElevationOutcome.NOT_READY, f.start(r), point); assertNull(f.controller.access(), point)
            assertEquals(if (point == "save-OPENING") 0 else 1, f.record!!.state.ledger.debits.size, point)
            f.failure = null; assertEquals(ElevationOutcome.IDLE, f.controller.onStart()); assertFalse(f.physicallyOpen)
        }
    }
    @Test fun `crash at every Start boundary revokes first and never refunds a committed debit`() {
        for (point in listOf("save-OPENING", "after-save-OPENING", "open", "after-open", "readback", "save-OPEN", "after-save-OPEN")) {
            val f = ElevationControllerTest(); f.setup(); val r = f.request(); f.advance(600_000); f.crash = point
            assertFailsWith<IllegalStateException>(point) { f.start(r) }
            f.crash = null; f.events.clear(); f.controller = f.controller()
            assertEquals(ElevationOutcome.IDLE, f.controller.onStart(), point)
            assertEquals("revoke", f.events.first(), point); assertFalse(f.physicallyOpen, point)
            assertNull(f.record!!.state.grant); assertNull(f.record!!.state.countdown); assertEquals(SessionJournal.Empty, f.record!!.journal)
            assertEquals(if (point == "save-OPENING") 0 else 1, f.record!!.state.ledger.debits.size, point)
            assertEquals(ElevationOutcome.REFUSED, f.start(r))
        }
    }
    @Test fun `Play candidate history is owned live intake and retained when package access closes`() {
        setup(); val r = request(); advance(600_000); assertEquals(ElevationOutcome.STARTED, start(r))
        assertNull(controller.candidate(app)); assertFalse(controller.installed("other-session", app))
        assertTrue(controller.installed(r.id, app)); assertEquals(identity, controller.candidate(app))
        val deadline = record!!.state.grant!!.deadline
        assertEquals(ElevationOutcome.ACCESS_CLOSED, controller.endPackageAccess(r.id, SessionEnd.LEFT_PACKAGES))
        assertNull(controller.access()); assertFalse(physicallyOpen); assertNotNull(record!!.state.grant)
        assertEquals(deadline, record!!.state.grant!!.deadline); assertEquals(identity, controller.candidate(app))
        assertFalse(controller.installed(r.id, app)); assertEquals(1, record!!.state.ledger.debits.size)
        advance(600_000); assertEquals(ElevationOutcome.LAPSED, controller.tick()); assertNull(controller.candidate(app))
    }
    @Test fun `candidate intake rejects ambiguous changed or missing live identities`() {
        setup(); val r = request(); advance(600_000); start(r)
        policy = policy!!.copy(installedIdentities = listOf(identity, identity)); assertFalse(controller.installed(r.id, app))
        policy = policy!!.copy(installedIdentities = emptyList()); assertFalse(controller.installed(r.id, app))
        policy = policy!!.copy(installedIdentities = listOf(identity)); assertTrue(controller.installed(r.id, app))
        policy = policy!!.copy(installedIdentities = listOf(ApprovalBinding(app, setOf(CertDigest("cd".repeat(32))))))
        assertNull(controller.candidate(app))
    }
    @Test fun `failed closing intent latches access off and retries without losing grant or debit`() {
        setup(); val r = request(); advance(600_000); start(r); failure = "save-CLOSING"
        assertEquals(ElevationOutcome.NOT_READY, controller.endPackageAccess(r.id, SessionEnd.SCREEN_OFF))
        assertNull(controller.access()); assertFalse(physicallyOpen)
        failure = null; assertEquals(ElevationOutcome.ACCESS_CLOSED, controller.tick())
        assertNotNull(record!!.state.grant); assertEquals(SessionJournal.Empty, record!!.journal)
        assertEquals(ElevationOutcome.ENDED, controller.done(r.id)); assertNull(record!!.state.grant)
    }
    @Test fun `closing crash boundaries never resurrect grant on restart`() {
        for (point in listOf("save-CLOSING", "after-save-CLOSING", "revoke", "protected", "readback", "save-clear", "after-save-clear")) {
            val f = ElevationControllerTest(); f.setup(); val r = f.request(); f.advance(600_000); f.start(r); f.crash = point
            assertFailsWith<IllegalStateException>(point) { f.controller.endPackageAccess(r.id, SessionEnd.INCOMING_CALL) }
            f.crash = null; f.events.clear(); f.controller = f.controller()
            assertEquals(ElevationOutcome.IDLE, f.controller.onStart()); assertEquals("revoke", f.events.first())
            assertNull(f.record!!.state.grant); assertFalse(f.physicallyOpen); assertEquals(1, f.record!!.state.ledger.debits.size)
        }
    }
    @Test fun `restart needs no credentials and damaged ledger repair must be durable before use`() {
        setup(); val r = request(); credentials = null; controller = controller(); assertEquals(ElevationOutcome.IDLE, controller.onStart())
        assertNull(record!!.state.countdown); assertEquals(ElevationOutcome.REFUSED, start(r))
        record = null; failure = "save-clear"; assertEquals(ElevationOutcome.NOT_READY, controller.onStart()); assertNull(record)
        failure = null; assertEquals(ElevationOutcome.IDLE, controller.onStart()); assertEquals(3, record!!.state.ledger.debits.size)
        credentials = CredentialState(Mode.SELF_MANAGED, mapOf(person to vault.prepareCredential(pin).credential))
        val blocked = prepare(); assertEquals(ElevationOutcome.LIMIT_REACHED, controller.request(blocked, auth(blocked)))
    }
    @Test fun `Settings access has fixed scope and deadline and other action kinds stay unavailable`() {
        setup(); assertNull(controller.prepare(person, ElevationTarget.Approve(identity)))
        assertNull(controller.prepare(person, ElevationTarget.Update(StagedUpdateBinding("ab".repeat(32), 2))))
        val r = request(ElevationTarget.Settings); advance(600_000); start(r)
        assertEquals(setOf(settings), controller.access()!!.packages); assertFalse(controller.installed(r.id, app))
        advance(300_000); assertNull(controller.access()); assertEquals(ElevationOutcome.LAPSED, controller.tick())
        assertFalse(physicallyOpen); assertEquals(1, record!!.state.ledger.debits.size)
    }
    @Test fun `time or credential changes inside open cannot publish live access`() {
        for (change in listOf("deadline", "credential", "battery")) {
            val f = ElevationControllerTest(); f.setup(); val r = f.request(); f.advance(600_000)
            f.hook = { if (it == "after-open") when (change) {
                "deadline" -> f.advance(600_000)
                "credential" -> f.credentials = f.credentials!!.replacing(f.person, f.credentials!!.people.getValue(f.person).copy(generation = 1))
                "battery" -> f.battery = BatteryState(4, false)
            } }
            assertEquals(ElevationOutcome.NOT_READY, f.start(r), change); assertNull(f.controller.access())
            assertFalse(f.physicallyOpen); assertEquals(1, f.record!!.state.ledger.debits.size)
        }
    }
    @Test fun `credential change during the OPEN write revokes before reporting Start success`() {
        setup(); val r = request(); advance(600_000)
        hook = { if (it == "after-save-OPEN") credentials = credentials!!.replacing(person,
            credentials!!.people.getValue(person).copy(generation = 1)) }
        assertEquals(ElevationOutcome.NOT_READY, start(r))
        assertFalse(physicallyOpen); assertNull(controller.access()); assertEquals(1, record!!.state.ledger.debits.size)
    }
    @Test fun `changed session package scope prevents Start`() {
        setup(); val r = request(ElevationTarget.Settings); advance(600_000)
        policy = policy!!.copy(inventory = BuildInventory("model", "build", 1, NeverHideList(setOf(sms)), setOf(sms), emptySet()))
        assertEquals(ElevationOutcome.REFUSED, start(r)); assertFalse(physicallyOpen)
    }
    @Test fun `Start credential compare failure has no debit and no effects`() {
        setup(); val r = request(); advance(600_000)
        hook = { if (it == "save-OPENING") credentials = credentials!!.replacing(person, credentials!!.people.getValue(person)) }
        assertEquals(ElevationOutcome.NOT_READY, start(r)); hook = null
        assertFalse(physicallyOpen); assertTrue(record!!.state.ledger.debits.isEmpty())
        assertEquals(ElevationOutcome.STARTED, start(r)); assertEquals(1, record!!.state.ledger.debits.size)
    }
    @Test fun `closing readback failure keeps access denied and retries the same direction`() {
        for (point in listOf("revoke", "protected", "readback", "save-clear")) {
            val f = ElevationControllerTest(); f.setup(); val r = f.request(); f.advance(600_000); f.start(r); f.failure = point
            assertEquals(ElevationOutcome.NOT_READY, f.controller.endPackageAccess(r.id, SessionEnd.LEFT_PACKAGES))
            assertNull(f.controller.access()); assertNull(f.controller.prepare(f.person, ElevationTarget.Settings))
            f.failure = null; assertEquals(ElevationOutcome.ACCESS_CLOSED, f.controller.tick())
            assertFalse(f.physicallyOpen); assertNotNull(f.record!!.state.grant); assertEquals(1, f.record!!.state.ledger.debits.size)
        }
    }
    @Test fun `reboot drops countdown or active grant with debit preserved and no wall clock input`() {
        for (active in listOf(false, true)) {
            val f = ElevationControllerTest(); f.setup(); val r = f.request()
            if (active) { f.advance(600_000); f.start(r) }
            f.now = ClockReading(RunningTime("epoch", 600_100), BootTime("new-boot", 100))
            assertNull(f.controller.access()); assertEquals(ElevationOutcome.LAPSED, f.controller.tick())
            assertNull(f.record!!.state.grant); assertNull(f.record!!.state.countdown)
            assertFalse(f.physicallyOpen); assertEquals(if (active) 1 else 0, f.record!!.state.ledger.debits.size)
        }
    }
    @Test fun `startup failures expose no authority and retries revoke before clearing the journal`() {
        for (point in listOf("revoke", "protected", "readback", "save-clear")) {
            val f = ElevationControllerTest(); f.setup(); val r = f.request(); f.advance(600_000); f.start(r)
            f.controller = f.controller(); f.failure = point
            assertEquals(ElevationOutcome.NOT_READY, f.controller.onStart()); assertNull(f.controller.access())
            assertNull(f.controller.prepare(f.person, ElevationTarget.Play)); assertEquals(1, f.record!!.state.ledger.debits.size)
            f.failure = null; f.events.clear(); assertEquals(ElevationOutcome.IDLE, f.controller.onStart())
            assertEquals("revoke", f.events.first()); assertFalse(f.physicallyOpen)
        }
    }
    @Test fun `record rejects mismatched active binding journal and missing debit`() {
        setup(); val r = request(); advance(600_000); start(r); val good = record!!
        assertFailsWith<IllegalArgumentException> { ElevationRecord(state = good.state, journal = good.journal) }
        assertFailsWith<IllegalArgumentException> { ElevationRecord(state = good.state, person = good.person) }
        assertFailsWith<IllegalArgumentException> { ElevationRecord(journal = SessionJournal.Damaged) }
        val active = good.journal as SessionJournal.Active
        val wrong = SessionJournal.Active(SessionAccess("other", active.access.kind, active.access.packages, active.access.deadline), SessionPhase.OPEN)
        assertFailsWith<IllegalArgumentException> { ElevationRecord(state = good.state, journal = wrong, person = good.person) }
        assertFailsWith<IllegalArgumentException> { ElevationState(grant = good.state.grant) }
    }

    @Test fun `countdown starts at the checkpoint after authority consumption`() {
        setup(); val r = prepare(); val a = auth(r); var calls = 0
        checkpointHook = { if (++calls == 3) advance(600_000) }
        assertEquals(ElevationOutcome.COUNTING, controller.request(r, a)); checkpointHook = null
        assertEquals(now!!.boot, record!!.state.countdown!!.startedAt)
        assertEquals(ElevationOutcome.REFUSED, start(r)); advance(600_000)
        assertEquals(ElevationOutcome.STARTED, start(r))
    }

    private fun sharedSessions() = SessionController(store, effects, clock, inventory, vault) { credentials?.mode }

    @Test fun `both startup orders preserve one debit through the shared session journal`() {
        for (supervisedFirst in listOf(false, true)) {
            val f = ElevationControllerTest(); f.setup(); val r = f.request(); f.advance(600_000); f.start(r)
            val debit = f.record!!.state.ledger.debits.single()
            f.credentials = null // Direct Boot must not need credentials for either startup path.
            val sessions = f.sharedSessions(); f.controller = f.controller()
            if (supervisedFirst) {
                assertTrue(sessions.onStart()); assertFalse(f.record!!.state.grant!!.packageAccessOpen)
                assertEquals(listOf(debit), f.record!!.state.ledger.debits)
                assertEquals(ElevationOutcome.IDLE, f.controller.onStart())
            } else {
                assertEquals(ElevationOutcome.IDLE, f.controller.onStart()); assertTrue(sessions.onStart())
            }
            assertNotNull(f.record); assertNull(f.record!!.state.grant); assertNull(f.record!!.state.countdown)
            assertEquals(SessionJournal.Empty, f.record!!.journal); assertEquals(listOf(debit), f.record!!.state.ledger.debits)
            assertFalse(f.physicallyOpen)
        }
    }
    @Test fun `legacy session closing saves access flag with shared journal and keeps Play grant data`() {
        setup(); val r = request(); advance(600_000); start(r); assertTrue(controller.installed(r.id, app))
        val deadline = record!!.state.grant!!.deadline
        val sessions = sharedSessions()
        assertEquals(SessionOutcome.CLOSED, sessions.end(r.id, SessionEnd.LEFT_PACKAGES))
        assertFalse(record!!.state.grant!!.packageAccessOpen); assertEquals(deadline, record!!.state.grant!!.deadline)
        assertEquals(SessionJournal.Empty, record!!.journal); assertEquals(1, record!!.state.ledger.debits.size)
        assertNull(controller.access()); assertEquals(identity, controller.candidate(app)); assertFalse(physicallyOpen)
    }
    @Test fun `shared closing CAS failures do not damage elevation or replace its debit`() {
        setup(); val r = request(); advance(600_000); start(r); val sessions = sharedSessions()
        failure = "save-CLOSING"
        assertEquals(SessionOutcome.NOT_READY, sessions.end(r.id, SessionEnd.INCOMING_CALL))
        assertTrue(record!!.state.grant!!.packageAccessOpen); assertEquals(1, record!!.state.ledger.debits.size)
        failure = null; assertEquals(SessionOutcome.CLOSED, sessions.tick())
        assertFalse(record!!.state.grant!!.packageAccessOpen); assertEquals(1, record!!.state.ledger.debits.size)
        assertEquals(ElevationOutcome.IDLE, controller.onStart()); assertEquals(1, record!!.state.ledger.debits.size)
    }
    @Test fun `supervised startup failure leaves valid open record and second controller preserves ledger`() {
        setup(); val r = request(); advance(600_000); start(r); val sessions = sharedSessions()
        failure = "save-clear"; assertFalse(sessions.onStart())
        assertTrue(record!!.state.grant!!.packageAccessOpen); assertEquals(1, record!!.state.ledger.debits.size)
        failure = null; assertEquals(ElevationOutcome.IDLE, controller.onStart()); assertTrue(sessions.onStart())
        assertEquals(1, record!!.state.ledger.debits.size); assertFalse(physicallyOpen)
    }
    @Test fun `shared supervised entry refuses self-managed mode and unknown mode without spending authority`() {
        setup(); val sessions = sharedSessions(); assertTrue(sessions.onStart())
        val action = PrivilegedAction("session:PLAY", "supervised")
        val auth = assertIs<PinResult.Authorized>(vault.authenticate(person, pin, action)).authority
        assertEquals(SessionOutcome.REFUSED, sessions.start("supervised", WindowKind.PLAY, battery, auth))
        credentials = null
        assertEquals(SessionOutcome.REFUSED, sessions.start("supervised", WindowKind.PLAY, battery, auth))
        assertEquals(SessionJournal.Empty, record!!.journal); assertTrue(record!!.state.ledger.debits.isEmpty())
    }
    @Test fun `supervised entry cannot replace an elevation countdown or a closed grant`() {
        for (grant in listOf(false, true)) {
            val f = ElevationControllerTest(); f.setup(); val r = f.request()
            if (grant) { f.advance(600_000); f.start(r) }
            val sessions = f.sharedSessions(); assertTrue(sessions.onStart())
            val before = f.record!!
            f.credentials = CredentialState(Mode.TRUSTED, f.credentials!!.people)
            val action = PrivilegedAction("session:PLAY", "supervised")
            val auth = assertIs<PinResult.Authorized>(f.vault.authenticate(f.person, f.pin, action)).authority
            assertEquals(SessionOutcome.NOT_READY, sessions.start("supervised", WindowKind.PLAY, f.battery, auth))
            assertSame(before, f.record); assertFalse(f.physicallyOpen)
        }
    }

}
