// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.BootTime
import org.plainphone.core.Mode
import org.plainphone.core.RunningTime
import org.plainphone.core.TrustedPersonId
import org.plainphone.device.runtime.FileClockStateStore
import org.plainphone.policy.BatteryState
import org.plainphone.policy.CancelIntent
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.PersonAuthorityConsumer
import org.plainphone.policy.PersonContact
import org.plainphone.policy.PersonCredential
import org.plainphone.policy.ProvenRunningClock
import org.plainphone.policy.SecretVerifier
import org.plainphone.policy.WaitingChangeKind
import org.plainphone.policy.WaitingIdentity
import org.plainphone.policy.WaitingOperation
import org.plainphone.policy.WaitingOutcome
import org.plainphone.policy.WaitingPhase
import org.plainphone.policy.WaitingState

/**
 * The waiting checkpoints on the real joint store and the real file clock: what every policy pass and
 * the checkpoint alarm do with a pending wait, and the PIN-free cancel. JVM evidence only.
 */
class WaitingCheckpointsTest {
    @get:Rule val tmp = TemporaryFolder()
    private val anna = TrustedPersonId("anna")
    private val ben = TrustedPersonId("ben")
    private val day = 86_400_000L

    private fun person(seed: Int, generation: Long = seed.toLong()) = PersonCredential(
        SecretVerifier(ByteArray(16) { seed.toByte() }, ByteArray(32) { seed.toByte() }, 600_000),
        SecretVerifier(ByteArray(16) { (seed + 8).toByte() }, ByteArray(32) { (seed + 8).toByte() }, 600_000),
        generation = generation, contact = PersonContact("synthetic-$seed", "synthetic-number-$seed"))
    private fun credentials(annaGeneration: Long = 1, benGeneration: Long = 2) = CredentialState(Mode.TRUSTED,
        mapOf(anna to person(1, annaGeneration), ben to person(2, benGeneration)), 0)

    private inner class Rig(fresh: Boolean = true) {
        val dir: File = tmp.newFolder()
        val store = JointAuthorityStore(File(dir, "authority"))
        var boot = BootTime("boot-1", 1_000)
        var battery: BatteryState? = BatteryState(80, false)
        val clock = ProvenRunningClock(FileClockStateStore(File(dir, "clock")), { boot }, { store.reserveEpoch() })
        val checkpoints = WaitingCheckpoints(store, clock, { battery }, PersonAuthorityConsumer { _, _, _ -> false })
        init { if (fresh) assertTrue(store.initialize(credentials(), WaitingState(storageGeneration = "live"), ElevationRecord())) }

        /** A pending turn-off by anna, started now on the proven clock, with a CLEAR cancel intent. */
        fun startTurnOff(requestId: String = "wait-1"): WaitingOperation {
            val now = clock.checkpoint()!!.proven
            val op = WaitingOperation(WaitingIdentity(requestId, "synthetic-cancel"), anna, 1, 0, Mode.TRUSTED,
                now, WaitingPhase.PENDING, mapOf(anna to 1L, ben to 2L), recipientContacts = mapOf(anna to null, ben to null))
            assertTrue(store.cancelIntents.initialize(requestId))
            assertTrue(store.waitingStore.compareAndSet(store.waitingStore.read()!!, WaitingState(1, op, emptyList(), "live")))
            return op
        }
        fun advance(millis: Long) { boot = boot.copy(elapsedMillis = boot.elapsedMillis + millis) }
        fun phase() = store.waitingStore.read()?.operation?.phase
    }

