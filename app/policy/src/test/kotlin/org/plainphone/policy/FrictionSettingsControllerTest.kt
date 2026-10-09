// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class FrictionSettingsControllerTest {
    private class F {
        val self = TrustedPersonId("self")
        val pin get() = CharArray(6) { '1' }
        var now: ClockReading? = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
        var credentials: CredentialState? = null
        var waiting: WaitingState? = WaitingState()
        var elevation: ElevationRecord? = ElevationRecord()
        var fail = false
        var unreadableAfterWrite = false
        var crash: String? = null
        var beforeWrite: (() -> Unit)? = null
        val receipts = mutableListOf<FrictionSettingsReceipt>()
        var writes = 0
        var serial = 0
        val credentialStore = object : CredentialStore {
            override fun read() = if (unreadableAfterWrite && writes > 0) null else credentials
            override fun compareAndSet(expected: CredentialState, replacement: CredentialState): Boolean {
                if (credentials !== expected) return false
                credentials = replacement; return true
            }
        }
        val waitingStore = object : WaitingChangeStore {
            override fun read() = waiting
            override fun compareAndSet(expected: WaitingState, replacement: WaitingState): Boolean {
                if (waiting !== expected) return false
                waiting = replacement; return true
            }
        }
        val elevationStore = object : ElevationStore(waitingStore) {
            override fun read() = elevation
            override fun compareAndSet(expected: ElevationRecord?, replacement: ElevationRecord, expectedCredentials: CredentialState?): Boolean {
                if (elevation !== expected || (expectedCredentials != null && credentials !== expectedCredentials)) return false
                elevation = replacement; return true
            }
        }
        val store = object : FrictionSettingsStore {
            override val waitingStore get() = this@F.waitingStore
            override val elevationStore get() = this@F.elevationStore
            override fun applyStricter(expectedCredentials: CredentialState, replacementCredentials: CredentialState,
                expectedWaiting: WaitingState, expectedElevation: ElevationRecord, replacementElevation: ElevationRecord, receipt: FrictionSettingsReceipt): Boolean {
                beforeWrite?.invoke()
                if (crash == "before") error("simulated crash before atomic write")
                if (fail || credentials !== expectedCredentials || waiting !== expectedWaiting || elevation !== expectedElevation) return false
                credentials = replacementCredentials; elevation = replacementElevation; receipts.add(receipt); writes++
                if (crash == "after") error("simulated crash after atomic write")
                return true
            }
        }
        val vault = PinVault(credentialStore, KeyDerivation { chars, salt, _ ->
            java.security.MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
        }, SecretEntropy { n -> ByteArray(n) { (++serial).toByte() } }, RunningClock { now })
        val prepared = vault.prepareCredential(pin)
        init {
            credentials = CredentialState(Mode.SELF_MANAGED, mapOf(self to prepared.credential),
                pendingNotices = setOf(LockoutNotice(self, CredentialChannel.PIN, 0)),
                recoveryProfile = RecoveryProfile(mapOf(self to "synthetic-user"), "synthetic-backup", FrictionSettings(600_000, 2)))
        }
        fun controller() = FrictionSettingsController(store, credentialStore, vault, RunningClock { now },
            AppApprovalIdSource { "edit-${++serial}" })
        val controller = controller()
        fun prepare(target: FrictionSettings = FrictionSettings(1_200_000, 1)) = assertNotNull(controller.prepareStricter(self, target))
        fun auth(request: FrictionSettingsRequest) = assertIs<PinResult.Authorized>(vault.authenticate(self, pin, request.action)).authority
        fun advance(ms: Long) { now = now!!.copy(proven = now!!.proven.copy(totalMillis = ms), boot = now!!.boot.copy(elapsedMillis = ms)) }
        fun limits() = credentials!!.recoveryProfile!!.friction.let { ElevationLimits(it.countdownMillis, it.dailyLimit) }
        fun countdown(ledger: ElevationLedger = ElevationLedger()) {
            val state = ElevationMachine.request(ElevationState(ledger), "turn", ElevationTarget.Settings, limits(), now).state
            elevation = ElevationRecord(state = state, person = ElevationPersonBinding(self, 0, 0))
        }
        fun replaceFriction(value: FrictionSettings) {
            val c = credentials!!; val p = c.recoveryProfile!!
            credentials = CredentialState(c.mode, c.people, c.revision + 1, c.structureGeneration, c.pendingNotices,
                RecoveryProfile(p.holderUserIds, p.backupUserId, value), c.appliedPeopleChange)
        }
    }

    @Test fun `patch classifies both waiting kinds and rejects mixed direction and no-op`() {
        val f = F(); f.replaceFriction(FrictionSettings(1_200_000, 1))
        assertEquals(FrictionDirection.STRICTER, FrictionSettingsPatch.create(f.credentials!!, f.self, FrictionSettings(1_800_000, 0))!!.direction)
        for (target in listOf(FrictionSettings(600_000, 1), FrictionSettings(1_200_000, 2), FrictionSettings(600_000, 3))) {
            val patch = assertNotNull(FrictionSettingsPatch.create(f.credentials!!, f.self, target))
            assertEquals(FrictionDirection.LOOSER, patch.direction); assertEquals(target, patch.target)
            assertNull(f.controller.prepareStricter(f.self, target))
        }
        for (target in listOf(FrictionSettings(600_000, 0), FrictionSettings(1_800_000, 2), FrictionSettings(1_200_000, 1)))
            assertNull(FrictionSettingsPatch.create(f.credentials!!, f.self, target))
        assertNull(FrictionSettingsPatch.create(f.credentials!!, TrustedPersonId("stranger"), FrictionSettings(1_800_000, 0)))
    }

    @Test fun `invalid values cannot construct settings and boundary values are accepted`() {
        for (duration in listOf(-1L, 0L, 599_999L, 86_400_001L, Long.MAX_VALUE))
            assertFailsWith<IllegalArgumentException> { FrictionSettings(duration, 2) }
        for (limit in listOf(-1, 4, Int.MAX_VALUE)) assertFailsWith<IllegalArgumentException> { FrictionSettings(600_000, limit) }
        val f = F(); val r = f.prepare(FrictionSettings(86_400_000, 0))
        assertEquals(FrictionSettingsOutcome.APPLIED, f.controller.applyStricter(r, f.auth(r)))
    }

    @Test fun `exact fresh PIN is required and authority cannot be replayed or transferred`() {
        val f = F(); val a = f.prepare(); val b = f.prepare(FrictionSettings(1_800_000, 0)); val pin = f.auth(a)
        assertEquals(FrictionSettingsOutcome.REFUSED, f.controller.applyStricter(b, pin))
        assertEquals(FrictionSettingsOutcome.REFUSED, f.controller().applyStricter(a, pin))
        assertEquals(FrictionSettingsOutcome.REFUSED, f.controller.applyStricter(a, FreshAuthority()))
        assertEquals(FrictionSettingsOutcome.APPLIED, f.controller.applyStricter(a, pin))
        assertEquals(FrictionSettingsOutcome.REFUSED, f.controller.applyStricter(a, pin)); assertEquals(1, f.writes)
    }

    @Test fun `expired PIN and recovery invalidate edit without resetting friction or debits`() {
        val f = F(); val r = f.prepare(); val pin = f.auth(r); f.advance(120_000)
        assertEquals(FrictionSettingsOutcome.REFUSED, f.controller.applyStricter(r, pin))
        val next = f.prepare(); val fresh = f.auth(next); val ledger = f.elevation!!.state.ledger
        assertIs<RecoveryResult.Replaced>(f.vault.recover(f.self, f.prepared.recoveryCode.toCharArray(), CharArray(6) { '2' }))
        assertEquals(FrictionSettingsOutcome.REFUSED, f.controller.applyStricter(next, fresh))
        assertEquals(FrictionSettings(600_000, 2), f.credentials!!.recoveryProfile!!.friction)
        assertSame(ledger, f.elevation!!.state.ledger); assertEquals(0, f.writes)
    }

    @Test fun `countdown is lengthened atomically even if no elevation tick occurs until old lapse`() {
        val f = F(); f.countdown(); f.advance(300_000)
        val before = f.elevation!!; val r = f.prepare(FrictionSettings(1_200_000, 2))
        assertEquals(FrictionSettingsOutcome.APPLIED, f.controller.applyStricter(r, f.auth(r)))
        assertEquals(before.state.countdown!!.startedAt, f.elevation!!.state.countdown!!.startedAt)
        assertEquals(1_200_000, f.elevation!!.state.countdown!!.durationMillis)
        f.advance(900_000)
        assertEquals(ElevationOutcome.COUNTING, ElevationMachine.tick(f.elevation!!.state, f.limits(), f.now).outcome)
        f.advance(1_200_000)
        assertEquals(ElevationOutcome.STARTED, ElevationMachine.start(f.elevation!!.state, "turn", ElevationTarget.Settings, f.limits(), f.now).outcome)
    }

    @Test fun `lower limit keeps every debit including expired ones and Start rechecks`() {
        val f = F(); val ledger = ElevationLedger(listOf(ElevationDebit("old", RunningTime("epoch", 0))))
        f.advance(86_400_000); f.countdown(ledger); val r = f.prepare(FrictionSettings(600_000, 0))
        assertEquals(FrictionSettingsOutcome.APPLIED, f.controller.applyStricter(r, f.auth(r)))
        assertSame(ledger, f.elevation!!.state.ledger); f.advance(87_000_000)
        assertEquals(ElevationOutcome.LIMIT_REACHED, ElevationMachine.start(f.elevation!!.state, "turn", ElevationTarget.Settings, f.limits(), f.now).outcome)
        assertEquals(listOf(ElevationDebit("old", RunningTime("epoch", 0))), f.elevation!!.state.ledger.debits)
    }

    @Test fun `setting edits preserve live grants and exact deadline journal and ledger`() {
        val f = F(); f.countdown(); f.advance(600_000)
        val state = ElevationMachine.start(f.elevation!!.state, "turn", ElevationTarget.Settings, f.limits(), f.now).state
        val access = SessionAccess("turn", WindowKind.SETTINGS, setOf(PackageName("test.settings")), state.grant!!.deadline)
        f.elevation = ElevationRecord(state = state, journal = SessionJournal.Active(access, SessionPhase.OPEN), person = f.elevation!!.person)
        val before = f.elevation!!; val r = f.prepare()
        assertEquals(FrictionSettingsOutcome.APPLIED, f.controller.applyStricter(r, f.auth(r)))
        assertSame(before.state.grant, f.elevation!!.state.grant); assertSame(before.journal, f.elevation!!.journal)
        assertSame(before.state.ledger, f.elevation!!.state.ledger)
    }

    @Test fun `already lapsed countdown is never revived at exact Start expiry`() {
        val f = F(); f.countdown(); f.advance(900_000); val r = f.prepare()
        assertEquals(FrictionSettingsOutcome.APPLIED, f.controller.applyStricter(r, f.auth(r)))
        assertNull(f.elevation!!.state.countdown); assertNull(f.elevation!!.person)
        assertTrue(f.elevation!!.state.ledger.debits.isEmpty())
    }

    @Test fun `epoch repair does not change boot deadline and reboot cannot restore countdown`() {
        for (reboot in listOf(false, true)) {
            val f = F(); f.countdown(); val ledger = f.elevation!!.state.ledger
            f.now = ClockReading(RunningTime("repaired", 0), BootTime(if (reboot) "new-boot" else "boot", 100_000))
            val r = f.prepare(FrictionSettings(600_000, 1))
            assertEquals(FrictionSettingsOutcome.APPLIED, f.controller.applyStricter(r, f.auth(r)))
            if (reboot) assertNull(f.elevation!!.state.countdown) else assertEquals(900_000, f.elevation!!.state.countdown!!.lapsesAt)
            assertSame(ledger, f.elevation!!.state.ledger)
        }
    }

    @Test fun `unrelated credential data survives while changed source friction rejects stale preparation`() {
        val f = F(); val r = f.prepare(); val fresh = f.auth(r); val c = f.credentials!!; val p = c.recoveryProfile!!
        assertEquals(FrictionSettingsOutcome.APPLIED, f.controller.applyStricter(r, fresh)); val saved = f.credentials!!
        assertEquals(c.people, saved.people); assertEquals(c.pendingNotices, saved.pendingNotices)
        assertEquals(c.structureGeneration, saved.structureGeneration); assertEquals(c.appliedPeopleChange, saved.appliedPeopleChange)
        assertEquals(p.holderUserIds, saved.recoveryProfile!!.holderUserIds); assertEquals(p.backupUserId, saved.recoveryProfile!!.backupUserId)
        val stale = f.prepare(FrictionSettings(1_800_000, 0)); f.replaceFriction(FrictionSettings(2_400_000, 1))
        assertEquals(FrictionSettingsOutcome.REFUSED, f.controller.applyStricter(stale, f.auth(stale)))
    }

    @Test fun `pending waiting operation is preserved and does not prevent a stricter edit`() {
        val f = F()
        f.waiting = WaitingState(operation = WaitingOperation(WaitingIdentity("waiting", "synthetic-cancel"), f.self, 0, 0,
            Mode.SELF_MANAGED, RunningTime("epoch", 0), WaitingPhase.PENDING, mapOf(f.self to 0)))
        val before = f.waiting; val r = f.prepare()
        assertEquals(FrictionSettingsOutcome.APPLIED, f.controller.applyStricter(r, f.auth(r))); assertSame(before, f.waiting)
    }

    @Test fun `missing records execution phases and exhausted revisions fail closed`() {
        for (case in 0..7) {
            val f = F(); val r = f.prepare(); val fresh = f.auth(r)
            when (case) {
                0 -> f.waiting = null
                1 -> f.elevation = null
                2 -> f.credentials = null
                3 -> f.now = null
                4, 5 -> f.waiting = WaitingState(operation = WaitingOperation(WaitingIdentity("waiting", "synthetic-cancel"), f.self, 0, 0,
                    Mode.SELF_MANAGED, RunningTime("epoch", 0), if (case == 4) WaitingPhase.COMMITTING else WaitingPhase.ROLLING_BACK, mapOf(f.self to 0)))
                6 -> f.elevation = ElevationRecord(revision = Long.MAX_VALUE)
                7 -> { val c = f.credentials!!; f.credentials = CredentialState(c.mode, c.people, Long.MAX_VALUE, recoveryProfile = c.recoveryProfile) }
            }
            assertEquals(FrictionSettingsOutcome.NOT_READY, f.controller.applyStricter(r, fresh), "case $case"); assertEquals(0, f.writes)
        }
    }

    @Test fun `failed or raced transaction never partly activates and consumes successful PIN attempt`() {
        for (race in 0..3) {
            val f = F(); f.countdown(); val r = f.prepare(); val fresh = f.auth(r)
            val before = f.elevation!!; val settings = f.credentials!!.recoveryProfile!!.friction
            if (race == 0) f.fail = true else f.beforeWrite = { when (race) {
                1 -> f.credentials = f.credentials!!.replacing(f.self, f.credentials!!.people.getValue(f.self))
                2 -> f.waiting = WaitingState(revision = 1)
                3 -> f.elevation = before.replacing(before.state)
            } }
            assertEquals(FrictionSettingsOutcome.NOT_READY, f.controller.applyStricter(r, fresh))
            assertEquals(settings, f.credentials!!.recoveryProfile!!.friction)
            assertSame(before.state, f.elevation!!.state); assertEquals(0, f.writes); assertTrue(f.receipts.isEmpty())
            f.fail = false; f.beforeWrite = null
            assertEquals(FrictionSettingsOutcome.REFUSED, f.controller.applyStricter(r, fresh))
        }
    }

    @Test fun `crash before or after atomic write leaves old or complete state and restart rejects stale request`() {
        for (edge in listOf("before", "after")) {
            val f = F(); f.countdown(); val r = f.prepare(); val fresh = f.auth(r)
            val ledger = f.elevation!!.state.ledger; f.crash = edge
            assertFailsWith<IllegalStateException> { f.controller.applyStricter(r, fresh) }
            val expected = if (edge == "before") 600_000L else 1_200_000L
            assertEquals(if (edge == "before") 0 else 1, f.receipts.size)
            assertEquals(expected, f.credentials!!.recoveryProfile!!.friction.countdownMillis)
            assertEquals(expected, f.elevation!!.state.countdown!!.durationMillis)
            assertSame(ledger, f.elevation!!.state.ledger)
            assertEquals(FrictionSettingsOutcome.REFUSED, f.controller().applyStricter(r, fresh))
        }
    }

    @Test fun `failed readback reports attention even when the restrictive transaction committed`() {
        val f = F(); val r = f.prepare(); val fresh = f.auth(r); f.unreadableAfterWrite = true
        assertEquals(FrictionSettingsOutcome.NOT_READY, f.controller.applyStricter(r, fresh))
        assertEquals(FrictionSettings(1_200_000, 1), f.credentials!!.recoveryProfile!!.friction); assertEquals(1, f.writes)
    }

    @Test fun `supervised mode and missing recovery profile have no setting authority`() {
        for (mode in listOf(Mode.TRUSTED, Mode.SELF_MANAGED)) {
            val f = F(); val c = f.credentials!!
            f.credentials = CredentialState(mode, c.people, recoveryProfile = if (mode == Mode.TRUSTED) c.recoveryProfile else null)
            assertNull(f.controller.prepareStricter(f.self, FrictionSettings(1_200_000, 1)))
        }
    }
    @Test fun `restrictive receipt fixes recipients and target at the atomic write`() {
        val f = F(); val contact = PersonContact("synthetic-label", "synthetic-address")
        f.credentials = f.credentials!!.replacing(f.self, f.credentials!!.people.getValue(f.self).copy(contact = contact))
        val r = f.prepare(); val fresh = f.auth(r)
        assertEquals(FrictionSettingsOutcome.APPLIED, f.controller.applyStricter(r, fresh))
        val receipt = f.receipts.single()
        assertEquals(r.patch.source, receipt.source); assertEquals(r.patch.target, receipt.target)
        assertEquals(f.self, receipt.actor); assertEquals(mapOf(f.self to 0L), receipt.recipientGenerations)
        assertEquals(mapOf(f.self to contact), receipt.recipientContacts)
        f.credentials = f.credentials!!.replacing(f.self, f.credentials!!.people.getValue(f.self).copy(contact = null, generation = 1))
        assertEquals(mapOf(f.self to contact), receipt.recipientContacts)
        assertEquals(mapOf(f.self to 0L), receipt.recipientGenerations)
    }

}
