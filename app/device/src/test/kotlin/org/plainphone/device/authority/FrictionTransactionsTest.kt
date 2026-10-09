// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.BootTime
import org.plainphone.core.ClockReading
import org.plainphone.core.Mode
import org.plainphone.core.RunningClock
import org.plainphone.core.RunningTime
import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.AppApprovalIdSource
import org.plainphone.policy.BatteryState
import org.plainphone.policy.CancelIntent
import org.plainphone.policy.CancelIntentStore
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationDebit
import org.plainphone.policy.ElevationLedger
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.ElevationState
import org.plainphone.policy.FrictionSettings
import org.plainphone.policy.FrictionSettingsController
import org.plainphone.policy.FrictionSettingsOutcome
import org.plainphone.policy.FrictionWaitingController
import org.plainphone.policy.FrictionWaitingOutcome
import org.plainphone.policy.KeyDerivation
import org.plainphone.policy.PersonContact
import org.plainphone.policy.PinResult
import org.plainphone.policy.PinVault
import org.plainphone.policy.RecoveryProfile
import org.plainphone.policy.SecretEntropy
import org.plainphone.policy.WaitingChangeController
import org.plainphone.policy.WaitingEvent
import org.plainphone.policy.WaitingIdentity
import org.plainphone.policy.WaitingIdentitySource
import org.plainphone.policy.WaitingOutcome
import org.plainphone.policy.WaitingPhase
import org.plainphone.policy.WaitingSkipCommitter
import org.plainphone.policy.WaitingSkipSupport
import org.plainphone.policy.WaitingStart
import org.plainphone.policy.WaitingState

/** Real policy controllers over the real file-backed joint store; every check also reopens from disk. */
class FrictionTransactionsTest {
    @get:Rule val tmp = TemporaryFolder()
    private val day = 86_400_000L
    private val base = FrictionSettings(1_200_000, 1)
    private val looser = FrictionSettings(600_000, 2)

    private inner class Rig(val dir: File = tmp.newFolder()) {
        val self = TrustedPersonId("self")
        val pin get() = CharArray(6) { '1' }
        var now: ClockReading? = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
        var onCheckpoint: (() -> Unit)? = null
        val clock = RunningClock { onCheckpoint?.invoke(); now }
        var faults = StorageFaults { _, _ -> }
        var sequence = 0
        val intents = mutableMapOf<String, CancelIntent>()
        val cancelStore = object : CancelIntentStore {
            override fun initialize(requestId: String): Boolean {
                if (intents[requestId] == CancelIntent.REQUESTED) return false
                intents[requestId] = CancelIntent.CLEAR; return true
            }
            override fun request(requestId: String): Boolean { intents[requestId] = CancelIntent.REQUESTED; return true }
            override fun read(requestId: String) = intents[requestId] ?: CancelIntent.UNKNOWN
        }
        lateinit var store: JointAuthorityStore
        lateinit var vault: PinVault
        lateinit var waiting: WaitingChangeController
        val debits = listOf(ElevationDebit("turn-old-epoch", RunningTime("older", 5)), ElevationDebit("turn-1", RunningTime("epoch", 0)))

        init {
            wire()
            val prepared = vault.prepareCredential(pin)
            val credentials = CredentialState(Mode.SELF_MANAGED,
                mapOf(self to prepared.credential.copy(contact = PersonContact("synthetic-self", "synthetic-contact"))),
                recoveryProfile = RecoveryProfile(mapOf(self to "synthetic-account"), "synthetic-backup", base))
            assertTrue(store.initialize(credentials, WaitingState(storageGeneration = "live"),
                ElevationRecord(3, ElevationState(ElevationLedger(debits)))))
        }

        /** Restart: a new store, vault and controllers over the same directory; nothing process-local survives. */
        fun wire() {
            store = JointAuthorityStore(dir) { point, d -> faults.at(point, d) }
            vault = PinVault(store.credentials, KeyDerivation { chars, salt, _ ->
                MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
            }, SecretEntropy { n -> ByteArray(n) { (++sequence).toByte() } }, clock)
            waiting = WaitingChangeController(store.waitingStore, { store.credentials.read() }, clock, vault,
                WaitingIdentitySource { WaitingIdentity("wait-${++sequence}", "synthetic-cancel-$sequence") }, cancelStore,
                WaitingSkipSupport(vault, WaitingSkipCommitter { _, _, _, _ -> error("self-managed recovery never skips") }))
        }
        fun disk() = JointAuthorityStore(dir)
        fun friction() = disk().credentials.read()!!.recoveryProfile!!.friction
        fun executor() = FrictionWaitingController(store, store.credentials, clock)
        fun editor() = FrictionSettingsController(store, store.credentials, vault, clock, AppApprovalIdSource { "edit-${++sequence}" })
        fun auth(r: WaitingStart) = (vault.authenticate(self, pin, r.action) as PinResult.Authorized).authority
        fun start(target: FrictionSettings = looser): String {
            val r = checkNotNull(waiting.prepareFriction(self, target))
            assertEquals(WaitingOutcome.PENDING, waiting.startFrictionChange(r, auth(r), true))
            return r.requestId
        }
        fun at(total: Long, epoch: String = "epoch", boot: String = "boot") { now = ClockReading(RunningTime(epoch, total), BootTime(boot, total)) }
        fun mature(id: String) { at(day); assertEquals(WaitingOutcome.COMMITTING, waiting.tick(id, BatteryState(1, false))) }
        fun stricter(target: FrictionSettings = FrictionSettings(1_800_000, 0)): FrictionSettingsOutcome {
            val editor = editor(); val r = checkNotNull(editor.prepareStricter(self, target))
            val fresh = (vault.authenticate(self, pin, r.action) as PinResult.Authorized).authority
            return editor.applyStricter(r, fresh)
        }
        fun elevationBytes() = disk().sectionBytesForTest(Section.ELEVATION)
    }

