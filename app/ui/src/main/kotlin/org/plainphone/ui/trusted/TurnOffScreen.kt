/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.trusted

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource as s
import org.plainphone.ui.R
import org.plainphone.ui.components.*

/** The host sequences both confirmations, fresh authority and the shared waiting-change screen. */
@Composable
fun TurnOffScreen(state: TurnOffUiState, onEvent: (TurnOffEvent) -> Unit) {
    val helper = state.helper.withoutBidiControls()
    val self = state.context.trustedPersonName == null
    val title = s(when (state.stage) {
        TurnOffStage.Intro -> R.string.off_title; TurnOffStage.FirstConfirmation -> R.string.off_sure
        TurnOffStage.LastConfirmation -> R.string.off_last; TurnOffStage.TurningOff -> R.string.off_turning
        TurnOffStage.Off -> R.string.off_done; TurnOffStage.Failed -> R.string.off_failed
    })
    val primary = when (state.stage) {
        TurnOffStage.Intro -> s(R.string.admin_continue)
        TurnOffStage.FirstConfirmation -> s(R.string.off_yes)
        TurnOffStage.LastConfirmation -> s(R.string.admin_start_wait)
        TurnOffStage.TurningOff -> null
        TurnOffStage.Off -> s(R.string.user_ok)
        TurnOffStage.Failed -> s(R.string.user_try_again)
    }
    AdminFrame(state.context, title, primary, { onEvent(TurnOffEvent.Continue) }, enabled = !state.sending,
        secondary = when (state.stage) {
            TurnOffStage.Intro -> s(R.string.off_keep)
            TurnOffStage.FirstConfirmation -> s(R.string.off_no)
            TurnOffStage.LastConfirmation -> s(R.string.off_cancel)
            TurnOffStage.Failed -> s(R.string.wait_helper_details)
            else -> null
        }, secondaryEnabled = !state.sending,
        onSecondary = { onEvent(if (state.stage == TurnOffStage.Failed) TurnOffEvent.Details else TurnOffEvent.KeepOn) }) {
        when (state.stage) {
            TurnOffStage.Intro -> {
                Words(if (self) s(R.string.off_intro_self) else phoneNameText(PhoneNameCopy.TurnOffIntro, state.context.userName))
                Words(s(R.string.off_kept))
                Words(if (state.testBuild || self) s(R.string.off_wait_self) else s(R.string.off_wait, trustedPeopleText(state.everyone, everyone = true).orEmpty()))
                if (state.testBuild) Words(s(R.string.preview_no_texts))
                Words(s(R.string.off_setup, helper)); Words(s(R.string.off_battery))
            }
            TurnOffStage.FirstConfirmation -> Words(
                if (state.testBuild || self) s(R.string.off_sure_body_self) else phoneNameText(PhoneNameCopy.TurnOffSure, state.context.userName)
            )
            TurnOffStage.LastConfirmation -> Words(s(R.string.off_last_body, helper))
            TurnOffStage.TurningOff -> { Words(s(R.string.off_steps, state.step, state.total)); if (state.resumed) Words(s(R.string.off_resumed)) }
            TurnOffStage.Off -> Words(s(R.string.off_done_body))
            TurnOffStage.Failed -> Words(s(R.string.off_failed_body, state.stepDescription.withoutBidiControls(), helper))
        }
    }
}
