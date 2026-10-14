// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.shell

import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.plainphone.core.Mode
import org.plainphone.policy.HomeNoticeKind
import org.plainphone.policy.NoticeLine
import org.plainphone.ui.home.HomeApp
import org.plainphone.ui.home.HomeNotice
import org.plainphone.ui.home.HomeNoticeDay
import org.plainphone.ui.home.HomeUiState
import org.plainphone.ui.user.AppRequestResult
import org.plainphone.ui.user.AppRequestResultUiState
import org.plainphone.ui.user.AppRequestRow
import org.plainphone.ui.user.AskForAppUiState
import org.plainphone.ui.user.MoreMode
import org.plainphone.ui.user.MoreUiState

/** Limits on app requests (flows-user.md U5). */
object RequestRules {
    const val MAX_LENGTH = 280
    const val COUNTER_FROM = 200
    const val DAILY_LIMIT = 5
    const val HISTORY_ROWS = 10

    fun accepts(draft: String) = draft.length <= MAX_LENGTH
    fun canSend(draft: String) = accepts(draft) && draft.any { it.isLetter() }
}

/** Maps one [PhoneSnapshot] to `:ui` screen state. Pure; the notice line is chosen by `:policy`. */
class ShellUiMapper(locale: Locale, is24Hour: Boolean) {
    private val time = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm", locale)
    private val date = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    private val weekday = DateTimeFormatter.ofPattern("EEE", locale)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)

    fun home(s: PhoneSnapshot) = HomeUiState(
        time = s.now.format(time),
        date = s.now.format(date),
        shortDate = s.now.format(shortDate),
        apps = s.apps.map { HomeApp(it.id, it.label) },
        notice = notice(s),
        showFamily = s.familyEnabled && s.familyCount > 0,
    )

    private fun notice(s: PhoneSnapshot): HomeNotice? {
        val line = NoticeLine.decide(s.notices).line ?: return null
        val remaining = line.remainingMillis ?: 0L
        return when (line.kind) {
            HomeNoticeKind.NEEDS_ATTENTION -> HomeNotice.NeedsAttention
            HomeNoticeKind.TURN_OFF_WAITING -> {
                val ends = s.now.plus(Duration.ofMillis(remaining))
                val days = ChronoUnit.DAYS.between(s.now.toLocalDate(), ends.toLocalDate()).toInt()
                HomeNotice.TurnOffWaiting(ends.format(time), HomeNoticeDay(ends.toLocalDate(), days))
            }
            HomeNoticeKind.PEOPLE_WAITING -> HomeNotice.PeopleChangeWaiting
            HomeNoticeKind.SETUP_WAITING -> HomeNotice.ModeChangeWaiting
            HomeNoticeKind.ELEVATION_COUNTDOWN -> HomeNotice.OpeningUp(((remaining + 59_999) / 60_000).toInt())
            HomeNoticeKind.ELEVATION_READY -> HomeNotice.ReadyToStart
            HomeNoticeKind.MONTHLY_SUPERVISED -> HomeNotice.MonthlyCheck(s.trustedPeople)
            HomeNoticeKind.MONTHLY_SELF_MANAGED -> HomeNotice.SelfManagedMonthlyCheck
        }
    }

    /** Every build is a test build for now, so the practice voice messages row always shows (PLA-457). */
    fun more(s: PhoneSnapshot) = MoreUiState(when (s.mode) {
        Mode.TRUSTED -> MoreMode.Trusted(s.trustedPeople, s.requestRecipient)
        Mode.SELF_MANAGED -> MoreMode.SelfManaged(s.selfManagedWaitMinutes)
    }, voicePractice = !(s.familyEnabled && s.familyCount > 0), pinHolderOnly = s.trustedPeople.isEmpty())

    fun ask(s: PhoneSnapshot, draft: String) = AskForAppUiState(
        recipientName = s.requestRecipient,
        message = draft,
        canSend = s.requestRecipient.isNotBlank() && RequestRules.canSend(draft),
        isSetUp = s.requestRecipient.isNotBlank(),
        charactersRemaining = if (draft.length > RequestRules.COUNTER_FROM) RequestRules.MAX_LENGTH - draft.length else null,
        requests = rows(s),
    )

    fun result(s: PhoneSnapshot, result: AppRequestResult) =
        AppRequestResultUiState(s.requestRecipient, result, rows(s))

    private fun rows(s: PhoneSnapshot) = s.requests.take(RequestRules.HISTORY_ROWS).map {
        val age = ChronoUnit.DAYS.between(it.sentOn, s.now.toLocalDate())
        AppRequestRow(it.id, it.message.lineSequence().first(), it.sentOn.format(if (age < 6) weekday else dayMonth), it.status)
    }
}
