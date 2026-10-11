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
}
