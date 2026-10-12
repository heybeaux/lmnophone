// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.home

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.plainphone.ui.R
import org.plainphone.ui.components.ScreenBox
import org.plainphone.ui.components.TaskScreen
import org.plainphone.ui.components.waitingTimeText
import org.plainphone.ui.theme.PlainphoneType

/** Presentation only. The host keeps calls and Emergency reachable outside this screen. */
@Composable
fun NeedsAttentionScreen(state: NeedsAttentionUiState, onEvent: (NeedsAttentionEvent) -> Unit) {
    val pending = state.pending
    TaskScreen(
        title = stringResource(R.string.notice_attention),
        primary = stringResource(R.string.user_ok),
        onPrimary = { onEvent(NeedsAttentionEvent.Back) },
        secondary = if (pending != null && !pending.committing) stringResource(R.string.wait_cancel) else null,
        onSecondary = { onEvent(NeedsAttentionEvent.CancelChange) },
    ) {
        Text(stringResource(R.string.attention_still_works), style = PlainphoneType.Body)
        if (pending != null) ScreenBox {
            if (pending.committing) {
                Text(stringResource(R.string.wait_progress), style = PlainphoneType.Row,
                    modifier = Modifier.semantics { heading() })
                Text(stringResource(if (pending.kind is PendingChangeKind.TurnOff)
                    R.string.wait_off_progress else R.string.wait_progress_body), style = PlainphoneType.Body)
            } else {
                val line = when (val kind = pending.kind) {
                    is PendingChangeKind.TurnOff -> stringResource(R.string.notice_turn_off,
                        waitingTimeText(kind.time, kind.day))
                    PendingChangeKind.People -> stringResource(R.string.notice_people)
                    PendingChangeKind.Setup -> stringResource(R.string.notice_mode)
                }
                Text(line, style = PlainphoneType.Body)
            }
        }
    }
}
