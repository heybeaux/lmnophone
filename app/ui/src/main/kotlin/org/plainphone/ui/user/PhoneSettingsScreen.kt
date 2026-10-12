// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.user

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.plainphone.ui.R
import org.plainphone.ui.components.ScreenLink
import org.plainphone.ui.components.plainphoneAction
import org.plainphone.ui.theme.*

/** Host owns the saved preference, permissions and availability; this screen only renders them. */
data class PhoneSettingsUiState(
    val showCommunicationCounts: Boolean = false,
    val missedCallsUnavailable: Boolean = false,
    val unreadTextsUnavailable: Boolean = false,
    val unsupportedApps: Boolean = false,
    val pendingChange: String? = null,
)

sealed interface PhoneSettingsEvent {
    data object OpenWaitingChange : PhoneSettingsEvent
    data object OpenBrightness : PhoneSettingsEvent
    data object OpenWifi : PhoneSettingsEvent
    data object OpenSound : PhoneSettingsEvent
    data object OpenTextSize : PhoneSettingsEvent
    data object OpenBattery : PhoneSettingsEvent
    data object Back : PhoneSettingsEvent
    data class ShowCommunicationCountsChanged(val enabled: Boolean) : PhoneSettingsEvent
}

/** PIN-free destination for MoreEvent.OpenPhoneSettings. Brightness and Battery are the first U7 rows. */
@Composable
fun PhoneSettingsScreen(state: PhoneSettingsUiState, onEvent: (PhoneSettingsEvent) -> Unit) {
    Column(Modifier.fillMaxSize().background(PlainphoneColors.Paper)
        .verticalScroll(rememberScrollState()).padding(horizontal = PlainphoneSpace.S6)
        .padding(top = PlainphoneSpace.S2, bottom = PlainphoneSpace.S6),
        verticalArrangement = Arrangement.spacedBy(PlainphoneSpace.S4)) {
        ScreenLink(stringResource(R.string.back_more), { onEvent(PhoneSettingsEvent.Back) }, centered = false)
        state.pendingChange?.let { change ->
            val spoken = stringResource(R.string.settings_waiting_spoken, change)
            val shape = RoundedCornerShape(PlainphoneSpace.BoxRadius)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .background(PlainphoneColors.Fill1, shape)
                .plainphoneAction(shape) { onEvent(PhoneSettingsEvent.OpenWaitingChange) }
                .clearAndSetSemantics { contentDescription = spoken }
                .padding(PlainphoneSpace.S4),
                horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S2),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PlainphoneSpace.S2)) {
                    Text(stringResource(R.string.wait_title), style = PlainphoneType.Row)
                    Text(change, style = PlainphoneType.Body)
                }
                Text("›", style = PlainphoneType.Row.copy(color = PlainphoneColors.Ink2))
            }
        }
        SettingsRow(stringResource(R.string.settings_wifi)) { onEvent(PhoneSettingsEvent.OpenWifi) }
        SettingsRow(stringResource(R.string.settings_brightness)) { onEvent(PhoneSettingsEvent.OpenBrightness) }
        SettingsRow(stringResource(R.string.settings_sound)) { onEvent(PhoneSettingsEvent.OpenSound) }
        SettingsRow(stringResource(R.string.settings_text_size)) { onEvent(PhoneSettingsEvent.OpenTextSize) }
        SettingsRow(stringResource(R.string.settings_battery)) { onEvent(PhoneSettingsEvent.OpenBattery) }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = state.showCommunicationCounts, role = Role.Switch,
                onValueChange = { onEvent(PhoneSettingsEvent.ShowCommunicationCountsChanged(it)) })
            .padding(vertical = PlainphoneSpace.S2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S4)) {
            Text(stringResource(R.string.settings_communication_counts), style = PlainphoneType.Body,
                modifier = Modifier.weight(1f))
            Switch(checked = state.showCommunicationCounts, onCheckedChange = null)
        }
        Text(stringResource(R.string.settings_counts_coverage), style = PlainphoneType.Small)
        Text(stringResource(R.string.settings_counts_permission), style = PlainphoneType.Small)
        if (state.showCommunicationCounts) {
            if (state.unsupportedApps) Text(stringResource(R.string.settings_counts_supported_apps), style = PlainphoneType.Small)
            if (state.missedCallsUnavailable) Text(stringResource(R.string.settings_calls_unavailable,
                stringResource(R.string.app_name)), style = PlainphoneType.Small)
            if (state.unreadTextsUnavailable) Text(stringResource(R.string.settings_texts_unavailable,
                stringResource(R.string.app_name)), style = PlainphoneType.Small)
        }
    }
}

@Composable
private fun SettingsRow(title: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).plainphoneAction(onClick = onClick)
        .padding(vertical = PlainphoneSpace.S2), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = PlainphoneType.Row, modifier = Modifier.weight(1f))
        Text("›", style = PlainphoneType.Row.copy(color = PlainphoneColors.Ink3))
    }
}
