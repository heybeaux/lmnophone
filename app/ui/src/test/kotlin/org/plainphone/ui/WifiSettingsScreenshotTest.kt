// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.user.*

@RunWith(Parameterized::class)
class WifiSettingsScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0)
    @Test fun captivePortal() = shot(WifiSettingsUiState(true, true,
        internetStatus = WifiInternetUiStatus.SignInRequired))
    @Test fun noInternet() = shot(WifiSettingsUiState(true, true,
        internetStatus = WifiInternetUiStatus.NoInternet))
    @Test fun readFailure() = shot(WifiSettingsUiState(readFailed = true))
    @Test fun connected() = shot(WifiSettingsUiState(true, true, "AndroidWifi", listOf("AndroidWifi", "Sam", "Jo")))
    @Test fun unnamed() = shot(WifiSettingsUiState(true, true))
    @Test fun disconnected() = shot(WifiSettingsUiState(true, false, nearby = listOf("Sam")))
    @Test fun off() = shot(WifiSettingsUiState(enabled = false))
    @Test fun emptyScan() = shot(WifiSettingsUiState(nearby = emptyList()))
    @Test fun longName() = shot(WifiSettingsUiState(true, true,
        "Sam and Jo's long network name", listOf("Sam and Jo's long network name", "Alex")))
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun shot(state: WifiSettingsUiState) = paparazzi.snapshot {
        PlainphoneTheme {
            val view = LocalView.current as ViewRootForTest
            Box(Modifier.onGloballyPositioned {
                val nodes = view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                val text = nodes.flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } }.map { it.text }
                assertTrue("Wi-Fi" in text)
                assertEquals(!state.readFailed && !state.enabled, "Wi-Fi is off." in text)
                assertEquals(!state.readFailed && state.enabled, "Nearby networks" in text)
                assertEquals(!state.readFailed && state.enabled && state.nearby == null, "Nearby networks can't be shown right now." in text)
                val expected = if (!state.connected) "Not connected" else state.network?.let { "Connected to $it" } ?: "Connected to Wi-Fi"
                if (state.enabled && !state.readFailed) assertTrue(expected in text)
                assertEquals(state.readFailed, "Wi-Fi status can't be read right now." in text)
                if (state.readFailed) assertFalse("Not connected" in text)
                val portal = "This Wi-Fi needs a sign-in page, which LMNOPhone doesn't allow. Use mobile data or another Wi-Fi."
                val offline = "This Wi-Fi isn't reaching the internet."
                assertEquals(state.internetStatus == WifiInternetUiStatus.SignInRequired, portal in text)
                assertEquals(state.internetStatus == WifiInternetUiStatus.NoInternet, offline in text)
                // The Back link is the only action; network labels cannot be tapped.
                assertEquals(1, nodes.count { it.config.contains(SemanticsActions.OnClick) })
            }) { HomeTestFrame { WifiSettingsScreen(state, {}) } }
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(1f, 1.3f, 1.5f, 1.8f, 2f).map { arrayOf(it) }
    }
}
