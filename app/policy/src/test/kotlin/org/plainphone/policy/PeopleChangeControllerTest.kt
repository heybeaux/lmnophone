// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class PeopleChangeControllerTest {
    private class F(val kind: WaitingChangeKind = WaitingChangeKind.HAND_OVER) {
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
        val transaction = object : PeopleChangeCommitter {
            override fun applyPatch(expectedCredentials: CredentialState, replacementCredentials: CredentialState,
                expectedWaiting: WaitingState): Boolean = edge("patch") {
                if (fail == "patch" || credentials !== expectedCredentials || waiting !== expectedWaiting ||
                    waiting?.operation?.phase != WaitingPhase.COMMITTING) false
                else { credentials = replacementCredentials; patchWrites++; true }
            }
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
        val effects = object : PeopleResetProtectionEffects {
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
            if (kind != WaitingChangeKind.ADD_PERSON) people[b] = second.credential.copy(contact = PersonContact("synthetic-second", "synthetic-second-number"))
            credentials = CredentialState(Mode.TRUSTED, people, recoveryProfile = RecoveryProfile(
                people.keys.associateWith { "synthetic-${it.value}-account" }, "synthetic-backup", FrictionSettings(600_000, 3)))
            original = credentials!!
            resetIds = original.recoveryProfile!!.resetProtectionUserIds
            val request = when (kind) {
                WaitingChangeKind.ADD_PERSON -> waitingController.prepareAdd(a, enrolled)
                WaitingChangeKind.REMOVE_PERSON -> waitingController.prepareRemove(a, b)
                else -> waitingController.prepareHandOver(a, enrolled)
            }!!
            val authority = assertIs<PinResult.Authorized>(vault.authenticate(a, pin, request.action)).authority
            assertEquals(WaitingOutcome.PENDING, waitingController.startPeopleChange(request, authority, true))
            id = request.requestId
            now = ClockReading(RunningTime("epoch", 86_400_000), BootTime("boot", 86_400_000))
            assertEquals(WaitingOutcome.COMMITTING, waitingController.tick(id, BatteryState(50, false)))
            events.clear()
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
        fun futureIds() = waiting!!.operation!!.peoplePatch!!.replacementForCommit(credentials!!)!!.recoveryProfile!!.resetProtectionUserIds
    }
    private fun outcome(expected: PeopleChangeOutcome, result: PeopleChangeResult) = assertEquals(expected, result.outcome)
    private fun failed(expected: PeopleChangeFailure, result: PeopleChangeResult) {
        outcome(PeopleChangeOutcome.NEEDS_ATTENTION, result)
        assertEquals(expected, result.failure)
    }

    @Test fun `every people kind sets broadcasts reads back then atomically patches then completes`() {
        for (kind in listOf(WaitingChangeKind.ADD_PERSON, WaitingChangeKind.HAND_OVER, WaitingChangeKind.REMOVE_PERSON)) {
            val f = F(kind)
            val future = f.futureIds()
            outcome(PeopleChangeOutcome.APPLIED, f.tick())
            assertEquals(listOf("set", "broadcast", "readback", "patch", "credential-readback"), f.events)
            assertEquals(future, f.resetIds)
            assertTrue("synthetic-backup" in f.resetIds)
            assertEquals(1, f.patchWrites)
            assertEquals(1L, f.credentials!!.structureGeneration)
            assertEquals(f.id, f.credentials!!.appliedPeopleChange!!.requestId)
            assertEquals("journal-epoch", f.credentials!!.appliedPeopleChange!!.waitingGeneration)
            assertEquals(WaitingPhase.COMMITTING, f.waiting!!.operation!!.phase)
            assertFalse(f.waiting!!.texts.any { it.event == WaitingEvent.COMPLETED })
            outcome(PeopleChangeOutcome.COMPLETED, f.tick(fresh = true))
            assertEquals(WaitingPhase.COMPLETED, f.waiting!!.operation!!.phase)
            val receipt = f.waiting!!.texts.last()
            assertEquals(WaitingEvent.COMPLETED, receipt.event)
            assertEquals(f.credentials!!.people.mapValues { it.value.generation }, receipt.recipientGenerations)
            assertEquals(f.credentials!!.people.mapValues { it.value.contact }, receipt.recipientContacts)
            assertEquals(1, f.events.count { it == "set" })
            assertEquals(1, f.waiting!!.texts.count { it.event == WaitingEvent.COMPLETED })
            outcome(PeopleChangeOutcome.IGNORED, f.tick())
        }
    }

    @Test fun `failed set broadcast or readback holds old authority and retries the whole reset sequence`() {
        for ((stage, reason) in listOf("set" to PeopleChangeFailure.RESET_APPLY,
            "broadcast" to PeopleChangeFailure.RESET_BROADCAST, "readback" to PeopleChangeFailure.RESET_READBACK)) {
            val f = F()
            val before = f.credentials
            f.fail = stage
            failed(reason, f.tick())
            assertSame(before, f.credentials)
            assertEquals(WaitingPhase.COMMITTING, f.waiting!!.operation!!.phase)
            assertEquals(0, f.patchWrites)
            assertIs<PinResult.Unavailable>(f.vault.authenticate(f.incoming, f.pin, PrivilegedAction("test", "pending")))
            f.fail = null
            outcome(PeopleChangeOutcome.APPLIED, f.tick(fresh = true))
            assertEquals(2, f.events.count { it == "set" })
        }
    }

    @Test fun `patch CAS failure keeps old holders and does not falsely complete`() {
        val f = F()
        val before = f.credentials
        f.fail = "patch"
        failed(PeopleChangeFailure.PERSISTENCE, f.tick())
        assertSame(before, f.credentials)
        assertNull(f.credentials!!.appliedPeopleChange)
        assertEquals(WaitingPhase.COMMITTING, f.waiting!!.operation!!.phase)
        f.fail = null
        outcome(PeopleChangeOutcome.APPLIED, f.tick(fresh = true))
    }

    @Test fun `credential changes after commit preserve latest unrelated slot and do not cancel`() {
        val f = F()
        assertIs<RecoveryResult.Replaced>(f.vault.recover(f.a, f.first.recoveryCode.toCharArray(), f.newPin))
        assertIs<RecoveryResult.Replaced>(f.vault.recover(f.b, f.second.recoveryCode.toCharArray(), f.newPin))
        val latest = f.credentials!!.people.getValue(f.b).copy(pinCounter = FailureCounter(5, 30_000), recoveryCounter = FailureCounter(6, 60_000))
        f.credentials = f.credentials!!.replacing(f.b, latest, CredentialChannel.PIN)
        val notices = f.credentials!!.pendingNotices
        outcome(PeopleChangeOutcome.APPLIED, f.tick())
        assertSame(latest, f.credentials!!.people[f.b])
        assertEquals(notices, f.credentials!!.pendingNotices)
        assertEquals(f.original.recoveryProfile!!.backupUserId, f.credentials!!.recoveryProfile!!.backupUserId)
        assertEquals(f.original.recoveryProfile!!.friction, f.credentials!!.recoveryProfile!!.friction)
        assertFalse(f.a in f.credentials!!.people)
    }

    @Test fun `new holder recovery before completion survives replay and patch marker survives all vault writes`() {
        val f = F()
        outcome(PeopleChangeOutcome.APPLIED, f.tick())
        val marker = f.credentials!!.appliedPeopleChange
        assertIs<RecoveryResult.Replaced>(f.vault.recover(f.incoming, f.prepared.recoveryCode.toCharArray(), f.newPin))
        val latest = f.credentials!!.people.getValue(f.incoming)
        assertSame(marker, f.credentials!!.appliedPeopleChange)
        outcome(PeopleChangeOutcome.COMPLETED, f.tick(fresh = true))
        assertSame(latest, f.credentials!!.people[f.incoming])
        assertEquals(1L, f.credentials!!.structureGeneration)
        assertEquals(1, f.patchWrites)
        assertEquals(1, f.events.count { it == "set" })
        assertIs<PinResult.Wrong>(f.vault.authenticate(f.incoming, f.pin, PrivilegedAction("test", "old")))
        assertIs<PinResult.Authorized>(f.vault.authenticate(f.incoming, f.newPin, PrivilegedAction("test", "new")))
        assertIs<PinResult.Unavailable>(f.vault.authenticate(f.a, f.pin, PrivilegedAction("test", "outgoing")))
        assertIs<RecoveryResult.Unavailable>(f.vault.recover(f.a, f.first.recoveryCode.toCharArray(), f.pin))
    }

    @Test fun `failed completion write retries only completion and freezes one current recipient snapshot`() {
        val f = F()
        outcome(PeopleChangeOutcome.APPLIED, f.tick())
        f.fail = "completed"
        failed(PeopleChangeFailure.PERSISTENCE, f.tick())
        assertEquals(WaitingPhase.COMMITTING, f.waiting!!.operation!!.phase)
        f.fail = null
        f.replace(f.incoming)
        val current = f.credentials!!
        outcome(PeopleChangeOutcome.COMPLETED, f.tick(fresh = true))
        assertEquals(current.people.mapValues { it.value.generation }, f.waiting!!.texts.last().recipientGenerations)
        assertEquals(current.people.mapValues { it.value.contact }, f.waiting!!.texts.last().recipientContacts)
        assertEquals(1, f.patchWrites)
        assertEquals(1, f.events.count { it == "set" })
    }

    @Test fun `every forward crash boundary preserves exactly old or new holder authority`() {
        for (stage in listOf("set", "broadcast", "readback", "patch", "credential-readback", "completed")) {
            for (after in listOf(false, true)) {
                val f = F()
                f.crash = "$stage:${if (after) "after" else "before"}"
                assertFailsWith<IllegalStateException> {
                    f.tick()
                    if (stage == "completed") f.tick()
                }
                f.crash = null
                val applied = f.credentials!!.appliedPeopleChange != null
                assertEquals(!applied, f.a in f.credentials!!.people)
                assertEquals(applied, f.incoming in f.credentials!!.people)
                assertTrue(f.b in f.credentials!!.people)
                assertEquals(if (applied) 1L else 0L, f.credentials!!.structureGeneration)
                val person = if (applied) f.incoming else f.a
                assertIs<PinResult.Authorized>(f.vault.authenticate(person, f.pin, PrivilegedAction("test", "survivor")))
                if (f.waiting!!.operation!!.phase != WaitingPhase.COMPLETED) {
                    val result = f.tick(fresh = true)
                    if (result.outcome == PeopleChangeOutcome.APPLIED) outcome(PeopleChangeOutcome.COMPLETED, f.tick(fresh = true))
                    else outcome(PeopleChangeOutcome.COMPLETED, result)
                }
                assertEquals(1, f.patchWrites)
                assertEquals(1, f.waiting!!.texts.count { it.event == WaitingEvent.COMPLETED })
            }
        }
    }

    @Test fun `stop persists authenticated rollback intent before any restoring effects`() {
        val f = F()
        f.fail = "readback"
        failed(PeopleChangeFailure.RESET_READBACK, f.tick())
        assertNotEquals(f.original.recoveryProfile!!.resetProtectionUserIds, f.resetIds)
        f.events.clear()
        outcome(PeopleChangeOutcome.ROLLING_BACK, f.stop(f.b))
        assertEquals(listOf("rollback-intent"), f.events)
        val op = f.waiting!!.operation!!
        assertEquals(WaitingPhase.ROLLING_BACK, op.phase)
        assertEquals(f.b, op.stopper)
        assertEquals(0L, op.stopperGeneration)
        assertEquals(WaitingEvent.ROLLING_BACK, f.waiting!!.texts.last().event)
        f.fail = null
        outcome(PeopleChangeOutcome.CANCELLED, f.tick(WaitingPhase.ROLLING_BACK, fresh = true))
        assertEquals(f.credentials!!.recoveryProfile!!.resetProtectionUserIds, f.resetIds)
        assertEquals(listOf("rollback-intent", "set", "broadcast", "readback", "cancelled"), f.events)
        assertEquals(WaitingEvent.CANCELLED_AFTER_ROLLBACK, f.waiting!!.texts.last().event)
        assertEquals(setOf(f.a, f.b), f.credentials!!.people.keys)
        assertEquals(0, f.patchWrites)
    }

    @Test fun `failed rollback write leaves committing without any reset effect`() {
        val f = F()
        f.fail = "rollback-intent"
        failed(PeopleChangeFailure.PERSISTENCE, f.stop())
        assertEquals(WaitingPhase.COMMITTING, f.waiting!!.operation!!.phase)
        assertEquals(listOf("rollback-intent"), f.events)
        assertFalse(f.waiting!!.texts.any { it.event == WaitingEvent.ROLLING_BACK })
    }

    @Test fun `every rollback failure stays rolling back and retries current active list`() {
        for (stage in listOf("set", "broadcast", "readback", "cancelled")) {
            val f = F()
            outcome(PeopleChangeOutcome.ROLLING_BACK, f.stop())
            f.fail = stage
            outcome(PeopleChangeOutcome.NEEDS_ATTENTION, f.tick(WaitingPhase.ROLLING_BACK))
            assertEquals(WaitingPhase.ROLLING_BACK, f.waiting!!.operation!!.phase)
            assertFalse(f.waiting!!.texts.any { it.event == WaitingEvent.CANCELLED_AFTER_ROLLBACK })
            f.fail = null
            f.replace(f.b)
            outcome(PeopleChangeOutcome.CANCELLED, f.tick(WaitingPhase.ROLLING_BACK, fresh = true))
            assertEquals(f.credentials!!.people.mapValues { it.value.generation }, f.waiting!!.texts.last().recipientGenerations)
            assertEquals(f.credentials!!.recoveryProfile!!.resetProtectionUserIds, f.resetIds)
            assertEquals(0, f.patchWrites)
        }
    }

    @Test fun `every stop and rollback crash keeps direction durable and never resumes handover`() {
        for (stage in listOf("rollback-intent", "set", "broadcast", "readback", "cancelled")) {
            for (after in listOf(false, true)) {
                val f = F()
                if (stage != "rollback-intent") outcome(PeopleChangeOutcome.ROLLING_BACK, f.stop())
                f.events.clear()
                f.crash = "$stage:${if (after) "after" else "before"}"
                assertFailsWith<IllegalStateException> {
                    if (stage == "rollback-intent") f.stop() else f.tick(WaitingPhase.ROLLING_BACK)
                }
                f.crash = null
                assertEquals(setOf(f.a, f.b), f.credentials!!.people.keys)
                assertEquals(0, f.patchWrites)
                if (stage == "rollback-intent" && !after) outcome(PeopleChangeOutcome.ROLLING_BACK, f.stop())
                f.events.clear()
                outcome(PeopleChangeOutcome.IGNORED, f.tick(WaitingPhase.COMMITTING, fresh = true))
                assertTrue(f.events.isEmpty())
                if (f.waiting!!.operation!!.phase != WaitingPhase.CANCELLED) {
                    outcome(PeopleChangeOutcome.CANCELLED, f.tick(WaitingPhase.ROLLING_BACK, fresh = true))
                }
                assertEquals(1, f.waiting!!.texts.count { it.event == WaitingEvent.CANCELLED_AFTER_ROLLBACK })
                assertEquals(0L, f.credentials!!.structureGeneration)
            }
        }
    }

    @Test fun `stop needs exact current holder fresh PIN and cannot use pending or expired authority`() {
        val f = F()
        assertNull(f.controller.prepareStop(f.id, f.incoming))
        val request = f.controller.prepareStop(f.id, f.a)!!
        val wrongPerson = assertIs<PinResult.Authorized>(f.vault.authenticate(f.b, f.pin, request.action)).authority
        outcome(PeopleChangeOutcome.REFUSED, f.controller.stop(request, wrongPerson))
        val wrongAction = assertIs<PinResult.Authorized>(f.vault.authenticate(f.a, f.pin, PrivilegedAction("other", "other"))).authority
        outcome(PeopleChangeOutcome.REFUSED, f.controller.stop(request, wrongAction))
        val expired = assertIs<PinResult.Authorized>(f.vault.authenticate(f.a, f.pin, request.action)).authority
        f.now = f.now!!.let { it.copy(boot = it.boot.copy(elapsedMillis = it.boot.elapsedMillis + 120_000)) }
        outcome(PeopleChangeOutcome.REFUSED, f.controller.stop(request, expired))
        assertEquals(WaitingPhase.COMMITTING, f.waiting!!.operation!!.phase)
        assertTrue(f.events.isEmpty())
    }

    @Test fun `stop preparation is one use process owned and invalidated by credential replacement`() {
        val f = F()
        val request = f.controller.prepareStop(f.id, f.a)!!
        val grant = assertIs<PinResult.Authorized>(f.vault.authenticate(f.a, f.pin, request.action)).authority
        outcome(PeopleChangeOutcome.REFUSED, f.controller().stop(request, grant))
        f.replace(f.a)
        outcome(PeopleChangeOutcome.REFUSED, f.controller.stop(request, grant))
        val next = f.controller.prepareStop(f.id, f.a)!!
        val fresh = assertIs<PinResult.Authorized>(f.vault.authenticate(f.a, f.pin, next.action)).authority
        f.fail = "rollback-intent"
        outcome(PeopleChangeOutcome.NEEDS_ATTENTION, f.controller.stop(next, fresh))
        f.fail = null
        outcome(PeopleChangeOutcome.REFUSED, f.controller.stop(next, fresh))
    }

    @Test fun `stop disappears after patch and an already prepared stop cannot undo new holders`() {
        val f = F()
        val stop = f.controller.prepareStop(f.id, f.b)!!
        val grant = assertIs<PinResult.Authorized>(f.vault.authenticate(f.b, f.pin, stop.action)).authority
        outcome(PeopleChangeOutcome.APPLIED, f.tick())
        assertNull(f.controller.prepareStop(f.id, f.b))
        assertNull(f.controller.prepareStop(f.id, f.incoming))
        outcome(PeopleChangeOutcome.REFUSED, f.controller.stop(stop, grant))
        assertEquals(WaitingPhase.COMMITTING, f.waiting!!.operation!!.phase)
        outcome(PeopleChangeOutcome.COMPLETED, f.tick())
    }

    @Test fun `stale ids phases lost journal and unavailable credentials never cause effects`() {
        val f = F()
        outcome(PeopleChangeOutcome.IGNORED, f.controller.tick("stale", WaitingPhase.COMMITTING))
        outcome(PeopleChangeOutcome.IGNORED, f.tick(WaitingPhase.ROLLING_BACK))
        outcome(PeopleChangeOutcome.IGNORED, f.tick(WaitingPhase.PENDING))
        assertNull(f.controller.prepareStop("stale", f.a))
        f.fail = "journal"
        failed(PeopleChangeFailure.JOURNAL_READ, f.tick())
        f.fail = "credentials"
        failed(PeopleChangeFailure.CREDENTIALS, f.tick())
        assertNull(f.controller.prepareStop(f.id, f.a))
        assertTrue(f.events.isEmpty())
    }

    @Test fun `unexpected structure missing profile and marker mismatch cannot imply a completed patch`() {
        for (change in listOf("structure", "profile", "marker")) {
            val f = F()
            val c = f.credentials!!
            f.credentials = when (change) {
                "structure" -> CredentialState(c.mode, c.people, c.revision + 1, 1, recoveryProfile = c.recoveryProfile)
                "profile" -> CredentialState(c.mode, c.people, c.revision + 1, c.structureGeneration)
                else -> CredentialState(c.mode, c.people, c.revision + 1, 1, recoveryProfile = c.recoveryProfile,
                    appliedPeopleChange = AppliedPeopleChange(f.id, "other-journal-epoch", 1))
            }
            outcome(PeopleChangeOutcome.NEEDS_ATTENTION, f.tick())
            assertNull(f.controller.prepareStop(f.id, f.a))
            assertFalse(f.events.any { it in setOf("set", "patch", "completed") })
        }
    }

    @Test fun `clock failure prevents forward patch but does not prevent restrictive rollback`() {
        val f = F()
        f.now = null
        failed(PeopleChangeFailure.CLOCK, f.tick())
        assertTrue(f.events.isEmpty())
        val g = F()
        outcome(PeopleChangeOutcome.ROLLING_BACK, g.stop())
        g.now = null
        outcome(PeopleChangeOutcome.CANCELLED, g.tick(WaitingPhase.ROLLING_BACK))
    }

    @Test fun `direction change during an effect prevents remaining forward steps`() {
        for (stage in listOf("set", "broadcast", "readback")) {
            val f = F()
            f.hook = { event ->
                if (event == stage) {
                    f.waiting = f.waiting!!.replacing(f.waiting!!.revision + 1, f.waiting!!.operation!!.withPhase(WaitingPhase.ROLLING_BACK))
                }
            }
            failed(PeopleChangeFailure.STATE_CHANGED, f.tick())
            assertEquals(0, f.patchWrites)
            assertEquals(stage, f.events.last())
            f.hook = null
            outcome(PeopleChangeOutcome.CANCELLED, f.tick(WaitingPhase.ROLLING_BACK, fresh = true))
        }
    }

    @Test fun `joint comparison refuses rollback racing patch and patch racing rollback intent`() {
        val f = F()
        f.hook = { event ->
            if (event == "patch") f.waiting = f.waiting!!.replacing(f.waiting!!.revision + 1,
                f.waiting!!.operation!!.withPhase(WaitingPhase.ROLLING_BACK))
        }
        failed(PeopleChangeFailure.PERSISTENCE, f.tick())
        assertEquals(0, f.patchWrites)
        assertEquals(setOf(f.a, f.b), f.credentials!!.people.keys)
        val g = F()
        g.hook = { event -> if (event == "rollback-intent") g.replace(g.b) }
        failed(PeopleChangeFailure.PERSISTENCE, g.stop())
        assertEquals(WaitingPhase.COMMITTING, g.waiting!!.operation!!.phase)
    }

    @Test fun `credential change during reset readback prevents stale patch and retry preserves latest state`() {
        val f = F()
        f.hook = { event -> if (event == "readback") f.replace(f.b) }
        failed(PeopleChangeFailure.STATE_CHANGED, f.tick())
        assertEquals(0, f.patchWrites)
        val latest = f.credentials!!.people[f.b]
        f.hook = null
        outcome(PeopleChangeOutcome.APPLIED, f.tick())
        assertSame(latest, f.credentials!!.people[f.b])
    }

    @Test fun `turn off and pending operations never enter people executor`() {
        val f = F()
        val op = f.waiting!!.operation!!
        f.waiting = f.waiting!!.replacing(f.waiting!!.revision + 1, op.withPhase(WaitingPhase.PENDING))
        outcome(PeopleChangeOutcome.IGNORED, f.tick())
        f.waiting = f.waiting!!.replacing(f.waiting!!.revision + 1, WaitingOperation(op.identity, op.starter, op.generation,
            op.structureGeneration, op.mode, op.startedAt, WaitingPhase.COMMITTING, op.recipientGenerations))
        outcome(PeopleChangeOutcome.IGNORED, f.tick())
        assertNull(f.controller.prepareStop(f.id, f.a))
        assertTrue(f.events.isEmpty())
    }

    @Test fun `unavailable credential readback after patch cannot report applied or enable stop`() {
        val f = F()
        f.hook = { event -> if (event == "patch") f.fail = "credentials" }
        failed(PeopleChangeFailure.PATCH_READBACK, f.tick())
        assertNotNull(f.credentials!!.appliedPeopleChange)
        assertNull(f.controller.prepareStop(f.id, f.b))
        f.fail = null
        f.hook = null
        outcome(PeopleChangeOutcome.COMPLETED, f.tick(fresh = true))
        assertEquals(1, f.patchWrites)
    }

    @Test fun `completion compares credentials with journal so changed recipients cannot be queued stale`() {
        val f = F()
        outcome(PeopleChangeOutcome.APPLIED, f.tick())
        f.hook = { event -> if (event == "completed") f.replace(f.incoming) }
        failed(PeopleChangeFailure.PERSISTENCE, f.tick())
        assertFalse(f.waiting!!.texts.any { it.event == WaitingEvent.COMPLETED })
        f.hook = null
        outcome(PeopleChangeOutcome.COMPLETED, f.tick())
        assertEquals(1L, f.waiting!!.texts.last().recipientGenerations[f.incoming])
    }

    @Test fun `old journal generation cannot complete a patch marked in a different generation`() {
        val f = F()
        outcome(PeopleChangeOutcome.APPLIED, f.tick())
        val before = f.waiting!!
        f.waiting = WaitingState(before.revision, before.operation, before.texts, "different-generation")
        failed(PeopleChangeFailure.STATE_CHANGED, f.tick())
        assertFalse(f.waiting!!.texts.any { it.event == WaitingEvent.COMPLETED })
        assertEquals(1, f.patchWrites)
    }

    @Test fun `legacy null contacts and prior removed receipts survive successful completion`() {
        val f = F()
        val start = f.waiting!!.texts.first()
        val c = f.credentials!!
        f.credentials = c.replacing(f.b, c.people.getValue(f.b).copy(contact = null))
        outcome(PeopleChangeOutcome.APPLIED, f.tick())
        outcome(PeopleChangeOutcome.COMPLETED, f.tick())
        val completed = f.waiting!!.texts.last()
        assertEquals(setOf(f.b, f.incoming), completed.recipients)
        assertNull(completed.recipientContacts[f.b])
        assertSame(f.enrolled.credential.contact, completed.recipientContacts[f.incoming])
        assertEquals(setOf(f.a, f.b), start.recipients)
        assertNotNull(start.recipientContacts[f.a])
        assertTrue(f.waiting!!.texts.any { it === start })
    }

    @Test fun `recovery skipper and PIN stopper identities survive rollback without reverting rotated secrets`() {
        val f = F()
        assertIs<RecoveryResult.Replaced>(f.vault.recover(f.b, f.second.recoveryCode.toCharArray(), f.newPin))
        f.waiting = f.waiting!!.replacing(f.waiting!!.revision + 1, f.waiting!!.operation!!.withSkipper(f.b, 1))
        val latest = f.credentials!!.people.getValue(f.b)
        outcome(PeopleChangeOutcome.ROLLING_BACK, f.stop())
        outcome(PeopleChangeOutcome.CANCELLED, f.tick(WaitingPhase.ROLLING_BACK, fresh = true))
        val op = f.waiting!!.operation!!
        assertEquals(f.b, op.skipper)
        assertEquals(1L, op.skipperGeneration)
        assertEquals(f.a, op.stopper)
        assertSame(latest, f.credentials!!.people[f.b])
        assertIs<PinResult.Authorized>(f.vault.authenticate(f.b, f.newPin, PrivilegedAction("test", "rotated")))
    }

    @Test fun `previous application marker does not block the next people change or revive old callbacks`() {
        val f = F(WaitingChangeKind.ADD_PERSON)
        outcome(PeopleChangeOutcome.APPLIED, f.tick())
        outcome(PeopleChangeOutcome.COMPLETED, f.tick())
        val oldId = f.id
        val request = f.waitingController.prepareRemove(f.a, f.incoming)!!
        val authority = assertIs<PinResult.Authorized>(f.vault.authenticate(f.a, f.pin, request.action)).authority
        assertEquals(WaitingOutcome.PENDING, f.waitingController.startPeopleChange(request, authority, true))
        f.now = ClockReading(RunningTime("epoch", 172_800_000), BootTime("boot", 172_800_000))
        assertEquals(WaitingOutcome.COMMITTING, f.waitingController.tick(request.requestId, BatteryState(50, false)))
        outcome(PeopleChangeOutcome.IGNORED, f.controller.tick(oldId, WaitingPhase.COMMITTING))
        f.id = request.requestId
        outcome(PeopleChangeOutcome.APPLIED, f.tick())
        outcome(PeopleChangeOutcome.COMPLETED, f.tick())
        assertEquals(2, f.patchWrites)
        assertEquals(2L, f.credentials!!.structureGeneration)
        assertEquals(setOf(f.a), f.credentials!!.people.keys)
        assertEquals(request.requestId, f.credentials!!.appliedPeopleChange!!.requestId)
    }

    @Test fun `lost journal before and after patch reconciles active holders and backup without replaying enrollment`() {
        for (applied in listOf(false, true)) {
            val f = F()
            if (applied) {
                outcome(PeopleChangeOutcome.APPLIED, f.tick())
                assertIs<RecoveryResult.Replaced>(f.vault.recover(f.incoming, f.prepared.recoveryCode.toCharArray(), f.newPin))
            } else {
                f.fail = "readback"
                outcome(PeopleChangeOutcome.NEEDS_ATTENTION, f.tick())
                f.fail = null
            }
            val truth = f.credentials!!
            f.waiting = null
            failed(PeopleChangeFailure.JOURNAL_READ, f.tick())
            val loss = LostWaitingRecord("lost-episode")
            val store = object : WaitingReconciliationStore {
                override fun read() = f.waiting
                override fun compareAndSet(expected: WaitingState, replacement: WaitingState) = false
                override fun lostRecord() = if (f.waiting == null) loss else null
                override fun completeReconciliation(lost: LostWaitingRecord, expectedCredentials: CredentialState,
                    replacement: WaitingState): Boolean {
                    if (f.waiting != null || f.credentials !== expectedCredentials || lost !== loss) return false
                    f.waiting = replacement
                    return true
                }
            }
            val dialer = PackageName("test.systemdialer")
            val effects = object : ReconciliationEffects {
                override fun revokeTemporaryAccess() = true
                override fun isDeviceOwner() = true
                override fun applyPolicy(plan: PolicyPlan) = true
                override fun applyResetProtection(userIds: Set<String>): Boolean { f.resetIds = userIds; return true }
                override fun applyFriction(settings: FrictionSettings) = true
                override fun readBackPolicy(plan: PolicyPlan) = true
                override fun readBackResetProtection(userIds: Set<String>) = f.resetIds == userIds
                override fun readBackFriction(settings: FrictionSettings) = true
            }
            val reconciler = WaitingReconciler(store, { f.credentials }, {
                PolicyInput("model", "build", PackageName("test.home"), PackageName("test.sms"),
                    listOf(InstalledPackage(dialer, true, 1)),
                    listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)))
            }, effects, f.vault)
            assertEquals(ReconcileOutcome.RECOVERED, reconciler.reconcile().outcome)
            assertSame(truth, f.credentials)
            assertEquals(truth.recoveryProfile!!.resetProtectionUserIds, f.resetIds)
            assertEquals(truth.people.mapValues { it.value.generation }, f.waiting!!.recoveryReceipts.single().recipientGenerations)
            assertEquals(RecoveredState.ACTIVE, f.waiting!!.recoveryReceipts.single().state)
            assertEquals("lost-episode", f.waiting!!.storageGeneration)
            assertNull(f.waiting!!.operation)
            outcome(PeopleChangeOutcome.IGNORED, f.tick(fresh = true))
            assertEquals(if (applied) setOf(f.b, f.incoming) else setOf(f.a, f.b), f.credentials!!.people.keys)
        }
    }

}
