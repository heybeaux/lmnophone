/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.plainphone.ui.R
import org.plainphone.ui.components.*
import org.plainphone.ui.theme.*

@Composable
fun MoreScreen(state: MoreUiState, onEvent: (MoreEvent) -> Unit) {
    Column(Modifier.fillMaxSize().background(PlainphoneColors.Paper)
        .verticalScroll(rememberScrollState()).padding(horizontal = PlainphoneSpace.S6)
        .padding(top = PlainphoneSpace.S2, bottom = PlainphoneSpace.S6)) {
        ScreenLink(stringResource(R.string.back_home), { onEvent(MoreEvent.Back) }, centered = false)
        Text(stringResource(R.string.home_more), style = PlainphoneType.Title,
            modifier = Modifier.padding(vertical = PlainphoneSpace.S4).semantics { heading() })
        MenuRow(stringResource(R.string.more_emergency), stringResource(R.string.more_emergency_note),
            spokenText = stringResource(R.string.more_emergency_spoken)) {
            onEvent(MoreEvent.OpenEmergencyDialer)
        }
        Spacer(Modifier.height(PlainphoneSpace.S4))
        MenuRow(stringResource(R.string.more_phone_settings), stringResource(R.string.more_phone_settings_note)) {
            onEvent(MoreEvent.OpenPhoneSettings)
        }
        when (val mode = state.mode) {
            is MoreMode.Trusted -> {
                MenuRow(stringResource(R.string.more_ask), stringResource(R.string.more_ask_note, mode.requestRecipient.withoutBidiControls())) {
                    onEvent(MoreEvent.AskForApp)
                }
                val everyone = trustedPeopleText(mode.people, everyone = true)
                val either = trustedPeopleText(mode.people)
                if (everyone != null && either != null) {
                    MenuRow(stringResource(R.string.more_for, everyone), stringResource(
                        if (mode.people.size == 1) R.string.more_for_note else R.string.more_for_two_note, either)) {
                        onEvent(MoreEvent.OpenTrustedPerson)
                    }
                }
            }
            is MoreMode.SelfManaged -> {
                MenuRow(stringResource(R.string.more_add), pluralStringResource(R.plurals.more_wait, mode.waitMinutes, mode.waitMinutes)) {
                    onEvent(MoreEvent.AddApp)
                }
                MenuRow(stringResource(R.string.more_your_settings), stringResource(R.string.more_your_settings_note)) {
                    onEvent(MoreEvent.OpenSelfManagedSettings)
                }
            }
        }
        Spacer(Modifier.height(PlainphoneSpace.S8))
        ScreenLink(stringResource(R.string.more_about), { onEvent(MoreEvent.OpenAbout) })
    }
}

@Composable
private fun MenuRow(title: String, description: String, spokenText: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().plainphoneAction(onClick = onClick).heightIn(min = 64.dp)
        .padding(vertical = PlainphoneSpace.S4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S2)) {
        Column(Modifier.weight(1f).then(if (spokenText == null) Modifier else Modifier.clearAndSetSemantics {
            contentDescription = spokenText
        }), verticalArrangement = Arrangement.spacedBy(PlainphoneSpace.S1)) {
            Text(title, style = PlainphoneType.Row)
            Text(description, style = PlainphoneType.Small)
        }
        Text("›", style = PlainphoneType.Row.copy(color = PlainphoneColors.Ink3),
            modifier = Modifier.clearAndSetSemantics {})
    }
}
