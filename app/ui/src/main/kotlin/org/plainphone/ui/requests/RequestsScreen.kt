// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.requests

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource as s
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.plainphone.ui.R
import org.plainphone.ui.components.*
import org.plainphone.ui.theme.*

enum class RequestUiStatus { Sending, Sent, NoSignal, NoPermission, Failed, NotConfirmed, NotSetUp, Invalid, DailyLimit, StorageError }
data class RequestUiRow(val text: String, val day: String, val status: RequestUiStatus, val id: String = "")
data class RequestsUiState(val isSetUp: Boolean = false, val recipientName: String = "", val message: String = "",
    val canSend: Boolean = false, val busy: Boolean = false, val result: RequestUiStatus? = null,
    val rows: List<RequestUiRow> = emptyList(), val historyOnly: Boolean = false,
    val userName: String = "", val removingId: String? = null, val removeFailed: Boolean = false)
sealed interface RequestEvent {
    data class Message(val text: String) : RequestEvent
    data object Send : RequestEvent
    data object Back : RequestEvent
    data object Compose : RequestEvent
    data class NotNow(val id: String) : RequestEvent
    data object Remove : RequestEvent
    data object Keep : RequestEvent
}
@Composable fun RequestsScreen(state: RequestsUiState, event: (RequestEvent) -> Unit) {
    if (state.historyOnly && state.removingId != null) {
        TaskScreen(s(R.string.requests_title), s(R.string.requests_remove), { event(RequestEvent.Remove) },
            primaryEnabled = !state.busy, secondary = s(R.string.admin_keep), secondaryEnabled = !state.busy,
            onSecondary = { event(RequestEvent.Keep) }) {
            val user = state.userName.withoutBidiControls()
            Text(if (user.isBlank() || user == s(R.string.current_phone_user)) s(R.string.requests_remove_unnamed)
                else s(R.string.admin_request_remove, user), style = PlainphoneType.Body)
            if (state.removeFailed) Text(s(R.string.requests_save_error), style = PlainphoneType.Body,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            state.rows.firstOrNull { it.id == state.removingId }?.let { Text(it.text, style = PlainphoneType.Body) }
        }
        return
    }
    val result = state.result
    val compose = !state.historyOnly && state.isSetUp && result == null
    TaskScreen(if (state.historyOnly) s(R.string.requests_title) else result?.let { statusTitle(it) } ?: s(R.string.more_ask),
        if (compose) s(if (state.busy) R.string.ask_sending else R.string.requests_send) else s(R.string.user_ok),
        { event(if (compose) RequestEvent.Send else if (result != null) RequestEvent.Compose else RequestEvent.Back) },
        primaryEnabled = !compose || state.canSend && !state.busy,
        back = s(R.string.admin_back), onBack = { event(RequestEvent.Back) },
        announceTitle = result != null, announcePrimary = compose && state.busy) {
        when {
            result == RequestUiStatus.StorageError -> Text(statusBody(result), style = PlainphoneType.Body)
            state.historyOnly -> if (state.rows.isEmpty()) Text(s(R.string.requests_empty), style = PlainphoneType.Body)
            result != null -> Text(statusBody(result), style = PlainphoneType.Body)
            !state.isSetUp -> {
                Text(s(R.string.requests_setup), style = PlainphoneType.Body)
                Text(s(R.string.requests_setup_help), style = PlainphoneType.Body)
            }
            else -> {
                Text(if (state.recipientName.isBlank()) s(R.string.requests_body) else s(R.string.ask_body, state.recipientName.withoutBidiControls()), style = PlainphoneType.Body)
                RequestField(s(R.string.ask_label), state.message, enabled = !state.busy) { event(RequestEvent.Message(it)) }
            }
        }
        if (state.rows.isNotEmpty()) {
            if (!state.historyOnly) Text(s(R.string.ask_history), style = PlainphoneType.Row)
            state.rows.forEach { row -> ScreenBox {
                Text(row.text, style = PlainphoneType.Body)
                Text(row.day, style = PlainphoneType.Small)
                Text(statusTitle(row.status), style = PlainphoneType.Small)
                if (row.status in setOf(RequestUiStatus.NoSignal, RequestUiStatus.NoPermission, RequestUiStatus.Failed, RequestUiStatus.NotConfirmed))
                    Text(statusBody(row.status), style = PlainphoneType.Small)
                if (state.historyOnly) ScreenLink(s(R.string.admin_not_now), { event(RequestEvent.NotNow(row.id)) },
                    centered = false, enabled = !state.busy && result != RequestUiStatus.StorageError)
            } }
        }
    }
}
@Composable private fun statusTitle(status: RequestUiStatus): String = s(when(status) {
    RequestUiStatus.Sending -> R.string.ask_sending
    RequestUiStatus.Sent -> R.string.requests_sent
    RequestUiStatus.NotConfirmed -> R.string.requests_unconfirmed
    RequestUiStatus.DailyLimit -> R.string.ask_limit_title
    RequestUiStatus.NotSetUp -> R.string.more_ask
    else -> R.string.requests_not_sent
})
@Composable private fun statusBody(status: RequestUiStatus): String = s(when(status) {
    RequestUiStatus.Sending -> R.string.requests_sending_body
    RequestUiStatus.Sent -> R.string.requests_sent_body
    RequestUiStatus.NoSignal -> R.string.requests_no_signal
    RequestUiStatus.NoPermission -> R.string.requests_no_permission
    RequestUiStatus.NotConfirmed -> R.string.requests_unconfirmed_body
    RequestUiStatus.NotSetUp -> R.string.ask_not_set_up
    RequestUiStatus.DailyLimit -> R.string.requests_limit_body
    RequestUiStatus.StorageError -> R.string.requests_storage_error
    RequestUiStatus.Invalid -> R.string.requests_invalid
    RequestUiStatus.Failed -> R.string.requests_failed_body
})

/** The host owns eligibility, validation, confirmation, PIN checks and saving. */
data class RequestRecipientUiState(val name: String = "", val number: String = "", val editing: Boolean = false,
    val confirming: Boolean = false, val removing: Boolean = false, val canSave: Boolean = false, val failed: Boolean = false,
    val readFailed: Boolean = false)
sealed interface RecipientEvent {
    data class Name(val text: String) : RecipientEvent
    data class Number(val text: String) : RecipientEvent
    data object Change : RecipientEvent
    data object Remove : RecipientEvent
    data object Continue : RecipientEvent
    data object Back : RecipientEvent
}
@Composable fun RequestRecipientScreen(state: RequestRecipientUiState, event: (RecipientEvent) -> Unit) {
    TaskScreen(s(R.string.requests_recipient),
        s(if (state.readFailed) R.string.user_ok else if (state.removing) R.string.requests_remove else if (state.confirming) R.string.waits_save else if (state.editing) R.string.admin_continue else if (state.number.isEmpty()) R.string.requests_set_up else R.string.requests_change),
        { event(if (state.readFailed) RecipientEvent.Back else if (state.editing || state.confirming || state.removing) RecipientEvent.Continue else RecipientEvent.Change) },
        primaryEnabled = !state.editing || state.canSave, back = s(R.string.admin_back), onBack = { event(RecipientEvent.Back) }) {
        if (state.failed) Text(s(R.string.requests_save_error), style = PlainphoneType.Body,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        when {
            state.readFailed -> Text(s(R.string.requests_recipient_read_error), style = PlainphoneType.Body,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            state.removing -> Text(s(R.string.requests_remove_confirm), style = PlainphoneType.Body)
            state.confirming -> {
                Text(s(R.string.requests_recipient_confirm), style = PlainphoneType.Body)
                if (state.name.isNotBlank()) Text(state.name.withoutBidiControls(), style = PlainphoneType.Body)
                Text(state.number, style = PlainphoneType.Body)
            }
            state.editing -> {
                RequestField(s(R.string.requests_name), state.name) { event(RecipientEvent.Name(it)) }
                RequestField(s(R.string.requests_number), state.number, phone = true) { event(RecipientEvent.Number(it)) }
            }
            else -> if (state.number.isEmpty()) Text(s(R.string.requests_setup), style = PlainphoneType.Body) else {
                if (state.name.isNotEmpty()) Text(state.name.withoutBidiControls(), style = PlainphoneType.Body)
                Text(state.number, style = PlainphoneType.Body)
                ScreenLink(s(R.string.requests_remove), { event(RecipientEvent.Remove) })
            }
        }
    }
}
@Composable private fun RequestField(label: String, value: String, phone: Boolean = false, enabled: Boolean = true, change: (String) -> Unit) {
    Text(label, style = PlainphoneType.Small)
    BasicTextField(value, change, enabled = enabled, textStyle = PlainphoneType.Body,
        keyboardOptions = KeyboardOptions(keyboardType = if(phone) KeyboardType.Phone else KeyboardType.Text, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = label }
            .border(PlainphoneSpace.Border, PlainphoneColors.Ink3).padding(PlainphoneSpace.S4))
}
