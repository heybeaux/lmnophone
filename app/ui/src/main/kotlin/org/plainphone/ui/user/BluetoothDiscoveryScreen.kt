// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.user

import org.plainphone.ui.components.statusAnnouncement
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import org.plainphone.ui.R
import org.plainphone.ui.components.TaskScreen
import org.plainphone.ui.components.plainphoneAction
import org.plainphone.ui.theme.*

data class NearbyBluetoothOption(val id: String, val name: String)
data class BluetoothDiscoveryUiState(val looking: Boolean = false,
    val devices: List<NearbyBluetoothOption> = emptyList(), val failed: Boolean = false)
sealed interface BluetoothDiscoveryEvent {
    data object Back : BluetoothDiscoveryEvent
    data object LookAgain : BluetoothDiscoveryEvent
    data class Select(val id: String) : BluetoothDiscoveryEvent
}

@Composable
fun BluetoothDiscoveryScreen(state: BluetoothDiscoveryUiState, onEvent: (BluetoothDiscoveryEvent) -> Unit) {
    TaskScreen(stringResource(R.string.bluetooth_pair_new),
        if (!state.looking && !state.failed) stringResource(R.string.bluetooth_look_again) else null,
        { onEvent(BluetoothDiscoveryEvent.LookAgain) },
        back = stringResource(R.string.bluetooth_back), onBack = { onEvent(BluetoothDiscoveryEvent.Back) }) {
        when {
            state.failed -> Text(stringResource(R.string.bluetooth_nearby_error), style = PlainphoneType.Body, modifier = Modifier.statusAnnouncement())
            else -> {
                if (state.looking) Text(stringResource(R.string.bluetooth_looking), style = PlainphoneType.Body, modifier = Modifier.statusAnnouncement())
                else if (state.devices.isEmpty()) Text(stringResource(R.string.bluetooth_nearby_none), style = PlainphoneType.Body, modifier = Modifier.statusAnnouncement())
                state.devices.forEach { option ->
                    BluetoothChoiceRow(option.name) { onEvent(BluetoothDiscoveryEvent.Select(option.id)) }
                }
            }
        }
    }
}

@Composable
fun BluetoothDeviceScreen(name: String, onBack: () -> Unit) {
    TaskScreen(name, null, {}, back = stringResource(R.string.bluetooth_back_pair), onBack = onBack) {
        Text(stringResource(R.string.bluetooth_pair_not_ready), style = PlainphoneType.Body)
    }
}

@Composable
internal fun BluetoothChoiceRow(title: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).plainphoneAction(onClick = onClick)
        .padding(vertical = PlainphoneSpace.S2), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = PlainphoneType.Row, modifier = Modifier.weight(1f))
        Text("›", style = PlainphoneType.Row.copy(color = PlainphoneColors.Ink3),
            modifier = Modifier.clearAndSetSemantics {})
    }
}
