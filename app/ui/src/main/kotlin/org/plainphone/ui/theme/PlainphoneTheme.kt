/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.plainphone.ui.R

/** The complete palette from docs/ux/design-tokens.md. Never use dynamic colour. */
object PlainphoneColors {
    val Ink = Color(0xFF000000)
    val Paper = Color(0xFFFFFFFF)
    val Ink2 = Color(0xFF545454)
    val Ink3 = Color(0xFF757575)
    val Line = Color(0xFFAFAFAF)
    val Fill2 = Color(0xFFE8E8E8)
    val Fill1 = Color(0xFFF6F6F6)
}

object PlainphoneSpace {
    val S1 = 4.dp
    val S2 = 8.dp
    val S3 = 12.dp
    val S4 = 16.dp
    val S6 = 24.dp
    val S8 = 32.dp
    val S12 = 48.dp
    val BoxRadius = 16.dp
    val Border = 1.5.dp
    val Focus = 2.dp
}

@OptIn(ExperimentalTextApi::class)
private val Lexend = FontFamily(
    Font(R.font.lexend, weight = FontWeight.Normal,
        loadingStrategy = FontLoadingStrategy.OptionalLocal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.lexend, weight = FontWeight.Medium,
        loadingStrategy = FontLoadingStrategy.OptionalLocal,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))),
)

object PlainphoneType {
    private fun style(size: Int, line: Int, medium: Boolean = false) = TextStyle(
        fontFamily = Lexend, fontSize = size.sp, lineHeight = line.sp,
        fontWeight = if (medium) FontWeight.Medium else FontWeight.Normal,
        color = PlainphoneColors.Ink,
    )
    val Clock = style(64, 72).copy(fontFeatureSettings = "tnum")
    val Date = style(18, 24).copy(color = PlainphoneColors.Ink2)
    val App = style(32, 40, true)
    val Title = style(28, 36, true)
    val Row = style(22, 28, true)
    val Body = style(18, 26)
    val Button = style(18, 24, true)
    val Small = style(16, 22).copy(color = PlainphoneColors.Ink2)
    val Host = style(24, 32, true)
}

private val Colors = with(PlainphoneColors) {
    lightColorScheme(
        primary = Ink, onPrimary = Paper, primaryContainer = Fill2, onPrimaryContainer = Ink,
        inversePrimary = Paper,
        secondary = Ink, onSecondary = Paper, secondaryContainer = Fill2, onSecondaryContainer = Ink,
        tertiary = Ink, onTertiary = Paper, tertiaryContainer = Fill2, onTertiaryContainer = Ink,
        background = Paper, onBackground = Ink,
        surface = Paper, onSurface = Ink, surfaceVariant = Fill1, onSurfaceVariant = Ink2,
        surfaceTint = Paper, inverseSurface = Ink, inverseOnSurface = Paper,
        error = Ink, onError = Paper, errorContainer = Fill1, onErrorContainer = Ink,
        outline = Ink3, outlineVariant = Fill2, scrim = Ink,
        surfaceBright = Paper, surfaceDim = Fill2, surfaceContainer = Fill1,
        surfaceContainerHigh = Fill2, surfaceContainerHighest = Fill2,
        surfaceContainerLow = Fill1, surfaceContainerLowest = Paper,
    )
}

@Composable
fun PlainphoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = Typography(
            displayLarge = PlainphoneType.Clock, displayMedium = PlainphoneType.Clock,
            displaySmall = PlainphoneType.Title, headlineLarge = PlainphoneType.Title,
            headlineMedium = PlainphoneType.Title, headlineSmall = PlainphoneType.Title,
            titleLarge = PlainphoneType.Title, titleMedium = PlainphoneType.Row,
            titleSmall = PlainphoneType.Row, bodyLarge = PlainphoneType.Body,
            bodyMedium = PlainphoneType.Body, bodySmall = PlainphoneType.Small,
            labelLarge = PlainphoneType.Button, labelMedium = PlainphoneType.Button,
            labelSmall = PlainphoneType.Small,
        ),
        content = content,
    )
}
