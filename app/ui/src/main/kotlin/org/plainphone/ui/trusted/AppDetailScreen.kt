/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.trusted

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource as s
import org.plainphone.ui.R
import org.plainphone.ui.components.*

@Composable
fun AppDetailScreen(state: AppDetailUiState, onEvent: (AppDetailEvent) -> Unit) {
    val app = state.app.name.withoutBidiControls()
    val user = state.context.userName.withoutBidiControls()
    val helper = state.helper.withoutBidiControls()
    val title = when (state.stage) {
        AppDetailStage.Details -> app
        AppDetailStage.DidNotUpdate -> s(R.string.app_failed_title, app)
        AppDetailStage.Unexpected -> s(R.string.app_unexpected)
        AppDetailStage.CouldNotHide -> s(R.string.app_not_hidden)
        AppDetailStage.TextsNotSending -> s(R.string.app_texts_title)
    }
    val primary = when (state.stage) {
        AppDetailStage.Details -> if (state.context.trustedPersonName == null) s(R.string.app_self_remove, app) else s(R.string.app_detail_remove, user)
        AppDetailStage.DidNotUpdate -> s(R.string.app_sorted)
        AppDetailStage.TextsNotSending -> s(if (state.sending) R.string.ask_sending else R.string.app_test_text)
        else -> s(R.string.app_leave_hidden)
    }
    AdminFrame(state.context, title, primary, { onEvent(AppDetailEvent.Primary) }, enabled = !state.sending,
        secondary = when (state.stage) {
            AppDetailStage.Details -> null
            AppDetailStage.DidNotUpdate -> s(R.string.app_keep_list)
            AppDetailStage.TextsNotSending -> s(R.string.app_ask_helper, helper)
            else -> s(R.string.app_add_it)
        }, secondaryEnabled = !state.sending, onSecondary = { onEvent(AppDetailEvent.Secondary) }) {
        when (state.stage) {
            AppDetailStage.Details -> {
                Words(s(R.string.app_detail_meta, state.version, state.added, state.updated))
                ScreenBox { when (val review = state.review) {
                    WebReview.Unknown -> Heading(s(R.string.admin_review_unknown))
                    is WebReview.Checked -> { Words(review.note); Words(s(R.string.admin_review_checked, review.version)) }
                    is WebReview.Older -> { Words(review.note); Heading(s(R.string.admin_review_older)); Words(s(R.string.admin_review_versions, review.reviewed, review.installed)) }
                } }
                Heading(s(R.string.app_detail_permissions, app))
                state.permissions.forEach { Words(it) }
                if (state.context.trustedPersonName == null) Words(s(R.string.app_self_permissions_later, app))
                else ActionRow(s(R.string.app_detail_change, app), enabled = !state.sending) { onEvent(AppDetailEvent.Permissions) }
            }
            AppDetailStage.DidNotUpdate -> Words(s(R.string.app_failed_body, state.updated, app, helper))
            AppDetailStage.Unexpected, AppDetailStage.CouldNotHide -> {
                Words(s(R.string.app_unexpected_body, app, state.app.developer.withoutBidiControls(), state.added))
                if (state.stage == AppDetailStage.CouldNotHide) Words(s(R.string.app_not_hidden_body, helper))
            }
            AppDetailStage.TextsNotSending -> Words(s(R.string.app_texts_body, user))
        }
    }
}
