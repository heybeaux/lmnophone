// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.plainphone.ui.R
import org.plainphone.ui.components.ScreenLink
import org.plainphone.ui.components.TaskScreen
import org.plainphone.ui.components.plainphoneAction
import org.plainphone.ui.theme.*

enum class WifiInternetUiStatus { SignInRequired, NoInternet }
/** The last Forget's outcome, only when Android's readback or the attempt itself settled it. */
enum class WifiForgetUiResult { Removed, NotRemoved }

data class WifiSettingsUiState(val enabled: Boolean = true, val connected: Boolean = false,
    val network: String? = null, val nearby: List<String>? = null,
    val readFailed: Boolean = false, val internetStatus: WifiInternetUiStatus? = null,
    val locationOff: Boolean = false, val locationEnableFailed: Boolean = false,
    val locationOffFailed: Boolean = false, val canForget: Boolean = false, val forgetFailed: Boolean = false, val choices: List<WifiNearbyUi> = emptyList(),
    val forgetResult: WifiForgetUiResult? = null)
data class WifiNearbyUi(val id: String, val name: String, val connected: Boolean = false)

/** The host supplies current facts and handles the explicit Location and network actions. */
@Composable
fun WifiSettingsScreen(state: WifiSettingsUiState, onBack: () -> Unit,
    onShow: () -> Unit = {}, onSelect: (String) -> Unit = {},
    onForget: () -> Unit = {}, onMessage: () -> Unit = {}) {
    if (state.locationOff && state.enabled && !state.readFailed) {
        TaskScreen(title = stringResource(R.string.settings_wifi),
            back = stringResource(R.string.settings_back), onBack = onBack,
            primary = stringResource(R.string.wifi_show_nearby), onPrimary = onShow) {
            if (state.locationOffFailed) Text(stringResource(R.string.wifi_location_off_failed), style = PlainphoneType.Body)
            WifiConnection(state, onForget, onMessage)
            Text(stringResource(R.string.wifi_nearby), style = PlainphoneType.Row, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.wifi_location_explanation), style = PlainphoneType.Body)
            if (state.locationEnableFailed) Text(stringResource(R.string.wifi_nearby_unavailable), style = PlainphoneType.Body)
        }
        return
    }
    Column(Modifier.fillMaxSize().background(PlainphoneColors.Paper)
        .verticalScroll(rememberScrollState()).padding(PlainphoneSpace.S6),
        verticalArrangement = Arrangement.spacedBy(PlainphoneSpace.S4)) {
        ScreenLink(stringResource(R.string.settings_back), onBack, centered = false)
        Text(stringResource(R.string.settings_wifi), style = PlainphoneType.Title, modifier = Modifier.semantics { heading() })
        if (state.locationOffFailed) Text(stringResource(R.string.wifi_location_off_failed), style = PlainphoneType.Body)
        if (state.readFailed) {
            Text(stringResource(R.string.wifi_status_unavailable), style = PlainphoneType.Body)
        } else if (!state.enabled) {
            Text(stringResource(R.string.wifi_off), style = PlainphoneType.Body)
        } else {
            WifiConnection(state, onForget, onMessage)
            Text(stringResource(R.string.wifi_nearby), style = PlainphoneType.Row, modifier = Modifier.semantics { heading() })
            if (state.locationOff) {
                Text(stringResource(R.string.wifi_location_explanation), style = PlainphoneType.Body)
                ScreenLink(stringResource(R.string.wifi_show_nearby), onShow, centered = false)
            }
            if (state.locationEnableFailed || (!state.locationOff && state.nearby == null))
                Text(stringResource(R.string.wifi_nearby_unavailable), style = PlainphoneType.Body)
            if (!state.locationOff) {
                if (state.choices.isNotEmpty()) state.choices.forEach { choice ->
                    if (choice.connected) Text(choice.name, style = PlainphoneType.Body)
                    else Box(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .plainphoneAction { onSelect(choice.id) }, contentAlignment = Alignment.CenterStart) {
                        Text(choice.name, style = PlainphoneType.Row)
                    }
                } else state.nearby?.forEach { Text(it, style = PlainphoneType.Body) }
            }
        }
    }
}

@Composable
private fun WifiConnection(state: WifiSettingsUiState, onForget: () -> Unit, onMessage: () -> Unit) {
    if (state.forgetFailed) Text(stringResource(R.string.wifi_status_unavailable), style = PlainphoneType.Body)
    state.forgetResult?.let {
        Text(stringResource(when (it) {
            WifiForgetUiResult.Removed -> R.string.wifi_forget_removed
            WifiForgetUiResult.NotRemoved -> R.string.wifi_forget_not_removed
        }), style = PlainphoneType.Body)
    }
    Text(when {
                !state.connected -> stringResource(R.string.wifi_not_connected)
                state.network == null -> stringResource(R.string.wifi_connected_unnamed)
                else -> stringResource(R.string.wifi_connected_named, state.network)
            }, style = PlainphoneType.Body)
            if (state.connected && state.canForget)
                ScreenLink(stringResource(R.string.wifi_forget), onForget, centered = false)
            if (state.connected) state.internetStatus?.let {
                ScreenLink(stringResource(when (it) {
                    WifiInternetUiStatus.SignInRequired -> R.string.wifi_signin
                    WifiInternetUiStatus.NoInternet -> R.string.wifi_no_internet
                }), onMessage, centered = false)
            }
}
