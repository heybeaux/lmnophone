// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.user

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.plainphone.ui.R
import org.plainphone.ui.components.TaskScreen
import org.plainphone.ui.theme.PlainphoneType

/** The network the question names; null when Android does not show its name. */
data class WifiForgetConfirmUiState(val network: String?)
enum class WifiForgetConfirmEvent { Forget, Keep }

/** Forget asks first (PLA-488). Keep and Back change nothing; only Forget starts the existing flow. */
@Composable
fun WifiForgetConfirmScreen(state: WifiForgetConfirmUiState, onEvent: (WifiForgetConfirmEvent) -> Unit) {
    TaskScreen(stringResource(R.string.wifi_forget_ask_title),
        stringResource(R.string.wifi_forget_ask_forget), { onEvent(WifiForgetConfirmEvent.Forget) },
        secondary = stringResource(R.string.wifi_forget_ask_keep),
        onSecondary = { onEvent(WifiForgetConfirmEvent.Keep) }) {
        Text(state.network ?: stringResource(R.string.wifi_forget_ask_unnamed), style = PlainphoneType.Row)
        Text(stringResource(R.string.wifi_forget_ask_password), style = PlainphoneType.Body)
    }
}
