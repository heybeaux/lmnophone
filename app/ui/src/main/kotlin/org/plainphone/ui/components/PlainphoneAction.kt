/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import org.plainphone.ui.theme.PlainphoneColors
import org.plainphone.ui.theme.PlainphoneSpace

/** No animation or coloured ripple. Text supplies the accessible name. */
@Composable
internal fun Modifier.plainphoneAction(
    shape: Shape = RectangleShape,
    onClick: () -> Unit,
): Modifier {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    return this
        .then(if (pressed) Modifier.background(PlainphoneColors.Fill2, shape) else Modifier)
        .drawWithContent {
            drawContent()
            if (focused) {
                val stroke = PlainphoneSpace.Focus.toPx()
                // Full-width rows and lazy-list edges clip an outside ring. Keep that ring
                // inside the touch target, with a 2 dp gap to its edge. Inset buttons have room.
                val outset = if (shape == RectangleShape) -stroke * 1.5f else stroke * 1.5f
                drawRoundRect(PlainphoneColors.Ink,
                    topLeft = Offset(-outset, -outset),
                    size = Size(size.width + 2 * outset, size.height + 2 * outset),
                    cornerRadius = CornerRadius(stroke), style = Stroke(stroke))
            }
        }
        .clickable(interactionSource = interactions, indication = null,
            role = Role.Button, onClick = onClick)
}
