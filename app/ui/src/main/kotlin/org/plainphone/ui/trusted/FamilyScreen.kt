// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.trusted

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.plainphone.ui.R
import org.plainphone.ui.components.ScreenLink
import org.plainphone.ui.theme.*

enum class FamilyStage { List, Name, Number, Picture, Confirm, Remove, TurnOff }
data class FamilyPersonRow(val id: String, val name: String)
data class FamilyUiState(val context: AdminContext, val stage: FamilyStage = FamilyStage.List,
    val enabled: Boolean = false, val people: List<FamilyPersonRow> = emptyList(),
    val name: String = "", val spokenName: String = "", val number: String = "", val editing: Boolean = false,
    val canContinue: Boolean = false, val canAdd: Boolean = true, val failed: Boolean = false) {
    override fun toString() = "FamilyUiState(redacted)"
}
sealed interface FamilyEvent {
    data object Back : FamilyEvent
    data object Enable : FamilyEvent
    data object Add : FamilyEvent
    data class Edit(val id: String) : FamilyEvent
    data class Name(val value: String) : FamilyEvent
    data class SpokenName(val value: String) : FamilyEvent
    data class Number(val value: String) : FamilyEvent
    data object Hear : FamilyEvent
    data object Continue : FamilyEvent
    data object AskRemove : FamilyEvent
    data object AskTurnOff : FamilyEvent
}

/** No persisted entry fields, number logging, message access or policy decisions in the screen. */
@Composable
fun FamilyScreen(state: FamilyUiState, onEvent: (FamilyEvent) -> Unit) {
    val title = when (state.stage) {
        FamilyStage.List -> stringResource(R.string.family_for, state.context.userName)
        FamilyStage.Name -> stringResource(R.string.family_name)
        FamilyStage.Number -> stringResource(R.string.family_number)
        FamilyStage.Picture -> stringResource(R.string.family_picture)
        FamilyStage.Confirm -> if (state.editing) stringResource(R.string.family_name) else
            stringResource(R.string.family_confirm, state.name, state.context.userName)
        FamilyStage.Remove -> stringResource(R.string.family_remove_confirm, state.name)
        FamilyStage.TurnOff -> stringResource(R.string.family_disable_confirm, state.context.userName)
    }
    val primary = when (state.stage) {
        FamilyStage.List -> if (state.enabled) null else stringResource(R.string.family_enable)
        FamilyStage.Confirm -> stringResource(if (state.editing) R.string.waits_save else R.string.family_add_confirm)
        FamilyStage.Remove -> stringResource(R.string.family_remove)
        FamilyStage.TurnOff -> stringResource(R.string.family_turn_off)
        FamilyStage.Picture -> stringResource(R.string.family_letter)
        else -> stringResource(R.string.admin_continue)
    }
    AdminFrame(state.context, title, primary,
        onPrimary = { onEvent(if (state.stage == FamilyStage.List) FamilyEvent.Enable else FamilyEvent.Continue) },
        enabled = state.canContinue, secondary = stringResource(R.string.family_back), onSecondary = { onEvent(FamilyEvent.Back) }) {
        if (state.failed) Words(stringResource(R.string.family_error))
        when (state.stage) {
            FamilyStage.List -> {
                if (!state.enabled) Words(stringResource(R.string.family_off_body, state.context.userName))
                else {
                    for (person in state.people) {
                        org.plainphone.ui.user.FamilyLetterPicture(person.name)
                        ActionRow(person.name) { onEvent(FamilyEvent.Edit(person.id)) }
                    }
                    ActionRow(stringResource(R.string.family_add), enabled = state.canAdd) { onEvent(FamilyEvent.Add) }
                    ScreenLink(stringResource(R.string.family_disable), { onEvent(FamilyEvent.AskTurnOff) })
                }
            }
            FamilyStage.Name -> {
                FamilyField(stringResource(R.string.family_name), state.name) { onEvent(FamilyEvent.Name(it)) }
                FamilyField(stringResource(R.string.family_spoken_name, state.context.userName), state.spokenName) { onEvent(FamilyEvent.SpokenName(it)) }
                ScreenLink(stringResource(R.string.family_hear), { onEvent(FamilyEvent.Hear) })
                if (state.editing) ScreenLink(stringResource(R.string.family_remove), { onEvent(FamilyEvent.AskRemove) })
            }
            FamilyStage.Number -> FamilyField(stringResource(R.string.family_mobile), state.number, phone = true) { onEvent(FamilyEvent.Number(it)) }
            FamilyStage.Picture -> {
                org.plainphone.ui.user.FamilyLetterPicture(state.name)
                Words(stringResource(R.string.family_letter))
            }
            FamilyStage.Confirm -> { Words(state.name); Words(state.number); org.plainphone.ui.user.FamilyLetterPicture(state.name) }
            FamilyStage.Remove, FamilyStage.TurnOff -> Unit
        }
    }
}

@Composable
private fun FamilyField(label: String, value: String, phone: Boolean = false, onChange: (String) -> Unit) {
    Text(label, style = PlainphoneType.Small)
    BasicTextField(value, onChange, textStyle = PlainphoneType.Body,
        keyboardOptions = KeyboardOptions(keyboardType = if (phone) KeyboardType.Phone else KeyboardType.Text, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = label }
            .border(PlainphoneSpace.Border, PlainphoneColors.Ink3).padding(PlainphoneSpace.S4))
}
