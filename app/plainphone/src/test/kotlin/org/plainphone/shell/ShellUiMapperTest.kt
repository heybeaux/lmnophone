// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.shell

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.core.Mode
import org.plainphone.policy.ElevationNotice
import org.plainphone.policy.MonthlyNotice
import org.plainphone.policy.MonthlyNoticeKind
import org.plainphone.policy.NoticeInput
import org.plainphone.policy.WaitingChangeKind
import org.plainphone.policy.WaitingNotice
import org.plainphone.policy.WaitingPhase
import org.plainphone.ui.home.HomeApp
import org.plainphone.ui.home.HomeNotice
import org.plainphone.ui.home.HomeNoticeDay
import org.plainphone.ui.user.AppRequestResult
import org.plainphone.ui.user.AppRequestRow
import org.plainphone.ui.user.AppRequestStatus
import org.plainphone.ui.user.MoreMode

class ShellUiMapperTest {
    private val now = ZonedDateTime.of(2026, 10, 8, 10, 42, 0, 0, ZoneId.of("Europe/London"))
    private val base = PhoneSnapshot(
        now = now, userName = "Alex", mode = Mode.TRUSTED, trustedPeople = listOf("Sam", "Jo"), requestRecipient = "Sam",
        selfManagedWaitMinutes = 10, apps = listOf(ApprovedApp("a.clock", "Clock"), ApprovedApp("a.maps", "Maps")),
        notices = NoticeInput(Mode.TRUSTED), requests = emptyList(),
    )
    private val map = ShellUiMapper(Locale.UK, is24Hour = true)

    private fun waiting(kind: WaitingChangeKind, ms: Long, phase: WaitingPhase = WaitingPhase.PENDING) =
        base.copy(notices = NoticeInput(Mode.TRUSTED, waiting = WaitingNotice("r1", kind, phase, ms)))

    @Test fun homeClockDatesAndApps() {
        val home = map.home(base)
        assertEquals("10:42", home.time)
        assertEquals("Thursday 8 October", home.date)
        assertEquals("Thu 8 Oct", home.shortDate)
        assertEquals(listOf(HomeApp("a.clock", "Clock"), HomeApp("a.maps", "Maps")), home.apps)
        assertNull(home.notice)
    }

    @Test fun twelveHourClockHasNoMarker() {
        assertEquals("1:05", ShellUiMapper(Locale.UK, is24Hour = false).home(base.copy(now = now.withHour(13).withMinute(5))).time)
    }

    @Test fun noticeComesFromThePolicyNoticeLine() {
        // needsAttention outranks a pending wait, as NoticeLine decides.
        val both = base.copy(notices = NoticeInput(Mode.TRUSTED, needsAttention = true,
            waiting = WaitingNotice("r1", WaitingChangeKind.TURN_OFF, WaitingPhase.PENDING, 1000)))
        assertEquals(HomeNotice.NeedsAttention, map.home(both).notice)
    }

    @Test fun turnOffWaitingShowsEndTimeAndDay() {
        val notice = map.home(waiting(WaitingChangeKind.TURN_OFF, 24L * 60 * 60 * 1000)).notice
        assertEquals(HomeNotice.TurnOffWaiting("10:42", HomeNoticeDay(LocalDate.of(2026, 10, 9), 1)), notice)
        val sameDay = map.home(waiting(WaitingChangeKind.TURN_OFF, 60L * 60 * 1000)).notice
        assertEquals(HomeNotice.TurnOffWaiting("11:42", HomeNoticeDay(LocalDate.of(2026, 10, 8), 0)), sameDay)
    }

    @Test fun peopleAndSetupWaits() {
        assertEquals(HomeNotice.PeopleChangeWaiting, map.home(waiting(WaitingChangeKind.ADD_PERSON, 5)).notice)
        assertEquals(HomeNotice.ModeChangeWaiting, map.home(waiting(WaitingChangeKind.SWITCH_MODE, 5)).notice)
        assertNull(map.home(waiting(WaitingChangeKind.TURN_OFF, 5, WaitingPhase.COMMITTING)).notice)
    }

    @Test fun elevationCountdownRoundsMinutesUp() {
        fun elevation(ms: Long) = base.copy(mode = Mode.SELF_MANAGED,
            notices = NoticeInput(Mode.SELF_MANAGED, elevation = ElevationNotice("g1", ms)))
        assertEquals(HomeNotice.OpeningUp(2), map.home(elevation(61_000)).notice)
        assertEquals(HomeNotice.OpeningUp(1), map.home(elevation(1)).notice)
        assertEquals(HomeNotice.ReadyToStart, map.home(elevation(0)).notice)
    }

    @Test fun monthlyCheckNamesTrustedPeople() {
        val supervised = base.copy(notices = NoticeInput(Mode.TRUSTED,
            monthly = MonthlyNotice(true, MonthlyNoticeKind.SUPERVISED_OVERDUE)))
        assertEquals(HomeNotice.MonthlyCheck(listOf("Sam", "Jo")), map.home(supervised).notice)
        val self = base.copy(mode = Mode.SELF_MANAGED, notices = NoticeInput(Mode.SELF_MANAGED,
            monthly = MonthlyNotice(true, MonthlyNoticeKind.SELF_MANAGED_OVERDUE)))
        assertEquals(HomeNotice.SelfManagedMonthlyCheck, map.home(self).notice)
    }

    /** Preview builds show the practice row in both modes; it was never set on the spare (PLA-457 build 38). */
    @Test fun moreShowsVoicePracticeRow() {
        assertTrue(map.more(base).voicePractice)
        assertTrue(map.more(base.copy(mode = Mode.SELF_MANAGED)).voicePractice)
    }

    @Test fun moreFollowsMode() {
        assertEquals(MoreMode.Trusted(listOf("Sam", "Jo"), "Sam"), map.more(base).mode)
        assertEquals(MoreMode.SelfManaged(10), map.more(base.copy(mode = Mode.SELF_MANAGED)).mode)
    }

    @Test fun askShowsCounterOnlyAfter200AndNeedsALetter() {
        assertNull(map.ask(base, "a".repeat(200)).charactersRemaining)
        assertEquals(79, map.ask(base, "a".repeat(200) + "b").charactersRemaining)
        assertTrue(map.ask(base, "Maps").canSend)
        assertFalse(map.ask(base, "  42 ").canSend)
        assertFalse(map.ask(base, "").canSend)
        assertEquals("Sam", map.ask(base, "").recipientName)
    }

    @Test fun requestHistoryIsNewestFirstCappedAtTenWithDays() {
        val rows = (0 until 12).map { SavedRequest("r$it", "App $it\nmore detail", now.toLocalDate().minusDays(it.toLong()), AppRequestStatus.Sent) }
        val ask = map.ask(base.copy(requests = rows), "")
        assertEquals(10, ask.requests.size)
        assertEquals(AppRequestRow("r0", "App 0", "Thu", AppRequestStatus.Sent), ask.requests[0])
        assertEquals("Wed", ask.requests[1].day)
        assertEquals("2 Oct", ask.requests[6].day)
    }

    @Test fun resultCarriesRecipientAndHistory() {
        val rows = listOf(SavedRequest("r0", "Maps", now.toLocalDate(), AppRequestStatus.Sent))
        val result = map.result(base.copy(requests = rows), AppRequestResult.Sent)
        assertEquals("Sam", result.recipientName)
        assertEquals(1, result.requests.size)
    }
}
