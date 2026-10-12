// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.user

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import org.plainphone.ui.R
import org.plainphone.ui.components.TaskScreen
import org.plainphone.ui.components.plainphoneAction
import org.plainphone.ui.theme.*
import kotlin.math.roundToInt

data class VolumeUiState(val level: Int, val min: Int, val max: Int)
data class SoundUiState(val ring: VolumeUiState? = null, val media: VolumeUiState? = null, val ringFailed: Boolean = false,
    val mediaFailed: Boolean = false, val ringAdjustable: Boolean = true)
enum class SoundStream { Ring, Media }
sealed interface SoundEvent {
    data class SetLevel(val stream: SoundStream, val level: Int) : SoundEvent
    data class Adjust(val stream: SoundStream, val delta: Int) : SoundEvent
    data object Done : SoundEvent
}

@Composable
fun SoundScreen(state: SoundUiState, onEvent: (SoundEvent) -> Unit) {
    TaskScreen(stringResource(R.string.settings_sound), stringResource(R.string.admin_done), { onEvent(SoundEvent.Done) }) {
        VolumeControl(stringResource(R.string.sound_ring), state.ring, SoundStream.Ring,
            state.ringFailed, state.ringAdjustable, onEvent)
        VolumeControl(stringResource(R.string.sound_media), state.media, SoundStream.Media,
            state.mediaFailed, true, onEvent)
    }
}

@Composable
private fun VolumeControl(label: String, state: VolumeUiState?, stream: SoundStream, failed: Boolean, adjustable: Boolean, onEvent: (SoundEvent) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label, style = PlainphoneType.Body)
    state?.let { Text(stringResource(R.string.volume_value, it.level), style = PlainphoneType.Body) }
    val min = state?.min ?: 0
    val max = state?.max ?: 1
    val enabled = adjustable && state != null && max > min
    var draft by remember(state) { mutableStateOf((state?.level ?: min).coerceIn(min, max).toFloat()) }
    Slider(value = draft, onValueChange = { draft = it }, enabled = enabled,
        onValueChangeFinished = {
            onEvent(SoundEvent.SetLevel(stream, draft.roundToInt()))
            draft = (state?.level ?: min).coerceIn(min, max).toFloat()
        }, valueRange = min.toFloat()..maxOf(min + 1, max).toFloat(),
        steps = (max - min - 1).coerceAtLeast(0),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = label })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        VolumeButton(stringResource(R.string.brightness_minus), stringResource(if (stream == SoundStream.Ring) R.string.sound_lower_ring else R.string.sound_lower_media), enabled && state!!.level > min) { onEvent(SoundEvent.Adjust(stream, -1)) }
        VolumeButton(stringResource(R.string.brightness_plus), stringResource(if (stream == SoundStream.Ring) R.string.sound_raise_ring else R.string.sound_raise_media), enabled && state!!.level < max) { onEvent(SoundEvent.Adjust(stream, 1)) }
    }
    if (!adjustable) Text(stringResource(R.string.sound_silent), style = PlainphoneType.Body)
    if (failed) Text(stringResource(R.string.volume_failed), style = PlainphoneType.Body)
    }
}

@Composable
private fun VolumeButton(label: String, spokenLabel: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.sizeIn(minWidth = 64.dp, minHeight = 56.dp).plainphoneAction(enabled = enabled, onClick = onClick).clearAndSetSemantics {
            contentDescription = spokenLabel
            role = Role.Button
            if (!enabled) disabled()
            onClick { if (enabled) onClick(); enabled }
        },
        contentAlignment = Alignment.Center) {
        Text(label, modifier = Modifier.clearAndSetSemantics {}, style = PlainphoneType.Row.copy(color = if (enabled) PlainphoneColors.Ink else PlainphoneColors.Ink3))
    }
}
