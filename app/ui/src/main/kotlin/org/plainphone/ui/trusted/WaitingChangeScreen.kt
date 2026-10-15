/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.trusted

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource as s
import org.plainphone.ui.R
import org.plainphone.ui.components.*

@Composable
fun WaitingChangeScreen(state: WaitingChangeUiState, onEvent: (WaitingChangeEvent) -> Unit) {
    val change = state.change.withoutBidiControls()
    val everyone = trustedPeopleText(state.everyone, everyone = true)
    val self = everyone == null
    val selfManaged = state.context != null && state.context.trustedPersonName == null
    val title = when (state.stage) {
        WaitingStage.Details -> s(if (state.context == null && state.turningOff) R.string.wait_turn_off_title else R.string.wait_title)
        WaitingStage.ConfirmCancel -> s(R.string.wait_cancel_title)
        WaitingStage.StillCancelling -> s(R.string.wait_still_cancelling)
        WaitingStage.Cancelled -> s(R.string.wait_cancelled)
        WaitingStage.Committing -> s(R.string.wait_progress)
        WaitingStage.Started -> s(R.string.wait_started)
        WaitingStage.Another -> s(R.string.wait_another)
        WaitingStage.SaveFailed -> s(R.string.wait_failed)
        WaitingStage.SkipIntro -> s(R.string.wait_skip_title, change)
        WaitingStage.SkipConfirm -> s(R.string.wait_skip_confirm)
        WaitingStage.SkipStarted -> s(R.string.wait_skip_started)
        WaitingStage.Stuck -> s(R.string.wait_stuck)
        WaitingStage.StopConfirm -> s(R.string.wait_stop_title)
        WaitingStage.Stopping -> s(R.string.wait_stopping)
        WaitingStage.Stopped -> s(if (selfManaged) R.string.wait_stopped_self else R.string.wait_stopped)
        WaitingStage.StillStopping -> s(R.string.wait_stopping)
        WaitingStage.Recovered -> s(R.string.wait_recovered)
        WaitingStage.Repair -> s(R.string.wait_repair)
    }
    val primary = if (state.giveBackFailureCount > 0) s(R.string.give_back_retry) else when (state.stage) {
        WaitingStage.Details -> s(R.string.wait_cancel)
        WaitingStage.ConfirmCancel -> s(if (state.sending) R.string.wait_cancelling else R.string.wait_yes_cancel)
        WaitingStage.SkipIntro -> s(R.string.admin_recovery_title)
        WaitingStage.SkipConfirm -> s(R.string.wait_yes_skip)
        WaitingStage.StopConfirm -> s(R.string.wait_stop_it)
        WaitingStage.Repair -> s(R.string.wait_repair_action)
        WaitingStage.Stopping -> null
        else -> s(R.string.user_ok)
    }
    val secondary = when (state.stage) {
        WaitingStage.Details -> if (state.context == null) s(R.string.wait_keep) else if (state.canSkip) s(R.string.wait_skip) else null
        WaitingStage.ConfirmCancel -> s(R.string.wait_no_cancel)
        WaitingStage.Another -> s(R.string.wait_see)
        WaitingStage.SkipIntro -> s(R.string.wait_dont_skip)
        WaitingStage.SkipConfirm -> s(R.string.wait_no_skip)
        WaitingStage.Stuck -> if (state.canStop) s(R.string.wait_stop) else null
        WaitingStage.StopConfirm -> s(R.string.wait_keep_trying)
        WaitingStage.Recovered -> if (!self) s(R.string.wait_see_people) else null
        WaitingStage.Repair -> s(R.string.wait_helper_details)
        else -> null
    }
    AdminFrame(state.context, title, primary, { onEvent(when (state.stage) {
        WaitingStage.Details -> WaitingChangeEvent.Cancel
        WaitingStage.ConfirmCancel -> WaitingChangeEvent.ConfirmCancel
        WaitingStage.SkipIntro -> WaitingChangeEvent.EnterRecoveryCode
        WaitingStage.SkipConfirm -> WaitingChangeEvent.ConfirmSkip
        WaitingStage.StopConfirm -> WaitingChangeEvent.ConfirmStop
        WaitingStage.Repair -> WaitingChangeEvent.Repair
        else -> WaitingChangeEvent.Ok
    }) }, enabled = !state.sending, secondary = secondary, secondaryEnabled = !state.sending, onSecondary = { onEvent(when (state.stage) {
        WaitingStage.Details -> if (state.context == null) WaitingChangeEvent.KeepWaiting else WaitingChangeEvent.Skip
        WaitingStage.Another -> WaitingChangeEvent.SeeWaiting
        WaitingStage.Stuck -> WaitingChangeEvent.Stop
        WaitingStage.Recovered -> WaitingChangeEvent.SeePeople
        WaitingStage.Repair -> WaitingChangeEvent.Details
        else -> WaitingChangeEvent.KeepWaiting
    }) }) {
        when (state.stage) {
            WaitingStage.Details -> {
                ScreenBox {
                    Heading(s(R.string.wait_box, change))
                    Words(if (self) s(R.string.wait_started_self, state.day, state.startedAt)
                        else s(R.string.wait_started_by, state.starterName.withoutBidiControls(), state.day, state.startedAt))
                }
                Words(s(if (state.context == null) R.string.wait_user_body else R.string.wait_body, state.happensAt))
                if (state.context == null) {
                    if (state.turningOff) Words(s(R.string.wait_normal))
                    Words(s(R.string.wait_anyone))
                    everyone?.let { Words(s(R.string.wait_text_either, it)) }
                }
            }
            WaitingStage.ConfirmCancel -> {
                Words(s(R.string.wait_cancel_body, change))
                everyone?.let { Words(s(R.string.wait_cancel_text, it)) }
                Words(s(R.string.wait_restart))
            }
            WaitingStage.StillCancelling -> Words(s(R.string.wait_still_body))
            WaitingStage.Cancelled -> { Words(s(R.string.wait_cancel_body, change)); everyone?.let { Words(s(R.string.wait_cancelled_text, it)) } }
            WaitingStage.Committing -> if (state.giveBackFailureCount > 0) {
                Words(androidx.compose.ui.res.pluralStringResource(R.plurals.give_back_failed,
                    state.giveBackFailureCount, state.giveBackFailureCount))
            } else Words(s(if (state.turningOff) R.string.wait_off_progress else R.string.wait_progress_body))
            WaitingStage.Started -> {
                Words(s(R.string.wait_started_body, change, state.happensAtDay?.let { waitingTimeText(state.happensAt, it) } ?: state.happensAt))
                everyone?.let { Words(s(R.string.wait_started_text, it)) }
                Words(s(R.string.wait_started_cancel))
            }
            WaitingStage.Another -> Words(s(if (state.otherCommitting) R.string.wait_another_progress else R.string.wait_another_body, change))
            WaitingStage.SkipIntro -> Words(s(R.string.wait_skip_body, everyone.orEmpty()))
            WaitingStage.SkipConfirm -> Words(s(R.string.wait_skip_confirm_body, change))
            WaitingStage.SkipStarted -> Words(s(R.string.wait_skip_started_body, change))
            WaitingStage.Stuck -> if (selfManaged) {
                Words(s(R.string.wait_stuck_self_start, state.newName.withoutBidiControls()))
                Words(s(R.string.wait_stuck_self_unchanged))
                Words(s(R.string.wait_stuck_self_unknown))
            } else Words(s(R.string.wait_stuck_body, change, everyone.orEmpty()))
            WaitingStage.StopConfirm -> Words(when {
                selfManaged -> s(R.string.wait_stop_self_body, state.newName.withoutBidiControls())
                state.changeToSelfManaged -> s(R.string.wait_stop_to_self_body, everyone.orEmpty(), state.context?.userName.orEmpty().withoutBidiControls())
                else -> s(R.string.wait_stop_body, everyone.orEmpty(), state.newName.withoutBidiControls())
            })
            WaitingStage.StillStopping -> Words(s(R.string.wait_still_stopping, state.helper.withoutBidiControls()))
            WaitingStage.Recovered -> {
                Words(if (self) s(R.string.wait_recovered_self, state.userName.withoutBidiControls())
                    else s(R.string.wait_recovered_people, everyone.orEmpty()))
                Words(s(R.string.wait_recovered_body))
            }
            WaitingStage.Repair -> Words(s(R.string.wait_repair_body))
            else -> Unit
        }
    }
}
