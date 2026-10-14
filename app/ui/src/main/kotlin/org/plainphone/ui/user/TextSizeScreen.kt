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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import org.plainphone.ui.R
import org.plainphone.ui.components.TaskScreen
import org.plainphone.ui.components.plainphoneAction
import org.plainphone.ui.theme.*

data class TextSizeUiState(val percent: String? = null, val canDecrease: Boolean = false,
    val canIncrease: Boolean = false, val failed: Boolean = false)
sealed interface TextSizeEvent {
    data object Smaller : TextSizeEvent
    data object Bigger : TextSizeEvent
    data object Done : TextSizeEvent
}

@Composable
fun TextSizeScreen(state: TextSizeUiState, onEvent: (TextSizeEvent) -> Unit) {
    TaskScreen(stringResource(R.string.settings_text_size), stringResource(R.string.admin_done),
        { onEvent(TextSizeEvent.Done) }, back = stringResource(R.string.settings_back),
        onBack = { onEvent(TextSizeEvent.Done) }) {
        // Use Android's configured sp scaling, including its non-linear large-text conversion.
        Text(stringResource(R.string.text_size_sample), style = PlainphoneType.Body)
        state.percent?.let { Text(stringResource(R.string.text_size_percent, it), style = PlainphoneType.Body) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SizeButton(stringResource(R.string.brightness_minus), stringResource(R.string.text_size_smaller), state.canDecrease) { onEvent(TextSizeEvent.Smaller) }
            SizeButton(stringResource(R.string.brightness_plus), stringResource(R.string.text_size_bigger), state.canIncrease) { onEvent(TextSizeEvent.Bigger) }
        }
        if (state.failed) Text(stringResource(R.string.text_size_failed), style = PlainphoneType.Body, modifier = Modifier.statusAnnouncement())
        Text(stringResource(R.string.text_size_note), style = PlainphoneType.Body)
    }
}

@Composable
private fun SizeButton(label: String, spokenLabel: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.sizeIn(minWidth = 64.dp, minHeight = 56.dp)
        .plainphoneAction(enabled = enabled, onClick = onClick).clearAndSetSemantics {
            contentDescription = spokenLabel
            role = Role.Button
            if (!enabled) disabled()
            onClick { if (enabled) onClick(); enabled }
        }, contentAlignment = Alignment.Center) {
        Text(label, modifier = Modifier.clearAndSetSemantics {},
            style = PlainphoneType.Row.copy(color = if (enabled) PlainphoneColors.Ink else PlainphoneColors.Ink3))
    }
}
