// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.user

import org.plainphone.ui.components.statusAnnouncement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.plainphone.ui.components.fieldKeyboardNavigation
import org.plainphone.ui.R
import org.plainphone.ui.components.TaskScreen
import org.plainphone.ui.theme.*

enum class WifiJoinKind { Open, Personal, Unsupported }
data class WifiJoinUiState(val name: String, val kind: WifiJoinKind, val password: String = "",
    val showPassword: Boolean = false, val joining: Boolean = false, val failed: Boolean = false)
sealed interface WifiJoinEvent {
    data class Password(val value: String) : WifiJoinEvent
    data class ShowPassword(val value: Boolean) : WifiJoinEvent
    data object Connect : WifiJoinEvent
    data object Cancel : WifiJoinEvent
}
@Composable
fun WifiJoinScreen(state: WifiJoinUiState, onEvent: (WifiJoinEvent) -> Unit) {
    val unsupported = state.kind == WifiJoinKind.Unsupported
    TaskScreen(title = state.name,
        primary = stringResource(if (unsupported) R.string.wifi_join_ok else if (state.joining) R.string.wifi_joining else R.string.wifi_connect),
        onPrimary = { onEvent(if (unsupported) WifiJoinEvent.Cancel else WifiJoinEvent.Connect) },
        primaryEnabled = !state.joining,
        secondary = if (unsupported) null else stringResource(R.string.wifi_cancel),
        onSecondary = { onEvent(WifiJoinEvent.Cancel) }, announcePrimary = state.joining) {
        when (state.kind) {
            WifiJoinKind.Unsupported -> Text(stringResource(R.string.wifi_unsupported), style = PlainphoneType.Body)
            WifiJoinKind.Open -> Text(stringResource(R.string.wifi_open), style = PlainphoneType.Body)
            WifiJoinKind.Personal -> {
                val label = stringResource(R.string.wifi_password)
                Text(label, style = PlainphoneType.Body)
                BasicTextField(state.password, { onEvent(WifiJoinEvent.Password(it)) }, enabled = !state.joining,
                    textStyle = PlainphoneType.Body, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!state.joining) onEvent(WifiJoinEvent.Connect) }),
                    visualTransformation = if (state.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fieldKeyboardNavigation().fillMaxWidth().heightIn(min = 56.dp)
                        .border(PlainphoneSpace.Border, PlainphoneColors.Ink3).padding(PlainphoneSpace.S4)
                        .semantics { contentDescription = label })
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .toggleable(state.showPassword, enabled = !state.joining, role = Role.Switch,
                        onValueChange = { onEvent(WifiJoinEvent.ShowPassword(it)) }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S4)) {
                    Text(stringResource(R.string.wifi_show_password), style = PlainphoneType.Body, modifier = Modifier.weight(1f))
                    Switch(state.showPassword, onCheckedChange = null, enabled = !state.joining)
                }
            }
        }
        if (state.failed) Text(stringResource(R.string.wifi_join_failed), style = PlainphoneType.Body, modifier = Modifier.statusAnnouncement())
    }
}
