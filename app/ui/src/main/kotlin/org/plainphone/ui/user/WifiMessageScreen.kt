/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.user

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.plainphone.ui.R
import org.plainphone.ui.components.TaskScreen
import org.plainphone.ui.theme.PlainphoneType

/** Approved U7 messages only. No route into an unapproved sign-in window. */
@Composable
fun WifiMessageScreen(state: WifiMessageUiState, onEvent: (WifiMessageEvent) -> Unit) {
    TaskScreen(stringResource(R.string.wifi_title), stringResource(R.string.wifi_forget),
        { onEvent(WifiMessageEvent.ForgetNetwork) }, primaryEnabled = state.canForget, secondary = stringResource(R.string.user_ok),
        onSecondary = { onEvent(WifiMessageEvent.Ok) }) {
        Text(stringResource(R.string.wifi_signal), style = PlainphoneType.Body)
        Text(stringResource(when (state.problem) {
            WifiProblem.SignInRequired -> R.string.wifi_signin
            WifiProblem.NoInternet -> R.string.wifi_no_internet
        }), style = PlainphoneType.Body)
        Text(stringResource(R.string.wifi_forget_explanation), style = PlainphoneType.Body)
    }
}
