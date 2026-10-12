// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.plainphone.core.Mode
import org.plainphone.policy.NoticeInput
import org.plainphone.policy.NoticeLine
import org.plainphone.policy.WaitingChangeKind
import org.plainphone.policy.WaitingNotice
import org.plainphone.policy.WaitingOutcome
import org.plainphone.policy.WaitingPhase
import org.plainphone.ui.home.HomeNotice
import org.plainphone.ui.home.HomeNoticeDay
import org.plainphone.ui.trusted.WaitingStage

/** The Home notice line for a pending wait, and what the PIN-free cancel screen shows after the write. */
class WaitingHomeLineTest {
    private val now = ZonedDateTime.of(2026, 10, 11, 21, 30, 0, 0, ZoneOffset.UTC)
    private val time = DateTimeFormatter.ofPattern("HH:mm")
    private fun line(attention: Boolean, kind: WaitingChangeKind?, phase: WaitingPhase = WaitingPhase.PENDING, remaining: Long = 0) =
        homeLine(NoticeLine.decide(NoticeInput(Mode.TRUSTED, attention,
            kind?.let { WaitingNotice("wait-1", it, phase, remaining) })).line, now, time)

    @Test fun attentionOutranksTheWaitingLine() {
        assertEquals(HomeNotice.NeedsAttention, line(true, WaitingChangeKind.TURN_OFF))
    }

    @Test fun noWaitNoLine() {
        assertNull(line(false, null))
    }

    /** "will turn off after {time}": now plus the proven time still to run, with its day. */
    @Test fun aTurnOffNamesTheEarliestTime() {
        assertEquals(HomeNotice.TurnOffWaiting("21:30", HomeNoticeDay(LocalDate.of(2026, 10, 12), 1)),
            line(false, WaitingChangeKind.TURN_OFF, remaining = 86_400_000L))
        assertEquals(HomeNotice.TurnOffWaiting("23:30", HomeNoticeDay(LocalDate.of(2026, 10, 11), 0)),
            line(false, WaitingChangeKind.TURN_OFF, remaining = 7_200_000L))
        assertEquals(HomeNotice.TurnOffWaiting("00:30", HomeNoticeDay(LocalDate.of(2026, 10, 12), 1)),
            line(false, WaitingChangeKind.TURN_OFF, remaining = 10_800_000L))
    }

    @Test fun peopleAndSetupLines() {
        for (k in listOf(WaitingChangeKind.ADD_PERSON, WaitingChangeKind.HAND_OVER, WaitingChangeKind.REMOVE_PERSON))
            assertEquals(HomeNotice.PeopleChangeWaiting, line(false, k))
        for (k in listOf(WaitingChangeKind.SWITCH_MODE, WaitingChangeKind.SHORTEN_COUNTDOWN, WaitingChangeKind.RAISE_DAILY_LIMIT))
            assertEquals(HomeNotice.ModeChangeWaiting, line(false, k))
    }

    /** Committing is not a pending line: no "Tap to cancel". */
    @Test fun committingHasNoCancelLine() {
        assertNull(line(false, WaitingChangeKind.TURN_OFF, WaitingPhase.COMMITTING))
    }

    /** "Cancelled" only after the cancelled state is written; otherwise still cancelling or in progress. */
    @Test fun cancelResultStages() {
        assertEquals(WaitingStage.Cancelled, cancelStage(WaitingOutcome.CANCELLED))
        assertEquals(WaitingStage.Committing, cancelStage(WaitingOutcome.ALREADY_IN_PROGRESS))
        assertEquals(WaitingStage.Committing, cancelStage(WaitingOutcome.COMMITTING))
        for (o in listOf(WaitingOutcome.NOT_READY, WaitingOutcome.BUSY, WaitingOutcome.REFUSED, WaitingOutcome.PENDING, null))
            assertEquals("$o", WaitingStage.StillCancelling, cancelStage(o))
        assertNull("a request that is no longer current closes the screen", cancelStage(WaitingOutcome.IGNORED))
    }

