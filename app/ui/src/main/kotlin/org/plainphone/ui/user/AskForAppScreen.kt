/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.user

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.plainphone.ui.R
import org.plainphone.ui.components.*
import org.plainphone.ui.theme.*

@Composable
fun AskForAppScreen(state: AskForAppUiState, onEvent: (AskForAppEvent) -> Unit) {
    if (!state.isSetUp) {
        TaskScreen(stringResource(R.string.more_ask), stringResource(R.string.user_ok), { onEvent(AskForAppEvent.Back) },
            back = stringResource(R.string.back_more), onBack = { onEvent(AskForAppEvent.Back) }) {
            Text(stringResource(R.string.ask_not_set_up), style = PlainphoneType.Body)
            Text(stringResource(R.string.ask_missing_recipient), style = PlainphoneType.Body)
        }
        return
    }
    TaskScreen(stringResource(R.string.more_ask), if (state.sending) stringResource(R.string.ask_sending) else stringResource(R.string.ask_send, state.recipientName.withoutBidiControls()),
        { onEvent(AskForAppEvent.Send) }, primaryEnabled = state.canSend && !state.sending,
        back = stringResource(R.string.back_more), onBack = { onEvent(AskForAppEvent.Back) }) {
        Text(stringResource(R.string.ask_body, state.recipientName.withoutBidiControls()), style = PlainphoneType.Body)
        val label = stringResource(R.string.ask_label)
        Column(verticalArrangement = Arrangement.spacedBy(PlainphoneSpace.S2)) {
            Text(label, style = PlainphoneType.Small.copy(color = PlainphoneColors.Ink, fontWeight = FontWeight.Medium))
            val interactions = remember { MutableInteractionSource() }
            val focused by interactions.collectIsFocusedAsState()
            BasicTextField(value = state.message,
                onValueChange = { onEvent(AskForAppEvent.MessageChanged(it)) },
                modifier = Modifier.fillMaxWidth().testTag("request-message")
                    .semantics { contentDescription = label }
                    .border(if (focused) PlainphoneSpace.Focus else PlainphoneSpace.Border,
                        if (focused) PlainphoneColors.Ink else PlainphoneColors.Ink3,
                        RoundedCornerShape(PlainphoneSpace.BoxRadius))
                    .heightIn(min = 96.dp).padding(PlainphoneSpace.S4),
                textStyle = PlainphoneType.Body, cursorBrush = SolidColor(PlainphoneColors.Ink),
                interactionSource = interactions,
                decorationBox = { inner ->
                    Box {
                        if (state.message.isEmpty()) Text(stringResource(R.string.ask_hint),
                            style = PlainphoneType.Body.copy(color = PlainphoneColors.Ink3))
                        inner()
                    }
                })
            state.charactersRemaining?.let {
                Text(pluralStringResource(R.plurals.ask_characters_left, it, it), style = PlainphoneType.Small)
            }
        }
        RequestHistory(state.requests) { onEvent(AskForAppEvent.Retry(it)) }
    }
}

@Composable
private fun RequestHistory(requests: List<AppRequestRow>, onRetry: (String) -> Unit) {
    if (requests.isEmpty()) return
    Text(stringResource(R.string.ask_history), style = PlainphoneType.Row,
        modifier = Modifier.semantics { heading() })
    requests.take(10).forEach { request ->
        ScreenBox {
            Text(request.message, style = PlainphoneType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(request.day, style = PlainphoneType.Small)
            if (request.status == AppRequestStatus.Failed) {
                ScreenLink(stringResource(R.string.ask_failed), { onRetry(request.id) }, centered = false)
            } else Text(requestStatusText(request.status), style = PlainphoneType.Small)
        }
    }
}

@Composable
private fun requestStatusText(status: AppRequestStatus): String = when (status) {
    AppRequestStatus.Sending -> stringResource(R.string.ask_sending)
    AppRequestStatus.Sent -> stringResource(R.string.ask_sent)
    is AppRequestStatus.Delivered -> stringResource(R.string.ask_delivered, status.recipientName.withoutBidiControls())
    AppRequestStatus.Failed -> stringResource(R.string.ask_failed)
    AppRequestStatus.Added -> stringResource(R.string.ask_added)
    AppRequestStatus.NotAdded -> stringResource(R.string.ask_not_added)
}

@Composable
fun AppRequestResultScreen(state: AppRequestResultUiState, onEvent: (AppRequestResultEvent) -> Unit) {
    val title = when (state.result) {
        AppRequestResult.Sent -> stringResource(R.string.ask_sent_title, state.recipientName.withoutBidiControls())
        AppRequestResult.Failed -> stringResource(R.string.ask_failed_title)
        AppRequestResult.DailyLimit -> stringResource(R.string.ask_limit_title)
    }
    val failed = state.result == AppRequestResult.Failed
    TaskScreen(title, stringResource(if (failed && state.sending) R.string.ask_sending else if (failed) R.string.user_try_again else R.string.user_ok),
        { onEvent(if (failed) AppRequestResultEvent.Retry else AppRequestResultEvent.Ok) },
        primaryEnabled = !failed || !state.sending,
        secondary = if (failed) stringResource(R.string.ask_leave_saved) else null,
        onSecondary = { onEvent(AppRequestResultEvent.Ok) }) {
        Text(stringResource(when (state.result) {
            AppRequestResult.Sent -> R.string.ask_sent_body
            AppRequestResult.Failed -> R.string.ask_failed_body
            AppRequestResult.DailyLimit -> R.string.ask_limit_body
        }, state.recipientName.withoutBidiControls()), style = PlainphoneType.Body)
        if (state.result == AppRequestResult.Sent) {
            RequestHistory(state.requests) { onEvent(AppRequestResultEvent.RetryRequest(it)) }
        }
    }
}