    @Test fun testBuildMaturityClosesWithoutCommittingAndSurvivesRestart() {
        val rig = Rig()
        rig.startTurnOff()
        val preview = WaitingCheckpoints(rig.store, rig.clock, { rig.battery },
            PersonAuthorityConsumer { _, _, _ -> false }, testBuild = true)
        rig.advance(day - 1)
        assertEquals(WaitingOutcome.PENDING, preview.checkpoint())
        rig.advance(1)
        assertEquals(WaitingOutcome.CANCELLED, preview.checkpoint())
        assertEquals(WaitingPhase.CANCELLED, rig.phase())
        assertEquals(0, rig.store.waitingStore.read()!!.operation!!.restoredSteps)
        assertFalse(rig.store.waitingStore.read()!!.texts.any { it.event == org.plainphone.policy.WaitingEvent.COMMITTING })
        assertNull(preview.notice())
        assertTrue(preview.previewTurnOffClosed())
        val restarted = WaitingCheckpoints(rig.store, rig.clock, { rig.battery },
            PersonAuthorityConsumer { _, _, _ -> false }, testBuild = true)
        assertNull(restarted.checkpoint())
        assertTrue(restarted.previewTurnOffClosed())
        assertFalse(rig.checkpoints.previewTurnOffClosed())
    }

    private fun files(dir: File) = dir.walk().filter { it.isFile }.map { it.relativeTo(dir).path }.toList()

    /** A phone without trusted people: nothing pending, the normal alarm period, nothing written. */
    @Test fun aFreshPhoneHasNoCheckpointAndWritesNothing() {
        val rig = Rig(fresh = false)
        assertNull(rig.checkpoints.pendingRequest())
        assertNull(rig.checkpoints.checkpoint())
        assertEquals(15 * 60_000L, rig.checkpoints.periodMillis(15 * 60_000L))
        assertTrue("nothing written: ${files(rig.dir)}", files(rig.dir).isEmpty())
    }

    /** A vacant slot: no checkpoint, normal period. */
    @Test fun aVacantSlotHasNoCheckpoint() {
        val rig = Rig()
        assertNull(rig.checkpoints.checkpoint())
        assertEquals(15 * 60_000L, rig.checkpoints.periodMillis(15 * 60_000L))
    }

    /** While a wait is pending, every checkpoint advances the proven clock and the alarm runs every 5 minutes. */
    @Test fun aPendingWaitIsCheckpointedEveryFiveMinutes() {
        val rig = Rig()
        val op = rig.startTurnOff()
        assertEquals("wait-1", rig.checkpoints.pendingRequest())
        assertEquals(WaitingCheckpoints.PENDING_PERIOD_MILLIS, rig.checkpoints.periodMillis(15 * 60_000L))
        assertEquals(5 * 60_000L, WaitingCheckpoints.PENDING_PERIOD_MILLIS)
        rig.advance(5 * 60_000L)
        assertEquals(WaitingOutcome.PENDING, rig.checkpoints.checkpoint())
        assertEquals(WaitingPhase.PENDING, rig.phase())
        val proven = rig.clock.checkpoint()!!.proven
        assertEquals(op.startedAt.epoch, proven.epoch)
        assertTrue("clock advanced: $proven", proven.since(op.startedAt) >= 5 * 60_000L)
    }

    /** After 24 proven hours the checkpoint moves the turn-off to COMMITTING, which the next plan reads. */
    @Test fun aMaturedTurnOffCommitsAtTheCheckpoint() {
        val rig = Rig()
        rig.startTurnOff()
        rig.advance(day - 1)
        assertEquals(WaitingOutcome.PENDING, rig.checkpoints.checkpoint())
        rig.advance(1)
        assertEquals(WaitingOutcome.COMMITTING, rig.checkpoints.checkpoint())
        assertEquals(WaitingPhase.COMMITTING, rig.phase())
        assertEquals(WaitingChangeKind.TURN_OFF, rig.store.waitingStore.read()?.operation?.kind)
        assertNull("committing is no longer pending", rig.checkpoints.pendingRequest())
        assertNull(rig.checkpoints.checkpoint())
    }

    /** Time on another boot counts only from that boot's start, so a reboot never matures a wait early. */
    @Test fun aRebootCreditsOnlyTimeSinceBoot() {
        val rig = Rig()
        rig.startTurnOff()
        rig.advance(day / 2)
        assertEquals(WaitingOutcome.PENDING, rig.checkpoints.checkpoint())
        rig.boot = BootTime("boot-2", day / 2 - 1)
        assertEquals(WaitingOutcome.PENDING, rig.checkpoints.checkpoint())
        rig.advance(1)
        assertEquals(WaitingOutcome.COMMITTING, rig.checkpoints.checkpoint())
    }

