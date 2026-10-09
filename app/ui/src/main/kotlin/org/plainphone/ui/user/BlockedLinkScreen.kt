/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.user

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import org.plainphone.ui.R
import org.plainphone.ui.components.*
import org.plainphone.ui.theme.PlainphoneType

/** Plain Text only: no clickable URL, WebView, clipboard, implicit intents or admin route. */
@Composable
fun BlockedLinkScreen(state: BlockedLinkUiState, onEvent: (BlockedLinkEvent) -> Unit) {
    when (state) {
        is BlockedLinkUiState.Blocked -> TaskScreen(
            stringResource(R.string.link_title), stringResource(R.string.link_back),
            { onEvent(BlockedLinkEvent.GoBack) },
            secondary = state.shareWith?.let { stringResource(R.string.link_share, it) },
            onSecondary = { onEvent(BlockedLinkEvent.ReviewSharing) },
        ) {
            Text(stringResource(R.string.link_goes_to), style = PlainphoneType.Body)
            ScreenBox { Text(state.host, style = PlainphoneType.Host.copy(textDirection = TextDirection.Ltr)) }
            if (state.unusualLetters) Text(stringResource(R.string.link_unusual), style = PlainphoneType.Body)
            Text(stringResource(R.string.link_body), style = PlainphoneType.Body)
            Text(stringResource(R.string.link_apps_note), style = PlainphoneType.Small)
        }
        BlockedLinkUiState.TooLong -> TaskScreen(
            stringResource(R.string.link_title), stringResource(R.string.link_back),
            { onEvent(BlockedLinkEvent.GoBack) },
        ) { Text(stringResource(R.string.link_too_long), style = PlainphoneType.Body) }
        is BlockedLinkUiState.Confirm -> TaskScreen(
            stringResource(R.string.link_confirm_title, state.recipientName), stringResource(R.string.user_send),
            { onEvent(BlockedLinkEvent.Send) },
            secondary = stringResource(R.string.link_dont_send), onSecondary = { onEvent(BlockedLinkEvent.DontSend) },
        ) {
            Text(stringResource(R.string.link_confirm_body, state.recipientName), style = PlainphoneType.Body)
            ScreenBox { Text(state.sanitizedSmsPreview, style = PlainphoneType.Body) }
        }
        is BlockedLinkUiState.Sent -> TaskScreen(
            stringResource(R.string.link_sent, state.recipientName), stringResource(R.string.user_ok),
            { onEvent(BlockedLinkEvent.Ok) }, content = {},
        )
        BlockedLinkUiState.Failed -> TaskScreen(
            stringResource(R.string.link_failed_title), stringResource(R.string.user_ok),
            { onEvent(BlockedLinkEvent.Ok) },
        ) { Text(stringResource(R.string.link_failed_body), style = PlainphoneType.Body) }

    }
}
