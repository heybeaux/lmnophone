/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.trusted

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource as s
import org.plainphone.ui.R
import org.plainphone.ui.components.*
import org.plainphone.ui.theme.*

@Composable
fun AppReviewScreen(state: AppReviewUiState, onEvent: (AppReviewEvent) -> Unit) {
    val app = state.app.name.withoutBidiControls()
    val user = state.context.userName.withoutBidiControls()
    val helper = state.helper.withoutBidiControls()
    val title = when (state.stage) {
        AppReviewStage.Review -> s(R.string.admin_add_title, app)
        AppReviewStage.Unreviewed -> s(R.string.admin_unreviewed_title)
        AppReviewStage.Confirm -> s(R.string.admin_add_confirm, app, user)
        AppReviewStage.Added -> s(R.string.admin_added, app)
        AppReviewStage.Hidden -> s(R.string.admin_hidden, app)
        AppReviewStage.Remove -> s(R.string.admin_remove_title, app)
        AppReviewStage.Removed -> s(R.string.admin_removed)
    }
    val primary = when (state.stage) {
        AppReviewStage.Review -> s(R.string.admin_next)
        AppReviewStage.Unreviewed -> s(R.string.admin_add_anyway)
        AppReviewStage.Confirm -> s(R.string.admin_add, app)
        AppReviewStage.Remove -> s(R.string.admin_remove)
        else -> s(R.string.user_ok)
    }
    val complete = state.stage in listOf(AppReviewStage.Added, AppReviewStage.Hidden, AppReviewStage.Removed)
    AdminFrame(state.context, title, primary, { onEvent(if (complete) AppReviewEvent.Ok else AppReviewEvent.Continue) },
        enabled = !state.sending,
        secondary = if (state.stage in listOf(AppReviewStage.Review, AppReviewStage.Unreviewed)) s(R.string.admin_dont_add) else null,
        secondaryEnabled = !state.sending, onSecondary = { onEvent(AppReviewEvent.DontAdd) }) {
        when (state.stage) {
            AppReviewStage.Review -> {
                Heading(app)
                Words(s(R.string.admin_made_by, state.app.developer.withoutBidiControls()))
                state.request?.let { Words(s(R.string.admin_request_mismatch, user, it, app, state.app.developer.withoutBidiControls())) }
                Heading(s(R.string.admin_web))
                ScreenBox {
                    when (val review = state.review) {
                        is WebReview.Checked -> { Words(review.note); Words(s(R.string.admin_review_checked, review.version)) }
                        is WebReview.Older -> { Words(review.note); Heading(s(R.string.admin_review_older)); Words(s(R.string.admin_review_versions, review.reviewed, review.installed)) }
                        WebReview.Unknown -> { Heading(s(R.string.admin_review_unknown)); Words(s(R.string.admin_review_team, app, helper)) }
                    }
                }
                ActionRow(s(R.string.app_more_about), enabled = !state.sending) { onEvent(AppReviewEvent.MoreAbout) }
            }
            AppReviewStage.Unreviewed -> Words(s(R.string.admin_unreviewed_body, app, user, helper))
            AppReviewStage.Confirm -> Words(s(R.string.admin_add_body, state.allowed, state.denied))
            AppReviewStage.Remove -> Words(s(R.string.admin_remove_body, app, user))
            else -> Unit
        }
    }
}