    @Test fun stricterEditIsDurableWithRoutineReceiptAndUntouchedLedger() {
        val rig = Rig(); val ledger = rig.elevationBytes()
        assertEquals(FrictionSettingsOutcome.APPLIED, rig.stricter())
        assertEquals(FrictionSettings(1_800_000, 0), rig.friction())
        val receipt = rig.disk().frictionReceipts()!!.single()
        assertEquals(base, receipt.source); assertEquals(FrictionSettings(1_800_000, 0), receipt.target)
        assertEquals(mapOf(rig.self to 0L), receipt.recipientGenerations)
        assertEquals("synthetic-contact", receipt.recipientContacts.getValue(rig.self)!!.number)
        val elevation = rig.disk().elevationStore.read()!!
        assertEquals(4L, elevation.revision)
        assertEquals(rig.debits, elevation.state.ledger.debits)
        assertTrue(!ledger.contentEquals(rig.elevationBytes()))
    }

    @Test fun looserChangeWaitsThenCompletesAtomicallyAndPreservesTheWholeElevationRecord() {
        for (target in listOf(FrictionSettings(600_000, 1), FrictionSettings(1_200_000, 3), FrictionSettings(600_000, 3))) {
            val rig = Rig(); val id = rig.start(target); val ledger = rig.elevationBytes()
            assertEquals(base, rig.friction())
            rig.wire(); rig.at(day - 1)
            assertEquals(WaitingOutcome.PENDING, rig.waiting.tick(id, BatteryState(50, false)))
            assertEquals(FrictionWaitingOutcome.IGNORED, rig.executor().finish(id))
            rig.mature(id)
            assertEquals(base, rig.friction())
            rig.wire()
            assertEquals(FrictionWaitingOutcome.COMPLETED, rig.executor().finish(id))
            val disk = rig.disk()
            assertEquals(target, disk.credentials.read()!!.recoveryProfile!!.friction)
            val waiting = disk.waitingStore.read()!!
            assertEquals(WaitingPhase.COMPLETED, waiting.operation!!.phase)
            assertEquals(WaitingEvent.COMPLETED, waiting.texts.last().event)
            assertArrayEquals(ledger, rig.elevationBytes())
            // Replay after completion is a no-op and never restores an older target.
            assertEquals(FrictionWaitingOutcome.COMPLETED, rig.executor().finish(id))
        }
    }

    @Test fun stricterEditWhileLooserIsPendingCancelsItInTheSameTransaction() {
        val rig = Rig(); val id = rig.start()
        assertEquals(FrictionSettingsOutcome.APPLIED, rig.stricter(FrictionSettings(1_800_000, 1)))
        val disk = rig.disk()
        assertEquals(FrictionSettings(1_800_000, 1), disk.credentials.read()!!.recoveryProfile!!.friction)
        val waiting = disk.waitingStore.read()!!
        assertEquals(WaitingPhase.CANCELLED, waiting.operation!!.phase)
        assertEquals(WaitingEvent.DEPENDENCY_CHANGED, waiting.texts.last().event)
        assertEquals(1, disk.frictionReceipts()!!.size)
        rig.mature2(id)
    }

    private fun Rig.mature2(id: String) {
        at(day)
        assertTrue(waiting.tick(id, BatteryState(1, false)) != WaitingOutcome.COMMITTING)
        assertEquals(FrictionWaitingOutcome.CANCELLED, executor().finish(id))
        assertEquals(FrictionSettings(1_800_000, 1), friction())
    }