    /** Unknown battery is treated as low: a matured turn-off waits, and commits once the battery reads. */
    @Test fun anUnknownBatteryNeverCommitsATurnOff() {
        val rig = Rig()
        rig.startTurnOff()
        rig.advance(day)
        rig.battery = null
        assertEquals(WaitingOutcome.LOW_BATTERY, rig.checkpoints.checkpoint())
        rig.battery = BatteryState(3, false)
        assertEquals(WaitingOutcome.LOW_BATTERY, rig.checkpoints.checkpoint())
        assertEquals(WaitingPhase.PENDING, rig.phase())
        rig.battery = BatteryState(3, true)
        assertEquals(WaitingOutcome.COMMITTING, rig.checkpoints.checkpoint())
    }

    /** No proven time: nothing matures. */
    @Test fun noBootTimeNeverMatures() {
        val rig = Rig()
        rig.startTurnOff()
        rig.advance(day * 2)
        var boot: BootTime? = rig.boot
        val blind = WaitingCheckpoints(rig.store,
            ProvenRunningClock(FileClockStateStore(File(rig.dir, "clock")), { boot }, { rig.store.reserveEpoch() }),
            { rig.battery }, PersonAuthorityConsumer { _, _, _ -> false })
        boot = null
        assertEquals(WaitingOutcome.NOT_READY, blind.checkpoint())
        assertEquals(WaitingPhase.PENDING, rig.phase())
    }

    /** The phone's own cancel needs no PIN; the intent is recorded first and the wait ends. */
    @Test fun cancelNeedsNoPin() {
        val rig = Rig()
        rig.startTurnOff()
        assertEquals(WaitingOutcome.CANCELLED, rig.checkpoints.cancel("wait-1"))
        assertEquals(CancelIntent.REQUESTED, rig.store.cancelIntents.read("wait-1"))
        assertFalse(rig.phase() == WaitingPhase.PENDING)
        assertNull(rig.checkpoints.pendingRequest())
        rig.advance(day)
        assertNull("a cancelled wait never commits", rig.checkpoints.checkpoint())
    }

    /** A cancel for another request changes nothing. */
    @Test fun aStaleCancelIsIgnored() {
        val rig = Rig()
        rig.startTurnOff()
        assertEquals(WaitingOutcome.IGNORED, rig.checkpoints.cancel("wait-0"))
        assertEquals(WaitingPhase.PENDING, rig.phase())
    }

    /** A cancel recorded on disk wins at maturity, even through a new checkpoint instance (a new process). */
    @Test fun aRecordedCancelWinsAtMaturityAfterRestart() {
        val rig = Rig()
        rig.startTurnOff()
        assertTrue(rig.store.cancelIntents.request("wait-1"))
        rig.advance(day)
        val restarted = WaitingCheckpoints(rig.store, rig.clock, { rig.battery }, PersonAuthorityConsumer { _, _, _ -> false })
        assertEquals(WaitingOutcome.CANCELLED, restarted.checkpoint())
        assertFalse(rig.phase() == WaitingPhase.COMMITTING)
    }

    /** A turn-off depends on its starter only: a recipient's credential change keeps it pending. */
    @Test fun aRecipientChangeKeepsTheWait() {
        val rig = Rig()
        rig.startTurnOff()
        assertTrue(rig.store.credentials.compareAndSet(rig.store.credentials.read()!!, credentials(benGeneration = 3)))
        assertEquals(WaitingOutcome.PENDING, rig.checkpoints.checkpoint())
    }

    /** A change to the starter's credential cancels the wait at the next checkpoint (the after-change check). */
    @Test fun aDependencyChangeCancelsAtTheNextCheckpoint() {
        val rig = Rig()
        rig.startTurnOff()
        assertTrue(rig.store.credentials.compareAndSet(rig.store.credentials.read()!!, credentials(annaGeneration = 3)))
        assertEquals(WaitingOutcome.CANCELLED, rig.checkpoints.checkpoint())
        assertNull(rig.checkpoints.pendingRequest())
    }

