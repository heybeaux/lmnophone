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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.plainphone.ui.R
import org.plainphone.ui.components.ScreenLink
import org.plainphone.ui.theme.*

data class WifiSettingsUiState(val enabled: Boolean = true, val connected: Boolean = false,
    val network: String? = null, val nearby: List<String>? = null)

/** Read-only information page. The host supplies fresh, cleaned names while visible. */
@Composable
fun WifiSettingsScreen(state: WifiSettingsUiState, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(PlainphoneColors.Paper)
        .verticalScroll(rememberScrollState()).padding(PlainphoneSpace.S6),
        verticalArrangement = Arrangement.spacedBy(PlainphoneSpace.S4)) {
        ScreenLink(stringResource(R.string.settings_back), onBack, centered = false)
        Text(stringResource(R.string.settings_wifi), style = PlainphoneType.Title, modifier = Modifier.semantics { heading() })
        if (!state.enabled) {
            Text(stringResource(R.string.wifi_off), style = PlainphoneType.Body)
        } else {
            Text(when {
                !state.connected -> stringResource(R.string.wifi_not_connected)
                state.network == null -> stringResource(R.string.wifi_connected_unnamed)
                else -> stringResource(R.string.wifi_connected_named, state.network)
            }, style = PlainphoneType.Body)
            Text(stringResource(R.string.wifi_nearby), style = PlainphoneType.Row, modifier = Modifier.semantics { heading() })
            if (state.nearby == null) Text(stringResource(R.string.wifi_nearby_unavailable), style = PlainphoneType.Body)
            else state.nearby.forEach { Text(it, style = PlainphoneType.Body) }
            Text(stringResource(R.string.wifi_preview_note), style = PlainphoneType.Small)
        }
    }
}
