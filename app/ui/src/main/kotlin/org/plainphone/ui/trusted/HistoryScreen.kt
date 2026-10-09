/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.trusted

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource as s
import org.plainphone.ui.R
import org.plainphone.ui.components.*

/** Host supplies the last 90 days, newest first, with no app content, URLs or message text. */
@Composable
fun HistoryScreen(state: HistoryUiState, onEvent: (HistoryEvent) -> Unit) {
    AdminFrame(state.context, s(R.string.admin_history), s(R.string.admin_done), { onEvent(HistoryEvent.Done) }) {
        Words(s(R.string.history_body))
        state.entries.forEach { entry -> ScreenBox {
            Words(entry.timestamp)
            Heading(entry.action.withoutBidiControls())
            Words(s(when (entry.authority) {
                HistoryAuthority.Pin -> R.string.history_pin; HistoryAuthority.Recovery -> R.string.history_recovery
                HistoryAuthority.NoPin -> R.string.history_no_pin; HistoryAuthority.Text -> R.string.history_text
                HistoryAuthority.Plainphone -> R.string.history_plainphone; HistoryAuthority.Number -> R.string.history_number
            }, entry.name.withoutBidiControls()))
            if (entry.receipts.isNotEmpty()) Words(s(R.string.history_texts))
            entry.receipts.forEach { receipt -> Words(s(when (receipt.status) {
                ReceiptStatus.Sent -> R.string.history_sent; ReceiptStatus.Waiting -> R.string.history_waiting; ReceiptStatus.Failed -> R.string.history_failed
            }, receipt.name.withoutBidiControls())) }
        } }
    }
}
