// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.trusted

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource as s
import org.plainphone.ui.R
import org.plainphone.ui.components.withoutBidiControls

data class PlayApprovalListUiState(val context: AdminContext, val apps: List<HiddenAppRow>,
    val expired: Boolean = false, val unavailable: Boolean = false, val busy: Boolean = false,
    val showGrantNotice: Boolean = false)

@Composable fun PlayApprovalListScreen(state: PlayApprovalListUiState, onOpen: (String) -> Unit, onDone: () -> Unit) {
    AdminFrame(state.context, s(R.string.play_approval_title), primary = s(R.string.admin_done), onPrimary = onDone) {
        when {
            state.expired -> Words(s(R.string.play_approval_expired))
            state.unavailable -> StatusWords(s(R.string.app_hidden_unavailable))
            else -> {
                if (state.showGrantNotice) Words(s(R.string.play_approval_notice))
                if (!state.busy && state.apps.isEmpty()) Words(s(R.string.play_approval_empty))
                state.apps.forEach { app -> ActionRow(app.name.withoutBidiControls(), enabled = !state.busy) { onOpen(app.id) } }
            }
        }
    }
}
