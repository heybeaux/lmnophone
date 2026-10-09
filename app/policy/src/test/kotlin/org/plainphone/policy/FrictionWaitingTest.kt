// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class FrictionWaitingTest {
    private val day = 86_400_000L
    private class F {
        val self = TrustedPersonId("self")
        val pin get() = CharArray(6) { '1' }
        var now: ClockReading? = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
        val clock = RunningClock { now }
        var credentials: CredentialState? = null
        var waiting: WaitingState? = WaitingState(storageGeneration = "live")
        var elevation: ElevationRecord? = ElevationRecord()
        val intents = mutableMapOf<String, CancelIntent>()
        val routineReceipts = mutableListOf<FrictionSettingsReceipt>()
        var sequence = 0
        var writes = 0
        var fail: String? = null
        var crash: String? = null
        var hook: (() -> Unit)? = null
        val credentialStore = object : CredentialStore {
            override fun read() = if (fail == "credential-readback" && writes > 0) null else credentials
            override fun compareAndSet(expected: CredentialState, replacement: CredentialState): Boolean {
                if (credentials !== expected) return false
                credentials = replacement; return true
            }
        }
        var loss: LostWaitingRecord? = null
        val waitingStore = object : WaitingReconciliationStore {
            override fun read() = if (fail == "waiting-readback" && writes > 0) null else waiting
            override fun compareAndSet(expected: WaitingState, replacement: WaitingState): Boolean {
                if (crash == "waiting-before") error("simulated crash")
                if (fail == "waiting" || waiting !== expected) return false
                waiting = replacement
                if (crash == "waiting-after") error("simulated crash")
                return true
            }
            override fun lostRecord() = loss
            override fun completeReconciliation(lost: LostWaitingRecord, expectedCredentials: CredentialState, replacement: WaitingState): Boolean {
                if (waiting != null || loss !== lost || credentials !== expectedCredentials) return false
                waiting = replacement; loss = null; return true
            }
        }
        val elevationStore = object : ElevationStore(waitingStore) {
            override fun read() = if (fail == "elevation-readback" && writes > 0) null else elevation
            override fun compareAndSet(expected: ElevationRecord?, replacement: ElevationRecord, expectedCredentials: CredentialState?): Boolean {
                if (elevation !== expected || (expectedCredentials != null && credentials !== expectedCredentials)) return false
                elevation = replacement; return true
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
        }, SecretEntropy { n -> ByteArray(n) { (++sequence).toByte() } }, clock)
        val prepared = vault.prepareCredential(pin)
        val base = FrictionSettings(1_200_000, 1)
        init {
            credentials = CredentialState(Mode.SELF_MANAGED,
                mapOf(self to prepared.credential.copy(contact = PersonContact("synthetic-self", "synthetic-contact"))),
                recoveryProfile = RecoveryProfile(mapOf(self to "synthetic-account"), "synthetic-backup", base))
        }
        val transactions = object : FrictionWaitingStore {
            override val waitingStore get() = this@F.waitingStore
            override val elevationStore get() = this@F.elevationStore
            private fun write(c: CredentialState, nextC: CredentialState, w: WaitingState, nextW: WaitingState,
                e: ElevationRecord, nextE: ElevationRecord, receipt: FrictionSettingsReceipt?): Boolean {
                hook?.invoke()
                if (crash == "joint-before") error("simulated crash")
                if (fail == "joint" || credentials !== c || waiting !== w || elevation !== e) return false
                credentials = nextC; waiting = nextW; elevation = nextE
                receipt?.let(routineReceipts::add); writes++
                if (crash == "joint-after") error("simulated crash")
                return true
            }
            override fun applyStricter(expectedCredentials: CredentialState, replacementCredentials: CredentialState,
                expectedWaiting: WaitingState, expectedElevation: ElevationRecord, replacementElevation: ElevationRecord,
                receipt: FrictionSettingsReceipt) = write(expectedCredentials, replacementCredentials, expectedWaiting,
                    expectedWaiting, expectedElevation, replacementElevation, receipt)
            override fun tightenAndCancel(expectedCredentials: CredentialState, replacementCredentials: CredentialState,
                expectedWaiting: WaitingState, replacementWaiting: WaitingState, expectedElevation: ElevationRecord,
                replacementElevation: ElevationRecord, receipt: FrictionSettingsReceipt) = write(expectedCredentials,
                    replacementCredentials, expectedWaiting, replacementWaiting, expectedElevation, replacementElevation, receipt)
            override fun completeFriction(expectedCredentials: CredentialState, replacementCredentials: CredentialState,
                expectedWaiting: WaitingState, replacementWaiting: WaitingState, expectedElevation: ElevationRecord) =
                write(expectedCredentials, replacementCredentials, expectedWaiting, replacementWaiting, expectedElevation, expectedElevation, null)
        }
        fun controller() = WaitingChangeController(waitingStore, { credentialStore.read() }, clock, vault,
            WaitingIdentitySource { WaitingIdentity("wait-${++sequence}", "synthetic-cancel-$sequence") }, cancelStore,
            WaitingSkipSupport(vault, WaitingSkipCommitter { _, _, _, _ -> error("self recovery must never skip") }))
        val controller = controller()
        fun executor() = FrictionWaitingController(transactions, credentialStore, clock)
        fun editor() = FrictionSettingsController(transactions, credentialStore, vault, clock, AppApprovalIdSource { "edit-${++sequence}" })
        fun auth(r: WaitingStart) = assertIs<PinResult.Authorized>(vault.authenticate(self, pin, r.action)).authority
        fun prepare(target: FrictionSettings = FrictionSettings(600_000, 2)) = assertNotNull(controller.prepareFriction(self, target))
        fun start(target: FrictionSettings = FrictionSettings(600_000, 2)): String {
            val r = prepare(target); assertEquals(WaitingOutcome.PENDING, controller.startFrictionChange(r, auth(r), true)); return r.requestId
        }
        fun at(total: Long, epoch: String = "epoch", boot: String = "boot", elapsed: Long = total) {
            now = ClockReading(RunningTime(epoch, total), BootTime(boot, elapsed))
        }
        fun mature(id: String) { at(86_400_000); assertEquals(WaitingOutcome.COMMITTING, controller.tick(id, BatteryState(1, false))) }
        fun stricter(target: FrictionSettings = FrictionSettings(1_800_000, 0)): FrictionSettingsOutcome {
            val editor = editor(); val r = assertNotNull(editor.prepareStricter(self, target))
            val fresh = assertIs<PinResult.Authorized>(vault.authenticate(self, pin, r.action)).authority
            return editor.applyStricter(r, fresh)
        }
    }

    @Test fun `both looser kinds and combined target wait without changing active values`() {
        val targets = listOf(FrictionSettings(600_000, 1), FrictionSettings(1_200_000, 3), FrictionSettings(600_000, 3))
        for (target in targets) {
            val f = F(); val id = f.start(target); val op = f.waiting!!.operation!!
            assertEquals(if (target.countdownMillis < f.base.countdownMillis) WaitingChangeKind.SHORTEN_COUNTDOWN else WaitingChangeKind.RAISE_DAILY_LIMIT, op.kind)
            assertEquals(target, op.frictionPatch!!.target); assertEquals(f.base, f.credentials!!.recoveryProfile!!.friction)
            assertNull(op.peoplePatch); assertNull(op.modePatch)
            assertEquals(FrictionWaitingOutcome.IGNORED, f.executor().finish(id))
            f.at(day - 1); assertEquals(WaitingOutcome.PENDING, f.controller.tick(id, BatteryState(50, false)))
            f.at(day); assertEquals(WaitingOutcome.COMMITTING, f.controller.tick(id, BatteryState(1, false)))
            assertEquals(f.base, f.credentials!!.recoveryProfile!!.friction)
            assertEquals(FrictionWaitingOutcome.COMPLETED, f.executor().finish(id))
            assertEquals(target, f.credentials!!.recoveryProfile!!.friction)
        }
    }

    @Test fun `start requires exact fresh PIN confirmation owner and supported proposal`() {
        val f = F(); val r = f.prepare(); val other = f.prepare(FrictionSettings(1_200_000, 2))
        assertEquals(WaitingOutcome.REFUSED, f.controller.startFrictionChange(r, f.auth(other), true))
        assertEquals(WaitingOutcome.REFUSED, f.controller().startFrictionChange(r, f.auth(r), true))
        val fresh = f.auth(r)
        assertEquals(WaitingOutcome.REFUSED, f.controller.startFrictionChange(r, fresh, false))
        assertEquals(WaitingOutcome.PENDING, f.controller.startFrictionChange(r, fresh, true))
        assertEquals(WaitingOutcome.REFUSED, f.controller.startFrictionChange(r, fresh, true))
        assertNull(f.controller.prepareFriction(f.self, f.base))
        assertNull(f.controller.prepareFriction(f.self, FrictionSettings(1_800_000, 0)))
        assertNull(f.controller.prepareFriction(f.self, FrictionSettings(600_000, 0)))
        assertNull(f.controller.prepareFriction(TrustedPersonId("other"), FrictionSettings(600_000, 2)))
        val g = F(); val stale = g.prepare(); val old = g.auth(stale); g.at(120_000)
        assertEquals(WaitingOutcome.REFUSED, g.controller.startFrictionChange(stale, old, true))
    }

    @Test fun `friction cannot enter turn off people mode or recovery handover start`() {
        val f = F(); val r = f.prepare()
        assertEquals(WaitingOutcome.REFUSED, f.controller.startTurnOff(r, f.auth(r), true, true))
        assertEquals(WaitingOutcome.REFUSED, f.controller.startPeopleChange(r, f.auth(r), true))
        assertEquals(WaitingOutcome.REFUSED, f.controller.startModeSwitch(r, f.auth(r), true))
        assertIs<WaitingSkipResult.NotSkipped>(f.controller.startHandOverWithRecovery(r, f.prepared.recoveryCode.toCharArray(), f.pin, true, true))
        val off = assertNotNull(f.controller.prepareTurnOff(f.self))
        assertEquals(WaitingOutcome.REFUSED, f.controller.startFrictionChange(off, f.auth(off), true)); assertNull(f.waiting!!.operation)
    }

    @Test fun `one shared slot holds pending and committing until terminal and rejects stale callbacks`() {
        val f = F(); val id = f.start(); val another = f.prepare()
        assertEquals(WaitingOutcome.BUSY, f.controller.startFrictionChange(another, f.auth(another), true))
        val off = assertNotNull(f.controller.prepareTurnOff(f.self))
        assertEquals(WaitingOutcome.BUSY, f.controller.startTurnOff(off, f.auth(off), true, true))
        f.mature(id)
        assertEquals(WaitingOutcome.BUSY, f.controller.startTurnOff(off, f.auth(off), true, true))
        assertEquals(WaitingOutcome.ALREADY_IN_PROGRESS, f.controller.cancel(id))
        assertEquals(FrictionWaitingOutcome.IGNORED, f.executor().finish("stale"))
        assertEquals(FrictionWaitingOutcome.COMPLETED, f.executor().finish(id))
        assertEquals(WaitingOutcome.PENDING, f.controller.startTurnOff(off, f.auth(off), true, true))
        assertEquals(FrictionWaitingOutcome.IGNORED, f.executor().finish(id))
    }

    @Test fun `reboot and epoch repair use proven age while damaged clock cannot mature`() {
        val f = F(); f.at(30 * 3_600_000L); val id = f.start()
        f.at(day - 1, "repair", "reboot", 100)
        assertEquals(WaitingOutcome.PENDING, f.controller().tick(id, BatteryState(50, false)))
        f.now = null; assertEquals(WaitingOutcome.NOT_READY, f.controller.tick(id, BatteryState(50, false)))
        f.at(day, "repair", "reboot", 101)
        assertEquals(WaitingOutcome.COMMITTING, f.controller().tick(id, BatteryState(50, false)))
        f.now = null; assertEquals(FrictionWaitingOutcome.NOT_READY, f.executor().finish(id))
        f.at(day, "repair", "reboot", 101)
        assertEquals(FrictionWaitingOutcome.COMPLETED, f.executor().finish(id))
    }

    @Test fun `self recovery never skips and pending generation changes cancel`() {
        val f = F(); val id = f.start(); val old = f.credentials
        assertEquals(WaitingOutcome.REFUSED, assertIs<WaitingSkipResult.NotSkipped>(f.controller.skip(id, f.self,
            f.prepared.recoveryCode.toCharArray(), f.pin, true, BatteryState(50, false))).outcome)
        assertSame(old, f.credentials)
        assertIs<RecoveryResult.Replaced>(f.vault.recover(f.self, f.prepared.recoveryCode.toCharArray(), f.pin))
        assertEquals(WaitingOutcome.CANCELLED, f.controller.tick(id, BatteryState(50, false)))
        assertEquals(FrictionWaitingOutcome.CANCELLED, f.executor().finish(id))
        assertEquals(f.base, f.credentials!!.recoveryProfile!!.friction)
    }

    @Test fun `PIN reset after committing preserves current credentials and does not stop completion`() {
        val f = F(); val id = f.start(); f.mature(id)
        assertIs<RecoveryResult.Replaced>(f.vault.recover(f.self, f.prepared.recoveryCode.toCharArray(), f.pin))
        val current = f.credentials!!; val profile = current.recoveryProfile!!
        assertEquals(FrictionWaitingOutcome.COMPLETED, f.executor().finish(id))
        assertEquals(current.people, f.credentials!!.people); assertEquals(current.pendingNotices, f.credentials!!.pendingNotices)
        assertEquals(current.structureGeneration, f.credentials!!.structureGeneration)
        assertEquals(current.appliedPeopleChange, f.credentials!!.appliedPeopleChange)
        assertEquals(profile.holderUserIds, f.credentials!!.recoveryProfile!!.holderUserIds)
        assertEquals(profile.backupUserId, f.credentials!!.recoveryProfile!!.backupUserId)
    }

    @Test fun `durable cancellation beats maturity after failed write and restart`() {
        val f = F(); val id = f.start(); f.fail = "waiting"
        assertEquals(WaitingOutcome.NOT_READY, f.controller.cancel(id)); f.at(day)
        assertEquals(WaitingOutcome.NOT_READY, f.controller().tick(id, BatteryState(50, false)))
        f.fail = null
        assertEquals(WaitingOutcome.CANCELLED, f.controller().tick(id, BatteryState(50, false)))
        assertEquals(FrictionWaitingOutcome.CANCELLED, f.executor().finish(id))
        assertEquals(f.base, f.credentials!!.recoveryProfile!!.friction)
    }

    @Test fun `stricter edit cancels looser operation atomically with fixed cancellation and routine receipts`() {
        val f = F(); val id = f.start(); val oldStart = f.waiting!!.texts.single(); val oldContact = oldStart.recipientContacts[f.self]
        f.credentials = f.credentials!!.replacing(f.self, f.credentials!!.people.getValue(f.self).copy(contact = null))
        assertEquals(FrictionSettingsOutcome.APPLIED, f.stricter())
        assertEquals(WaitingPhase.CANCELLED, f.waiting!!.operation!!.phase)
        assertEquals(WaitingEvent.DEPENDENCY_CHANGED, f.waiting!!.texts.last().event)
        assertSame(oldContact, oldStart.recipientContacts[f.self]); assertNull(f.waiting!!.texts.last().recipientContacts[f.self])
        assertEquals(1, f.routineReceipts.size)
        f.at(day); assertEquals(FrictionWaitingOutcome.CANCELLED, f.executor().finish(id))
        assertEquals(FrictionSettings(1_800_000, 0), f.credentials!!.recoveryProfile!!.friction)
    }

    @Test fun `failed joint tightening changes neither active friction nor pending target`() {
        val f = F(); f.start(); val before = f.waiting; f.fail = "joint"
        assertEquals(FrictionSettingsOutcome.NOT_READY, f.stricter())
        assertEquals(f.base, f.credentials!!.recoveryProfile!!.friction); assertSame(before, f.waiting)
        assertTrue(f.routineReceipts.isEmpty())
    }

    @Test fun `intervening source or structure edits reject preparation and cancel pending wait`() {
        for (structure in listOf(false, true)) {
            val f = F(); val r = f.prepare(); val id = f.start(); val c = f.credentials!!; val p = c.recoveryProfile!!
            f.credentials = CredentialState(c.mode, c.people, c.revision + 1, c.structureGeneration + if (structure) 1 else 0,
                c.pendingNotices, RecoveryProfile(p.holderUserIds, p.backupUserId, if (structure) p.friction else FrictionSettings(1_800_000, 1)))
            assertEquals(WaitingOutcome.CANCELLED, f.controller.tick(id, BatteryState(50, false)))
            assertEquals(WaitingOutcome.REFUSED, f.controller.startFrictionChange(r, f.auth(r), true))
        }
    }

    @Test fun `failed completion and crashes expose old or entire new credential journal receipt pair`() {
        for (edge in listOf("joint-before", "joint-after", "failure")) {
            val f = F(); val id = f.start(); f.mature(id); val old = f.credentials; val waiting = f.waiting
            if (edge == "failure") {
                f.fail = "joint"; assertEquals(FrictionWaitingOutcome.NOT_READY, f.executor().finish(id))
            } else {
                f.crash = edge; assertFailsWith<IllegalStateException> { f.executor().finish(id) }
            }
            if (edge == "joint-after") {
                assertEquals(FrictionSettings(600_000, 2), f.credentials!!.recoveryProfile!!.friction)
                assertEquals(WaitingPhase.COMPLETED, f.waiting!!.operation!!.phase)
                assertEquals(1, f.waiting!!.texts.count { it.event == WaitingEvent.COMPLETED })
            } else { assertSame(old, f.credentials); assertSame(waiting, f.waiting) }
            f.fail = null; f.crash = null
            assertEquals(FrictionWaitingOutcome.COMPLETED, f.executor().finish(id))
            assertEquals(FrictionWaitingOutcome.COMPLETED, f.executor().finish(id)); assertEquals(1, f.writes)
        }
    }

    @Test fun `readback failures cannot report completion and retry recognizes durable terminal`() {
        for (failure in listOf("credential-readback", "waiting-readback", "elevation-readback")) {
            val f = F(); val id = f.start(); f.mature(id); f.fail = failure
            assertEquals(FrictionWaitingOutcome.NOT_READY, f.executor().finish(id))
            assertEquals(WaitingPhase.COMPLETED, f.waiting!!.operation!!.phase)
            f.fail = null; assertEquals(FrictionWaitingOutcome.COMPLETED, f.executor().finish(id)); assertEquals(1, f.writes)
        }
    }

    @Test fun `completion CAS fences credentials waiting generation direction and elevation revisions`() {
        for (race in 0..3) {
            val f = F(); val id = f.start(); f.mature(id); val target = f.credentials!!.recoveryProfile!!.friction
            f.hook = { when (race) {
                0 -> f.credentials = f.credentials!!.replacing(f.self, f.credentials!!.people.getValue(f.self))
                1 -> f.waiting = WaitingState(storageGeneration = "replacement")
                2 -> f.waiting = f.waiting!!.replacing(f.waiting!!.revision + 1, f.waiting!!.operation!!.withPhase(WaitingPhase.CANCELLED))
                3 -> f.elevation = f.elevation!!.replacing(f.elevation!!.state)
            } }
            assertEquals(FrictionWaitingOutcome.NOT_READY, f.executor().finish(id)); assertEquals(0, f.writes)
            assertEquals(target, f.credentials!!.recoveryProfile!!.friction)
        }
    }

    @Test fun `completion preserves exact ledger and live countdown and never shortens that countdown`() {
        val f = F(); val id = f.start(); f.mature(id)
        val ledger = ElevationLedger(listOf(ElevationDebit("expired", RunningTime("epoch", 0)),
            ElevationDebit("foreign", RunningTime("old-epoch", 80_000_000))))
        val countdown = ElevationCountdown("live-countdown", ElevationTarget.Settings, f.now!!.boot, f.base.countdownMillis)
        val before = ElevationRecord(state = ElevationState(ledger, countdown), person = ElevationPersonBinding(f.self, 0, 0))
        f.elevation = before
        assertEquals(FrictionWaitingOutcome.COMPLETED, f.executor().finish(id)); assertSame(before, f.elevation)
        f.at(day + 600_000)
        assertEquals(ElevationOutcome.COUNTING, ElevationMachine.tick(before.state, ElevationLimits(600_000, 2), f.now).outcome)
        assertSame(ledger, f.elevation!!.state.ledger)
    }

    @Test fun `completion preserves live grant original deadline and full debit list`() {
        val f = F(); val id = f.start(); f.mature(id)
        val debit = ElevationDebit("grant", f.now!!.proven); val ledger = ElevationLedger(listOf(debit))
        val grant = ElevationGrant("grant", ElevationTarget.Settings, f.now!!.boot, BootTime("boot", day + 300_000), false)
        val before = ElevationRecord(state = ElevationState(ledger, grant = grant), person = ElevationPersonBinding(f.self, 0, 0))
        f.elevation = before
        assertEquals(FrictionWaitingOutcome.COMPLETED, f.executor().finish(id)); assertSame(before, f.elevation)
        f.at(day + 300_000)
        assertEquals(ElevationOutcome.LAPSED, ElevationMachine.tick(before.state, ElevationLimits(600_000, 2), f.now).outcome)
        assertEquals(listOf(debit), ledger.debits)
    }

    @Test fun `completion receipts retain fixed contacts despite later recovery and delivery acknowledgment`() {
        val f = F(); val id = f.start(); val start = f.waiting!!.texts.single()
        f.credentials = f.credentials!!.replacing(f.self, f.credentials!!.people.getValue(f.self).copy(contact = null))
        f.mature(id); assertEquals(FrictionWaitingOutcome.COMPLETED, f.executor().finish(id))
        val receipt = f.waiting!!.texts.last(); assertEquals(WaitingEvent.COMPLETED, receipt.event)
        assertNotNull(start.recipientContacts[f.self]); assertNull(receipt.recipientContacts[f.self])
        assertIs<RecoveryResult.Replaced>(f.vault.recover(f.self, f.prepared.recoveryCode.toCharArray(), f.pin))
        assertEquals(mapOf(f.self to 0L), receipt.recipientGenerations)
        assertTrue(f.controller.acknowledge(receipt.key)); assertEquals(FrictionWaitingOutcome.COMPLETED, f.executor().finish(id))
        assertEquals(1, f.writes); assertTrue(f.waiting!!.texts.none { it.key == receipt.key })
    }
    @Test fun `lost waiting record before and after activation reconciles active friction not pending target`() {
        for (activated in listOf(false, true)) {
            val f = F(); val id = f.start(); f.mature(id); val stale = f.waiting!!.operation!!
            if (activated) assertEquals(FrictionWaitingOutcome.COMPLETED, f.executor().finish(id))
            val expected = f.credentials!!.recoveryProfile!!.friction
            val ledger = ElevationLedger.repair(RunningTime("repair", 0))
            f.elevation = ElevationRecord(state = ElevationState(ledger))
            f.waiting = null; f.loss = LostWaitingRecord("loss-1")
            var applied: FrictionSettings? = null; var plan: PolicyPlan? = null
            val effects = object : ReconciliationEffects {
                override fun revokeTemporaryAccess() = true
                override fun isDeviceOwner(): Boolean? = true
                override fun applyPolicy(value: PolicyPlan): Boolean { plan = value; return true }
                override fun applyResetProtection(userIds: Set<String>) = userIds == setOf("synthetic-account", "synthetic-backup")
                override fun applyFriction(settings: FrictionSettings): Boolean { applied = settings; return true }
                override fun readBackPolicy(value: PolicyPlan) = value === plan
                override fun readBackResetProtection(userIds: Set<String>) = userIds == setOf("synthetic-account", "synthetic-backup")
                override fun readBackFriction(settings: FrictionSettings) = settings == applied
            }
            val dialer = PackageName("test.dialer")
            val input = PolicyInput("model", "build", PackageName("test.home"), PackageName("test.sms"),
                listOf(InstalledPackage(dialer, true, 1)), listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)),
                waitingOperation = stale)
            assertEquals(FrictionWaitingOutcome.NOT_READY, f.executor().finish(id))
            val reconciler = WaitingReconciler(f.waitingStore, { f.credentials }, { input }, effects, f.vault)
            assertEquals(ReconcileOutcome.RECOVERED, reconciler.reconcile().outcome)
            assertEquals(expected, applied); assertEquals(expected, f.waiting!!.recoveryReceipts.single().friction)
            assertNull(f.waiting!!.operation); assertEquals("loss-1", f.waiting!!.storageGeneration)
            assertSame(ledger, f.elevation!!.state.ledger); assertTrue(dialer in plan!!.neverHide)
            assertEquals(FrictionWaitingOutcome.IGNORED, f.executor().finish(id))
        }
    }

    @Test fun `read failures revision exhaustion foreign mode and rollback never activate target`() {
        for (case in 0..7) {
            val f = F(); val id = f.start(); f.mature(id)
            when (case) {
                0 -> f.credentials = null
                1 -> f.elevation = null
                2 -> f.waiting = null
                3 -> { val c = f.credentials!!; f.credentials = CredentialState(c.mode, c.people, Long.MAX_VALUE, recoveryProfile = c.recoveryProfile) }
                4 -> f.waiting = WaitingState(Long.MAX_VALUE, f.waiting!!.operation)
                5 -> { val c = f.credentials!!; f.credentials = CredentialState(Mode.TRUSTED, c.people, c.revision + 1, 1, recoveryProfile = c.recoveryProfile) }
                6 -> f.waiting = f.waiting!!.replacing(f.waiting!!.revision + 1, f.waiting!!.operation!!.withPhase(WaitingPhase.ROLLING_BACK))
                7 -> f.elevationStore.revocationPending = true
            }
            assertTrue(f.executor().finish(id) in setOf(FrictionWaitingOutcome.NOT_READY, FrictionWaitingOutcome.IGNORED))
            assertEquals(0, f.writes)
        }
    }

    @Test fun `start or maturity crash preserves whole record and receipt without activation`() {
        for (phase in listOf("start", "mature")) for (after in listOf(false, true)) {
            val f = F(); val r = f.prepare(); val fresh = f.auth(r)
            if (phase == "mature") { assertEquals(WaitingOutcome.PENDING, f.controller.startFrictionChange(r, fresh, true)); f.at(day) }
            f.crash = if (after) "waiting-after" else "waiting-before"
            assertFailsWith<IllegalStateException> {
                if (phase == "start") f.controller.startFrictionChange(r, fresh, true)
                else f.controller.tick(r.requestId, BatteryState(50, false))
            }
            val expected = if (phase == "start") (if (after) WaitingPhase.PENDING else null)
                else (if (after) WaitingPhase.COMMITTING else WaitingPhase.PENDING)
            assertEquals(expected, f.waiting!!.operation?.phase)
            assertEquals(if (phase == "start") (if (after) 1 else 0) else (if (after) 2 else 1), f.waiting!!.texts.size)
            assertEquals(f.base, f.credentials!!.recoveryProfile!!.friction)
        }
    }

    @Test fun `stricter cancellation crash cannot expose new settings with stale pending target`() {
        for (after in listOf(false, true)) {
            val f = F(); val id = f.start(); f.crash = if (after) "joint-after" else "joint-before"
            assertFailsWith<IllegalStateException> { f.stricter() }
            assertEquals(if (after) WaitingPhase.CANCELLED else WaitingPhase.PENDING, f.waiting!!.operation!!.phase)
            assertEquals(if (after) FrictionSettings(1_800_000, 0) else f.base, f.credentials!!.recoveryProfile!!.friction)
            assertEquals(if (after) 1 else 0, f.routineReceipts.size)
            f.crash = null; f.at(day)
            if (after) assertEquals(FrictionWaitingOutcome.CANCELLED, f.executor().finish(id))
        }
    }

}
