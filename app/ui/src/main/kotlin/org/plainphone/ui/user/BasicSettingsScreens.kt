// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.user

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import org.plainphone.ui.R
import org.plainphone.ui.components.TaskScreen
import org.plainphone.ui.components.plainphoneAction
import org.plainphone.ui.theme.*
import kotlin.math.roundToInt

data class BrightnessUiState(val percent: Int?, val automatic: Boolean, val failed: Boolean = false)
sealed interface BrightnessEvent {
    data class SetPercent(val percent: Int) : BrightnessEvent
    data class Adjust(val delta: Int) : BrightnessEvent
    data class SetAutomatic(val enabled: Boolean) : BrightnessEvent
    data object Done : BrightnessEvent
}
enum class BatteryStatus { Charging, Charged, NotCharging }
data class BatteryUiState(val percent: Int? = null, val charging: Boolean? = null,
    val status: BatteryStatus? = charging?.let { if (it) BatteryStatus.Charging else BatteryStatus.NotCharging })

@Composable
fun BatteryScreen(state: BatteryUiState, onBack: () -> Unit) {
    TaskScreen(stringResource(R.string.settings_battery), null, {},
        back = stringResource(R.string.settings_back), onBack = onBack) {
        Text(if (state.percent == null) stringResource(R.string.battery_unknown)
            else stringResource(R.string.battery_level, state.percent), style = PlainphoneType.Body)
        state.status?.let { Text(stringResource(when (it) {
            BatteryStatus.Charging -> R.string.battery_charging
            BatteryStatus.Charged -> R.string.battery_charged
            BatteryStatus.NotCharging -> R.string.battery_not_charging
        }),
            style = PlainphoneType.Body) }
    }
}

@Composable
fun BrightnessScreen(state: BrightnessUiState, onEvent: (BrightnessEvent) -> Unit) {
    TaskScreen(stringResource(R.string.settings_brightness), stringResource(R.string.admin_done),
        { onEvent(BrightnessEvent.Done) }) {
        val enabled = state.percent != null && !state.automatic
        // The thumb may track a drag, but only the read-back percentage is presented as saved.
        var draft by remember(state) { mutableStateOf((state.percent ?: 10).coerceAtLeast(10).toFloat()) }
        if (state.percent != null && !state.automatic) Text(stringResource(R.string.brightness_percent, state.percent), style = PlainphoneType.Body)
        val sliderLabel = stringResource(R.string.settings_brightness)
        Slider(value = draft, onValueChange = { draft = it }, enabled = enabled,
            onValueChangeFinished = {
                onEvent(BrightnessEvent.SetPercent(draft.roundToInt()))
                draft = (state.percent ?: 10).coerceAtLeast(10).toFloat()
            },
            valueRange = 10f..100f, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .semantics { contentDescription = sliderLabel })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BrightnessButton(stringResource(R.string.brightness_minus), stringResource(R.string.brightness_lower), enabled && (state.percent ?: 0) > 10) {
                onEvent(BrightnessEvent.Adjust(-10))
            }
            BrightnessButton(stringResource(R.string.brightness_plus), stringResource(R.string.brightness_raise), enabled && (state.percent ?: 100) < 100) {
                onEvent(BrightnessEvent.Adjust(10))
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(state.automatic, enabled = state.percent != null, role = Role.Switch,
                onValueChange = { onEvent(BrightnessEvent.SetAutomatic(it)) }),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.brightness_automatic), style = PlainphoneType.Body, modifier = Modifier.weight(1f))
            Switch(state.automatic, onCheckedChange = null, enabled = state.percent != null)
        }
        if (state.failed) Text(stringResource(R.string.brightness_failed), style = PlainphoneType.Body)
    }
}

@Composable
private fun BrightnessButton(label: String, spokenLabel: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.sizeIn(minWidth = 64.dp, minHeight = 56.dp)
        .plainphoneAction(enabled = enabled, onClick = onClick).semantics { contentDescription = spokenLabel }, contentAlignment = Alignment.Center) {
        Text(label, modifier = Modifier.clearAndSetSemantics {}, style = PlainphoneType.Row.copy(color = if (enabled) PlainphoneColors.Ink else PlainphoneColors.Ink3))
    }
}