    /** The checking screen's "needs attention" is for the attention line only, never a waiting line. */
    @Test fun aWaitingLineIsNotCheckingAttention() {
        org.junit.Assert.assertTrue(checkingShowsAttention(HomeGate.NOT_READY, HomeNotice.NeedsAttention))
        org.junit.Assert.assertFalse(checkingShowsAttention(HomeGate.NOT_READY, HomeNotice.PeopleChangeWaiting))
        org.junit.Assert.assertFalse(checkingShowsAttention(HomeGate.NOT_READY,
            HomeNotice.TurnOffWaiting("21:30", HomeNoticeDay(LocalDate.of(2026, 10, 12), 1))))
    }

    // PLA-371 R2: the shown time is rounded up, so it is never earlier than maturity.
    @Test fun reviewTimeRoundsUpToTheMinute() {
        val at = ZonedDateTime.parse("2026-10-11T21:30:45Z")
        val shown = homeLine(NoticeLine.decide(NoticeInput(Mode.TRUSTED, false,
            WaitingNotice("wait-1", WaitingChangeKind.TURN_OFF, WaitingPhase.PENDING, 86_400_000L))).line, at, time)
        assertEquals(HomeNotice.TurnOffWaiting("21:31", HomeNoticeDay(LocalDate.of(2026, 10, 12), 1)), shown)
    }

    @Test fun roundingUpCrossesMidnight() {
        val at = ZonedDateTime.parse("2026-10-11T23:59:00.001Z")
        val shown = homeLine(NoticeLine.decide(NoticeInput(Mode.TRUSTED, false,
            WaitingNotice("wait-1", WaitingChangeKind.TURN_OFF, WaitingPhase.PENDING, 0L))).line, at, time)
        assertEquals(HomeNotice.TurnOffWaiting("00:00", HomeNoticeDay(LocalDate.of(2026, 10, 12), 1)), shown)
    }

    @Test fun anExactMinuteIsNotRounded() {
        assertEquals(HomeNotice.TurnOffWaiting("21:30", HomeNoticeDay(LocalDate.of(2026, 10, 12), 1)),
            line(false, WaitingChangeKind.TURN_OFF, remaining = 86_400_000L))
    }

    // PLA-371 R1: an open confirmation follows the current operation.
    @Test fun anOpenConfirmationFollowsTheOperation() {
        fun current(phase: WaitingPhase, id: String = "wait-1") = WaitingNotice(id, WaitingChangeKind.TURN_OFF, phase, 0)
        assertEquals(WaitingStage.ConfirmCancel, confirmStage("wait-1", current(WaitingPhase.PENDING)))
        assertEquals("no Cancel from committing on", WaitingStage.Committing, confirmStage("wait-1", current(WaitingPhase.COMMITTING)))
        assertEquals(WaitingStage.Committing, confirmStage("wait-1", current(WaitingPhase.ROLLING_BACK)))
        assertNull("finished", confirmStage("wait-1", null))
        assertNull("another request", confirmStage("wait-1", current(WaitingPhase.PENDING, "wait-2")))
    }

    // Wiring 4: the pending change on the needs-attention screen.
    private fun wait(kind: WaitingChangeKind, phase: WaitingPhase = WaitingPhase.PENDING, remaining: Long = 0) =
        WaitingNotice("wait-1", kind, phase, remaining)

    @Test fun noPendingChangeWithoutAWait() {
        assertNull(pendingChange(null, now, time))
    }

    /** The same rounded-up time and day as Home's line. */
    @Test fun aPendingTurnOffShowsTheSameTimeAsHome() {
        val at = ZonedDateTime.parse("2026-10-11T21:30:45Z")
        assertEquals(org.plainphone.ui.home.PendingChange(org.plainphone.ui.home.PendingChangeKind.TurnOff("21:31",
            HomeNoticeDay(LocalDate.of(2026, 10, 12), 1))),
            pendingChange(wait(WaitingChangeKind.TURN_OFF, remaining = 86_400_000L), at, time))
    }

