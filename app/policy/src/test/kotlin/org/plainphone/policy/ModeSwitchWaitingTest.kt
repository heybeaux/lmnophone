// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class ModeSwitchWaitingTest {
    private class F(val mode: Mode = Mode.TRUSTED) {
        val a = TrustedPersonId("first")
        val b = TrustedPersonId("second")
        val incoming = TrustedPersonId("incoming")
        val pin get() = CharArray(6) { '1' }
        val newPin get() = CharArray(6) { '2' }
        var now: ClockReading? = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
        val clock = RunningClock { now }
        var credentials: CredentialState? = null
        var waiting: WaitingState? = WaitingState(storageGeneration = "live")
        var failWrite = false
        var failJoint = false
        var crashWrite: Boolean? = null
        val intents = mutableMapOf<String, CancelIntent>()
        var entropy = 0
        var sequence = 0
        val credentialStore = object : CredentialStore {
            override fun read() = credentials
            override fun compareAndSet(expected: CredentialState, replacement: CredentialState): Boolean {
                if (credentials !== expected) return false
                credentials = replacement; return true
            }
        }
        val store = object : WaitingChangeStore {
            override fun read() = waiting
            override fun compareAndSet(expected: WaitingState, replacement: WaitingState): Boolean {
                if (crashWrite == false) error("before write")
                if (failWrite || waiting !== expected) return false
                waiting = replacement
                if (crashWrite == true) error("after write")
                return true
            }
        }
        val cancelStore = object : CancelIntentStore {
            override fun initialize(requestId: String): Boolean {
                if (intents[requestId] == CancelIntent.REQUESTED) return false
                intents[requestId] = CancelIntent.CLEAR; return true
            }
            override fun request(requestId: String): Boolean { intents[requestId] = CancelIntent.REQUESTED; return true }
            override fun read(requestId: String) = intents[requestId] ?: CancelIntent.UNKNOWN
        }
        val vault = PinVault(credentialStore, KeyDerivation { chars, salt, _ ->
            java.security.MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
        }, SecretEntropy { n -> ByteArray(n) { (++entropy).toByte() } }, clock)
        val first = vault.prepareCredential(pin)
        val second = vault.prepareCredential(pin)
        val prepared = vault.prepareCredential(pin)
        val checks = SelfManagedEnrollmentConfirmations(true, true, true, true, true, true)
        fun self(checks: SelfManagedEnrollmentConfirmations = this.checks, person: TrustedPersonId = incoming,
            account: String = "synthetic-incoming-account", prepared: PreparedCredential = this.prepared,
            witness: EnrollmentWitness = EnrollmentWitness.Helper) =
            IncomingSelfManagedEnrollment.confirm(person, account, prepared, checks, witness)
        fun trusted(person: TrustedPersonId = incoming, witness: EnrollmentWitness = EnrollmentWitness.Helper) =
            IncomingPersonEnrollment.confirm(person, PersonContact("synthetic-name", "synthetic-number"),
                "synthetic-incoming-account", prepared,
                EnrollmentConfirmations(true, true, true, true, true, true, true, true, true), witness)!!
        init {
            val people = linkedMapOf(a to first.credential.copy(contact = PersonContact("synthetic-first", "synthetic-first-number")))
            if (mode == Mode.TRUSTED) people[b] = second.credential.copy(contact = PersonContact("synthetic-second", "synthetic-second-number"))
            credentials = CredentialState(mode, people, structureGeneration = 7, recoveryProfile = RecoveryProfile(
                people.keys.associateWith { "synthetic-${it.value}-account" }, "synthetic-backup", FrictionSettings(600_000, 2)))
        }
        fun controller() = WaitingChangeController(store, { credentials }, clock, vault,
            WaitingIdentitySource { WaitingIdentity("request-${++sequence}", "synthetic-cancel-$sequence") }, cancelStore,
            WaitingSkipSupport(vault, WaitingSkipCommitter { old, next, before, after ->
                if (failJoint || credentials !== old || waiting !== before ||
                    cancelStore.read(after.operation!!.identity.requestId) != CancelIntent.CLEAR) false
                else { credentials = next; waiting = after; true }
            }))
        val controller = controller()
        fun prepare(starter: TrustedPersonId = a): WaitingStart? = if (mode == Mode.TRUSTED)
            controller.prepareSelfManaged(starter, self()!!) else controller.prepareSupervised(starter, trusted())
        fun auth(request: WaitingStart, person: TrustedPersonId = a) =
            assertIs<PinResult.Authorized>(vault.authenticate(person, pin, request.action)).authority
        fun start(): String {
            val request = prepare()!!
            assertEquals(WaitingOutcome.PENDING, controller.startModeSwitch(request, auth(request), true))
            return request.requestId
        }
        fun at(total: Long, epoch: String = "epoch", boot: String = "boot", elapsed: Long = total) {
            now = ClockReading(RunningTime(epoch, total), BootTime(boot, elapsed))
        }
        fun replace(person: TrustedPersonId) {
            val c = credentials!!
            credentials = c.replacing(person, c.people.getValue(person).copy(generation = c.people.getValue(person).generation + 1))
        }
        val battery = BatteryState(1, false)
        fun skip(id: String, person: TrustedPersonId = a, code: String = first.recoveryCode) =
            controller.skip(id, person, code.toCharArray(), newPin, true, battery)
    }
    private val day = 86_400_000L

    @Test fun `self enrollment requires each credential and account check without inventing a contact`() {
        val f = F(); val c = f.checks
        listOf(c.copy(ownPinSet = false), c.copy(recoveryCodeReceived = false),
            c.copy(resetProtectionIdFromOwnDevice = false), c.copy(offDeviceSignInChecked = false),
            c.copy(independentSecondFactorChecked = false), c.copy(accountAtLeast72HoursOld = false))
            .forEach { assertNull(f.self(it)) }
        for (account in listOf("", " ", "synthetic account")) assertNull(f.self(account = account))
        for (credential in listOf(f.prepared.credential.copy(generation = 1),
            f.prepared.credential.copy(pinCounter = FailureCounter(1)), f.prepared.credential.copy(recoveryCounter = FailureCounter(1))))
            assertNull(f.self(prepared = PreparedCredential(credential, f.prepared.recoveryCode)))
        val enrolled = f.self()!!
        assertNull(enrolled.credential.contact)
        assertSame(f.prepared.credential.pin, enrolled.credential.pin)
        assertSame(c, enrolled.confirmations)
        assertFalse(enrolled.toString().contains("synthetic-incoming-account"))
        assertFalse(enrolled.toString().contains(f.prepared.recoveryCode))
    }

    @Test fun `patch binds direction every outgoing slot and account witness`() {
        for (mode in Mode.entries) {
            val f = F(mode); val patch = f.prepare()!!.modePatch!!
            assertEquals(mode, patch.sourceMode)
            assertEquals(if (mode == Mode.TRUSTED) Mode.SELF_MANAGED else Mode.TRUSTED, patch.targetMode)
            assertEquals(f.credentials!!.people.keys, patch.removedSlots)
            assertEquals(f.credentials!!.people.mapValues { it.value.generation }, patch.dependencyGenerations)
            assertEquals(f.credentials!!.people.keys + f.incoming, patch.changedSlots)
            assertTrue(patch.dependenciesMatch(f.credentials!!))
            assertFailsWith<UnsupportedOperationException> { (patch.dependencyGenerations as MutableMap).clear() }
            assertFailsWith<UnsupportedOperationException> { (patch.removedSlots as MutableSet).clear() }
        }
        val f = F()
        assertNull(f.controller.prepareSelfManaged(f.a, f.self(witness = EnrollmentWitness.CurrentPerson(f.incoming))!!))
        assertNotNull(f.controller.prepareSelfManaged(f.a, f.self(witness = EnrollmentWitness.CurrentPerson(f.b))!!))
        val g = F(Mode.SELF_MANAGED)
        assertNull(g.controller.prepareSupervised(g.a, g.trusted(witness = EnrollmentWitness.CurrentPerson(g.incoming))))
        assertNotNull(g.controller.prepareSupervised(g.a, g.trusted(witness = EnrollmentWitness.CurrentPerson(g.a))))
    }

    @Test fun `preparation rejects wrong direction reused identity ordinary user and missing profile`() {
        val f = F()
        assertNull(f.prepare(f.incoming))
        assertNull(f.controller.prepareSupervised(f.a, f.trusted()))
        assertNull(f.controller.prepareSelfManaged(f.a, f.self(person = f.b)!!))
        val g = F(Mode.SELF_MANAGED)
        assertNull(g.controller.prepareSelfManaged(g.a, g.self()!!))
        assertNull(g.controller.prepareSupervised(g.a, g.trusted(person = g.a)))
        g.credentials = CredentialState(Mode.SELF_MANAGED, g.credentials!!.people)
        assertNull(g.prepare())
    }

    @Test fun `start needs exact fresh PIN holder request action confirmation and process owner`() {
        for (mode in Mode.entries) {
            val f = F(mode); val r = f.prepare()!!; val other = f.prepare()!!
            assertEquals(WaitingOutcome.REFUSED, f.controller.startModeSwitch(r, f.auth(other), true))
            assertEquals(WaitingOutcome.REFUSED, f.controller().startModeSwitch(r, f.auth(r), true))
            val grant = f.auth(r)
            assertEquals(WaitingOutcome.REFUSED, f.controller.startModeSwitch(r, grant, false))
            assertEquals(WaitingOutcome.PENDING, f.controller.startModeSwitch(r, grant, true))
            assertEquals(WaitingOutcome.REFUSED, f.controller.startModeSwitch(r, grant, true))
        }
        val f = F(); val r = f.prepare()!!
        assertEquals(WaitingOutcome.REFUSED, f.controller.startModeSwitch(r, f.auth(r, f.b), true))
        val grant = f.auth(r); f.at(120_000)
        assertEquals(WaitingOutcome.REFUSED, f.controller.startModeSwitch(r, grant, true))
    }

    @Test fun `mode start cannot use turn off people or recovery handover entry points`() {
        val f = F(); val r = f.prepare()!!
        assertEquals(WaitingOutcome.REFUSED, f.controller.startTurnOff(r, f.auth(r), true, true))
        assertEquals(WaitingOutcome.REFUSED, f.controller.startPeopleChange(r, f.auth(r), true))
        val off = f.controller.prepareTurnOff(f.a)!!
        assertEquals(WaitingOutcome.REFUSED, f.controller.startModeSwitch(off, f.auth(off), true))
        val people = f.controller.prepareHandOver(f.a, f.trusted())!!
        assertEquals(WaitingOutcome.REFUSED, f.controller.startModeSwitch(people, f.auth(people), true))
        assertIs<WaitingSkipResult.NotSkipped>(f.controller.startHandOverWithRecovery(r,
            f.first.recoveryCode.toCharArray(), f.newPin, true, true))
        assertNull(f.waiting!!.operation)
    }

    @Test fun `both directions occupy shared slot and queue current contacts only`() {
        for (mode in Mode.entries) {
            val f = F(mode); val id = f.start(); val op = f.waiting!!.operation!!
            assertEquals(WaitingChangeKind.SWITCH_MODE, op.kind)
            assertEquals(id, op.identity.requestId)
            assertNull(op.peoplePatch); assertNotNull(op.modePatch)
            val receipt = f.waiting!!.texts.single()
            assertEquals(f.credentials!!.people.keys, receipt.recipients)
            for (p in receipt.recipients) assertSame(f.credentials!!.people[p]!!.contact, receipt.recipientContacts[p])
            val off = f.controller.prepareTurnOff(f.a)!!
            assertEquals(WaitingOutcome.BUSY, f.controller.startTurnOff(off, f.auth(off), true, true))
            val next = f.prepare()!!
            assertEquals(WaitingOutcome.BUSY, f.controller.startModeSwitch(next, f.auth(next), true))
            assertFalse(f.incoming in receipt.recipients)
        }
    }

    @Test fun `all removed generations and structure are rechecked before start and while pending`() {
        for (mode in Mode.entries) {
            for (person in F(mode).credentials!!.people.keys) {
                val f = F(mode); val r = f.prepare()!!; f.replace(person)
                assertEquals(WaitingOutcome.REFUSED, f.controller.startModeSwitch(r, f.auth(r), true))
                val id = f.start(); f.replace(person)
                assertEquals(WaitingOutcome.CANCELLED, f.controller.tick(id, f.battery))
                assertEquals(WaitingEvent.DEPENDENCY_CHANGED, f.waiting!!.texts.last().event)
            }
            val f = F(mode); val id = f.start(); val c = f.credentials!!
            f.credentials = CredentialState(c.mode, c.people, c.revision + 1, c.structureGeneration + 1, recoveryProfile = c.recoveryProfile)
            assertEquals(WaitingOutcome.CANCELLED, f.controller.tick(id, f.battery))
        }
    }

    @Test fun `counter only writes keep wait and old receipt contact snapshots`() {
        val f = F(); val id = f.start(); val receipt = f.waiting!!.texts.single()
        val original = receipt.recipientContacts[f.a]; val c = f.credentials!!
        f.credentials = c.replacing(f.a, c.people.getValue(f.a).copy(pinCounter = FailureCounter(1),
            contact = PersonContact("synthetic-later", "synthetic-later-number")))
        assertEquals(WaitingOutcome.PENDING, f.controller.tick(id, f.battery))
        assertEquals(WaitingOutcome.CANCELLED, f.controller.cancel(id))
        assertSame(original, receipt.recipientContacts[f.a])
        assertSame(f.credentials!!.people[f.a]!!.contact, f.waiting!!.texts.last().recipientContacts[f.a])
    }

    @Test fun `24 proven hours exact boundary survives controller restart and reboot without wall time`() {
        for (mode in Mode.entries) {
            val f = F(mode); f.at(3_600_000); val id = f.start()
            f.at(3_600_000 + day - 1, boot = "reboot", elapsed = 100)
            assertEquals(WaitingOutcome.PENDING, f.controller().tick(id, f.battery))
            f.at(3_600_000 + day, boot = "reboot", elapsed = 101)
            assertEquals(WaitingOutcome.COMMITTING, f.controller().tick(id, f.battery))
            assertEquals(mode, f.credentials!!.mode)
            assertFalse(f.incoming in f.credentials!!.people)
            assertEquals(WaitingOutcome.ALREADY_IN_PROGRESS, f.controller.cancel(id))
        }
    }

    @Test fun `clock repair resets age and checkpoint failure cannot mature either direction`() {
        for (mode in Mode.entries) {
            val f = F(mode); f.at(30 * 3_600_000L); val id = f.start()
            f.at(0, "repair", elapsed = 60 * 3_600_000L)
            assertEquals(WaitingOutcome.PENDING, f.controller.tick(id, f.battery))
            f.at(day - 1, "repair", elapsed = 90 * 3_600_000L)
            assertEquals(WaitingOutcome.PENDING, f.controller().tick(id, f.battery))
            f.now = null
            assertEquals(WaitingOutcome.NOT_READY, f.controller.tick(id, f.battery))
            f.at(day, "repair")
            assertEquals(WaitingOutcome.COMMITTING, f.controller.tick(id, f.battery))
        }
    }

    @Test fun `PIN free cancel persists across failed operation write and restart`() {
        for (mode in Mode.entries) {
            val f = F(mode); val id = f.start(); f.failWrite = true
            assertEquals(WaitingOutcome.NOT_READY, f.controller.cancel(id))
            assertEquals(WaitingPhase.PENDING, f.waiting!!.operation!!.phase)
            f.at(day)
            assertEquals(WaitingOutcome.NOT_READY, f.controller().tick(id, f.battery))
            f.failWrite = false
            assertEquals(WaitingOutcome.CANCELLED, f.controller().tick(id, f.battery))
            assertEquals(WaitingEvent.CANCELLED_ON_PHONE, f.waiting!!.texts.last().event)
        }
    }

    @Test fun `start write crash exposes whole pending operation plus receipt or nothing`() {
        for (mode in Mode.entries) for (after in listOf(false, true)) {
            val f = F(mode); val r = f.prepare()!!; val grant = f.auth(r); f.crashWrite = after
            assertFailsWith<IllegalStateException> { f.controller.startModeSwitch(r, grant, true) }
            f.crashWrite = null
            assertEquals(after, f.waiting!!.operation != null)
            assertEquals(if (after) 1 else 0, f.waiting!!.texts.size)
            assertEquals(WaitingOutcome.REFUSED, f.controller.startModeSwitch(r, grant, true))
            assertEquals(mode, f.credentials!!.mode)
        }
    }

    @Test fun `maturity write failure holds slot without changing credentials`() {
        val f = F(Mode.SELF_MANAGED); val id = f.start(); val old = f.credentials
        f.at(day); f.failWrite = true
        assertEquals(WaitingOutcome.NOT_READY, f.controller.tick(id, f.battery))
        assertEquals(WaitingPhase.PENDING, f.waiting!!.operation!!.phase)
        assertSame(old, f.credentials)
        f.failWrite = false
        assertEquals(WaitingOutcome.COMMITTING, f.controller().tick(id, f.battery))
    }

    @Test fun `either supervised holder skips exact switch with removed starter dependency retained`() {
        for (other in listOf(false, true)) {
            val f = F(); val id = f.start(); val person = if (other) f.b else f.a
            val code = if (other) f.second.recoveryCode else f.first.recoveryCode
            assertIs<WaitingSkipResult.NotSkipped>(f.skip("wrong-request", person, code))
            assertIs<WaitingSkipResult.Committing>(f.skip(id, person, code))
            val op = f.waiting!!.operation!!
            assertEquals(person, op.skipper)
            assertEquals(setOf(f.a, f.b), op.dependencyGenerations.keys)
            assertEquals(1L, op.dependencyGenerations[person])
            assertEquals(WaitingChangeKind.SWITCH_MODE, op.kind)
            assertEquals(WaitingEvent.SKIPPED, f.waiting!!.texts.last().event)
            assertTrue(op.dependenciesMatch(f.credentials!!))
            assertFalse(f.incoming in f.credentials!!.people)
        }
    }

    @Test fun `skip failed joint write cannot rotate PIN while still pending`() {
        val f = F(); val id = f.start(); val old = f.credentials; f.failJoint = true
        assertIs<WaitingSkipResult.NotSkipped>(f.skip(id))
        assertSame(old, f.credentials)
        assertEquals(WaitingPhase.PENDING, f.waiting!!.operation!!.phase)
        f.failJoint = false
        assertIs<WaitingSkipResult.Committing>(f.skip(id))
    }

    @Test fun `skip cannot override cancellation or changed outgoing dependency`() {
        for (cancel in listOf(false, true)) {
            val f = F(); val id = f.start()
            if (cancel) f.intents[id] = CancelIntent.REQUESTED else f.replace(f.b)
            val old = f.credentials
            assertEquals(WaitingOutcome.CANCELLED, assertIs<WaitingSkipResult.NotSkipped>(f.skip(id)).outcome)
            assertSame(old, f.credentials)
        }
    }

    @Test fun `self code cannot skip and ordinary recovery cancels dependency without skipping`() {
        val f = F(Mode.SELF_MANAGED); val id = f.start(); val old = f.credentials
        assertEquals(WaitingOutcome.REFUSED, assertIs<WaitingSkipResult.NotSkipped>(f.skip(id)).outcome)
        assertSame(old, f.credentials)
        assertIs<RecoveryResult.Replaced>(f.vault.recover(f.a, f.first.recoveryCode.toCharArray(), f.newPin))
        assertEquals(WaitingPhase.PENDING, f.waiting!!.operation!!.phase)
        assertEquals(WaitingOutcome.CANCELLED, f.controller.tick(id, f.battery))
    }

    @Test fun `pending credentials grant neither PIN recovery nor skip authority in either direction`() {
        for (mode in Mode.entries) {
            val f = F(mode); val id = f.start(); val old = f.credentials
            assertIs<PinResult.Unavailable>(f.vault.authenticate(f.incoming, f.pin, PrivilegedAction("synthetic", "pending")))
            assertIs<RecoveryResult.Unavailable>(f.vault.recover(f.incoming, f.prepared.recoveryCode.toCharArray(), f.newPin))
            assertIs<WaitingSkipResult.NotSkipped>(f.skip(id, f.incoming, f.prepared.recoveryCode))
            assertSame(old, f.credentials)
        }
    }

    @Test fun `candidate calculation swaps once preserving friction backup audit and rejects replay overflow`() {
        for (mode in Mode.entries) {
            val f = F(mode); val patch = f.prepare()!!.modePatch!!
            f.replace(f.a) // After commit, replacing a PIN no longer cancels the patch.
            val c = f.credentials!!
            assertFalse(patch.dependenciesMatch(c))
            val next = patch.replacementForCommit(c)!!
            assertEquals(patch.targetMode, next.mode)
            assertEquals(setOf(f.incoming), next.people.keys)
            assertEquals(c.structureGeneration + 1, next.structureGeneration)
            assertEquals(c.revision + 1, next.revision)
            assertEquals(c.pendingNotices, next.pendingNotices)
            assertSame(c.recoveryProfile!!.friction, next.recoveryProfile!!.friction)
            assertEquals(setOf("synthetic-incoming-account", "synthetic-backup"), next.recoveryProfile!!.resetProtectionUserIds)
            assertSame(c, f.credentials) // Calculation is not a store write or authority to apply.
            assertNull(patch.replacementForCommit(next))
            assertNull(patch.replacementForCommit(CredentialState(c.mode, c.people, Long.MAX_VALUE,
                c.structureGeneration, recoveryProfile = c.recoveryProfile)))
        }
    }

    private fun policy(f: F): PolicyInput {
        val home = PackageName("test.home"); val sms = PackageName("test.sms")
        val dialer = PackageName("test.dialer"); val settings = PackageName("test.settings")
        return PolicyInput("model", "build", home, sms,
            listOf(home, sms, dialer, PLAY_PACKAGE, settings).map { InstalledPackage(it, true, 1) },
            listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)),
            BuildInventory("model", "build", 1, NeverHideList(setOf(sms)), setOf(sms), setOf(settings)),
            waitingOperation = f.waiting?.operation)
    }
    private inner class Elevations(val f: F, ledger: ElevationLedger = ElevationLedger()) {
        var record: ElevationRecord? = ElevationRecord(state = ElevationState(ledger))
        val store = object : ElevationStore() {
            override fun read() = record
            override fun compareAndSet(expected: ElevationRecord?, replacement: ElevationRecord, expectedCredentials: CredentialState?): Boolean {
                if (record !== expected || (expectedCredentials != null && f.credentials !== expectedCredentials)) return false
                record = replacement; return true
            }
        }
        val effects = object : SessionEffects {
            override fun revokeAllRelaxations() = true
            override fun open(access: SessionAccess) = true
            override fun protectedStateCheck() = true
            override fun readBackPolicy() = true
        }
        var sequence = 0
        fun controller() = ElevationController(store, { f.credentials }, f.vault, f.clock,
            { ElevationLimits() }, { policy(f) }, { BatteryState(50, false) }, effects,
            AppApprovalIdSource { "elevation-${++sequence}" })
        val controller = controller().also { assertEquals(ElevationOutcome.IDLE, it.onStart()) }
        fun request(): ElevationRequest {
            val r = controller.prepare(f.a, ElevationTarget.Play)!!
            val auth = assertIs<PinResult.Authorized>(f.vault.authenticate(f.a, f.pin, r.action)).authority
            assertEquals(ElevationOutcome.COUNTING, controller.request(r, auth)); return r
        }
    }

    @Test fun `committing switch keeps lockdown and emergency floor instead of turn off policy`() {
        for (mode in Mode.entries) {
            val f = F(mode); val id = f.start(); f.at(day)
            assertEquals(WaitingOutcome.COMMITTING, f.controller.tick(id, f.battery))
            val plan = PolicyEngine.decide(policy(f))
            assertEquals(Restriction.entries.toSet(), plan.restrictions)
            assertTrue(PLAY_PACKAGE in plan.hidden)
            assertTrue(PackageName("test.dialer") in plan.neverHide)
            assertNotNull(plan.expected.hidden)
        }
    }

    @Test fun `second identity cannot replenish exhausted elevation ledger during wait or restart`() {
        val f = F(Mode.SELF_MANAGED)
        val ledger = ElevationLedger(listOf(ElevationDebit("used-1", f.now!!.proven), ElevationDebit("used-2", f.now!!.proven)))
        val e = Elevations(f, ledger); f.start()
        assertNull(e.controller.prepare(f.incoming, ElevationTarget.Play))
        val r = e.controller.prepare(f.a, ElevationTarget.Play)!!
        val auth = assertIs<PinResult.Authorized>(f.vault.authenticate(f.a, f.pin, r.action)).authority
        assertEquals(ElevationOutcome.LIMIT_REACHED, e.controller.request(r, auth))
        assertSame(ledger, e.record!!.state.ledger)
        assertEquals(ElevationOutcome.IDLE, e.controller().onStart())
        assertSame(ledger, e.record!!.state.ledger)
        f.at(day - 1); assertEquals(0, ledger.availability(2, f.now!!.proven).turnsLeft)
        f.at(day); assertEquals(2, ledger.availability(2, f.now!!.proven).turnsLeft)
    }

    @Test fun `countdown continues through pending switch and cancellation with original deadline`() {
        val f = F(Mode.SELF_MANAGED); val e = Elevations(f); val r = e.request()
        val countdown = e.record!!.state.countdown; val id = f.start(); f.at(599_999)
        assertEquals(ElevationOutcome.COUNTING, e.controller.tick())
        assertSame(countdown, e.record!!.state.countdown)
        assertEquals(WaitingOutcome.CANCELLED, f.controller.cancel(id)); f.at(600_000)
        assertEquals(ElevationOutcome.STARTED, e.controller.start(r.id, r.target))
        assertEquals(1, e.record!!.state.ledger.debits.size)
    }

    @Test fun `pending countdown can start and committing closes grant without erasing debit`() {
        val f = F(Mode.SELF_MANAGED); val e = Elevations(f); val r = e.request(); val id = f.start()
        f.at(600_000)
        assertEquals(ElevationOutcome.STARTED, e.controller.start(r.id, r.target))
        val ledger = e.record!!.state.ledger; f.at(day)
        assertEquals(WaitingOutcome.COMMITTING, f.controller.tick(id, f.battery))
        assertNull(e.controller.access()); e.controller.tick()
        assertNull(e.record!!.state.grant)
        assertSame(ledger, e.record!!.state.ledger)
    }
}
