// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.Mode

enum class WaitingChangeKind { TURN_OFF, ADD_PERSON, HAND_OVER, REMOVE_PERSON, SWITCH_MODE, SHORTEN_COUNTDOWN, RAISE_DAILY_LIMIT }
enum class WaitingPhase { PENDING, COMMITTING, ROLLING_BACK, COMPLETED, CANCELLED }

/** Projection of the current durable operation, not an operation or authority to change policy. */
data class WaitingNotice(val requestId: String, val kind: WaitingChangeKind, val phase: WaitingPhase, val remainingMillis: Long) {
    init { require(requestId.isNotBlank() && remainingMillis >= 0) }
}
/** Supply only an active countdown or unexpired ready grant; zero means ready, not permission to start. */
data class ElevationNotice(val grantId: String, val remainingMillis: Long) {
    init { require(grantId.isNotBlank() && remainingMillis >= 0) }
}

enum class HomeNoticeKind {
    NEEDS_ATTENTION, TURN_OFF_WAITING, PEOPLE_WAITING, SETUP_WAITING,
    ELEVATION_COUNTDOWN, ELEVATION_READY, MONTHLY_SUPERVISED, MONTHLY_SELF_MANAGED,
}

/** Navigation/command descriptions only. Controllers must revalidate IDs/state before any effect. */
sealed interface NoticeTap {
    data object NONE : NoticeTap
    data object OPEN_ATTENTION : NoticeTap
    data object EXPLAIN_MONTHLY : NoticeTap
    data class ConfirmCancel(val requestId: String) : NoticeTap
    data class RequestCancellation(val requestId: String) : NoticeTap
    data class AlreadyInProgress(val requestId: String) : NoticeTap
    data class OpenElevation(val grantId: String) : NoticeTap
    data class ConfirmElevationStart(val grantId: String) : NoticeTap
}

data class HomeNotice(val kind: HomeNoticeKind, val tap: NoticeTap, val remainingMillis: Long? = null)
data class ChangeNoticeDetails(val waiting: WaitingNotice, val action: NoticeTap)
data class NoticeSelection(val line: HomeNotice?, val attentionChange: ChangeNoticeDetails?, val settingsChange: ChangeNoticeDetails?)
data class NoticeInput(val mode: Mode, val needsAttention: Boolean = false, val waiting: WaitingNotice? = null,
    val elevation: ElevationNotice? = null, val monthly: MonthlyNotice? = null)

/** Selects at most one line. No product copy, PIN checks, session opening or storage effects. */
object NoticeLine {
    fun decide(input: NoticeInput): NoticeSelection {
        val waiting = input.waiting?.takeUnless { it.phase == WaitingPhase.COMPLETED || it.phase == WaitingPhase.CANCELLED }
        val details = waiting?.let { ChangeNoticeDetails(it, if (it.phase == WaitingPhase.PENDING)
            NoticeTap.ConfirmCancel(it.requestId) else NoticeTap.AlreadyInProgress(it.requestId)) }
        val elevation = input.elevation.takeIf { input.mode == Mode.SELF_MANAGED }
        val line = when {
            input.needsAttention -> HomeNotice(HomeNoticeKind.NEEDS_ATTENTION, NoticeTap.OPEN_ATTENTION)
            waiting?.phase == WaitingPhase.PENDING -> HomeNotice(when (waiting.kind) {
                WaitingChangeKind.TURN_OFF -> HomeNoticeKind.TURN_OFF_WAITING
                WaitingChangeKind.ADD_PERSON, WaitingChangeKind.HAND_OVER, WaitingChangeKind.REMOVE_PERSON -> HomeNoticeKind.PEOPLE_WAITING
                WaitingChangeKind.SWITCH_MODE, WaitingChangeKind.SHORTEN_COUNTDOWN, WaitingChangeKind.RAISE_DAILY_LIMIT -> HomeNoticeKind.SETUP_WAITING
            }, NoticeTap.ConfirmCancel(waiting.requestId), waiting.remainingMillis)
            elevation != null -> if (elevation.remainingMillis == 0L)
                HomeNotice(HomeNoticeKind.ELEVATION_READY, NoticeTap.ConfirmElevationStart(elevation.grantId), 0)
                else HomeNotice(HomeNoticeKind.ELEVATION_COUNTDOWN, NoticeTap.OpenElevation(elevation.grantId), elevation.remainingMillis)
            input.monthly?.visible == true -> HomeNotice(if (input.mode == Mode.TRUSTED)
                HomeNoticeKind.MONTHLY_SUPERVISED else HomeNoticeKind.MONTHLY_SELF_MANAGED, NoticeTap.EXPLAIN_MONTHLY)
            else -> null
        }
        // Keep the change reachable on both fallback screens even when a higher-priority line hides it.
        return NoticeSelection(line, details, details)
    }

    /** After user confirmation, re-read the operation. This never claims cancellation has been persisted. */
    fun confirmCancel(requestId: String, current: WaitingNotice?): NoticeTap {
        if (current == null || current.requestId != requestId) return NoticeTap.NONE
        return when (current.phase) {
            WaitingPhase.PENDING -> NoticeTap.RequestCancellation(requestId)
            WaitingPhase.COMMITTING, WaitingPhase.ROLLING_BACK -> NoticeTap.AlreadyInProgress(requestId)
            WaitingPhase.COMPLETED, WaitingPhase.CANCELLED -> NoticeTap.NONE
        }
    }
}
