/* Copyright 2026 LMNOPhone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import java.time.LocalDate
import org.plainphone.ui.home.HomeNoticeDay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.home.HomeApp
import org.plainphone.ui.home.HomeNotice
import org.plainphone.ui.home.HomeScreen
import org.plainphone.ui.home.HomeUiState
import org.plainphone.ui.theme.PlainphoneTheme

/** Synthetic data only. 360 × 640 includes simulated 32/48 dp system bars. */
@RunWith(Parameterized::class)
class BrandingScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 360, screenHeight = 640,
            xdpi = 160, ydpi = 160, density = Density.MEDIUM,
            fontScale = scale, locale = "en",
        ),
        showSystemUi = false,
        maxPercentDifference = 0.0,
    )

    @Test fun attention() = snapshot(HomeNotice.NeedsAttention)
    @Test fun turnOff() = snapshot(HomeNotice.TurnOffWaiting("21:15",
        HomeNoticeDay(LocalDate.of(2026, 10, 15), 7)))
    @Test fun wifiSignIn() = paparazzi.snapshot {
        PlainphoneTheme { HomeTestFrame {
            org.plainphone.ui.user.WifiMessageScreen(org.plainphone.ui.user.WifiMessageUiState(
                org.plainphone.ui.user.WifiProblem.SignInRequired), {})
        } }
    }

    private fun snapshot(notice: HomeNotice?, apps: List<HomeApp> = listOf(
        HomeApp("music", "Spotify"), HomeApp("chat", "Slack"), HomeApp("camera", "Camera"))) {
        paparazzi.snapshot {
            PlainphoneTheme {
                HomeTestFrame {
                    HomeScreen(
                        HomeUiState("10:42", "Thursday 8 October", "Thu 8 Oct",
                            apps, notice),
                        onEvent = {},
                    )
                }
            }
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(arrayOf(1.3f), arrayOf(1.5f), arrayOf(1.8f))
    }
}
