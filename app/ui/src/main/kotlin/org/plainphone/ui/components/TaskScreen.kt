/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.plainphone.ui.theme.*

/** Body scrolls independently; primary/secondary remain reachable, including above the IME.
 * The host supplies the safe content area after system bars.
 */
@Composable
internal fun TaskScreen(
    title: String,
    primary: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    secondary: String? = null,
    secondaryEnabled: Boolean = true,
    onSecondary: () -> Unit = {},
    back: String? = null,
    onBack: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val largeText = LocalDensity.current.fontScale >= 2f
    // Tighten whitespace, never type, so the next body line peeks above the pinned action.
    val spacing = if (largeText) PlainphoneSpace.S2 else PlainphoneSpace.S4
    Column(Modifier.fillMaxSize().background(PlainphoneColors.Paper).imePadding()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = PlainphoneSpace.S6).padding(top = PlainphoneSpace.S2, bottom = PlainphoneSpace.S6),
            verticalArrangement = Arrangement.spacedBy(spacing)) {
            if (back != null) ScreenLink(back, onBack, centered = false)
            else if (!largeText) Spacer(Modifier.height(PlainphoneSpace.S6))
            Text(title, style = PlainphoneType.Title, modifier = Modifier.semantics { heading() })
            content()
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = PlainphoneSpace.S6)
            .padding(top = PlainphoneSpace.S2, bottom = PlainphoneSpace.S2),
            verticalArrangement = Arrangement.spacedBy(PlainphoneSpace.S2)) {
            if (secondary != null) ScreenLink(secondary, onSecondary, enabled = secondaryEnabled)
            val shape = RoundedCornerShape(percent = 50)
            Box(Modifier.fillMaxWidth().background(
                if (primaryEnabled) PlainphoneColors.Ink else PlainphoneColors.Fill2, shape)
                .plainphoneAction(shape, enabled = primaryEnabled, filled = true, onClick = onPrimary)
                .heightIn(min = 56.dp).padding(horizontal = PlainphoneSpace.S6, vertical = if (largeText) PlainphoneSpace.S2 else PlainphoneSpace.S4),
                contentAlignment = Alignment.Center) {
                Text(primary, style = PlainphoneType.Button.copy(
                    color = if (primaryEnabled) PlainphoneColors.Paper else PlainphoneColors.Line),
                    textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
internal fun ScreenLink(text: String, onClick: () -> Unit, centered: Boolean = true, enabled: Boolean = true) {
    Box(Modifier.fillMaxWidth().plainphoneAction(enabled = enabled, onClick = onClick).heightIn(min = 48.dp)
        .padding(vertical = PlainphoneSpace.S2),
        contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart) {
        Text(text, style = PlainphoneType.Button.copy(textDecoration = TextDecoration.Underline,
            color = if (enabled) PlainphoneColors.Ink else PlainphoneColors.Line),
            textAlign = if (centered) TextAlign.Center else TextAlign.Start)
    }
}

@Composable
internal fun ScreenBox(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(PlainphoneColors.Fill1,
        RoundedCornerShape(PlainphoneSpace.BoxRadius)).padding(PlainphoneSpace.S4),
        verticalArrangement = Arrangement.spacedBy(PlainphoneSpace.S2), content = content)
}
