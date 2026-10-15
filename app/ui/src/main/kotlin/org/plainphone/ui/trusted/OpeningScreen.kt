/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.trusted

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource as s
import org.plainphone.ui.R
import org.plainphone.ui.components.*
import org.plainphone.ui.theme.*

@Composable
fun OpeningScreen(state: OpeningUiState, onEvent: (OpeningEvent) -> Unit) {
    val app = state.appName.withoutBidiControls()
    val kind = s(when (state.kind) {
        OpeningKind.Play -> R.string.opening_play; OpeningKind.Settings -> R.string.opening_settings
        OpeningKind.App -> R.string.opening_app; OpeningKind.Update -> R.string.opening_update
    }, app)
    val title = when (state.stage) {
        OpeningStage.Before -> s(when (state.kind) {
            OpeningKind.Play -> R.string.opening_play_title; OpeningKind.Settings -> R.string.opening_settings_title
            OpeningKind.App -> R.string.opening_app_title; OpeningKind.Update -> R.string.opening_update_title
        })
        OpeningStage.Countdown -> when (state.kind) {
            OpeningKind.App -> if (state.minutes == 1) s(R.string.opening_countdown_app_one, app) else s(R.string.opening_countdown_app, app, state.minutes)
            OpeningKind.Update -> if (state.minutes == 1) s(R.string.opening_countdown_update_one) else s(R.string.opening_countdown_update, state.minutes)
            else -> if (state.minutes == 1) s(R.string.opening_countdown_one) else s(R.string.opening_countdown, state.minutes)
        }
        OpeningStage.Ready -> when (state.kind) {
            OpeningKind.App -> s(R.string.opening_ready_app, app)
            OpeningKind.Update -> s(R.string.opening_ready_update)
            else -> s(R.string.opening_ready, kind)
        }
        OpeningStage.NoOpenings -> s(R.string.opening_none)
        OpeningStage.AlreadyOpening -> s(R.string.opening_already)
        OpeningStage.SaveFailed -> s(R.string.opening_failed)
        OpeningStage.OpenFailed -> s(R.string.opening_refused)
        OpeningStage.AddInstalled -> s(R.string.opening_installed)
        OpeningStage.TooLate -> s(R.string.opening_late, app)
        OpeningStage.UsedUp -> s(R.string.opening_used)
        OpeningStage.UpdateExpired -> s(R.string.opening_update_expired)
    }
    val primary = s(when (state.stage) {
        OpeningStage.Before -> R.string.opening_start_wait
        OpeningStage.Ready -> R.string.opening_start
        OpeningStage.AddInstalled -> R.string.admin_next
        else -> R.string.user_ok
    })
    AdminFrame(state.context, title, primary, { onEvent(OpeningEvent.Continue) }, enabled = !state.sending,
        secondary = when (state.stage) {
            OpeningStage.Before, OpeningStage.Ready -> s(R.string.admin_not_now)
            OpeningStage.Countdown -> s(R.string.opening_stop)
            OpeningStage.AlreadyOpening -> s(R.string.opening_see)
            else -> null
        }, secondaryEnabled = !state.sending, onSecondary = { onEvent(when (state.stage) {
            OpeningStage.Countdown -> OpeningEvent.Stop
            OpeningStage.AlreadyOpening -> OpeningEvent.See
            else -> OpeningEvent.NotNow
        }) }) {
        when (state.stage) {
            OpeningStage.Before -> {
                Words(pluralStringResource(R.plurals.opening_before_body, state.left, state.waitMinutes, kind, state.durationMinutes, state.left))
                if (state.playDisclosure) Words(s(R.string.play_preview_disclosure))
            }
            OpeningStage.Countdown -> {
                Words(when (state.kind) {
                    OpeningKind.App -> s(R.string.opening_countdown_app_body, app)
                    OpeningKind.Update -> s(R.string.opening_countdown_update_body)
                    else -> s(R.string.opening_countdown_body, kind.replaceFirstChar { it.uppercase() })
                })
                Words(s(R.string.opening_countdown_rest))
            }
            OpeningStage.Ready -> Words(if (state.minutes == 1) pluralStringResource(R.plurals.opening_ready_body_one, state.left, state.left) else pluralStringResource(R.plurals.opening_ready_body, state.left, state.minutes, state.left))
            OpeningStage.NoOpenings -> Words(if (state.limit == 0) s(R.string.opening_zero) else s(R.string.opening_none_body, state.limit, state.time))
            OpeningStage.AlreadyOpening -> Words(if (state.alreadyOpen) s(R.string.opening_already_open, kind, state.time) else s(R.string.opening_already_countdown, kind))
            OpeningStage.AddInstalled -> Words(s(R.string.opening_installed_body, state.time))
            OpeningStage.TooLate -> Words(s(R.string.opening_late_body, app))
            OpeningStage.UsedUp -> Words(s(R.string.opening_used_body, app))
            OpeningStage.UpdateExpired -> Words(s(R.string.opening_update_expired_body, state.time))
            OpeningStage.SaveFailed, OpeningStage.OpenFailed -> Unit
        }
    }
}

/** All step sizes, bounds, immediate/24-hour routing and PIN checks are host decisions. */
@Composable
fun WaitsAndLimitsScreen(state: WaitsAndLimitsUiState, onEvent: (WaitsAndLimitsEvent) -> Unit) {
    AdminFrame(state.context, if (state.saved) s(R.string.waits_saved) else s(R.string.admin_waits),
        s(if (state.saved) R.string.user_ok else R.string.waits_save),
        { onEvent(if (state.saved) WaitsAndLimitsEvent.Ok else WaitsAndLimitsEvent.Save) },
        enabled = !state.sending && (state.saved || state.canSave)) {
        if (!state.saved) {
            Words(s(R.string.waits_body))
            Heading(s(R.string.waits_wait)); Words(state.waitLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S2)) {
                Box(Modifier.weight(1f)) { Choice(s(R.string.waits_minus), false, state.canDecreaseWait && !state.sending, s(R.string.waits_decrease_wait)) { onEvent(WaitsAndLimitsEvent.DecreaseWait) } }
                Box(Modifier.weight(1f)) { Choice(s(R.string.waits_plus), false, state.canIncreaseWait && !state.sending, s(R.string.waits_increase_wait)) { onEvent(WaitsAndLimitsEvent.IncreaseWait) } }
            }
            Heading(s(R.string.waits_limit)); Words(state.limit.toString())
            Row(horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S2)) {
                Box(Modifier.weight(1f)) { Choice(s(R.string.waits_minus), false, state.canDecreaseLimit && !state.sending, s(R.string.waits_decrease_limit)) { onEvent(WaitsAndLimitsEvent.DecreaseLimit) } }
                Box(Modifier.weight(1f)) { Choice(s(R.string.waits_plus), false, state.canIncreaseLimit && !state.sending, s(R.string.waits_increase_limit)) { onEvent(WaitsAndLimitsEvent.IncreaseLimit) } }
            }
        }
    }
}
