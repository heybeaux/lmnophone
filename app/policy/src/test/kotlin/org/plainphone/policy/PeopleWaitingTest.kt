// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class PeopleWaitingTest {
    private class F(two: Boolean = true, val contacts: Boolean = true) {
        val a = TrustedPersonId("first")
        val b = TrustedPersonId("second")
        val incoming = TrustedPersonId("incoming")
        val pin get() = CharArray(6) { '1' }
        val newPin get() = CharArray(6) { '2' }
        var now: ClockReading? = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
        var credentials: CredentialState? = null
        var credentialReads: MutableList<CredentialState?>? = null
        var waiting: WaitingState? = WaitingState(storageGeneration = "live")
        var failWaiting = false
        var failJoint = false
        var crashWaiting: Boolean? = null
        var crashJoint: Boolean? = null
        var beforeJoint: (() -> Unit)? = null
        var jointWrites = 0
        var hashChecks = 0
        var identity = 0
        var entropy = 0
        val intents = mutableMapOf<String, CancelIntent>()
        var failIntent = false
        val cancelStore = object : CancelIntentStore {
            override fun initialize(requestId: String): Boolean {
                if (failIntent || intents[requestId] == CancelIntent.REQUESTED) return false
                intents[requestId] = CancelIntent.CLEAR
                return true
            }
            override fun request(requestId: String): Boolean {
                if (failIntent) return false
                intents[requestId] = CancelIntent.REQUESTED
                return true
            }
            override fun read(requestId: String) = intents[requestId] ?: CancelIntent.UNKNOWN
        }
        val credentialStore = object : CredentialStore {
            override fun read() = credentials
            override fun compareAndSet(expected: CredentialState, replacement: CredentialState): Boolean {
                if (credentials !== expected) return false
                credentials = replacement
                return true
            }
        }
        val store = object : WaitingChangeStore {
            override fun read() = waiting
            override fun compareAndSet(expected: WaitingState, replacement: WaitingState): Boolean {
                if (crashWaiting == false) error("before waiting write")
                if (failWaiting || waiting !== expected) return false
                waiting = replacement
                if (crashWaiting == true) error("after waiting write")
                return true
            }
        }
        val vault = PinVault(credentialStore, KeyDerivation { chars, salt, _ ->
            hashChecks++
            java.security.MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
        }, SecretEntropy { n -> ByteArray(n) { (++entropy).toByte() } }, RunningClock { now })
        val firstPrepared = vault.prepareCredential(pin)
        val secondPrepared = vault.prepareCredential(pin)
        val incomingPrepared = vault.prepareCredential(pin)
        val enrolled = IncomingPersonEnrollment.confirm(incoming, PersonContact("synthetic-incoming", "synthetic-incoming-number"),
            "synthetic-incoming-account", incomingPrepared,
            EnrollmentConfirmations(true, true, true, true, true, true, true, true, true), EnrollmentWitness.Helper)!!
        init {
            val people = linkedMapOf(a to firstPrepared.credential.copy(contact = contact("first")))
            if (two) people[b] = secondPrepared.credential.copy(contact = contact("second"))
            credentials = CredentialState(Mode.TRUSTED, people, recoveryProfile = RecoveryProfile(
                people.keys.associateWith { "synthetic-${it.value}-account" }, "synthetic-backup", FrictionSettings(600_000, 3)))
        }
        fun contact(label: String) = if (contacts) PersonContact("synthetic-$label", "synthetic-$label-number") else null
        fun joint(old: CredentialState, next: CredentialState, before: WaitingState, after: WaitingState): Boolean {
            beforeJoint?.invoke()
            if (crashJoint == false) error("before joint write")
            if (failJoint || credentials !== old || waiting !== before ||
                cancelStore.read(after.operation!!.identity.requestId) != CancelIntent.CLEAR) return false
            credentials = next
            waiting = after
            jointWrites++
            if (crashJoint == true) error("after joint write")
            return true
        }
        fun controller() = WaitingChangeController(store, {
            credentialReads?.takeIf { it.isNotEmpty() }?.removeAt(0)
                ?: if (credentialReads == null) credentials else null
        }, RunningClock { now }, vault,
            WaitingIdentitySource { identity++; WaitingIdentity("request-$identity", "synthetic-cancel-$identity") }, cancelStore,
            WaitingSkipSupport(vault, WaitingSkipCommitter(::joint)),
            WaitingHandOverSupport(vault, WaitingHandOverCommitter(::joint)))
        val controller = controller()
        val battery = BatteryState(50, false)
        fun auth(request: WaitingStart, person: TrustedPersonId = a): FreshAuthority =
            assertIs<PinResult.Authorized>(vault.authenticate(person, pin, request.action)).authority
        fun start(kind: WaitingChangeKind = WaitingChangeKind.HAND_OVER): String {
            val request = when (kind) {
                WaitingChangeKind.ADD_PERSON -> controller.prepareAdd(a, enrolled)
                WaitingChangeKind.REMOVE_PERSON -> controller.prepareRemove(a, b)
                else -> controller.prepareHandOver(a, enrolled)
            }!!
            assertEquals(WaitingOutcome.PENDING, controller.startPeopleChange(request, auth(request), true))
            return request.requestId
        }
        fun at(total: Long, epoch: String = "epoch", boot: String = "boot", elapsed: Long = total) {
            now = ClockReading(RunningTime(epoch, total), BootTime(boot, elapsed))
        }
        fun replace(person: TrustedPersonId) {
            val c = credentials!!
            credentials = c.replacing(person, c.people.getValue(person).copy(generation = c.people.getValue(person).generation + 1))
        }
        fun skip(id: String, person: TrustedPersonId = b, code: String = secondPrepared.recoveryCode) =
            controller.skip(id, person, code.toCharArray(), newPin, true, battery)
        fun recoveryStart(request: WaitingStart, code: String = firstPrepared.recoveryCode,
            confirmed: Boolean = true, skipConfirmed: Boolean = true) = controller.startHandOverWithRecovery(
                request, code.toCharArray(), newPin, confirmed, skipConfirmed)
    }
    private val day = 86_400_000L
    private fun outcome(expected: WaitingOutcome, result: WaitingSkipResult) =
        assertEquals(expected, assertIs<WaitingSkipResult.NotSkipped>(result).outcome)

    @Test fun `all people kinds start in shared slot and queue only current holder contacts`() {
        for (kind in listOf(WaitingChangeKind.ADD_PERSON, WaitingChangeKind.HAND_OVER, WaitingChangeKind.REMOVE_PERSON)) {
            val f = F(two = kind != WaitingChangeKind.ADD_PERSON)
            val id = f.start(kind)
            val op = f.waiting!!.operation!!
            assertEquals(id, op.identity.requestId)
            assertEquals(kind, op.kind)
            assertEquals(WaitingPhase.PENDING, op.phase)
            assertNotNull(op.peoplePatch)
            val text = f.waiting!!.texts.single()
            assertEquals(WaitingEvent.STARTED, text.event)
            assertEquals(f.credentials!!.people.keys, text.recipients)
            for (person in text.recipients) assertSame(f.credentials!!.people[person]!!.contact, text.recipientContacts[person])
            assertFalse(f.incoming in text.recipients)
            assertEquals(f.credentials!!.structureGeneration, op.structureGeneration)
        }
    }

    @Test fun `handover start requires outgoing fresh PIN exact action and confirmation`() {
        val f = F()
        val request = f.controller.prepareHandOver(f.a, f.enrolled)!!
        assertEquals(WaitingOutcome.REFUSED, f.controller.startPeopleChange(request, f.auth(request, f.b), true))
        val unrelated = assertIs<PinResult.Authorized>(f.vault.authenticate(f.a, f.pin, PrivilegedAction("other", "other"))).authority
        assertEquals(WaitingOutcome.REFUSED, f.controller.startPeopleChange(request, unrelated, true))
        val grant = f.auth(request)
        assertEquals(WaitingOutcome.REFUSED, f.controller.startPeopleChange(request, grant, false))
        assertNull(f.waiting!!.operation)
        assertEquals(WaitingOutcome.PENDING, f.controller.startPeopleChange(request, grant, true))
        assertEquals(WaitingOutcome.REFUSED, f.controller.startPeopleChange(request, grant, true))
    }

    @Test fun `expired rebooted forged and other-controller preparations cannot start`() {
        for (reboot in listOf(false, true)) {
            val f = F()
            val request = f.controller.prepareHandOver(f.a, f.enrolled)!!
            val grant = f.auth(request)
            f.at(120_000, boot = if (reboot) "reboot" else "boot")
            assertEquals(WaitingOutcome.REFUSED, f.controller.startPeopleChange(request, grant, true))
        }
        val f = F()
        val request = f.controller.prepareHandOver(f.a, f.enrolled)!!
        assertEquals(WaitingOutcome.REFUSED, f.controller().startPeopleChange(request, f.auth(request), true))
        val off = f.controller.prepareTurnOff(f.a)!!
        assertEquals(WaitingOutcome.REFUSED, f.controller.startPeopleChange(off, f.auth(off), true))
        assertEquals(WaitingOutcome.REFUSED, f.controller.startTurnOff(request, f.auth(request), true, true))
    }

    @Test fun `preparation refuses holder limits strangers and self managed mode`() {
        val f = F()
        assertNull(f.controller.prepareAdd(f.a, f.enrolled))
        assertNull(f.controller.prepareHandOver(f.incoming, f.enrolled))
        assertNull(f.controller.prepareRemove(f.a, f.incoming))
        val solo = F(false)
        assertNull(solo.controller.prepareRemove(solo.a, solo.a))
        solo.credentials = CredentialState(Mode.SELF_MANAGED, solo.credentials!!.people, recoveryProfile = solo.credentials!!.recoveryProfile)
        assertNull(solo.controller.prepareAdd(solo.a, solo.enrolled))
        assertNull(solo.controller.prepareHandOver(solo.a, solo.enrolled))
    }

    @Test fun `start rechecks all dependencies after preparation without accepting incoming authority`() {
        val f = F()
        val request = f.controller.prepareRemove(f.a, f.b)!!
        f.replace(f.b)
        assertEquals(WaitingOutcome.REFUSED, f.controller.startPeopleChange(request, f.auth(request), true))
        assertNull(f.waiting!!.operation)
        assertIs<PinResult.Unavailable>(f.vault.authenticate(f.incoming, f.pin, request.action))
        assertIs<RecoveryResult.Unavailable>(f.vault.recover(f.incoming, f.incomingPrepared.recoveryCode.toCharArray(), f.pin))
    }

    @Test fun `twenty four proven hours is an exact deadline across process restart and reboot`() {
        for (kind in listOf(WaitingChangeKind.ADD_PERSON, WaitingChangeKind.HAND_OVER, WaitingChangeKind.REMOVE_PERSON)) {
            val f = F(kind != WaitingChangeKind.ADD_PERSON)
            f.at(3_600_000)
            val id = f.start(kind)
            f.at(3_600_000 + day - 1, boot = "reboot", elapsed = 12_345)
            assertEquals(WaitingOutcome.PENDING, f.controller().tick(id, f.battery))
            f.at(3_600_000 + day, boot = "reboot", elapsed = 12_346)
            assertEquals(WaitingOutcome.COMMITTING, f.controller().tick(id, f.battery))
            assertEquals(WaitingEvent.COMMITTING, f.waiting!!.texts.last().event)
            assertFalse(f.incoming in f.credentials!!.people)
            assertEquals(WaitingOutcome.ALREADY_IN_PROGRESS, f.controller.cancel(id))
        }
    }

    @Test fun `clock repair credits no old epoch time and failed checkpoint holds pending`() {
        val f = F()
        f.at(30 * 3_600_000L)
        val id = f.start()
        f.at(0, "repair", elapsed = 60 * 3_600_000L)
        assertEquals(WaitingOutcome.PENDING, f.controller.tick(id, f.battery))
        f.at(day - 1, "repair", elapsed = 90 * 3_600_000L)
        assertEquals(WaitingOutcome.PENDING, f.controller().tick(id, f.battery))
        f.now = null
        assertEquals(WaitingOutcome.NOT_READY, f.controller.tick(id, f.battery))
        f.at(day, "repair")
        assertEquals(WaitingOutcome.COMMITTING, f.controller.tick(id, f.battery))
    }

    @Test fun `people changes need no discretionary unhide and are not held by low battery`() {
        val f = F()
        val id = f.start()
        f.at(day)
        assertEquals(WaitingOutcome.COMMITTING, f.controller.tick(id, BatteryState(1, false)))
        val g = F()
        val other = g.start()
        assertIs<WaitingSkipResult.Committing>(g.controller.skip(other, g.b, g.secondPrepared.recoveryCode.toCharArray(),
            g.newPin, true, BatteryState(1, false)))
    }

    @Test fun `changed starter or removed slot cancels immediately while unrelated recovery survives`() {
        for (person in listOf("starter", "removed")) {
            val f = F()
            val id = f.start(WaitingChangeKind.REMOVE_PERSON)
            f.replace(if (person == "starter") f.a else f.b)
            assertEquals(WaitingOutcome.CANCELLED, f.controller.tick(id, f.battery))
            assertEquals(WaitingEvent.DEPENDENCY_CHANGED, f.waiting!!.texts.last().event)
        }
        val f = F()
        val id = f.start()
        assertIs<RecoveryResult.Replaced>(f.vault.recover(f.b, f.secondPrepared.recoveryCode.toCharArray(), f.newPin))
        assertEquals(WaitingOutcome.PENDING, f.controller.tick(id, f.battery))
        assertIs<RecoveryResult.Replaced>(f.vault.recover(f.a, f.firstPrepared.recoveryCode.toCharArray(), f.newPin))
        assertEquals(WaitingOutcome.CANCELLED, f.controller.tick(id, f.battery))
        assertNull(f.waiting!!.operation!!.skipper)
    }

    @Test fun `structure and mode changes cancel before deadline`() {
        for (modeChange in listOf(false, true)) {
            val f = F(false)
            val id = f.start(WaitingChangeKind.ADD_PERSON)
            val c = f.credentials!!
            f.credentials = CredentialState(if (modeChange) Mode.SELF_MANAGED else c.mode, c.people,
                c.revision + 1, if (modeChange) c.structureGeneration else c.structureGeneration + 1, recoveryProfile = c.recoveryProfile)
            assertEquals(WaitingOutcome.CANCELLED, f.controller.tick(id, f.battery))
        }
    }

    @Test fun `people and turn off share one slot through pending committing and rollback`() {
        val f = F()
        val id = f.start()
        for (phase in listOf(WaitingPhase.PENDING, WaitingPhase.COMMITTING, WaitingPhase.ROLLING_BACK)) {
            f.waiting = f.waiting!!.replacing(f.waiting!!.revision + 1, f.waiting!!.operation!!.withPhase(phase))
            val people = f.controller.prepareRemove(f.a, f.b)!!
            assertEquals(WaitingOutcome.BUSY, f.controller.startPeopleChange(people, f.auth(people), true))
            val off = f.controller.prepareTurnOff(f.a)!!
            assertEquals(WaitingOutcome.BUSY, f.controller.startTurnOff(off, f.auth(off), true, true))
        }
        assertEquals(id, f.waiting!!.operation!!.identity.requestId)
    }

    @Test fun `failed cancellation holds against maturity and skip and survives restart`() {
        val f = F()
        val id = f.start()
        f.failWaiting = true
        assertEquals(WaitingOutcome.NOT_READY, f.controller.cancel(id))
        f.at(day)
        outcome(WaitingOutcome.NOT_READY, f.skip(id))
        assertEquals(WaitingOutcome.NOT_READY, f.controller().tick(id, f.battery))
        f.failWaiting = false
        assertEquals(WaitingOutcome.CANCELLED, f.controller().tick(id, f.battery))
        assertEquals(0, f.jointWrites)
    }

    @Test fun `unknown cancel intent and missing records never mature or skip`() {
        val f = F()
        val id = f.start()
        f.intents[id] = CancelIntent.UNKNOWN
        f.at(day)
        assertEquals(WaitingOutcome.NOT_READY, f.controller.tick(id, f.battery))
        outcome(WaitingOutcome.NOT_READY, f.skip(id))
        f.waiting = null
        assertEquals(WaitingOutcome.NOT_READY, f.controller.tick(id, f.battery))
        outcome(WaitingOutcome.NOT_READY, f.skip(id))
    }

    @Test fun `failed start consumes preparation and never publishes pending or receipts`() {
        val f = F()
        val request = f.controller.prepareHandOver(f.a, f.enrolled)!!
        f.failWaiting = true
        assertEquals(WaitingOutcome.NOT_READY, f.controller.startPeopleChange(request, f.auth(request), true))
        assertNull(f.waiting!!.operation)
        assertTrue(f.waiting!!.texts.isEmpty())
        f.failWaiting = false
        assertEquals(WaitingOutcome.REFUSED, f.controller.startPeopleChange(request, f.auth(request), true))
    }

    @Test fun `crash before and after start cancel and maturity writes preserves state with its receipt`() {
        for (stage in listOf("start", "cancel", "mature")) for (after in listOf(false, true)) {
            val f = F()
            val request = f.controller.prepareHandOver(f.a, f.enrolled)!!
            val grant = f.auth(request)
            if (stage != "start") assertEquals(WaitingOutcome.PENDING, f.controller.startPeopleChange(request, grant, true))
            if (stage != "start") f.at(day)
            f.crashWaiting = after
            assertFailsWith<IllegalStateException> {
                when (stage) {
                    "start" -> f.controller.startPeopleChange(request, grant, true)
                    "cancel" -> f.controller.cancel(request.requestId)
                    else -> f.controller.tick(request.requestId, f.battery)
                }
            }
            f.crashWaiting = null
            val expected = when (stage) { "start" -> WaitingPhase.PENDING; "cancel" -> WaitingPhase.CANCELLED; else -> WaitingPhase.COMMITTING }
            if (after) assertEquals(expected, f.waiting!!.operation!!.phase)
            else assertEquals(if (stage == "start") null else WaitingPhase.PENDING, f.waiting!!.operation?.phase)
            assertEquals(if (stage == "start") { if (after) 1 else 0 } else { if (after) 2 else 1 }, f.waiting!!.texts.size)
            assertEquals(setOf(f.a, f.b), f.credentials!!.people.keys)
        }
    }

    @Test fun `skip by starter other holder or removal target replaces only skipper atomically`() {
        for (kind in listOf(WaitingChangeKind.HAND_OVER, WaitingChangeKind.REMOVE_PERSON)) for (starter in listOf(false, true)) {
            val f = F()
            val id = f.start(kind)
            val person = if (starter) f.a else f.b
            val code = if (starter) f.firstPrepared.recoveryCode else f.secondPrepared.recoveryCode
            assertIs<WaitingSkipResult.Committing>(f.skip(id, person, code))
            val op = f.waiting!!.operation!!
            assertEquals(kind, op.kind)
            assertEquals(person, op.skipper)
            assertEquals(1L, op.dependencyGenerations[person])
            if (!starter) assertEquals(if (kind == WaitingChangeKind.HAND_OVER) 0L else null, op.dependencyGenerations[f.a])
            if (kind == WaitingChangeKind.HAND_OVER) assertSame(f.enrolled, op.peoplePatch!!.incoming)
            else assertNull(op.peoplePatch!!.incoming)
            assertEquals(1, f.jointWrites)
            assertEquals(1L, f.credentials!!.people[person]!!.generation)
            assertFalse(f.incoming in f.credentials!!.people)
        }
    }

    @Test fun `stale removal dependency cancels before skip can rotate credentials`() {
        val f = F()
        val id = f.start(WaitingChangeKind.REMOVE_PERSON)
        f.replace(f.b)
        val before = f.credentials
        outcome(WaitingOutcome.CANCELLED, f.skip(id, f.a, f.firstPrepared.recoveryCode))
        assertSame(before, f.credentials)
        assertEquals(0, f.jointWrites)
    }

    @Test fun `failed and interrupted skip transaction never separates recovery from commit direction`() {
        for (after in listOf(false, true)) {
            val f = F()
            val id = f.start()
            val old = f.credentials
            f.failJoint = true
            outcome(WaitingOutcome.NOT_READY, f.skip(id))
            assertSame(old, f.credentials)
            f.failJoint = false
            f.crashJoint = after
            assertFailsWith<IllegalStateException> { f.skip(id) }
            f.crashJoint = null
            assertEquals(if (after) WaitingPhase.COMMITTING else WaitingPhase.PENDING, f.waiting!!.operation!!.phase)
            assertEquals(if (after) 1L else 0L, f.credentials!!.people[f.b]!!.generation)
            assertEquals(if (after) WaitingEvent.SKIPPED else WaitingEvent.STARTED, f.waiting!!.texts.last().event)
        }
    }

    @Test fun `outgoing recovery starts and skips exact handover in one transaction`() {
        val f = F()
        val request = f.controller.prepareHandOver(f.a, f.enrolled)!!
        val result = assertIs<WaitingSkipResult.Committing>(f.recoveryStart(request))
        assertNotEquals(f.firstPrepared.recoveryCode, result.recoveryCode)
        val op = f.waiting!!.operation!!
        assertEquals(WaitingChangeKind.HAND_OVER, op.kind)
        assertEquals(WaitingPhase.COMMITTING, op.phase)
        assertEquals(f.a, op.starter)
        assertEquals(f.a, op.skipper)
        assertEquals(1L, op.skipperGeneration)
        assertEquals(listOf(WaitingEvent.STARTED, WaitingEvent.SKIPPED), f.waiting!!.texts.map { it.event })
        assertEquals(2, f.waiting!!.texts.map { it.key }.toSet().size)
        assertEquals(1, f.jointWrites)
        assertFalse(f.incoming in f.credentials!!.people)
        assertIs<PinResult.Authorized>(f.vault.authenticate(f.a, f.newPin, PrivilegedAction("test", "new")))
        outcome(WaitingOutcome.REFUSED, f.recoveryStart(request, result.recoveryCode))
    }

    @Test fun `recovery handover needs both confirmations and cannot use other holder or incoming code`() {
        val f = F()
        val request = f.controller.prepareHandOver(f.a, f.enrolled)!!
        val checks = f.hashChecks
        outcome(WaitingOutcome.REFUSED, f.recoveryStart(request, confirmed = false))
        outcome(WaitingOutcome.REFUSED, f.recoveryStart(request, skipConfirmed = false))
        assertEquals(checks, f.hashChecks)
        assertIs<WaitingSkipResult.Wrong>(f.recoveryStart(request, f.secondPrepared.recoveryCode))
        assertIs<WaitingSkipResult.Wrong>(f.recoveryStart(request, f.incomingPrepared.recoveryCode))
        assertEquals(2, f.credentials!!.people[f.a]!!.recoveryCounter.failures)
        assertEquals(0, f.credentials!!.people[f.b]!!.recoveryCounter.failures)
        assertNull(f.waiting!!.operation)
        val removal = f.controller.prepareRemove(f.a, f.b)!!
        outcome(WaitingOutcome.REFUSED, f.recoveryStart(removal))
    }

    @Test fun `recovery handover observes shared recovery lockout independent of PIN lockout`() {
        val f = F()
        val request = f.controller.prepareHandOver(f.a, f.enrolled)!!
        repeat(5) { assertIs<WaitingSkipResult.Wrong>(f.recoveryStart(request, f.secondPrepared.recoveryCode)) }
        assertIs<WaitingSkipResult.Delayed>(f.recoveryStart(request))
        assertIs<RecoveryResult.Delayed>(f.vault.recover(f.a, f.firstPrepared.recoveryCode.toCharArray(), f.newPin))
        f.at(30_000)
        repeat(5) { f.vault.authenticate(f.a, CharArray(6) { '9' }, request.action) }
        assertIs<WaitingSkipResult.Committing>(f.recoveryStart(request))
    }

    @Test fun `recovery handover respects cancellation busy slot stale dependency and controller ownership`() {
        val f = F()
        val request = f.controller.prepareHandOver(f.a, f.enrolled)!!
        outcome(WaitingOutcome.REFUSED, f.controller().startHandOverWithRecovery(request, f.firstPrepared.recoveryCode.toCharArray(), f.newPin, true, true))
        f.intents[request.requestId] = CancelIntent.REQUESTED
        outcome(WaitingOutcome.NOT_READY, f.recoveryStart(request))
        val other = f.controller.prepareHandOver(f.a, f.enrolled)!!
        f.replace(f.a)
        outcome(WaitingOutcome.REFUSED, f.recoveryStart(other))
        val g = F()
        g.start()
        outcome(WaitingOutcome.BUSY, g.recoveryStart(g.controller.prepareHandOver(g.a, g.enrolled)!!))
    }

    @Test fun `recovery handover crash on each side keeps old or new outgoing credentials with whole journal`() {
        for (after in listOf(false, true)) {
            val f = F()
            val request = f.controller.prepareHandOver(f.a, f.enrolled)!!
            val before = f.credentials
            f.crashJoint = after
            assertFailsWith<IllegalStateException> { f.recoveryStart(request) }
            f.crashJoint = null
            if (after) {
                assertEquals(WaitingPhase.COMMITTING, f.waiting!!.operation!!.phase)
                assertEquals(1L, f.credentials!!.people[f.a]!!.generation)
                assertEquals(2, f.waiting!!.texts.size)
                assertIs<PinResult.Authorized>(f.vault.authenticate(f.a, f.newPin, PrivilegedAction("test", "new")))
            } else {
                assertSame(before, f.credentials)
                assertNull(f.waiting!!.operation)
                assertTrue(f.waiting!!.texts.isEmpty())
                assertIs<PinResult.Authorized>(f.vault.authenticate(f.a, f.pin, PrivilegedAction("test", "old")))
            }
        }
    }

    @Test fun `cancel at recovery transaction boundary wins without a new PIN or operation`() {
        val f = F()
        val request = f.controller.prepareHandOver(f.a, f.enrolled)!!
        val before = f.credentials
        f.beforeJoint = { f.intents[request.requestId] = CancelIntent.REQUESTED }
        outcome(WaitingOutcome.NOT_READY, f.recoveryStart(request))
        assertSame(before, f.credentials)
        assertNull(f.waiting!!.operation)
    }

    @Test fun `queued receipts keep removed contact while later receipts include only current holders`() {
        val f = F()
        val id = f.start()
        val started = f.waiting!!.texts.single()
        val originalContact = started.recipientContacts[f.a]
        val c = f.credentials!!
        val notice = LockoutNotice(f.a, CredentialChannel.PIN, c.revision)
        f.credentials = CredentialState(c.mode, mapOf(f.b to c.people.getValue(f.b)), c.revision + 1, c.structureGeneration + 1,
            setOf(notice), RecoveryProfile(mapOf(f.b to "synthetic-second-account"), "synthetic-backup", c.recoveryProfile!!.friction))
        assertEquals(WaitingOutcome.CANCELLED, f.controller.tick(id, f.battery))
        assertSame(originalContact, started.recipientContacts[f.a])
        assertEquals(setOf(f.a, f.b), started.recipients)
        assertEquals(setOf(f.b), f.waiting!!.texts.last().recipients)
        assertEquals(setOf(f.b), f.waiting!!.texts.last().recipientContacts.keys)
        assertTrue(notice in f.credentials!!.pendingNotices) // audit is not a current-recipient lookup
        assertTrue(f.waiting!!.texts.any { it.key == started.key })
    }

    @Test fun `legacy absent contacts stay explicit unknown and cannot be rebound on retry`() {
        val f = F(contacts = false)
        val id = f.start()
        val started = f.waiting!!.texts.single()
        assertEquals(mapOf(f.a to null, f.b to null), started.recipientContacts)
        val c = f.credentials!!
        f.credentials = c.replacing(f.a, c.people.getValue(f.a).copy(contact = PersonContact("synthetic-later", "synthetic-later-number")))
        f.controller.cancel(id)
        assertNull(started.recipientContacts[f.a])
        assertNotNull(f.waiting!!.texts.last().recipientContacts[f.a])
        assertFailsWith<UnsupportedOperationException> { (started.recipientContacts as MutableMap).clear() }
    }

    @Test fun `cancel with unreadable credentials uses frozen start contacts even after start receipt acknowledgement`() {
        val f = F()
        val id = f.start()
        val snapshot = f.waiting!!.texts.single().recipientContacts
        assertTrue(f.controller.acknowledge(f.waiting!!.texts.single().key))
        f.credentials = null
        assertEquals(WaitingOutcome.CANCELLED, f.controller.cancel(id))
        assertEquals(snapshot, f.waiting!!.texts.single().recipientContacts)
    }

    @Test fun `cancel uses entire start snapshot when credential read fails then becomes available`() {
        for (legacy in listOf(false, true)) {
            val f = F(contacts = !legacy)
            val id = f.start()
            val started = f.waiting!!.texts.single()
            val c = f.credentials!!
            val latest = c.replacing(f.a, c.people.getValue(f.a).copy(generation = 1,
                contact = PersonContact("synthetic-later", "synthetic-later-number")))
            f.credentials = latest
            f.credentialReads = mutableListOf(null, latest)
            assertEquals(WaitingOutcome.CANCELLED, f.controller.cancel(id))
            val cancelled = f.waiting!!.texts.last()
            assertEquals(started.recipientGenerations, cancelled.recipientGenerations)
            assertEquals(started.recipientContacts, cancelled.recipientContacts)
        }
    }

    @Test fun `cancel keeps entire current snapshot when a later credential read fails`() {
        for (legacy in listOf(false, true)) {
            val f = F(contacts = !legacy)
            val id = f.start()
            val c = f.credentials!!
            val latest = c.replacing(f.a, c.people.getValue(f.a).copy(generation = 1,
                contact = PersonContact("synthetic-later", "synthetic-later-number")))
            f.credentials = latest
            f.credentialReads = mutableListOf(latest, null)
            assertEquals(WaitingOutcome.CANCELLED, f.controller.cancel(id))
            val cancelled = f.waiting!!.texts.last()
            assertEquals(latest.people.mapValues { it.value.generation }, cancelled.recipientGenerations)
            assertEquals(latest.people.mapValues { it.value.contact }, cancelled.recipientContacts)
        }
    }

    @Test fun `turn off runner never restores or completes people operation even if device owner absent`() {
        val f = F()
        val id = f.start()
        f.at(day)
        assertEquals(WaitingOutcome.COMMITTING, f.controller.tick(id, f.battery))
        for (owner in listOf(false, true)) {
            var calls = 0
            val effects = object : TurnOffEffects {
                override fun revokeTemporaryAccess(): Boolean { calls++; return true }
                override fun isDeviceOwner(): Boolean { calls++; return owner }
                override fun restoreRecorded(step: TurnOffStep): Boolean { calls++; return true }
                override fun readBackRecorded(step: TurnOffStep): Boolean { calls++; return true }
                override fun anotherHomeResolves(): Boolean { calls++; return true }
                override fun systemDialerResolves(): Boolean { calls++; return true }
                override fun removeDeviceOwner(): Boolean { calls++; return true }
            }
            assertEquals(TurnOffOutcome.IGNORED, TurnOffController(f.store, effects) { f.credentials }.tick(id).outcome)
            assertEquals(0, calls)
            assertEquals(WaitingPhase.COMMITTING, f.waiting!!.operation!!.phase)
        }
    }

    @Test fun `people journal cannot use turn off restore progress or a mismatched patch starter`() {
        val f = F()
        f.start()
        val op = f.waiting!!.operation!!
        assertFailsWith<IllegalArgumentException> { op.withRestoredSteps(1) }
        assertFailsWith<IllegalArgumentException> {
            WaitingOperation(op.identity, f.b, 0, op.structureGeneration, Mode.TRUSTED, op.startedAt,
                WaitingPhase.PENDING, op.recipientGenerations, peoplePatch = op.peoplePatch)
        }
    }

    @Test fun `people journal preserves full lockdown and emergency floor in every phase`() {
        val f = F()
        f.start()
        val dialer = PackageName("test.systemdialer")
        val hidden = PackageName("test.unapproved")
        val input = PolicyInput("model", "build", PackageName("test.home"), PackageName("test.sms"),
            listOf(InstalledPackage(dialer, true, 1), InstalledPackage(hidden, false, 1)),
            listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)))
        for (phase in WaitingPhase.entries) {
            val op = f.waiting!!.operation!!.withPhase(phase)
            for (boot in listOf(false, true)) {
                val plan = PolicyEngine.decide(input.copy(waitingOperation = op, lockedBoot = boot))
                assertEquals(Restriction.entries.toSet(), plan.restrictions)
                assertTrue(dialer in plan.neverHide)
                assertTrue(dialer in plan.lockTaskPackages)
                assertEquals(PolicyEngine.decide(input.copy(lockedBoot = boot)), plan)
            }
        }
    }

    @Test fun `unwired recovery handover and skip remain unavailable without consuming code`() {
        val f = F()
        val controller = WaitingChangeController(f.store, { f.credentials }, RunningClock { f.now }, f.vault,
            WaitingIdentitySource { WaitingIdentity("unwired", "synthetic-cancel") }, f.cancelStore)
        val request = controller.prepareHandOver(f.a, f.enrolled)!!
        val before = f.credentials
        outcome(WaitingOutcome.REFUSED, controller.startHandOverWithRecovery(request,
            f.firstPrepared.recoveryCode.toCharArray(), f.newPin, true, true))
        assertSame(before, f.credentials)
        assertEquals(WaitingOutcome.PENDING, controller.startPeopleChange(request, f.auth(request), true))
        outcome(WaitingOutcome.REFUSED, controller.skip(request.requestId, f.a,
            f.firstPrepared.recoveryCode.toCharArray(), f.newPin, true, f.battery))
        assertEquals(WaitingPhase.PENDING, f.waiting!!.operation!!.phase)
    }

    @Test fun `old timer and cancellation never target the next people wait`() {
        val f = F()
        val old = f.start()
        assertEquals(WaitingOutcome.CANCELLED, f.controller.cancel(old))
        val current = f.start()
        f.at(day)
        assertEquals(WaitingOutcome.IGNORED, f.controller.tick(old, f.battery))
        assertEquals(WaitingOutcome.IGNORED, f.controller.cancel(old))
        assertEquals(WaitingOutcome.COMMITTING, f.controller.tick(current, f.battery))
        assertEquals(current, f.waiting!!.operation!!.identity.requestId)
    }

}
