/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.trusted

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource as s
import org.plainphone.ui.R
import org.plainphone.ui.components.*
import org.plainphone.ui.theme.*

@Composable
fun SessionScreen(state: SessionUiState, onEvent: (SessionEvent) -> Unit) {
    val user = state.context.userName.withoutBidiControls()
    val helper = state.helper.withoutBidiControls()
    val self = state.context.trustedPersonName == null
    val title = s(when (state.stage) {
        SessionStage.Play -> R.string.session_play_title
        SessionStage.Settings -> R.string.session_settings_title
        SessionStage.CheckIntro -> R.string.admin_updates
        SessionStage.CheckResult -> R.string.session_result_title
        SessionStage.FailedApps -> R.string.session_failed_title
        SessionStage.CheckConfirm -> R.string.session_confirm
        SessionStage.Checking -> R.string.session_checking
        SessionStage.Restored -> R.string.session_restored
        SessionStage.NotReady -> if (self) R.string.session_not_ready_self else R.string.session_not_ready
    })
    val primary = when (state.stage) {
        SessionStage.Play -> s(R.string.session_play_open)
        SessionStage.Settings -> s(R.string.session_settings_open)
        SessionStage.CheckIntro -> s(R.string.session_check_start)
        SessionStage.CheckResult -> s(R.string.session_all_updated)
        SessionStage.FailedApps -> s(R.string.admin_next)
        SessionStage.CheckConfirm -> s(R.string.session_confirm_action)
        SessionStage.Checking -> null
        SessionStage.Restored -> s(R.string.admin_done)
        SessionStage.NotReady -> s(R.string.session_check_again)
    }
    AdminFrame(state.context, title, primary, { onEvent(SessionEvent.Continue) }, enabled = !state.sending,
        secondary = when (state.stage) {
            SessionStage.CheckIntro -> s(R.string.admin_not_now)
            SessionStage.CheckResult -> s(R.string.session_some_failed)
            SessionStage.NotReady -> s(R.string.session_details, helper)
            else -> null
        }, secondaryEnabled = !state.sending, onSecondary = { onEvent(when (state.stage) {
            SessionStage.CheckResult -> SessionEvent.SomeDidNotUpdate
            SessionStage.NotReady -> SessionEvent.Details
            else -> SessionEvent.NotNow
        }) }) {
        when (state.stage) {
            SessionStage.Play, SessionStage.Settings -> {
                if (self) Heading(s(R.string.session_closes_at, state.closesAt))
                else {
                    Heading(s(R.string.session_stay))
                    Words(s(if (state.stage == SessionStage.Play) R.string.session_play_custody else R.string.session_settings_custody, user))
                }
                Words(s(if (state.stage == SessionStage.Play) R.string.session_play_steps else R.string.session_settings_steps))
                Words(s(if (state.stage == SessionStage.Play) R.string.session_play_closes else R.string.session_settings_closes))
                if (state.stage == SessionStage.Settings) Words(s(R.string.session_settings_kept))
            }
            SessionStage.CheckIntro -> {
                Words(s(R.string.session_check_body))
                Heading(if (self) s(R.string.session_closes_at, state.closesAt) else s(R.string.session_stay))
                Words(s(R.string.session_check_steps))
                if (state.playServicesUnchanged) Words(s(R.string.session_services_unchanged, helper))
            }
            SessionStage.CheckResult -> Words(s(R.string.session_result_body))
            SessionStage.FailedApps -> {
                Words(s(R.string.session_failed_body))
                state.apps.forEach { app -> Choice(app.name.withoutBidiControls(), app.selected, !state.sending) { onEvent(SessionEvent.ToggleApp(app.id)) } }
            }
            SessionStage.CheckConfirm -> Words(s(R.string.session_confirm_body, state.dueDate))
            SessionStage.Checking -> { Words(s(R.string.session_checking_body)); CircularProgressIndicator(color = PlainphoneColors.Ink, trackColor = PlainphoneColors.Fill2) }
            SessionStage.Restored -> {
                if (!self) Words(s(R.string.session_restored_body, user))
                state.restoredLines.forEach { Words(it.withoutBidiControls()) }
                if (state.checkUnfinished) Words(s(R.string.session_unfinished))
            }
            SessionStage.NotReady -> {
                Words(if (state.helperIsCurrentPerson) s(R.string.session_helper_body) else s(R.string.session_not_ready_body, helper))
                if (state.showDetails) {
                    Heading(s(R.string.session_for, helper))
                    state.details.forEach { Words(it.withoutBidiControls()) }
                }
            }
        }
    }
}
