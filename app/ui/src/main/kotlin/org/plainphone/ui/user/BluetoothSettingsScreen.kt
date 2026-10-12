// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.plainphone.ui.R
import org.plainphone.ui.components.ScreenLink
import org.plainphone.ui.theme.*

data class BluetoothSettingsUiState(val enabled: Boolean = false,
    val paired: List<String?> = emptyList(), val readFailed: Boolean = true)

@Composable
fun BluetoothSettingsScreen(state: BluetoothSettingsUiState, onBack: () -> Unit, onPairNew: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().background(PlainphoneColors.Paper)
        .verticalScroll(rememberScrollState()).padding(PlainphoneSpace.S6),
        verticalArrangement = Arrangement.spacedBy(PlainphoneSpace.S4)) {
        ScreenLink(stringResource(R.string.settings_back), onBack, centered = false)
        Text(stringResource(R.string.settings_bluetooth), style = PlainphoneType.Title,
            modifier = Modifier.semantics { heading() })
        when {
            state.readFailed -> Text(stringResource(R.string.bluetooth_unavailable), style = PlainphoneType.Body)
            !state.enabled -> Text(stringResource(R.string.bluetooth_off), style = PlainphoneType.Body)
            else -> {
                Text(stringResource(R.string.bluetooth_paired), style = PlainphoneType.Row,
                    modifier = Modifier.semantics { heading() })
                if (state.paired.isEmpty()) Text(stringResource(R.string.bluetooth_none), style = PlainphoneType.Body)
                else state.paired.forEach { Text(it ?: stringResource(R.string.bluetooth_unnamed), style = PlainphoneType.Body) }
            }
        }
        if (state.enabled && !state.readFailed)
            BluetoothChoiceRow(stringResource(R.string.bluetooth_pair_new), onPairNew)
        else Text(stringResource(R.string.bluetooth_preview_note), style = PlainphoneType.Small)
    }
}
