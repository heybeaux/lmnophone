// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.user.*
@RunWith(Parameterized::class)
class WifiJoinScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"), showSystemUi = false, maxPercentDifference = 0.0)
    private fun join(state: WifiJoinUiState) = paparazzi.snapshot {
        PlainphoneTheme { HomeTestFrame { WifiJoinScreen(state) {} } }
    }
    private fun page(state: WifiSettingsUiState) = paparazzi.snapshot {
        PlainphoneTheme { HomeTestFrame { WifiSettingsScreen(state, {}) } }
    }
    @Test fun personal() = join(WifiJoinUiState("Sam", WifiJoinKind.Personal, password = "example-only"))
    @Test fun shownPassword() = join(WifiJoinUiState("Sam", WifiJoinKind.Personal, password = "example-only", showPassword = true))
    @Test fun open() = join(WifiJoinUiState("Jo", WifiJoinKind.Open))
    @Test fun unsupported() = join(WifiJoinUiState("Alex", WifiJoinKind.Unsupported))
    @Test fun joining() = join(WifiJoinUiState("Sam", WifiJoinKind.Personal, joining = true))
    @Test fun failed() = join(WifiJoinUiState("Sam and Jo's long network name", WifiJoinKind.Personal, failed = true))
    @Test fun locationOff() = page(WifiSettingsUiState(locationOff = true))
    @Test fun enableFailed() = page(WifiSettingsUiState(locationOff = true, locationEnableFailed = true))
    @Test fun offFailed() = page(WifiSettingsUiState(locationOffFailed = true))
    @Test fun choices() = page(WifiSettingsUiState(connected = true, network = "Sam", nearby = listOf("Sam", "Jo"),
        choices = listOf(WifiNearbyUi("1", "Sam", true), WifiNearbyUi("2", "Jo"))))
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(1f, 1.3f, 1.5f, 1.8f, 2f).map { arrayOf(it) }
    }
}