    @Test fun noNewSettingWhileALooserChangeIsCommitting() {
        val rig = Rig(); val id = rig.start(); rig.mature(id)
        assertEquals(FrictionSettingsOutcome.NOT_READY, rig.stricter())
        assertEquals(base, rig.friction())
        assertEquals(FrictionWaitingOutcome.COMPLETED, rig.executor().finish(id))
    }

    @Test fun crashAroundEachFrictionTransactionExposesOldOrCompleteNew() {
        for (point in WritePoint.entries) {
            // Completion
            Rig().run {
                val id = start(); mature(id); val ledger = elevationBytes()
                faults = StorageFaults { at, _ -> if (at == point) throw SimulatedCrash() }
                try { executor().finish(id) } catch (_: SimulatedCrash) { }
                faults = StorageFaults { _, _ -> }
                val disk = disk(); val phase = disk.waitingStore.read()!!.operation!!.phase
                val active = disk.credentials.read()!!.recoveryProfile!!.friction
                assertTrue("$point", (phase == WaitingPhase.COMMITTING && active == base) || (phase == WaitingPhase.COMPLETED && active == looser))
                assertArrayEquals(ledger, elevationBytes())
                wire(); assertEquals("$point", FrictionWaitingOutcome.COMPLETED, executor().finish(id))
                assertEquals(looser, friction())
            }
            // Stricter edit with a pending looser change
            Rig().run {
                start()
                faults = StorageFaults { at, _ -> if (at == point) throw SimulatedCrash() }
                try { stricter() } catch (_: SimulatedCrash) { }
                faults = StorageFaults { _, _ -> }
                val disk = disk(); val phase = disk.waitingStore.read()!!.operation!!.phase
                val active = disk.credentials.read()!!.recoveryProfile!!.friction
                val receipts = disk.frictionReceipts()!!.size
                assertTrue("$point", (phase == WaitingPhase.PENDING && active == base && receipts == 0) ||
                    (phase == WaitingPhase.CANCELLED && active == FrictionSettings(1_800_000, 0) && receipts == 1))
            }
        }
    }

    @Test fun readbackFailureDuringCompletionReportsNotReadyAndChangesNothing() {
        val rig = Rig(); val id = rig.start(); rig.mature(id)
        rig.faults = StorageFaults { at, d -> if (at == WritePoint.AFTER_COPY_A) File(d, "authority-a.rec").writeBytes(ByteArray(3)) }
        assertEquals(FrictionWaitingOutcome.NOT_READY, rig.executor().finish(id))
        rig.faults = StorageFaults { _, _ -> }
        assertEquals(base, rig.friction())
        assertEquals(WaitingPhase.COMMITTING, rig.disk().waitingStore.read()!!.operation!!.phase)
        rig.wire(); assertEquals(FrictionWaitingOutcome.COMPLETED, rig.executor().finish(id))
    }

    @Test fun credentialChangeBetweenReadAndJointWriteLosesTheRace() {
        val rig = Rig(); val id = rig.start(); rig.mature(id)
        rig.onCheckpoint = {
            rig.onCheckpoint = null
            // The executor already read credentials; a failed PIN attempt is recorded before its joint write.
            val c = rig.store.credentials.read()!!
            val person = c.people.getValue(rig.self)
            assertTrue(rig.store.credentials.compareAndSet(c,
                c.replacing(rig.self, person.copy(pinCounter = person.pinCounter.failed(RunningTime("epoch", day))))))
        }
        assertEquals(FrictionWaitingOutcome.NOT_READY, rig.executor().finish(id))
        assertEquals(base, rig.friction())
        assertEquals(WaitingPhase.COMMITTING, rig.disk().waitingStore.read()!!.operation!!.phase)
        assertEquals(FrictionWaitingOutcome.COMPLETED, rig.executor().finish(id))
        assertEquals(looser, rig.friction())
        assertEquals(1, rig.disk().credentials.read()!!.people.getValue(rig.self).pinCounter.failures)
    }

    @Test fun completionAfterRestartAndClockEpochChangeUsesStoredPatchOnly() {
        val rig = Rig(); val id = rig.start(); rig.mature(id)
        rig.wire(); rig.at(5, epoch = "after-reboot", boot = "boot-2")
        assertEquals(FrictionWaitingOutcome.COMPLETED, rig.executor().finish(id))
        assertEquals(looser, rig.friction())
        assertEquals(rig.debits, rig.disk().elevationStore.read()!!.state.ledger.debits)
        assertEquals(FrictionSettingsOutcome.APPLIED, rig.stricter(FrictionSettings(1_800_000, 0)))
        rig.wire(); assertEquals(FrictionWaitingOutcome.COMPLETED, rig.executor().finish(id))
        assertEquals(FrictionSettings(1_800_000, 0), rig.friction())
    }
}