private val permissionResources = mapOf(
    PermissionGroup.Camera to (R.string.permission_camera to R.string.permission_camera_why),
    PermissionGroup.Microphone to (R.string.permission_microphone to R.string.permission_microphone_why),
    PermissionGroup.Location to (R.string.permission_location to R.string.permission_location_why),
    PermissionGroup.Contacts to (R.string.permission_contacts to R.string.permission_contacts_why),
    PermissionGroup.Phone to (R.string.permission_phone to R.string.permission_phone_why),
    PermissionGroup.CallHistory to (R.string.permission_callhistory to R.string.permission_callhistory_why),
    PermissionGroup.Sms to (R.string.permission_sms to R.string.permission_sms_why),
    PermissionGroup.Calendar to (R.string.permission_calendar to R.string.permission_calendar_why),
    PermissionGroup.Nearby to (R.string.permission_nearby to R.string.permission_nearby_why),
    PermissionGroup.Media to (R.string.permission_media to R.string.permission_media_why),
    PermissionGroup.Notifications to (R.string.permission_notifications to R.string.permission_notifications_why),
    PermissionGroup.Health to (R.string.permission_health to R.string.permission_health_why),
)
@Composable
fun PermissionsScreen(state: PermissionsUiState, onEvent: (PermissionsEvent) -> Unit) {
    val app = state.appName.withoutBidiControls()
    AdminFrame(state.context, s(R.string.admin_permissions_title, app), s(R.string.admin_next),
        { onEvent(PermissionsEvent.Continue) }, enabled = state.canContinue && !state.sending) {
        Words(s(R.string.admin_permissions_body, state.context.userName.withoutBidiControls(), app))
        state.permissions.forEach { permission ->
            val (title, why) = permissionResources.getValue(permission.group)
            val label = s(title)
            Heading(label)
            Words(permission.why ?: s(why, app))
            if (permission.unavailable) Words(s(R.string.admin_permission_unavailable, app))
            else {
                @Composable fun options() {
                    PermissionDecision.entries.forEach { decision ->
                        val text = s(if (decision == PermissionDecision.Allow) R.string.admin_allow else R.string.admin_deny)
                        Choice(text, permission.decision == decision, !state.sending, "$label: $text") { onEvent(PermissionsEvent.Choose(permission.id, decision)) }
                    }
                }
                // Stacked choices preserve the full words at large text, never shrink either label.
                if (LocalDensity.current.fontScale > 1f) Column(verticalArrangement = Arrangement.spacedBy(PlainphoneSpace.S2)) { options() }
                else Row(horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S2)) {
                    PermissionDecision.entries.forEach { decision ->
                        Box(Modifier.weight(1f)) {
                            val text = s(if (decision == PermissionDecision.Allow) R.string.admin_allow else R.string.admin_deny)
                            Choice(text, permission.decision == decision, !state.sending, "$label: $text") { onEvent(PermissionsEvent.Choose(permission.id, decision)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppsOnPhoneScreen(state: AppsOnPhoneUiState, onEvent: (AppsOnPhoneEvent) -> Unit) {
    AdminFrame(state.context, s(R.string.admin_apps), secondary = s(R.string.admin_back), onSecondary = { onEvent(AppsOnPhoneEvent.Back) }) {
        state.apps.forEach { row ->
            val name = row.app.name.withoutBidiControls()
            if (row.alwaysOn) { Heading(name); Words(s(R.string.admin_always)) }
            else {
                ActionRow(name) { onEvent(AppsOnPhoneEvent.Open(row.app.id)) }
                Choice(s(R.string.admin_move_up), false, row.canMoveUp && !state.sending, s(R.string.admin_move_up_named, name)) { onEvent(AppsOnPhoneEvent.MoveUp(row.app.id)) }
                Choice(s(R.string.admin_move_down), false, row.canMoveDown && !state.sending, s(R.string.admin_move_down_named, name)) { onEvent(AppsOnPhoneEvent.MoveDown(row.app.id)) }
            }
        }
    }
}

@Composable
fun TrustedRequestsScreen(state: TrustedRequestsUiState, onEvent: (TrustedRequestsEvent) -> Unit) {
    val user = state.context.userName.withoutBidiControls()
    if (state.removeRequestId != null) {
        AdminFrame(state.context, s(R.string.admin_request_remove, user), s(R.string.admin_remove),
            { onEvent(TrustedRequestsEvent.Remove) }, enabled = !state.sending, secondary = s(R.string.admin_keep),
            secondaryEnabled = !state.sending, onSecondary = { onEvent(TrustedRequestsEvent.Keep) }) {}
    } else AdminFrame(state.context, s(R.string.admin_requests, user), secondary = s(R.string.admin_back), onSecondary = { onEvent(TrustedRequestsEvent.Back) }) {
        state.requests.forEach { request -> ScreenBox {
            Heading(s(R.string.admin_from, user))
            // Request text is deliberately unmodified plain text, never a link or installed identity.
            Words(s(R.string.admin_quote, request.text))
            Text(request.timestamp, style = PlainphoneType.Small)
            Words(s(if (request.textSent) R.string.admin_request_sent else R.string.admin_request_failed))
            Choice(s(R.string.admin_find), false, !state.sending) { onEvent(TrustedRequestsEvent.Find(request.id)) }
            Choice(s(R.string.admin_not_now), false, !state.sending) { onEvent(TrustedRequestsEvent.NotNow(request.id)) }
        } }
    }
}
