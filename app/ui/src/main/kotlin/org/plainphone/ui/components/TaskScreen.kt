/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import kotlin.math.max
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.plainphone.ui.theme.*

/** The host supplies safe bounds. UX's measured fallback includes the band and actions in one
 * scroll when a complete title plus a body line cannot fit. The choice never depends on scroll.
 */
@Composable
internal fun TaskScreen(
    title: String,
    primary: String?,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    secondary: String? = null,
    secondaryEnabled: Boolean = true,
    onSecondary: () -> Unit = {},
    back: String? = null,
    onBack: () -> Unit = {},
    band: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val orientation = LocalConfiguration.current.orientation
    val largeText = density.fontScale >= 2f
    val spacing = if (largeText) PlainphoneSpace.S2 else PlainphoneSpace.S4
    val measurer = rememberTextMeasurer()
    val bandStyle = PlainphoneType.Small.copy(color = PlainphoneColors.Paper, fontWeight = FontWeight.Medium)
    BoxWithConstraints(Modifier.fillMaxSize().background(PlainphoneColors.Paper)) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        // Measure before IME padding: opening the keyboard must not replace the focused body.
        // New task identity, action presence, and display/font/rotation changes may remeasure.
        // In-flight action labels, body updates, and keyboard/scroll changes do not.
        val wholePage = remember(title, band, back, primary != null, secondary != null,
            width, height, density.density, density.fontScale, orientation) {
            with(density) {
                val side = PlainphoneSpace.S6.roundToPx()
                val inner = (width - 2 * side).coerceAtLeast(1)
                fun textHeight(text: String, style: TextStyle, available: Int = inner) =
                    measurer.measure(AnnotatedString(text), style,
                        constraints = Constraints(maxWidth = available.coerceAtLeast(1))).size.height
                val bandHeight = band?.let { max(40.dp.roundToPx(), textHeight(it, bandStyle) + PlainphoneSpace.S2.roundToPx() * 2) } ?: 0
                val primaryHeight = primary?.let { max(56.dp.roundToPx(), textHeight(it, PlainphoneType.Button, inner - 2 * side) +
                    (if (largeText) PlainphoneSpace.S2 else PlainphoneSpace.S4).roundToPx() * 2) } ?: 0
                val secondaryHeight = secondary?.let { max(48.dp.roundToPx(), textHeight(it, PlainphoneType.Button) + PlainphoneSpace.S2.roundToPx() * 2) } ?: 0
                val footerHeight = PlainphoneSpace.S2.roundToPx() * 2 + primaryHeight + secondaryHeight +
                    if (primary != null && secondary != null) PlainphoneSpace.S2.roundToPx() else 0
                val leadingHeight = PlainphoneSpace.S2.roundToPx() + when {
                    back != null -> max(48.dp.roundToPx(), textHeight(back, PlainphoneType.Button) + PlainphoneSpace.S2.roundToPx() * 2) + spacing.roundToPx()
                    !largeText -> PlainphoneSpace.S6.roundToPx() + spacing.roundToPx()
                    else -> 0
                }
                val oneBodyLine = max(textHeight("Ag", PlainphoneType.Body), textHeight("Ag", PlainphoneType.Row))
                bandHeight + leadingHeight + textHeight(title, PlainphoneType.Title) + spacing.roundToPx() + oneBodyLine + footerHeight > height
            }
        }
        @Composable fun Band() {
            if (band != null) Text(band, style = bandStyle, modifier = Modifier.fillMaxWidth()
                .background(PlainphoneColors.Ink).heightIn(min = 40.dp)
                .padding(horizontal = PlainphoneSpace.S6, vertical = PlainphoneSpace.S2))
        }
        @Composable fun Body() {
            Column(Modifier.fillMaxWidth().padding(horizontal = PlainphoneSpace.S6)
                .padding(top = PlainphoneSpace.S2, bottom = PlainphoneSpace.S6),
                verticalArrangement = Arrangement.spacedBy(spacing)) {
                if (back != null) ScreenLink(back, onBack, centered = false)
                else if (!largeText) Spacer(Modifier.height(PlainphoneSpace.S6))
                Text(title, style = PlainphoneType.Title, modifier = Modifier.semantics { heading() })
                content()
            }
        }
        @Composable fun Actions() {
            Column(Modifier.fillMaxWidth().padding(horizontal = PlainphoneSpace.S6)
                .padding(top = PlainphoneSpace.S2, bottom = PlainphoneSpace.S2),
                verticalArrangement = Arrangement.spacedBy(PlainphoneSpace.S2)) {
                if (secondary != null) ScreenLink(secondary, onSecondary, enabled = secondaryEnabled)
                if (primary != null) {
                    val shape = RoundedCornerShape(percent = 50)
                    Box(Modifier.fillMaxWidth().background(
                        if (primaryEnabled) PlainphoneColors.Ink else PlainphoneColors.Fill2, shape)
                        .plainphoneAction(shape, enabled = primaryEnabled, filled = true, onClick = onPrimary)
                        .heightIn(min = 56.dp).padding(horizontal = PlainphoneSpace.S6,
                            vertical = if (largeText) PlainphoneSpace.S2 else PlainphoneSpace.S4),
                        contentAlignment = Alignment.Center) {
                        Text(primary, style = PlainphoneType.Button.copy(
                            color = if (primaryEnabled) PlainphoneColors.Paper else PlainphoneColors.Line),
                            textAlign = TextAlign.Center)
                    }
                }
            }
        }
        key(title, band, back) {
        if (wholePage) {
            TaskScroll(Modifier.fillMaxSize().imePadding().testTag("task-whole-page")) { Band(); Body(); Actions() }
        } else Column(Modifier.fillMaxSize().imePadding().testTag("task-pinned")) {
            Band()
            TaskScroll(Modifier.weight(1f).fillMaxWidth()) { Body() }
            Actions()
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
