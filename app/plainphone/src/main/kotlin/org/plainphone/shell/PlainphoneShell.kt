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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.plainphone.R
import org.plainphone.ui.user.*
import org.plainphone.ui.user.AboutScreen
import org.plainphone.ui.user.AboutUiState
import org.plainphone.ui.user.PhoneSettingsScreen
import org.plainphone.ui.user.PhoneSettingsUiState
import org.plainphone.ui.user.PhoneSettingsEvent
import org.plainphone.counts.CommunicationCounts
import org.plainphone.ui.home.HomeNotice
import org.plainphone.ui.home.HomeEvent
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
    versionName: String,
    isDeviceSecure: Boolean,
    notice: HomeNotice?,
    onNotice: () -> Unit,
    settings: PhoneSettingsUiState,
    onSettings: (PhoneSettingsEvent) -> Unit,
    textSize: TextSizeUiState,
    onTextSize: (TextSizeEvent) -> Unit,
    wifi: WifiSettingsUiState,
    wifiJoin: WifiJoinUiState?,
    onWifiJoin: (WifiJoinEvent) -> Unit,
    onWifiShow: () -> Unit,
    onWifiSelect: (String) -> Unit,
    onWifiForget: () -> Unit,
    onWifiMessage: () -> Unit,
    bluetooth: BluetoothSettingsUiState,
    discovery: BluetoothDiscoveryUiState,
    onDiscovery: (BluetoothDiscoveryEvent) -> Unit,
    onPairNew: () -> Unit,
    sound: SoundUiState,
    onSound: (SoundEvent) -> Unit,
    ringtone: RingtoneUiState,
    onRingtone: (RingtoneEvent) -> Unit,
    brightness: BrightnessUiState,
    battery: BatteryUiState,
    onBrightness: (BrightnessEvent) -> Unit,
    voice: VoiceUiState,
    onVoiceOpen: () -> Unit,
    onVoiceClose: () -> Unit,
    onVoice: (VoiceUiEvent) -> Unit,
    onStep: (Step) -> Unit,
) {
    BackHandler {
        if (state.current == ShellRoute.WifiSettings && wifiJoin != null) onWifiJoin(WifiJoinEvent.Cancel)
        else if (state.current == ShellRoute.VoicePractice) onVoice(VoiceUiEvent.Back)
        else onStep(navigator.back(state))
    }
    PlainphoneTheme {
        // The :ui screens expect the safe content area; the request screen handles the keyboard itself.
        Box(Modifier.fillMaxSize().background(PlainphoneColors.Paper).systemBarsPadding().displayCutoutPadding()) {
            when (val route = state.current) {
                ShellRoute.Home -> HomeScreen(mapper.home(snapshot).copy(notice = notice, missedCalls = counts.missedCalls, unreadTexts = counts.unreadTexts)) {
                    if (it == HomeEvent.OpenNotice) onNotice() else onStep(navigator.onHome(state, it))
                }
                ShellRoute.About -> AboutScreen(AboutUiState(versionName, snapshot.trustedPeople,
                    snapshot.requestRecipient, stringResource(R.string.product_name), isDeviceSecure)) { onStep(navigator.onAbout(state, it)) }
                ShellRoute.TextSize -> TextSizeScreen(textSize, onTextSize)
                ShellRoute.VoicePractice -> {
                    // Leaving this route by any way (Back, Home key) stops the recorder and keeps the take.
                    DisposableEffect(Unit) { onVoiceOpen(); onDispose(onVoiceClose) }
                    VoicePracticeScreen(voice, onVoice)
                }
                ShellRoute.BluetoothSettings -> BluetoothSettingsScreen(bluetooth,
                    { onStep(navigator.back(state)) }, onPairNew)
                ShellRoute.BluetoothDiscovery -> BluetoothDiscoveryScreen(discovery, onDiscovery)
                is ShellRoute.BluetoothDevice -> BluetoothDeviceScreen(route.name) { onStep(navigator.back(state)) }
                ShellRoute.WifiSettings -> if (wifiJoin != null) WifiJoinScreen(wifiJoin, onWifiJoin)
                    else WifiSettingsScreen(wifi, { onStep(navigator.back(state)) }, onWifiShow, onWifiSelect, onWifiForget, onWifiMessage)
                ShellRoute.Sound -> SoundScreen(sound, onSound)
                ShellRoute.Ringtone -> RingtoneScreen(ringtone, onRingtone)
                ShellRoute.Brightness -> BrightnessScreen(brightness, onBrightness)
                ShellRoute.Battery -> BatteryScreen(battery) { onStep(navigator.back(state)) }
                ShellRoute.PhoneSettings -> PhoneSettingsScreen(settings, onSettings)
                ShellRoute.More -> MoreScreen(mapper.more(snapshot)) { onStep(navigator.onMore(state, it)) }
                ShellRoute.AskForApp ->
                    AskForAppScreen(mapper.ask(snapshot, state.draft)) { onStep(navigator.onAsk(state, it)) }
                is ShellRoute.RequestResult ->
                    AppRequestResultScreen(mapper.result(snapshot, route.result)) { onStep(navigator.onResult(state, it)) }
                is ShellRoute.BlockedLink -> BlockedLinkScreen(route.stage) { onStep(navigator.onLink(state, it)) }
                ShellRoute.NotInBuild -> NotInBuildScreen { onStep(navigator.back(state)) }
                is ShellRoute.Wifi ->
                    WifiMessageScreen(WifiMessageUiState(route.problem, wifi.canForget)) {
                        if (it == WifiMessageEvent.ForgetNetwork && state.stack.dropLast(1).lastOrNull() == ShellRoute.WifiSettings) onWifiForget()
                        onStep(navigator.onWifi(state, it))
                    }
            }
        }
    }
}