    @Test fun peopleAndSetupPendingChanges() {
        for (k in listOf(WaitingChangeKind.ADD_PERSON, WaitingChangeKind.HAND_OVER, WaitingChangeKind.REMOVE_PERSON))
            assertEquals(org.plainphone.ui.home.PendingChange(org.plainphone.ui.home.PendingChangeKind.People),
                pendingChange(wait(k), now, time))
        for (k in listOf(WaitingChangeKind.SWITCH_MODE, WaitingChangeKind.SHORTEN_COUNTDOWN, WaitingChangeKind.RAISE_DAILY_LIMIT))
            assertEquals(org.plainphone.ui.home.PendingChange(org.plainphone.ui.home.PendingChangeKind.Setup),
                pendingChange(wait(k), now, time))
    }

    /** Committing or rolling back: shown as in progress, so the screen offers no Cancel. */
    @Test fun committingIsInProgress() {
        for (phase in listOf(WaitingPhase.COMMITTING, WaitingPhase.ROLLING_BACK)) {
            assertEquals(true, pendingChange(wait(WaitingChangeKind.TURN_OFF, phase), now, time)?.committing)
            assertEquals(true, pendingChange(wait(WaitingChangeKind.ADD_PERSON, phase), now, time)?.committing)
        }
        assertEquals(false, pendingChange(wait(WaitingChangeKind.TURN_OFF), now, time)?.committing)
    }

    /**
     * Found on the emulator: the screen added the remaining time read at the last pass to a later "now",
     * so it named a later time than Home's line. Both now come from the same read and its time.
     */
    @Test fun attentionTimeMatchesHomeFromTheSameRead() {
        val readAt = ZonedDateTime.parse("2026-10-11T19:20:42Z")
        val notice = wait(WaitingChangeKind.TURN_OFF, remaining = 86_399_000L)
        val read = WaitingRead(notice, readAt)
        val home = homeLine(NoticeLine.decide(NoticeInput(Mode.TRUSTED, false, notice)).line, readAt, time)
            as HomeNotice.TurnOffWaiting
        assertEquals(org.plainphone.ui.home.PendingChangeKind.TurnOff(home.time, home.day), pendingChange(read, time)?.kind)
        assertEquals("19:21", home.time)
        assertNull(pendingChange(WaitingRead(null, readAt), time))
    }
    @Test fun settingsReadsCurrentPendingChangeWithoutKeepingACopy() {
        val pending = WaitingRead(wait(WaitingChangeKind.REMOVE_PERSON), now)
        assertEquals(pending.notice, settingsWaitingChange(pending))
        assertNull(settingsWaitingChange(WaitingRead(null, now))) // cancelled or completed
        assertNull(settingsWaitingChange(null))
        for (phase in listOf(WaitingPhase.COMMITTING, WaitingPhase.ROLLING_BACK))
            assertNull(settingsWaitingChange(WaitingRead(wait(WaitingChangeKind.REMOVE_PERSON, phase), now)))
    }

    @Test fun settingsAndCancelUseTheExistingChangeDescriptions() {
        assertEquals(org.plainphone.R.string.wait_change_turn_off, waitingChangeTextResource(WaitingChangeKind.TURN_OFF))
        for (kind in listOf(WaitingChangeKind.ADD_PERSON, WaitingChangeKind.HAND_OVER, WaitingChangeKind.REMOVE_PERSON))
            assertEquals(org.plainphone.R.string.wait_change_people, waitingChangeTextResource(kind))
        for (kind in listOf(WaitingChangeKind.SWITCH_MODE, WaitingChangeKind.SHORTEN_COUNTDOWN, WaitingChangeKind.RAISE_DAILY_LIMIT))
            assertEquals(org.plainphone.R.string.wait_change_setup, waitingChangeTextResource(kind))
    }

}
