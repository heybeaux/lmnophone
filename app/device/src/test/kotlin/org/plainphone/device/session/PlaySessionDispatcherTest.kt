// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.session

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.*
import org.plainphone.device.authority.*
import org.plainphone.policy.*

class PlaySessionDispatcherTest {
    @get:Rule val tmp = TemporaryFolder()
    private val home = PackageName("org.plainphone")
    private val play = PackageName("com.android.vending")
    private val dialer = PackageName("test.dialer")
    private val sms = PackageName("test.sms")
    private val candidate = PackageName("test.newapp")
    private val identity = ApprovalBinding(candidate, setOf(CertDigest("a".repeat(64))))
    private fun pin() = CharArray(6) { '1' }
    private inner class Rig(fullProfile: Boolean = true) {
        var now = 0L
        var sequence = 0
        val clock = RunningClock { ClockReading(RunningTime("epoch", now), BootTime("boot", now)) }
        val dir = tmp.newFolder()
        val store = JointAuthorityStore(dir)
        val vault = PinVault(store.credentials, KeyDerivation { chars, salt, _ ->
            MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
        }, SecretEntropy { count -> ByteArray(count) { (++sequence).toByte() } }, clock)
        val menu = PreviewPinAccess(store, vault)
        val events = mutableListOf<String>()
        val journalAtEffect = mutableListOf<SessionJournal?>()
        var fail: String? = null
        var faultHits = 0
        var open = false
        var policy: PolicyInput? = PolicyInput("test", "build", home, sms,
            listOf(home, play, dialer, sms, candidate).map { InstalledPackage(it, true, 1) },
            listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)),
            BuildInventory("test", "build", 1, NeverHideList(setOf(sms)), setOf(sms), emptySet()),
            installedIdentities = listOf(identity))
        private fun step(name: String): Boolean {
            events += name
            journalAtEffect += store.elevationStore.read()?.journal
            if (name == fail) { faultHits++; return false }
            return true
        }
        val effects = object : SessionEffects {
            override fun revokeAllRelaxations(): Boolean { open = false; return step("revoke") }
            override fun open(access: SessionAccess): Boolean {
                val record = store.elevationStore.read()!!
                assertEquals(SessionPhase.OPENING, (record.journal as SessionJournal.Active).phase)
                assertEquals(access.id, record.state.ledger.debits.last().id)
                open = true
                return step("open")
            }
            override fun protectedStateCheck(): Boolean {
                if (open) error("protected-state repair must follow revoke")
                return step("protected")
            }
            override fun readBackPolicy(): Boolean = step("readback")
        }
        fun dispatcher() = PlaySessionDispatcher(store, vault, clock, { policy },
            { BatteryState(50, false) }, effects, AppApprovalIdSource { "play-${++sequence}" })
        var dispatcher = dispatcher()
        init {
            val self = TrustedPersonId("self")
            if (fullProfile) assertTrue(store.initialize(CredentialState(Mode.SELF_MANAGED,
                mapOf(self to vault.prepareCredential(pin()).credential),
                recoveryProfile = RecoveryProfile(mapOf(self to "synthetic-account"), null,
                    FrictionSettings(600_000, 2))), WaitingState(), ElevationRecord()))
            else assertTrue(menu.enroll(pin()) is PinEnrollmentResult.Created)
            assertTrue(dispatcher.onStart())
            events.clear()
        }
        fun start(): String {
            assertEquals(PlayRequestResult.Counting, dispatcher.request(pin()))
            val id = store.elevationStore.read()!!.state.countdown!!.id
            now += 600_000
            assertEquals(ElevationOutcome.READY, dispatcher.tick())
            assertEquals(ElevationOutcome.STARTED, dispatcher.start(id))
            return id
        }
    }

    @Test fun freshPinStartsCountdownAndAtomicDebitPrecedesOpenReadback() {
        val r = Rig()
        assertEquals(PinAccessResult.Open, r.menu.authenticate(pin()))
        assertEquals(ElevationOutcome.REFUSED, r.dispatcher.start("no-countdown"))
        val secret = pin()
        assertEquals(PlayRequestResult.Counting, r.dispatcher.request(secret))
        assertTrue(secret.all { it == '\u0000' })
        assertFalse(r.open)
        val id = r.store.elevationStore.read()!!.state.countdown!!.id
        assertEquals(ElevationOutcome.REFUSED, r.dispatcher.start(id))
        r.now = 600_000
        assertEquals(ElevationOutcome.STARTED, r.dispatcher.start(id))
        assertEquals(listOf("open", "readback"), r.events)
        assertEquals(setOf(play), r.dispatcher.access()!!.packages)
        assertEquals(SessionPhase.OPEN, (r.store.elevationStore.read()!!.journal as SessionJournal.Active).phase)
    }

    @Test fun everyEndTriggerRevokesChecksReadsBackAndClearsSharedJournal() {
        for (trigger in SessionEnd.entries) {
            val r = Rig(); val id = r.start(); r.events.clear(); r.journalAtEffect.clear()
            assertTrue(r.dispatcher.end(id, trigger) in setOf(ElevationOutcome.ENDED, ElevationOutcome.ACCESS_CLOSED))
            assertEquals(listOf("revoke", "protected", "readback"), r.events)
            assertTrue(r.journalAtEffect.all { (it as? SessionJournal.Active)?.phase == SessionPhase.CLOSING })
            assertEquals(SessionJournal.Empty, r.store.elevationStore.read()!!.journal)
            assertNull(r.dispatcher.access()); assertFalse(r.open)
            assertFalse(r.dispatcher.installed(id, candidate))
        }
    }

    @Test fun failedReadbackRetainsJournalAndRetryNeverReopens() {
        for (failure in listOf("revoke", "protected", "readback")) {
            val r = Rig(); val id = r.start(); r.events.clear(); r.fail = failure
            assertEquals(ElevationOutcome.NOT_READY, r.dispatcher.end(id, SessionEnd.DONE))
            assertEquals(1, r.faultHits)
            assertFalse(r.dispatcher.handbackReady)
            assertTrue(r.store.elevationStore.read()!!.journal is SessionJournal.Active)
            assertNull(r.dispatcher.access())
            r.fail = null; r.events.clear()
            assertEquals(ElevationOutcome.ENDED, r.dispatcher.tick())
            assertEquals(listOf("revoke", "protected", "readback"), r.events)
            assertTrue(r.dispatcher.handbackReady)
            assertEquals(SessionJournal.Empty, r.store.elevationStore.read()!!.journal)
        }
    }

    @Test fun sharedSessionControllerCanRevokeElevationAndRestartRetainsDebit() {
        val r = Rig(); r.start(); r.events.clear()
        assertTrue(r.dispatcher.sessions.onStart())
        assertEquals(listOf("revoke", "protected", "readback"), r.events)
        assertEquals(SessionJournal.Empty, JointAuthorityStore(r.dir).elevationStore.read()!!.journal)
        assertNull(r.dispatcher.access())
        r.dispatcher = r.dispatcher(); r.events.clear()
        assertTrue(r.dispatcher.onStart())
        assertEquals("revoke", r.events.first())
        assertEquals(1, r.store.elevationStore.read()!!.state.ledger.debits.size)
        assertNull(r.store.elevationStore.read()!!.state.grant)
    }

    @Test fun busyDailyLimitAndFiveMinuteStartLapseUseEngine() {
        val r = Rig()
        assertEquals(PlayRequestResult.Counting, r.dispatcher.request(pin()))
        assertEquals(PlayRequestResult.Busy, r.dispatcher.request(pin()))
        r.now = 900_000
        assertEquals(ElevationOutcome.LAPSED, r.dispatcher.tick())
        assertTrue(r.store.elevationStore.read()!!.state.ledger.debits.isEmpty())
        repeat(2) { val id = r.start(); r.dispatcher.end(id, SessionEnd.DONE) }
        assertEquals(PlayRequestResult.LimitReached, r.dispatcher.request(pin()))
        assertEquals(2, r.store.elevationStore.read()!!.state.ledger.debits.size)
    }

    @Test fun deadlineClosesAndCandidateEventsCannotArriveAfterEnd() {
        val r = Rig(); val id = r.start()
        assertTrue(r.dispatcher.installed(id, candidate))
        assertFalse(r.dispatcher.installed("stale", candidate))
        r.now += 600_000; r.events.clear()
        assertEquals(ElevationOutcome.LAPSED, r.dispatcher.tick())
        assertEquals(listOf("revoke", "protected", "readback"), r.events)
        assertNull(r.dispatcher.access())
        assertFalse(r.dispatcher.installed(id, candidate))
    }

    @Test fun activeSessionReportsBusyInsteadOfOfferingAnotherOpening() {
        val r = Rig(); r.start()
        assertEquals(PlayRequestResult.Busy, r.dispatcher.request(pin()))
        assertEquals(1, r.store.elevationStore.read()!!.state.ledger.debits.size)
    }

    @Test fun previewEnrollmentUsesStandardLimitsWithoutInventingRecoveryIdentity() {
        val r = Rig(fullProfile = false)
        assertEquals(PlayRequestResult.Counting, r.dispatcher.request(pin()))
        assertNull(r.store.credentials.read()!!.recoveryProfile)
        assertEquals(600_000L, r.store.elevationStore.read()!!.state.countdown!!.durationMillis)
        assertEquals(ElevationLimits(), r.store.activeLimits())
        assertTrue(r.store.elevationStore.read()!!.state.ledger.debits.isEmpty())
    }

    @Test fun wrongPinNeverCreatesCountdownAndIsWiped() {
        val r = Rig(); val secret = CharArray(6) { '2' }
        assertTrue(r.dispatcher.request(secret) is PlayRequestResult.Wrong)
        assertTrue(secret.all { it == '\u0000' })
        assertNull(r.store.elevationStore.read()!!.state.countdown)
    }

    @Test fun playWithoutInventoryStillRequiresCountdownAndFreshPin() {
        val r = Rig(); r.policy = r.policy!!.copy(inventory = null)
        assertEquals(PlayRequestResult.Counting, r.dispatcher.request(pin()))
        assertNotNull(r.store.elevationStore.read()!!.state.countdown)
        assertTrue(r.store.elevationStore.read()!!.state.ledger.debits.isEmpty())
        assertFalse(r.open)
    }
}
