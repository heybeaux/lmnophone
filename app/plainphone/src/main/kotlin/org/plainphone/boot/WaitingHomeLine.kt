// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import org.plainphone.policy.HomeNoticeKind
import org.plainphone.policy.WaitingOutcome
import org.plainphone.ui.home.HomeNotice
import org.plainphone.ui.home.HomeNoticeDay
import org.plainphone.ui.trusted.WaitingStage
import org.plainphone.policy.HomeNotice as PolicyLine

/**
 * The Home notice for the line policy `NoticeLine` selected. A turn-off names [now] plus the proven time
 * still to run, formatted with [time], and its day: the earliest it can happen, since only time the phone
 * is on counts ("will turn off after {time}"). Kinds not wired yet (elevation, monthly) show nothing.
 */
fun homeLine(line: PolicyLine?, now: ZonedDateTime, time: DateTimeFormatter): HomeNotice? = when (line?.kind) {
    null -> null
    HomeNoticeKind.NEEDS_ATTENTION -> HomeNotice.NeedsAttention
    HomeNoticeKind.TURN_OFF_WAITING -> {
        val at = now.plus(line.remainingMillis ?: 0L, ChronoUnit.MILLIS)
        HomeNotice.TurnOffWaiting(at.format(time),
            HomeNoticeDay(at.toLocalDate(), ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate()).toInt()))
    }
    HomeNoticeKind.PEOPLE_WAITING -> HomeNotice.PeopleChangeWaiting
    HomeNoticeKind.SETUP_WAITING -> HomeNotice.ModeChangeWaiting
    else -> null
}

/**
 * The PIN-free cancel screen after the cancel ran. "Cancelled" only when the cancelled state was written;
 * a change already committing says "Already in progress"; anything unfinished says "Still cancelling"
 * (the recorded intent and the next checkpoint keep trying, and nothing commits meanwhile). Null closes
 * the screen: the request is no longer the current one.
 */
fun cancelStage(result: WaitingOutcome?): WaitingStage? = when (result) {
    WaitingOutcome.CANCELLED -> WaitingStage.Cancelled
    WaitingOutcome.ALREADY_IN_PROGRESS, WaitingOutcome.COMMITTING -> WaitingStage.Committing
    WaitingOutcome.IGNORED -> null
    else -> WaitingStage.StillCancelling
}
