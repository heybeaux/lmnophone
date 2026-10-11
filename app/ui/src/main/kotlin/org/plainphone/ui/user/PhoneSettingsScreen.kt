// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.user

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
import org.plainphone.ui.theme.*

/** Host owns the saved preference, permissions and availability; this screen only renders them. */
data class PhoneSettingsUiState(
    val showCommunicationCounts: Boolean = false,
    val missedCallsUnavailable: Boolean = false,
    val unreadTextsUnavailable: Boolean = false,
)

sealed interface PhoneSettingsEvent {
    data object Back : PhoneSettingsEvent
    data class ShowCommunicationCountsChanged(val enabled: Boolean) : PhoneSettingsEvent
}

/** PIN-free destination for MoreEvent.OpenPhoneSettings. Other settings are a later slice. */
@Composable
fun PhoneSettingsScreen(state: PhoneSettingsUiState, onEvent: (PhoneSettingsEvent) -> Unit) {
    Column(Modifier.fillMaxSize().background(PlainphoneColors.Paper)
        .verticalScroll(rememberScrollState()).padding(horizontal = PlainphoneSpace.S6)
        .padding(top = PlainphoneSpace.S2, bottom = PlainphoneSpace.S6),
        verticalArrangement = Arrangement.spacedBy(PlainphoneSpace.S4)) {
        ScreenLink(stringResource(R.string.back_more), { onEvent(PhoneSettingsEvent.Back) }, centered = false)
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
            if (state.missedCallsUnavailable) Text(stringResource(R.string.settings_calls_unavailable,
                stringResource(R.string.app_name)), style = PlainphoneType.Small)
            if (state.unreadTextsUnavailable) Text(stringResource(R.string.settings_texts_unavailable,
                stringResource(R.string.app_name)), style = PlainphoneType.Small)
        }
    }
}
