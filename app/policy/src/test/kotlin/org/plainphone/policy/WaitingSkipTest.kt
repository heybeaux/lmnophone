// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class WaitingSkipTest {
    private val first = TrustedPersonId("first")
    private val second = TrustedPersonId("second")
    private val pin get() = CharArray(6) { '1' }
    private val newPin get() = CharArray(6) { '2' }
    private val wrong get() = CharArray(32) { '0' }
    private var time: ClockReading? = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
    private val clock = RunningClock { time }
    private var credentials: CredentialState? = null
    private var waiting: WaitingState? = WaitingState(storageGeneration = "live-generation")
    private var failCredentials = false
    private var failWaiting = false
    private var failCommit = false
    private var crashCommit: Boolean? = null
    private var commits = 0
    private var checks = 0
    private var beforeCommit: (() -> Unit)? = null
    private val credentialStore = object : CredentialStore {
        override fun read() = credentials
        override fun compareAndSet(expected: CredentialState, replacement: CredentialState): Boolean {
            if (failCredentials || credentials !== expected) return false
            credentials = replacement; return true
        }
    }
    private val waitingStore = object : WaitingChangeStore {
        override fun read() = waiting
        override fun compareAndSet(expected: WaitingState, replacement: WaitingState): Boolean {
            if (failWaiting || waiting !== expected) return false
            waiting = replacement; return true
        }
    }
    private val intents = object : CancelIntentStore {
        var value = CancelIntent.CLEAR
        var fail = false
        override fun initialize(requestId: String) = true
        override fun read(requestId: String) = value
        override fun request(requestId: String): Boolean {
            if (fail) return false
            value = CancelIntent.REQUESTED; return true
        }
    }
    private var entropy = 0
    private val vault = PinVault(credentialStore, KeyDerivation { chars, salt, _ ->
        checks++
        java.security.MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
    }, SecretEntropy { count -> ByteArray(count) { (++entropy).toByte() } }, clock)
    private val committer = WaitingSkipCommitter { oldCredentials, nextCredentials, oldWaiting, nextWaiting ->
        beforeCommit?.invoke()
        if (crashCommit == false) error("crash before transaction")
        if (failCommit || credentials !== oldCredentials || waiting !== oldWaiting || intents.value != CancelIntent.CLEAR) false
        else {
            credentials = nextCredentials; waiting = nextWaiting; commits++
            if (crashCommit == true) error("crash after transaction")
            true
        }
    }
    private fun controller() = WaitingChangeController(waitingStore, { credentials }, clock, vault,
        WaitingIdentitySource { WaitingIdentity("request", "synthetic-cancel") }, intents, WaitingSkipSupport(vault, committer))
    private val controller = controller()
    private var codeFirst = ""
    private var codeSecond = ""
    private fun start(mode: Mode = Mode.TRUSTED) {
        val a = vault.prepareCredential(pin); val b = vault.prepareCredential(pin)
        codeFirst = a.recoveryCode; codeSecond = b.recoveryCode
        val people = if (mode == Mode.TRUSTED) mapOf(first to a.credential, second to b.credential) else mapOf(first to a.credential)
        credentials = CredentialState(mode, people, recoveryProfile = RecoveryProfile(people.keys.associateWith { "synthetic-${it.value}" }, null, FrictionSettings(600_000, 3)))
        val request = controller.prepareTurnOff(first)!!
        val auth = assertIs<PinResult.Authorized>(vault.authenticate(first, pin, request.action)).authority
        assertEquals(WaitingOutcome.PENDING, controller.startTurnOff(request, auth, true, true))
    }
    private fun skip(person: TrustedPersonId = second, code: CharArray = codeSecond.toCharArray(), requestId: String = "request", confirmed: Boolean = true,
        battery: BatteryState = BatteryState(50, false)) = controller.skip(requestId, person, code, newPin, confirmed, battery)
    private fun assertNotSkipped(outcome: WaitingOutcome, result: WaitingSkipResult) = assertEquals(outcome, assertIs<WaitingSkipResult.NotSkipped>(result).outcome)

    @Test fun `other persons recovery code replaces only that slot and commits with skipper receipt atomically`() {
        start()
        val starter = credentials!!.people.getValue(first)
        val profile = credentials!!.recoveryProfile
        val oldRevision = waiting!!.revision
        val result = assertIs<WaitingSkipResult.Committing>(skip())
        assertNotEquals(codeSecond, result.recoveryCode)
        assertSame(starter, credentials!!.people.getValue(first))
        assertSame(profile, credentials!!.recoveryProfile)
        assertEquals(1, commits)
        assertEquals(oldRevision + 1, waiting!!.revision)
        assertEquals("live-generation", waiting!!.storageGeneration)
        val op = waiting!!.operation!!
        assertEquals(WaitingPhase.COMMITTING, op.phase)
        assertEquals(first, op.starter)
        assertEquals(second, op.skipper)
        assertEquals(1L, op.skipperGeneration)
        assertEquals(second, op.authorityPerson)
        assertEquals(WaitingEvent.SKIPPED, waiting!!.texts.last().event)
        assertEquals(mapOf(first to 0L, second to 1L), waiting!!.texts.last().recipientGenerations)
        assertFalse(result.toString().contains(result.recoveryCode))
        assertIs<PinResult.Authorized>(vault.authenticate(second, newPin, PrivilegedAction("test", "new-pin")))
        assertIs<RecoveryResult.Wrong>(vault.recover(second, codeSecond.toCharArray(), pin))
        assertNotSkipped(WaitingOutcome.ALREADY_IN_PROGRESS, skip())
        assertEquals(1, commits)
    }

    @Test fun `starter can skip but ordinary PIN recovery never skips`() {
        start()
        assertIs<WaitingSkipResult.Committing>(skip(first, codeFirst.toCharArray()))
        assertEquals(first, waiting!!.operation!!.skipper)
        assertEquals(1L, waiting!!.operation!!.skipperGeneration)
        waiting = waiting!!.replacing(waiting!!.revision + 1, waiting!!.operation!!.let {
            WaitingOperation(it.identity, first, 1, 0, Mode.TRUSTED, it.startedAt, WaitingPhase.PENDING, mapOf(first to 1L, second to 0L))
        })
        assertIs<RecoveryResult.Replaced>(vault.recover(second, codeSecond.toCharArray(), newPin))
        assertEquals(WaitingPhase.PENDING, waiting!!.operation!!.phase)
        assertNull(waiting!!.operation!!.skipper)
    }

    @Test fun `self managed mode forbids skip without checking code or replacing PIN`() {
        start(Mode.SELF_MANAGED)
        val before = credentials; val checked = checks
        assertNotSkipped(WaitingOutcome.REFUSED, skip(first, codeFirst.toCharArray()))
        assertEquals(checked, checks)
        assertSame(before, credentials)
        assertEquals(WaitingPhase.PENDING, waiting!!.operation!!.phase)
        assertIs<RecoveryResult.Replaced>(vault.recover(first, codeFirst.toCharArray(), newPin))
        assertEquals(WaitingPhase.PENDING, waiting!!.operation!!.phase)
    }

    @Test fun `exact request confirmation person direction and battery are checked before recovery`() {
        start(); val before = credentials; val checked = checks
        assertNotSkipped(WaitingOutcome.REFUSED, skip(confirmed = false))
        assertNotSkipped(WaitingOutcome.IGNORED, skip(requestId = "stale"))
        assertNotSkipped(WaitingOutcome.REFUSED, skip(TrustedPersonId("stranger")))
        assertNotSkipped(WaitingOutcome.LOW_BATTERY, skip(battery = BatteryState(4, false)))
        for (phase in listOf(WaitingPhase.ROLLING_BACK, WaitingPhase.COMMITTING, WaitingPhase.CANCELLED, WaitingPhase.COMPLETED)) {
            waiting = waiting!!.replacing(waiting!!.revision + 1, waiting!!.operation!!.withPhase(phase))
            assertNotSkipped(if (phase in setOf(WaitingPhase.ROLLING_BACK, WaitingPhase.COMMITTING)) WaitingOutcome.ALREADY_IN_PROGRESS else WaitingOutcome.IGNORED, skip())
        }
        assertSame(before, credentials); assertEquals(checked, checks); assertEquals(0, commits)
    }

    @Test fun `skip and ordinary recovery share counters and exact schedule without charging PIN`() {
        start()
        for (failure in 1..12) {
            val result = assertIs<WaitingSkipResult.Wrong>(skip(code = wrong))
            val expected = when (failure) { in 1..4 -> 0L; 5 -> 30_000L; 6 -> 60_000L; 7 -> 120_000L; 8 -> 240_000L; 9 -> 480_000L; 10 -> 960_000L; 11 -> 1_920_000L; else -> 3_600_000L }
            assertEquals(expected, result.remainingMillis)
            assertEquals(failure == 10, result.notifyLockout)
            if (expected > 0) {
                val checked = checks
                assertIs<WaitingSkipResult.Delayed>(skip())
                assertIs<RecoveryResult.Delayed>(vault.recover(second, codeSecond.toCharArray(), newPin))
                assertEquals(checked, checks)
            }
            time = time!!.let { ClockReading(it.proven.copy(totalMillis = it.proven.totalMillis + expected), it.boot.copy(elapsedMillis = it.boot.elapsedMillis + expected)) }
        }
        assertEquals(0, credentials!!.people.getValue(second).pinCounter.failures)
        assertEquals(1, credentials!!.pendingNotices.size)
        assertIs<WaitingSkipResult.Committing>(skip())
        assertEquals(0, credentials!!.people.getValue(second).recoveryCounter.failures)
    }

    @Test fun `PIN lockout cannot block a valid recovery skip and skip invalidates old grants`() {
        start()
        val action = PrivilegedAction("test", "previous-grant")
        val grant = assertIs<PinResult.Authorized>(vault.authenticate(second, pin, action)).authority
        repeat(5) { vault.authenticate(second, CharArray(6) { '9' }, action) }
        assertIs<WaitingSkipResult.Committing>(skip())
        assertFalse(vault.consumeFor(grant, action, second))
        assertEquals(0, credentials!!.people.getValue(second).pinCounter.failures)
    }

    @Test fun `failed joint write leaves old PIN code operation and receipts together`() {
        start(); val before = credentials; val beforeWaiting = waiting
        failCommit = true
        assertNotSkipped(WaitingOutcome.NOT_READY, skip())
        assertSame(before, credentials); assertSame(beforeWaiting, waiting)
        failCommit = false
        assertIs<WaitingSkipResult.Committing>(skip())
        assertEquals(1, commits)
    }

    @Test fun `crash on either side of joint write produces none or all of skip`() {
        for (after in listOf(false, true)) {
            waiting = WaitingState(); start(); commits = 0
            val before = credentials; val beforeWaiting = waiting
            crashCommit = after
            assertFailsWith<IllegalStateException> { skip() }
            crashCommit = null
            if (after) {
                assertEquals(WaitingPhase.COMMITTING, waiting!!.operation!!.phase)
                assertEquals(second, waiting!!.operation!!.skipper)
                assertEquals(1L, credentials!!.people.getValue(second).generation)
                assertEquals(WaitingEvent.SKIPPED, waiting!!.texts.last().event)
                assertNotSkipped(WaitingOutcome.ALREADY_IN_PROGRESS, controller().skip("request", second, codeSecond.toCharArray(), newPin, true, BatteryState(50, false)))
            } else {
                assertSame(before, credentials); assertSame(beforeWaiting, waiting)
                assertIs<WaitingSkipResult.Committing>(skip())
            }
            assertEquals(1, commits)
        }
    }

    @Test fun `persisted and in memory cancellations take precedence over recovery code`() {
        start(); val before = credentials
        failWaiting = true; intents.fail = true
        assertEquals(WaitingOutcome.NOT_READY, controller.cancel("request"))
        assertEquals(CancelIntent.CLEAR, intents.value)
        assertNotSkipped(WaitingOutcome.NOT_READY, skip())
        assertSame(before, credentials); assertEquals(0, commits)
        failWaiting = false
        assertNotSkipped(WaitingOutcome.CANCELLED, skip())
        assertSame(before, credentials)
        waiting = WaitingState(); start(); intents.fail = false; intents.value = CancelIntent.REQUESTED
        assertNotSkipped(WaitingOutcome.CANCELLED, controller().skip("request", second, codeSecond.toCharArray(), newPin, true, BatteryState(50, false)))
        assertEquals(0, commits)
    }

    @Test fun `unknown intent unreadable records and failed clock never skip`() {
        start(); val before = credentials
        intents.value = CancelIntent.UNKNOWN
        assertNotSkipped(WaitingOutcome.NOT_READY, skip())
        intents.value = CancelIntent.CLEAR
        val saved = waiting; waiting = null
        assertNotSkipped(WaitingOutcome.NOT_READY, skip())
        waiting = saved; time = null
        assertNotSkipped(WaitingOutcome.NOT_READY, skip())
        assertSame(before, credentials); assertEquals(0, commits)
    }

    @Test fun `changed starter mode or structure cancels before code replacement`() {
        for (change in listOf("starter", "structure", "mode")) {
            waiting = WaitingState(); start()
            val c = credentials!!
            credentials = when (change) {
                "starter" -> c.replacing(first, c.people.getValue(first).copy(generation = 1))
                "structure" -> CredentialState(c.mode, c.people, c.revision + 1, c.structureGeneration + 1, recoveryProfile = c.recoveryProfile)
                else -> CredentialState(Mode.SELF_MANAGED, mapOf(first to c.people.getValue(first)), c.revision + 1)
            }
            val before = credentials
            assertNotSkipped(WaitingOutcome.CANCELLED, skip())
            assertSame(before, credentials)
            assertEquals(WaitingEvent.DEPENDENCY_CHANGED, waiting!!.texts.last().event)
        }
    }

    @Test fun `credential or operation replacement at atomic boundary rejects stale skip`() {
        start()
        beforeCommit = { credentials = credentials!!.replacing(first, credentials!!.people.getValue(first).copy(generation = 1)) }
        assertNotSkipped(WaitingOutcome.NOT_READY, skip())
        assertEquals(0L, credentials!!.people.getValue(second).generation)
        assertEquals(WaitingPhase.PENDING, waiting!!.operation!!.phase)
        assertEquals(0, commits)
    }

    @Test fun `failed counter save reports unavailable instead of wrong or a new attempt budget`() {
        start(); failCredentials = true
        assertNotSkipped(WaitingOutcome.NOT_READY, skip(code = wrong))
        assertEquals(0, credentials!!.people.getValue(second).recoveryCounter.failures)
        assertEquals(WaitingPhase.PENDING, waiting!!.operation!!.phase)
    }

    @Test fun `grouped uppercase recovery works after reboot without completing wait`() {
        start()
        time = ClockReading(RunningTime("new-epoch", 0), BootTime("new-boot", 0))
        val code = codeSecond.uppercase().chunked(4).joinToString(" - ").toCharArray()
        assertIs<WaitingSkipResult.Committing>(controller().skip("request", second, code, newPin, true, BatteryState(4, true)))
        assertEquals(WaitingPhase.COMMITTING, waiting!!.operation!!.phase)
        assertEquals(0L, time!!.proven.totalMillis)
    }

    @Test fun `self recovery cancels exact turn off with one receipt and no skip across restart and clock repair`() {
        for (restart in listOf(false, true)) for (repair in listOf(false, true)) {
            val f = WaitingSkipTest()
            f.start(Mode.SELF_MANAGED)
            val original = f.waiting!!.texts.single()
            val checked = f.checks
            f.assertNotSkipped(WaitingOutcome.REFUSED, f.skip(f.first, f.codeFirst.toCharArray()))
            assertEquals(checked, f.checks)
            assertIs<RecoveryResult.Replaced>(f.vault.recover(f.first, f.codeFirst.toCharArray(), f.newPin))
            assertEquals(1L, f.credentials!!.people.getValue(f.first).generation)
            // The host must invoke dependency reconciliation immediately after successful recovery.
            val c = if (restart) f.controller() else f.controller
            if (repair) f.time = ClockReading(RunningTime("repaired", 0), BootTime("reboot", 0))
            assertEquals(WaitingOutcome.CANCELLED, c.tick("request", BatteryState(1, false)))
            assertEquals(WaitingPhase.CANCELLED, f.waiting!!.operation!!.phase)
            assertNull(f.waiting!!.operation!!.skipper)
            assertEquals(0, f.waiting!!.operation!!.restoredSteps)
            assertEquals(listOf(WaitingEvent.STARTED, WaitingEvent.DEPENDENCY_CHANGED), f.waiting!!.texts.map { it.event })
            assertEquals(mapOf(f.first to 0L), original.recipientGenerations)
            assertEquals(mapOf(f.first to 1L), f.waiting!!.texts.last().recipientGenerations)
            assertIs<PinResult.Wrong>(f.vault.authenticate(f.first, f.pin, PrivilegedAction("test", "old-pin")))
            assertIs<PinResult.Authorized>(f.vault.authenticate(f.first, f.newPin, PrivilegedAction("test", "new-pin")))
            assertIs<RecoveryResult.Wrong>(f.vault.recover(f.first, f.codeFirst.toCharArray(), f.pin))
            f.time = ClockReading(RunningTime(if (repair) "repaired" else "epoch", 86_400_000), BootTime("later", 0))
            repeat(3) { assertEquals(WaitingOutcome.IGNORED, f.controller().tick("request", BatteryState(100, true))) }
            assertEquals(2, f.waiting!!.texts.size)
            assertEquals(0, f.commits)
        }
    }

    @Test fun `failed cancellation after self recovery never matures across restart`() {
        start(Mode.SELF_MANAGED)
        assertIs<RecoveryResult.Replaced>(vault.recover(first, codeFirst.toCharArray(), newPin))
        failWaiting = true
        assertEquals(WaitingOutcome.NOT_READY, controller.tick("request", BatteryState(50, false)))
        time = ClockReading(RunningTime("epoch", 86_400_000), BootTime("reboot", 0))
        assertEquals(WaitingOutcome.NOT_READY, controller().tick("request", BatteryState(50, false)))
        assertEquals(WaitingPhase.PENDING, waiting!!.operation!!.phase)
        assertFalse(waiting!!.texts.any { it.event == WaitingEvent.COMMITTING || it.event == WaitingEvent.SKIPPED })
        failWaiting = false
        assertEquals(WaitingOutcome.CANCELLED, controller().tick("request", BatteryState(50, false)))
        assertEquals(1, waiting!!.texts.count { it.event == WaitingEvent.DEPENDENCY_CHANGED })
        assertEquals(0, commits)
    }

    @Test fun `failed self recovery preserves old credentials and wait then successful retry cancels`() {
        start(Mode.SELF_MANAGED)
        val before = credentials
        failCredentials = true
        assertIs<RecoveryResult.Unavailable>(vault.recover(first, codeFirst.toCharArray(), newPin))
        assertSame(before, credentials)
        assertEquals(WaitingOutcome.PENDING, controller().tick("request", BatteryState(50, false)))
        failCredentials = false
        assertIs<RecoveryResult.Replaced>(vault.recover(first, codeFirst.toCharArray(), newPin))
        assertEquals(WaitingOutcome.CANCELLED, controller.tick("request", BatteryState(50, false)))
        assertEquals(1, waiting!!.texts.count { it.event == WaitingEvent.DEPENDENCY_CHANGED })
        assertEquals(0, commits)
    }
}
