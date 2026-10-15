// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction

/** Tab leaves an editor rather than inserting a tab into a multi-line message.
 * Form editors also release Up/Down to focus navigation; message arrows still edit text.
 * Wrapping name fields use their Next/Done action for Enter; message editors keep newlines.
 */
internal fun Modifier.fieldKeyboardNavigation(singleLine: Boolean = true,
    action: ImeAction = ImeAction.Default, onSubmit: () -> Unit = {}): Modifier = composed {
    val focus = LocalFocusManager.current
    onPreviewKeyEvent { event ->
        if (!singleLine && action != ImeAction.Default && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                if (action == ImeAction.Next) focus.moveFocus(FocusDirection.Next) else onSubmit()
            }
            // Consumed here, so the editor cannot also insert a newline or submit a second time.
            return@onPreviewKeyEvent true
        }
        val direction = when {
            event.key == Key.Tab -> if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next
            (singleLine || action != ImeAction.Default) && event.key == Key.DirectionDown -> FocusDirection.Down
            (singleLine || action != ImeAction.Default) && event.key == Key.DirectionUp -> FocusDirection.Up
            else -> null
        }
        if (direction == null) false else {
            if (event.type == KeyEventType.KeyDown) focus.moveFocus(direction)
            true
        }
    }
}