    /** An unreadable waiting record is not pending: no checkpoint and no commit (reconciliation owns it). */
    @Test fun anUnreadableRecordIsNotTicked() {
        val rig = Rig()
        rig.startTurnOff()
        assertTrue(rig.store.replaceSectionForTest(Section.WAITING, byteArrayOf(7, 7, 7)))
        rig.advance(day)
        assertNull(rig.checkpoints.pendingRequest())
        assertNull(rig.checkpoints.checkpoint())
    }

    /**
     * One instance per process keeps the controller's cancel latch: a cancel accepted while the record
     * did not read still wins after the record returns, even if both intent files are later lost.
     */
    @Test fun theProcessCancelLatchSurvivesALostIntent() {
        val rig = Rig()
        rig.startTurnOff()
        val waiting = rig.store.sectionBytesForTest(Section.WAITING)!!
        assertTrue(rig.store.replaceSectionForTest(Section.WAITING, byteArrayOf(7, 7, 7)))
        assertEquals(WaitingOutcome.NOT_READY, rig.checkpoints.cancel("wait-1"))
        assertTrue(rig.store.replaceSectionForTest(Section.WAITING, waiting))
        val hash = intentHash("wait-1")
        val lost = File(rig.dir, "authority").listFiles()!!.filter { hash in it.name }
        assertEquals("entry and marker: ${lost.map { it.name }}", 2, lost.size)
        lost.forEach { assertTrue(it.delete()) }
        assertEquals(CancelIntent.UNKNOWN, rig.store.cancelIntents.read("wait-1"))
        rig.advance(day)
        assertEquals(WaitingOutcome.CANCELLED, rig.checkpoints.checkpoint())
        assertFalse(rig.phase() == WaitingPhase.COMMITTING)
    }

    // Wiring 3: the notice projection for Home (policy NoticeLine input).
    @Test fun noNoticeWithoutAWait() {
        assertNull(Rig(fresh = false).checkpoints.notice())
        assertNull(Rig().checkpoints.notice())
    }

    /** A pending wait's notice carries the proven time still to run; a reading never shortens it. */
    @Test fun aPendingNoticeCarriesTheRemainingProvenTime() {
        val rig = Rig()
        rig.startTurnOff()
        rig.advance(3_600_000L)
        val notice = rig.checkpoints.notice()!!
        assertEquals("wait-1", notice.requestId)
        assertEquals(WaitingChangeKind.TURN_OFF, notice.kind)
        assertEquals(WaitingPhase.PENDING, notice.phase)
        assertEquals(day - 3_600_000L, notice.remainingMillis)
    }

    /** Without a clock reading the whole 24 hours remain, so Home never names an earlier time. */
    @Test fun anUnreadableClockShowsTheFullWait() {
        val rig = Rig()
        rig.startTurnOff()
        rig.advance(day / 2)
        var boot: org.plainphone.core.BootTime? = null
        val blind = WaitingCheckpoints(rig.store,
            ProvenRunningClock(FileClockStateStore(File(rig.dir, "clock")), { boot }, { rig.store.reserveEpoch() }),
            { rig.battery }, PersonAuthorityConsumer { _, _, _ -> false })
        assertEquals(day, blind.notice()!!.remainingMillis)
    }

    @Test fun committingAndCancelledNotices() {
        val rig = Rig()
        rig.startTurnOff()
        rig.advance(day)
        assertEquals(WaitingOutcome.COMMITTING, rig.checkpoints.checkpoint())
        val committing = rig.checkpoints.notice()!!
        assertEquals(WaitingPhase.COMMITTING, committing.phase)
        assertEquals(0L, committing.remainingMillis)
        val other = Rig()
        other.startTurnOff()
        assertEquals(WaitingOutcome.CANCELLED, other.checkpoints.cancel("wait-1"))
        assertNull(other.checkpoints.notice())
    }
}
