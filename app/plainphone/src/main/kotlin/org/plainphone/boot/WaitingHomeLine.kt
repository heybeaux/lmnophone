// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import org.plainphone.policy.HomeNoticeKind
import org.plainphone.policy.NoticeLine
import org.plainphone.policy.NoticeTap
import org.plainphone.policy.WaitingChangeKind
import org.plainphone.policy.WaitingNotice
import org.plainphone.policy.WaitingPhase
import org.plainphone.ui.home.PendingChange
import org.plainphone.ui.home.PendingChangeKind
import org.plainphone.policy.WaitingOutcome
import org.plainphone.ui.home.HomeNotice
import org.plainphone.ui.home.HomeNoticeDay
import org.plainphone.ui.trusted.WaitingStage
import org.plainphone.policy.HomeNotice as PolicyLine

/**
 * The Home notice for the line policy `NoticeLine` selected. A turn-off names [now] plus the proven time
 * still to run, rounded up to the minute and formatted with [time], and its day: the earliest it can
 * happen, since only time the phone is on counts ("will turn off after {time}"). Kinds not wired yet
 * (elevation, monthly) show nothing.
 */
fun homeLine(line: PolicyLine?, now: ZonedDateTime, time: DateTimeFormatter): HomeNotice? = when (line?.kind) {
    null -> null
    HomeNoticeKind.NEEDS_ATTENTION -> HomeNotice.NeedsAttention
    HomeNoticeKind.TURN_OFF_WAITING -> turnOffAt(line.remainingMillis ?: 0L, now, time).let { (t, day) -> HomeNotice.TurnOffWaiting(t, day) }
    HomeNoticeKind.PEOPLE_WAITING -> HomeNotice.PeopleChangeWaiting
    HomeNoticeKind.SETUP_WAITING -> HomeNotice.ModeChangeWaiting
    else -> null
}

/**
 * The pending change for the needs-attention screen (wiring 4), from the same projection as Home's line:
 * a turn-off with the same rounded-up time and day; committing or rolling back shows as in progress,
 * so the screen offers no Cancel. Null with no current change.
 */
fun pendingChange(wait: WaitingNotice?, now: ZonedDateTime, time: DateTimeFormatter): PendingChange? {
    wait ?: return null
    val kind = when (wait.kind) {
        WaitingChangeKind.TURN_OFF -> turnOffAt(wait.remainingMillis, now, time).let { (t, day) -> PendingChangeKind.TurnOff(t, day) }
        WaitingChangeKind.ADD_PERSON, WaitingChangeKind.HAND_OVER, WaitingChangeKind.REMOVE_PERSON -> PendingChangeKind.People
        WaitingChangeKind.SWITCH_MODE, WaitingChangeKind.SHORTEN_COUNTDOWN, WaitingChangeKind.RAISE_DAILY_LIMIT -> PendingChangeKind.Setup
    }
    return PendingChange(kind, committing = wait.phase != WaitingPhase.PENDING)
}

/** One pass's read of the current operation ([notice], null with none) and the time it was read. */
data class WaitingRead(val notice: WaitingNotice?, val at: ZonedDateTime)

/** [pendingChange] for [read], timed from when it was read, so it names the same time as Home's line. */
fun pendingChange(read: WaitingRead?, time: DateTimeFormatter): PendingChange? =
    read?.let { pendingChange(it.notice, it.at, time) }

/** [now] plus [remainingMillis], rounded up to the shown minute (PLA-371 R2), and its day. */
private fun turnOffAt(remainingMillis: Long, now: ZonedDateTime, time: DateTimeFormatter): Pair<String, HomeNoticeDay> {
    val exact = now.plus(remainingMillis, ChronoUnit.MILLIS)
    val minute = exact.truncatedTo(ChronoUnit.MINUTES)
    val at = if (minute == exact) minute else minute.plusMinutes(1)
    return at.format(time) to HomeNoticeDay(at.toLocalDate(), ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate()).toInt())
}

/**
 * The open confirmation for [requestId] against the [current] operation (PLA-371 R1), via policy
 * `NoticeLine.confirmCancel`: still pending keeps "Cancel this change?"; committing or rolling back shows
 * "Already in progress" with no Cancel; finished or another request closes the screen (null).
 */
fun confirmStage(requestId: String, current: WaitingNotice?): WaitingStage? =
    when (NoticeLine.confirmCancel(requestId, current)) {
        is NoticeTap.RequestCancellation -> WaitingStage.ConfirmCancel
        is NoticeTap.AlreadyInProgress -> WaitingStage.Committing
        else -> null
    }

/**
 * Home's own sub-screens: the PIN-free cancel confirmation ([cancelOpen], [sending] while its cancel is
 * being saved) and the needs-attention screen ([attentionOpen]).
 */
data class HomeScreens(val cancelOpen: Boolean, val sending: Boolean, val attentionOpen: Boolean) {
    /** Android Back is ours only while a sub-screen is open; plain Home keeps the system's own Back. */
    val handlesBack: Boolean get() = cancelOpen || attentionOpen

    /**
     * Android Back closes the top sub-screen, as its own No/OK would (PLA-383 note): the confirmation
     * returns to the attention screen or Home, the attention screen to Home. A cancel being saved stays
     * open, so its saved result still shows.
     */
    fun back(): HomeScreens = when {
        cancelOpen && sending -> this
        cancelOpen -> copy(cancelOpen = false, sending = false)
        attentionOpen -> copy(attentionOpen = false)
        else -> this
    }
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
