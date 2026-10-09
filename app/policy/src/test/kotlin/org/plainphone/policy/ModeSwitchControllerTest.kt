// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class ModeSwitchControllerTest {
    private class F(val mode: Mode = Mode.TRUSTED) {
        val a = TrustedPersonId("first")
        val b = TrustedPersonId("second")
        val incoming = TrustedPersonId("incoming")
        val pin get() = CharArray(6) { '1' }
        val newPin get() = CharArray(6) { '2' }
        var now: ClockReading? = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
        var credentials: CredentialState? = null
        var waiting: WaitingState? = WaitingState(storageGeneration = "journal-epoch")
        val events = mutableListOf<String>()
        var crash: String? = null
        var fail: String? = null
        var hook: ((String) -> Unit)? = null
        var patchWrites = 0
        var resetIds: Set<String> = emptySet()
        var entropy = 0
        var identities = 0
        fun <T> edge(name: String, action: () -> T): T {
            events.add(name)
            if (crash == "$name:before") error("crash before $name")
            hook?.invoke(name)
            val result = action()
            if (crash == "$name:after") error("crash after $name")
            return result
        }
        val credentialStore = object : CredentialStore {
            override fun read(): CredentialState? {
                if (fail == "credentials") return null
                if (credentials?.appliedPeopleChange != null) return edge("credential-readback") { credentials }
                return credentials
            }
            override fun compareAndSet(expected: CredentialState, replacement: CredentialState): Boolean {
                if (credentials !== expected) return false
                credentials = replacement
                return true
            }
        }
        val store = object : WaitingChangeStore {
            override fun read() = if (fail == "journal") null else waiting
            override fun compareAndSet(expected: WaitingState, replacement: WaitingState): Boolean {
                if (waiting !== expected) return false
                waiting = replacement
                return true
            }
        }
        val vault = PinVault(credentialStore, KeyDerivation { chars, salt, _ ->
            java.security.MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
        }, SecretEntropy { n -> ByteArray(n) { (++entropy).toByte() } }, RunningClock { now })
        val first = vault.prepareCredential(pin)
        val second = vault.prepareCredential(pin)
        val prepared = vault.prepareCredential(pin)
        val enrolled = IncomingPersonEnrollment.confirm(incoming, PersonContact("synthetic-incoming", "synthetic-incoming-number"),
            "synthetic-incoming-account", prepared,
            EnrollmentConfirmations(true, true, true, true, true, true, true, true, true), EnrollmentWitness.Helper)!!
        val waitingController = WaitingChangeController(store, { credentials }, RunningClock { now }, vault,
            WaitingIdentitySource { identities++; WaitingIdentity("request-$identities", "synthetic-cancel-$identities") },
            object : CancelIntentStore {
                override fun initialize(requestId: String) = true
                override fun request(requestId: String) = true
                override fun read(requestId: String) = CancelIntent.CLEAR
            })
        var elevation: ElevationRecord? = ElevationRecord()
        val elevationStore = object : ElevationStore() {
            override fun read() = if (fail == "elevation-read") null else elevation
            override fun compareAndSet(expected: ElevationRecord?, replacement: ElevationRecord,
                expectedCredentials: CredentialState?): Boolean = edge("clear-authority") {
                if (fail == "clear-authority" || elevation !== expected ||
                    (expectedCredentials != null && credentials !== expectedCredentials)) false
                else { elevation = replacement; true }
            }
        }
        val transaction = object : ModeSwitchCommitter {
            override val elevationStore: ElevationStore get() = this@F.elevationStore
            override fun applyModePatch(expectedCredentials: CredentialState, replacementCredentials: CredentialState,
                expectedWaiting: WaitingState, expectedElevation: ElevationRecord): Boolean = edge("patch") {
                if (fail == "patch" || credentials !== expectedCredentials || waiting !== expectedWaiting ||
                    elevation !== expectedElevation || waiting?.operation?.phase != WaitingPhase.COMMITTING) false
                else {
                    check(!physicallyOpen && expectedElevation.journal == SessionJournal.Empty)
                    check(expectedElevation.state.grant == null && expectedElevation.state.countdown == null)
                    credentials = replacementCredentials; patchWrites++; true
                }
            }
            override fun applyPatch(expectedCredentials: CredentialState, replacementCredentials: CredentialState,
                expectedWaiting: WaitingState): Boolean = error("mode switch called people-only patch")
            override fun move(expectedCredentials: CredentialState, expectedWaiting: WaitingState,
                replacementWaiting: WaitingState): Boolean {
                val stage = when (replacementWaiting.operation!!.phase) {
                    WaitingPhase.ROLLING_BACK -> "rollback-intent"
                    WaitingPhase.CANCELLED -> "cancelled"
                    WaitingPhase.COMPLETED -> "completed"
                    else -> error("unexpected executor transition")
                }
                return edge(stage) {
                    if (fail == stage || credentials !== expectedCredentials || waiting !== expectedWaiting) false
                    else { waiting = replacementWaiting; true }
                }
            }
        }
        var physicallyOpen = true
        val effects = object : ModeSwitchEffects {
            override fun revokeTemporaryAuthority(): Boolean = edge("revoke") {
                if (fail == "revoke") false else { physicallyOpen = false; true }
            }
            override fun readBackProtectedState(): Boolean = edge("protected") { fail != "protected" && !physicallyOpen }
            override fun setResetProtection(userIds: Set<String>): Boolean = edge("set") {
                if (fail == "set") false else { resetIds = userIds.toSet(); true }
            }
            override fun broadcastResetProtectionChanged(): Boolean = edge("broadcast") { fail != "broadcast" }
            override fun readBackResetProtection(userIds: Set<String>): Boolean = edge("readback") {
                fail != "readback" && userIds == resetIds
            }
        }
        fun controller() = PeopleChangeController(store, credentialStore, transaction, effects, RunningClock { now }, vault)
        val controller = controller()
        lateinit var id: String
        lateinit var original: CredentialState
        init {
            val people = linkedMapOf(a to first.credential.copy(contact = PersonContact("synthetic-first", "synthetic-first-number")))
            if (mode == Mode.TRUSTED) people[b] = second.credential.copy(contact = PersonContact("synthetic-second", "synthetic-second-number"))
            credentials = CredentialState(mode, people, recoveryProfile = RecoveryProfile(
                people.keys.associateWith { "synthetic-${it.value}-account" }, "synthetic-backup", FrictionSettings(600_000, 3)))
            original = credentials!!
            resetIds = original.recoveryProfile!!.resetProtectionUserIds
            val self = IncomingSelfManagedEnrollment.confirm(incoming, "synthetic-incoming-account", prepared,
                SelfManagedEnrollmentConfirmations(true, true, true, true, true, true), EnrollmentWitness.Helper)!!
            val request = if (mode == Mode.TRUSTED) waitingController.prepareSelfManaged(a, self)!!
                else waitingController.prepareSupervised(a, enrolled)!!
            val authority = assertIs<PinResult.Authorized>(vault.authenticate(a, pin, request.action)).authority
            assertEquals(WaitingOutcome.PENDING, waitingController.startModeSwitch(request, authority, true))
            id = request.requestId
            now = ClockReading(RunningTime("epoch", 86_400_000), BootTime("boot", 86_400_000))
            assertEquals(WaitingOutcome.COMMITTING, waitingController.tick(id, BatteryState(50, false)))
            seedAuthority()
            events.clear()
        }
        fun seedAuthority(countdown: Boolean = false) {
            val time = now!!
            val ledger = ElevationLedger(listOf(ElevationDebit("old-session", time.proven), ElevationDebit("used-2", time.proven)))
            val deadline = time.boot.copy(elapsedMillis = time.boot.elapsedMillis + 600_000)
            val access = SessionAccess("old-session", WindowKind.PLAY, setOf(PLAY_PACKAGE), deadline)
            val binding = ElevationPersonBinding(a, credentials!!.people.getValue(a).generation, credentials!!.structureGeneration)
            elevation = when {
                countdown -> ElevationRecord(state = ElevationState(ledger,
                    ElevationCountdown("countdown", ElevationTarget.Play, time.boot, 600_000)), person = binding)
                mode == Mode.TRUSTED -> ElevationRecord(state = ElevationState(ledger), journal = SessionJournal.Active(access, SessionPhase.OPEN))
                else -> ElevationRecord(state = ElevationState(ledger, grant = ElevationGrant("old-session", ElevationTarget.Play,
                    time.boot, deadline, true)), journal = SessionJournal.Active(access, SessionPhase.OPEN), person = binding)
            }
        }
        fun tick(direction: WaitingPhase = WaitingPhase.COMMITTING, fresh: Boolean = false) =
            (if (fresh) controller() else controller).tick(id, direction)
        fun stop(person: TrustedPersonId = a): PeopleChangeResult {
            val request = controller.prepareStop(id, person)!!
            val grant = assertIs<PinResult.Authorized>(vault.authenticate(person, pin, request.action)).authority
            return controller.stop(request, grant)
        }
        fun replace(person: TrustedPersonId) {
            val c = credentials!!
            credentials = c.replacing(person, c.people.getValue(person).copy(generation = c.people.getValue(person).generation + 1))
        }
        fun futureIds() = waiting!!.operation!!.modePatch!!.replacementForCommit(credentials!!)!!.recoveryProfile!!.resetProtectionUserIds
    }
    private fun outcome(expected: PeopleChangeOutcome, result: PeopleChangeResult) = assertEquals(expected, result.outcome)
    private fun failed(expected: PeopleChangeFailure, result: PeopleChangeResult) {
        outcome(PeopleChangeOutcome.NEEDS_ATTENTION, result)
        assertEquals(expected, result.failure)
    }

    @Test fun `both directions revoke old authority then read back target and atomically swap mode holders marker`() {
        for (mode in Mode.entries) {
            val f = F(mode); val ledger = f.elevation!!.state.ledger; val target = f.futureIds()
            val started = f.waiting!!.texts.first()
            outcome(PeopleChangeOutcome.APPLIED, f.tick())
            assertEquals(listOf("clear-authority", "revoke", "protected", "set", "broadcast", "readback", "patch", "credential-readback"), f.events)
            assertEquals(target, f.resetIds)
            assertEquals(setOf("synthetic-incoming-account", "synthetic-backup"), target)
            assertEquals(if (mode == Mode.TRUSTED) Mode.SELF_MANAGED else Mode.TRUSTED, f.credentials!!.mode)
            assertEquals(setOf(f.incoming), f.credentials!!.people.keys)
            assertEquals(1L, f.credentials!!.structureGeneration)
            assertEquals(f.id, f.credentials!!.appliedPeopleChange!!.requestId)
            assertEquals("journal-epoch", f.credentials!!.appliedPeopleChange!!.waitingGeneration)
            assertSame(ledger, f.elevation!!.state.ledger)
            assertNull(f.elevation!!.state.grant); assertNull(f.elevation!!.person)
            assertEquals(SessionJournal.Empty, f.elevation!!.journal)
            assertFalse(f.physicallyOpen)
            outcome(PeopleChangeOutcome.COMPLETED, f.tick(fresh = true))
            assertEquals(1, f.patchWrites)
            assertFalse(f.elevationStore.revocationPending)
            val receipt = f.waiting!!.texts.last()
            assertEquals(setOf(f.incoming), receipt.recipients)
            assertEquals(f.credentials!!.people[f.incoming]!!.contact, receipt.recipientContacts[f.incoming])
            assertEquals(f.original.people.keys, started.recipients)
            assertEquals(f.original.recoveryProfile!!.friction, f.credentials!!.recoveryProfile!!.friction)
            outcome(PeopleChangeOutcome.IGNORED, f.tick())
        }
    }

    @Test fun `old adapters cannot execute or stop mode changes`() {
        val f = F()
        val old = object : PeopleChangeCommitter {
            override fun applyPatch(expectedCredentials: CredentialState, replacementCredentials: CredentialState,
                expectedWaiting: WaitingState) = error("legacy patch")
            override fun move(expectedCredentials: CredentialState, expectedWaiting: WaitingState,
                replacementWaiting: WaitingState) = error("legacy move")
        }
        val legacy = PeopleChangeController(f.store, f.credentialStore, old, f.effects, RunningClock { f.now }, f.vault)
        outcome(PeopleChangeOutcome.IGNORED, legacy.tick(f.id, WaitingPhase.COMMITTING))
        assertNull(legacy.prepareStop(f.id, f.a))
        assertTrue(f.events.isEmpty())
    }

    @Test fun `every forward failure holds current credentials and debit ledger until successful retry`() {
        for (mode in Mode.entries) for (stage in listOf("elevation-read", "clear-authority", "revoke", "protected", "set", "broadcast", "readback", "patch")) {
            val f = F(mode); val old = f.credentials; val ledger = f.elevation!!.state.ledger
            f.fail = stage
            outcome(PeopleChangeOutcome.NEEDS_ATTENTION, f.tick())
            assertSame(old, f.credentials)
            assertSame(ledger, f.elevation!!.state.ledger)
            assertEquals(WaitingPhase.COMMITTING, f.waiting!!.operation!!.phase)
            assertTrue(f.elevationStore.revocationPending)
            assertIs<PinResult.Unavailable>(f.vault.authenticate(f.incoming, f.pin, PrivilegedAction("test", "pending")))
            f.fail = null
            outcome(PeopleChangeOutcome.APPLIED, f.tick(fresh = true))
            outcome(PeopleChangeOutcome.COMPLETED, f.tick(fresh = true))
            assertEquals(1, f.patchWrites)
            assertSame(ledger, f.elevation!!.state.ledger)
        }
    }

    @Test fun `every forward crash boundary preserves exactly one active mode and never reapplies enrollment`() {
        for (mode in Mode.entries) for (stage in listOf("clear-authority", "revoke", "protected", "set", "broadcast", "readback", "patch", "credential-readback", "completed")) {
            for (after in listOf(false, true)) {
                val f = F(mode); val ledger = f.elevation!!.state.ledger
                f.crash = "$stage:${if (after) "after" else "before"}"
                assertFailsWith<IllegalStateException> { f.tick(); if (stage == "completed") f.tick() }
                f.crash = null
                val applied = f.credentials!!.appliedPeopleChange != null
                assertEquals(applied, f.incoming in f.credentials!!.people)
                assertEquals(!applied, f.a in f.credentials!!.people)
                assertEquals(if (applied) 1L else 0L, f.credentials!!.structureGeneration)
                if (applied) {
                    assertFalse(f.physicallyOpen)
                    assertEquals(SessionJournal.Empty, f.elevation!!.journal)
                    assertNull(f.elevation!!.state.grant)
                    assertIs<RecoveryResult.Replaced>(f.vault.recover(f.incoming, f.prepared.recoveryCode.toCharArray(), f.newPin))
                }
                if (f.waiting!!.operation!!.phase != WaitingPhase.COMPLETED) {
                    val result = f.tick(fresh = true)
                    if (result.outcome == PeopleChangeOutcome.APPLIED) outcome(PeopleChangeOutcome.COMPLETED, f.tick(fresh = true))
                    else outcome(PeopleChangeOutcome.COMPLETED, result)
                }
                assertEquals(1, f.patchWrites)
                assertEquals(1, f.waiting!!.texts.count { it.event == WaitingEvent.COMPLETED })
                assertSame(ledger, f.elevation!!.state.ledger)
                if (applied) assertEquals(1L, f.credentials!!.people[f.incoming]!!.generation)
            }
        }
    }

    @Test fun `fresh current holder PIN stops pre swap and durable rollback precedes restoring list`() {
        for (mode in Mode.entries) {
            val holders = if (mode == Mode.TRUSTED) listOf(TrustedPersonId("first"), TrustedPersonId("second")) else listOf(TrustedPersonId("first"))
            for (person in holders) {
                val f = F(mode); f.fail = "readback"
                outcome(PeopleChangeOutcome.NEEDS_ATTENTION, f.tick())
                f.events.clear()
                outcome(PeopleChangeOutcome.ROLLING_BACK, f.stop(person))
                assertEquals(listOf("rollback-intent"), f.events)
                assertEquals(person, f.waiting!!.operation!!.stopper)
                f.fail = null
                outcome(PeopleChangeOutcome.IGNORED, f.tick(fresh = true))
                outcome(PeopleChangeOutcome.CANCELLED, f.tick(WaitingPhase.ROLLING_BACK, fresh = true))
                assertEquals(f.original.recoveryProfile!!.resetProtectionUserIds, f.resetIds)
                assertEquals(mode, f.credentials!!.mode)
                assertEquals(0, f.patchWrites)
                assertEquals(WaitingEvent.CANCELLED_AFTER_ROLLBACK, f.waiting!!.texts.last().event)
                assertFalse(f.elevationStore.revocationPending)
                assertEquals(SessionJournal.Empty, f.elevation!!.journal)
            }
        }
    }

    @Test fun `rollback failures retain direction and debits and retry without swapping`() {
        for (mode in Mode.entries) for (stage in listOf("clear-authority", "revoke", "protected", "set", "broadcast", "readback", "cancelled")) {
            val f = F(mode); val ledger = f.elevation!!.state.ledger
            outcome(PeopleChangeOutcome.ROLLING_BACK, f.stop())
            f.fail = stage
            outcome(PeopleChangeOutcome.NEEDS_ATTENTION, f.tick(WaitingPhase.ROLLING_BACK))
            assertEquals(WaitingPhase.ROLLING_BACK, f.waiting!!.operation!!.phase)
            assertSame(ledger, f.elevation!!.state.ledger)
            f.fail = null; f.replace(f.a)
            outcome(PeopleChangeOutcome.IGNORED, f.tick(fresh = true))
            outcome(PeopleChangeOutcome.CANCELLED, f.tick(WaitingPhase.ROLLING_BACK, fresh = true))
            assertEquals(0, f.patchWrites)
            assertEquals(1L, f.waiting!!.texts.last().recipientGenerations[f.a])
        }
    }

    @Test fun `every rollback crash retains durable direction and cannot turn into a forward run`() {
        for (mode in Mode.entries) for (stage in listOf("rollback-intent", "clear-authority", "revoke", "protected", "set", "broadcast", "readback", "cancelled")) {
            for (after in listOf(false, true)) {
                val f = F(mode); val ledger = f.elevation!!.state.ledger
                if (stage != "rollback-intent") outcome(PeopleChangeOutcome.ROLLING_BACK, f.stop())
                f.crash = "$stage:${if (after) "after" else "before"}"
                assertFailsWith<IllegalStateException> {
                    if (stage == "rollback-intent") f.stop() else f.tick(WaitingPhase.ROLLING_BACK)
                }
                f.crash = null
                if (stage == "rollback-intent" && !after) outcome(PeopleChangeOutcome.ROLLING_BACK, f.stop())
                f.events.clear()
                outcome(PeopleChangeOutcome.IGNORED, f.tick(fresh = true))
                assertTrue(f.events.isEmpty())
                if (f.waiting!!.operation!!.phase != WaitingPhase.CANCELLED)
                    outcome(PeopleChangeOutcome.CANCELLED, f.tick(WaitingPhase.ROLLING_BACK, fresh = true))
                assertEquals(mode, f.credentials!!.mode)
                assertEquals(0, f.patchWrites)
                assertEquals(1, f.waiting!!.texts.count { it.event == WaitingEvent.CANCELLED_AFTER_ROLLBACK })
                assertSame(ledger, f.elevation!!.state.ledger)
            }
        }
    }

    @Test fun `stop rejects pending wrong expired replaced foreign and replayed authority`() {
        for (mode in Mode.entries) {
            val f = F(mode)
            assertNull(f.controller.prepareStop(f.id, f.incoming))
            val request = f.controller.prepareStop(f.id, f.a)!!
            val wrong = assertIs<PinResult.Authorized>(f.vault.authenticate(f.a, f.pin, PrivilegedAction("wrong", "action"))).authority
            outcome(PeopleChangeOutcome.REFUSED, f.controller.stop(request, wrong))
            val fresh = assertIs<PinResult.Authorized>(f.vault.authenticate(f.a, f.pin, request.action)).authority
            outcome(PeopleChangeOutcome.REFUSED, f.controller().stop(request, fresh))
            f.replace(f.a)
            outcome(PeopleChangeOutcome.REFUSED, f.controller.stop(request, fresh))
            val next = f.controller.prepareStop(f.id, f.a)!!
            val expired = assertIs<PinResult.Authorized>(f.vault.authenticate(f.a, f.pin, next.action)).authority
            f.now = f.now!!.let { it.copy(boot = it.boot.copy(elapsedMillis = it.boot.elapsedMillis + 120_000)) }
            outcome(PeopleChangeOutcome.REFUSED, f.controller.stop(next, expired))
            val again = assertIs<PinResult.Authorized>(f.vault.authenticate(f.a, f.pin, next.action)).authority
            f.fail = "rollback-intent"
            outcome(PeopleChangeOutcome.NEEDS_ATTENTION, f.controller.stop(next, again))
            f.fail = null
            outcome(PeopleChangeOutcome.REFUSED, f.controller.stop(next, again))
            assertEquals(WaitingPhase.COMMITTING, f.waiting!!.operation!!.phase)
        }
    }

    @Test fun `stop disappears after swap and old PIN recovery fresh tokens cannot authorize new mode`() {
        for (mode in Mode.entries) {
            val f = F(mode); val stop = f.controller.prepareStop(f.id, f.a)!!
            val token = assertIs<PinResult.Authorized>(f.vault.authenticate(f.a, f.pin, stop.action)).authority
            outcome(PeopleChangeOutcome.APPLIED, f.tick())
            assertNull(f.controller.prepareStop(f.id, f.incoming))
            assertNull(f.controller.prepareStop(f.id, f.a))
            outcome(PeopleChangeOutcome.REFUSED, f.controller.stop(stop, token))
            assertFalse(f.vault.consumeFor(token, stop.action, f.a))
            assertIs<PinResult.Unavailable>(f.vault.authenticate(f.a, f.pin, stop.action))
            assertIs<RecoveryResult.Unavailable>(f.vault.recover(f.a, f.first.recoveryCode.toCharArray(), f.pin))
            assertIs<PinResult.Authorized>(f.vault.authenticate(f.incoming, f.pin, PrivilegedAction("new", "mode")))
        }
    }

    @Test fun `old self countdown is invalidated at actual switch without refunding ledger`() {
        val f = F(Mode.SELF_MANAGED); f.seedAuthority(countdown = true)
        val ledger = f.elevation!!.state.ledger
        outcome(PeopleChangeOutcome.APPLIED, f.tick())
        assertNull(f.elevation!!.state.countdown); assertNull(f.elevation!!.person)
        assertSame(ledger, f.elevation!!.state.ledger)
    }

    @Test fun `changed direction or credentials at each effect boundary fences the next step`() {
        for (mode in Mode.entries) for (stage in listOf("clear-authority", "revoke", "protected", "set", "broadcast", "readback")) {
            for (direction in listOf(false, true)) {
                val f = F(mode)
                f.hook = { event -> if (event == stage) {
                    if (direction) f.waiting = f.waiting!!.replacing(f.waiting!!.revision + 1,
                        f.waiting!!.operation!!.withPhase(WaitingPhase.ROLLING_BACK)) else f.replace(f.a)
                } }
                outcome(PeopleChangeOutcome.NEEDS_ATTENTION, f.tick())
                assertEquals(0, f.patchWrites)
                assertEquals(stage, f.events.last())
                f.hook = null
                if (direction) outcome(PeopleChangeOutcome.CANCELLED, f.tick(WaitingPhase.ROLLING_BACK, fresh = true))
                else outcome(PeopleChangeOutcome.APPLIED, f.tick(fresh = true))
            }
        }
    }

    @Test fun `joint swap compares elevation revision and direction at final write boundary`() {
        for (mode in Mode.entries) for (change in listOf("elevation", "direction", "credentials")) {
            val f = F(mode)
            f.hook = { event -> if (event == "patch") when (change) {
                "elevation" -> f.elevation = f.elevation!!.replacing(f.elevation!!.state)
                "direction" -> f.waiting = f.waiting!!.replacing(f.waiting!!.revision + 1,
                    f.waiting!!.operation!!.withPhase(WaitingPhase.ROLLING_BACK))
                else -> f.replace(f.a)
            } }
            failed(PeopleChangeFailure.PERSISTENCE, f.tick())
            assertEquals(0, f.patchWrites)
            assertEquals(mode, f.credentials!!.mode)
        }
    }

    @Test fun `late credential recovery after swap survives completion failure and replay`() {
        for (mode in Mode.entries) {
            val f = F(mode)
            outcome(PeopleChangeOutcome.APPLIED, f.tick())
            assertIs<RecoveryResult.Replaced>(f.vault.recover(f.incoming, f.prepared.recoveryCode.toCharArray(), f.newPin))
            val latest = f.credentials!!.people[f.incoming]
            f.fail = "completed"
            failed(PeopleChangeFailure.PERSISTENCE, f.tick(fresh = true))
            f.fail = null
            outcome(PeopleChangeOutcome.COMPLETED, f.tick(fresh = true))
            assertSame(latest, f.credentials!!.people[f.incoming])
            assertEquals(1, f.patchWrites)
            assertEquals(1, f.events.count { it == "set" })
            assertEquals(1L, f.waiting!!.texts.last().recipientGenerations[f.incoming])
        }
    }

    @Test fun `damaged or exhausted revision ledger cannot be replaced with empty fresh quota`() {
        for (corrupt in listOf(false, true)) {
            val f = F()
            f.elevation = if (corrupt) null else ElevationRecord(Long.MAX_VALUE, f.elevation!!.state, f.elevation!!.journal)
            outcome(PeopleChangeOutcome.NEEDS_ATTENTION, f.tick())
            assertEquals(0, f.patchWrites)
            assertEquals(Mode.TRUSTED, f.credentials!!.mode)
            assertTrue(f.elevationStore.revocationPending)
        }
    }

    @Test fun `clock failure and stale journal identities never swap mode and rollback needs no working clock`() {
        val f = F()
        outcome(PeopleChangeOutcome.IGNORED, f.controller.tick("other", WaitingPhase.COMMITTING))
        outcome(PeopleChangeOutcome.IGNORED, f.tick(WaitingPhase.ROLLING_BACK))
        f.now = null
        failed(PeopleChangeFailure.CLOCK, f.tick())
        assertEquals(0, f.patchWrites)
        val g = F(Mode.SELF_MANAGED)
        outcome(PeopleChangeOutcome.ROLLING_BACK, g.stop()); g.now = null
        outcome(PeopleChangeOutcome.CANCELLED, g.tick(WaitingPhase.ROLLING_BACK))
    }

    @Test fun `applied marker cannot complete another waiting generation`() {
        val f = F(); outcome(PeopleChangeOutcome.APPLIED, f.tick())
        val old = f.waiting!!
        f.waiting = WaitingState(old.revision, old.operation, old.texts, "new-storage-generation")
        failed(PeopleChangeFailure.STATE_CHANGED, f.tick())
        assertEquals(1, f.patchWrites)
        assertFalse(f.waiting!!.texts.any { it.event == WaitingEvent.COMPLETED })
    }

    private fun policy(f: F): PolicyInput {
        val home = PackageName("test.home"); val sms = PackageName("test.sms"); val dialer = PackageName("test.dialer")
        return PolicyInput("model", "build", home, sms,
            listOf(home, sms, dialer, PLAY_PACKAGE).map { InstalledPackage(it, true, 1) },
            listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)),
            BuildInventory("model", "build", 1, NeverHideList(setOf(sms)), setOf(sms), emptySet()), waitingOperation = f.waiting?.operation)
    }
    private fun elevationController(f: F): ElevationController = ElevationController(f.elevationStore, { f.credentials }, f.vault,
        RunningClock { f.now }, { ElevationLimits() }, { policy(f) }, { BatteryState(50, false) }, object : SessionEffects {
            override fun revokeAllRelaxations(): Boolean { f.physicallyOpen = false; return true }
            override fun open(access: SessionAccess): Boolean { f.physicallyOpen = true; return true }
            override fun protectedStateCheck() = true
            override fun readBackPolicy() = true
        }, AppApprovalIdSource { "new-elevation" })

    @Test fun `switch then immediately request elevation preserves exhausted debits through restart and epochs`() {
        for (restart in listOf(false, true)) for (repair in listOf(false, true)) {
            val f = F(); val e = elevationController(f)
            assertEquals(ElevationOutcome.IDLE, e.onStart())
            val ledger = f.elevation!!.state.ledger
            outcome(PeopleChangeOutcome.APPLIED, f.tick())
            outcome(PeopleChangeOutcome.COMPLETED, f.tick())
            if (repair) f.now = ClockReading(RunningTime("repaired", 0), f.now!!.boot)
            val controller = if (restart) elevationController(f).also { assertEquals(ElevationOutcome.IDLE, it.onStart()) } else e
            val r = controller.prepare(f.incoming, ElevationTarget.Play)!!
            val grant = assertIs<PinResult.Authorized>(f.vault.authenticate(f.incoming, f.pin, r.action)).authority
            assertEquals(ElevationOutcome.LIMIT_REACHED, controller.request(r, grant))
            assertSame(ledger, f.elevation!!.state.ledger)
            assertEquals(0, ledger.availability(2, f.now!!.proven).turnsLeft)
        }
    }

    @Test fun `new self PIN may request a countdown when preserved ledger has room`() {
        val f = F()
        f.elevation = ElevationRecord(state = ElevationState(ElevationLedger()))
        val e = elevationController(f); assertEquals(ElevationOutcome.IDLE, e.onStart())
        outcome(PeopleChangeOutcome.APPLIED, f.tick()); outcome(PeopleChangeOutcome.COMPLETED, f.tick())
        val r = e.prepare(f.incoming, ElevationTarget.Play)!!
        val grant = assertIs<PinResult.Authorized>(f.vault.authenticate(f.incoming, f.pin, r.action)).authority
        assertEquals(ElevationOutcome.COUNTING, e.request(r, grant))
        assertTrue(f.elevation!!.state.ledger.debits.isEmpty())
        assertFalse(f.physicallyOpen)
    }

    @Test fun `lost record before or after swap recovers active mode and accounts across every recovery crash`() {
        val stages = listOf("recover-revoke", "recover-policy", "recover-reset", "recover-friction", "recover-policy-read",
            "recover-reset-read", "recover-friction-read", "recover-release")
        for (mode in Mode.entries) for (applied in listOf(false, true)) for (stage in stages) for (after in listOf(false, true)) {
            val f = F(mode)
            if (applied) {
                outcome(PeopleChangeOutcome.APPLIED, f.tick())
                assertIs<RecoveryResult.Replaced>(f.vault.recover(f.incoming, f.prepared.recoveryCode.toCharArray(), f.newPin))
            } else {
                f.fail = "readback"; outcome(PeopleChangeOutcome.NEEDS_ATTENTION, f.tick()); f.fail = null
            }
            val truth = f.credentials!!; val ledger = f.elevation!!.state.ledger
            f.waiting = null
            val loss = LostWaitingRecord("mode-loss")
            val store = object : WaitingReconciliationStore {
                override fun read() = f.waiting
                override fun compareAndSet(expected: WaitingState, replacement: WaitingState) = false
                override fun lostRecord() = if (f.waiting == null) loss else null
                override fun completeReconciliation(lost: LostWaitingRecord, expectedCredentials: CredentialState,
                    replacement: WaitingState): Boolean = f.edge("recover-release") {
                    if (f.waiting != null || f.credentials !== expectedCredentials || lost !== loss) false
                    else { f.waiting = replacement; true }
                }
            }
            val effects = object : ReconciliationEffects {
                override fun revokeTemporaryAccess(): Boolean = f.edge("recover-revoke") {
                    f.physicallyOpen = false
                    f.elevation = f.elevation!!.replacing(ElevationMachine.onRestart(f.elevation!!.state), SessionJournal.Empty, null)
                    true
                }
                override fun isDeviceOwner() = true
                override fun applyPolicy(plan: PolicyPlan) = f.edge("recover-policy") { true }
                override fun applyResetProtection(userIds: Set<String>) = f.edge("recover-reset") { f.resetIds = userIds; true }
                override fun applyFriction(settings: FrictionSettings) = f.edge("recover-friction") { settings == truth.recoveryProfile!!.friction }
                override fun readBackPolicy(plan: PolicyPlan) = f.edge("recover-policy-read") { true }
                override fun readBackResetProtection(userIds: Set<String>) = f.edge("recover-reset-read") { f.resetIds == userIds }
                override fun readBackFriction(settings: FrictionSettings) = f.edge("recover-friction-read") { settings == truth.recoveryProfile!!.friction }
            }
            val reconciler = WaitingReconciler(store, { f.credentials }, { policy(f) }, effects, f.vault)
            f.crash = "$stage:${if (after) "after" else "before"}"
            assertFailsWith<IllegalStateException> { reconciler.reconcile() }
            f.crash = null
            if (f.waiting == null) assertEquals(ReconcileOutcome.RECOVERED, reconciler.reconcile().outcome)
            assertEquals(ReconcileOutcome.IGNORED, reconciler.reconcile().outcome)
            assertSame(truth, f.credentials)
            assertSame(ledger, f.elevation!!.state.ledger)
            assertEquals(truth.mode, f.waiting!!.recoveryReceipts.single().mode)
            assertEquals(truth.people.mapValues { it.value.generation }, f.waiting!!.recoveryReceipts.single().recipientGenerations)
            assertEquals(RecoveredState.ACTIVE, f.waiting!!.recoveryReceipts.single().state)
            assertEquals(truth.recoveryProfile!!.resetProtectionUserIds, f.resetIds)
            assertNull(f.waiting!!.operation)
            outcome(PeopleChangeOutcome.IGNORED, f.tick(fresh = true))
            assertEquals(if (applied) 1 else 0, f.patchWrites)
        }
    }

    @Test fun `completion credential comparison freezes current recipients and refuses a changed snapshot`() {
        val f = F(Mode.SELF_MANAGED)
        outcome(PeopleChangeOutcome.APPLIED, f.tick())
        f.hook = { event -> if (event == "completed") f.replace(f.incoming) }
        failed(PeopleChangeFailure.PERSISTENCE, f.tick())
        assertFalse(f.waiting!!.texts.any { it.event == WaitingEvent.COMPLETED })
        f.hook = null
        outcome(PeopleChangeOutcome.COMPLETED, f.tick())
        assertEquals(1L, f.waiting!!.texts.last().recipientGenerations[f.incoming])
    }

    @Test fun `failed credential readback after atomic swap never reapplies setup credentials`() {
        val f = F()
        f.hook = { event -> if (event == "patch") f.fail = "credentials" }
        failed(PeopleChangeFailure.PATCH_READBACK, f.tick())
        assertNotNull(f.credentials!!.appliedPeopleChange)
        assertNull(f.controller.prepareStop(f.id, f.incoming))
        f.hook = null; f.fail = null
        outcome(PeopleChangeOutcome.COMPLETED, f.tick(fresh = true))
        assertEquals(1, f.patchWrites)
    }
}
