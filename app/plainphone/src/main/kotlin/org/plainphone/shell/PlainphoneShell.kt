// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.plainphone.ui.user.PhoneSettingsScreen
import org.plainphone.ui.user.PhoneSettingsUiState
import org.plainphone.ui.user.PhoneSettingsEvent
import org.plainphone.counts.CommunicationCounts
import org.plainphone.ui.home.HomeScreen
import org.plainphone.ui.theme.PlainphoneColors
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.user.AppRequestResultScreen
import org.plainphone.ui.user.AskForAppScreen
import org.plainphone.ui.user.BlockedLinkScreen
import org.plainphone.ui.user.MoreScreen
import org.plainphone.ui.user.WifiMessageScreen
import org.plainphone.ui.user.WifiMessageUiState

/** Shows the current route's `:ui` screen and routes its events through [ShellNavigator]. */
@Composable
fun PlainphoneShell(
    state: ShellState,
    snapshot: PhoneSnapshot,
    mapper: ShellUiMapper,
    navigator: ShellNavigator,
    counts: CommunicationCounts,
    settings: PhoneSettingsUiState,
    onSettings: (PhoneSettingsEvent) -> Unit,
    onStep: (Step) -> Unit,
) {
    BackHandler { onStep(navigator.back(state)) }
    PlainphoneTheme {
        // The :ui screens expect the safe content area; the request screen handles the keyboard itself.
        Box(Modifier.fillMaxSize().background(PlainphoneColors.Paper).systemBarsPadding().displayCutoutPadding()) {
            when (val route = state.current) {
                ShellRoute.Home -> HomeScreen(mapper.home(snapshot).copy(missedCalls = counts.missedCalls, unreadTexts = counts.unreadTexts)) { onStep(navigator.onHome(state, it)) }
                ShellRoute.PhoneSettings -> PhoneSettingsScreen(settings, onSettings)
                ShellRoute.More -> MoreScreen(mapper.more(snapshot)) { onStep(navigator.onMore(state, it)) }
                ShellRoute.AskForApp ->
                    AskForAppScreen(mapper.ask(snapshot, state.draft)) { onStep(navigator.onAsk(state, it)) }
                is ShellRoute.RequestResult ->
                    AppRequestResultScreen(mapper.result(snapshot, route.result)) { onStep(navigator.onResult(state, it)) }
                is ShellRoute.BlockedLink -> BlockedLinkScreen(route.stage) { onStep(navigator.onLink(state, it)) }
                ShellRoute.NotInBuild -> NotInBuildScreen { onStep(navigator.back(state)) }
                is ShellRoute.Wifi ->
                    WifiMessageScreen(WifiMessageUiState(route.problem)) { onStep(navigator.onWifi(state, it)) }
            }
        }
    }
}
