/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.trusted

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.plainphone.ui.R
import org.plainphone.ui.components.*
import org.plainphone.ui.theme.*

@Composable
internal fun AdminFrame(context: AdminContext?, title: String, primary: String? = null,
    onPrimary: () -> Unit = {}, enabled: Boolean = true, secondary: String? = null,
    secondaryEnabled: Boolean = true, onSecondary: () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(PlainphoneColors.Paper)) {
        if (context != null) Text(
            context.trustedPersonName?.let { stringResource(R.string.more_for, it.withoutBidiControls()) }
                ?: stringResource(R.string.more_your_settings),
            style = PlainphoneType.Small.copy(color = PlainphoneColors.Paper, fontWeight = FontWeight.Medium),
            modifier = Modifier.fillMaxWidth().background(PlainphoneColors.Ink)
                .heightIn(min = 40.dp).padding(horizontal = PlainphoneSpace.S6, vertical = PlainphoneSpace.S2))
        Box(Modifier.weight(1f)) {
            TaskScreen(title, primary, onPrimary, primaryEnabled = enabled, secondary = secondary,
                secondaryEnabled = secondaryEnabled, onSecondary = onSecondary, content = content)
        }
    }
}
@Composable internal fun Words(text: String) = Text(text, style = PlainphoneType.Body)
@Composable internal fun Heading(text: String) = Text(text, style = PlainphoneType.Row, modifier = Modifier.semantics { heading() })
@Composable internal fun ActionRow(text: String, detail: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().plainphoneAction(enabled = enabled, onClick = onClick)
        .heightIn(min = 64.dp).padding(vertical = PlainphoneSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S2)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PlainphoneSpace.S1)) {
            Text(text, style = PlainphoneType.Row.copy(color = if (enabled) PlainphoneColors.Ink else PlainphoneColors.Line))
            if (!detail.isNullOrEmpty()) Text(detail, style = PlainphoneType.Small)
        }
        Text("›", style = PlainphoneType.Row.copy(color = PlainphoneColors.Ink2), modifier = Modifier.clearAndSetSemantics {})
    }
}
@Composable internal fun Choice(text: String, selected: Boolean, enabled: Boolean = true,
    spoken: String = text, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    Box(Modifier.fillMaxWidth().background(if (selected) PlainphoneColors.Ink else PlainphoneColors.Paper, shape)
        .border(PlainphoneSpace.Border, PlainphoneColors.Ink3, shape)
        .semantics { this.selected = selected; contentDescription = spoken }
        .plainphoneAction(shape, enabled = enabled, filled = selected, onClick = onClick)
        .heightIn(min = 56.dp).padding(PlainphoneSpace.S4)) {
        Text(if (selected) stringResource(R.string.admin_choice_selected, text) else text,
            style = PlainphoneType.Button.copy(color = if (!enabled) PlainphoneColors.Line else if (selected) PlainphoneColors.Paper else PlainphoneColors.Ink))
    }
}
/** No local secret state or persistence. Host supplies and clears it, and owns FLAG_SECURE. */
@Composable internal fun SecretField(label: String, value: String, tag: String, numeric: Boolean,
    enabled: Boolean, onChange: (String) -> Unit) {
    Text(label, style = PlainphoneType.Small)
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    BasicTextField(value, onChange, enabled = enabled, singleLine = true,
        textStyle = PlainphoneType.Body, interactionSource = interactions,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.NumberPassword else KeyboardType.Password),
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription = label }
            .border(if (focused) PlainphoneSpace.Focus else PlainphoneSpace.Border,
                if (focused) PlainphoneColors.Ink else PlainphoneColors.Ink3, RoundedCornerShape(PlainphoneSpace.BoxRadius))
            .heightIn(min = 56.dp).padding(PlainphoneSpace.S4))
}
