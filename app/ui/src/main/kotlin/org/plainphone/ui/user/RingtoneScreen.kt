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

data class RingtoneOption(val id: String, val title: String)
data class RingtoneUiState(val choices: List<RingtoneOption> = emptyList(), val selectedId: String? = null,
    val currentUnlisted: Boolean = false, val canSave: Boolean = false, val failed: Boolean = false)
sealed interface RingtoneEvent {
    data class Select(val id: String) : RingtoneEvent
    data object Done : RingtoneEvent
    data object Back : RingtoneEvent
}

@Composable
fun RingtoneScreen(state: RingtoneUiState, onEvent: (RingtoneEvent) -> Unit) {
    TaskScreen(stringResource(R.string.ringtone_title), stringResource(R.string.admin_done),
        { onEvent(RingtoneEvent.Done) }, primaryEnabled = state.canSave,
        back = stringResource(R.string.ringtone_back), onBack = { onEvent(RingtoneEvent.Back) }) {
        if (state.failed) Text(stringResource(R.string.ringtone_failed), style = PlainphoneType.Body, modifier = Modifier.statusAnnouncement())
        if (state.currentUnlisted) Text(stringResource(R.string.ringtone_current_stays), style = PlainphoneType.Body)
        for (choice in state.choices) {
            val isSelected = choice.id == state.selectedId
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .plainphoneAction { onEvent(RingtoneEvent.Select(choice.id)) }
                .semantics { selected = isSelected; role = Role.RadioButton }
                .padding(vertical = PlainphoneSpace.S2), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S2)) {
                Text(choice.title, style = PlainphoneType.Row, modifier = Modifier.weight(1f))
                if (isSelected) Text("✓", style = PlainphoneType.Row, modifier = Modifier.clearAndSetSemantics {})
            }
        }
    }
}
