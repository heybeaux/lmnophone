/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
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
class HomeScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 360, screenHeight = 640,
            xdpi = 160, ydpi = 160, density = Density.MEDIUM,
            fontScale = scale,
        ),
        showSystemUi = false,
        maxPercentDifference = 0.0,
    )

    @Test fun firstLook() = snapshot(null)
    @Test fun overdueMonthlyCheck() = snapshot(HomeNotice.MonthlyCheck("Sam or Jo"))
    @Test fun attention() = snapshot(HomeNotice.NeedsAttention)
    @Test fun pendingTurnOff() = snapshot(HomeNotice.TurnOffWaiting("21:15 tomorrow"))
    @Test fun longestWaitingNotice() = snapshot(HomeNotice.ModeChangeWaiting)
    @Test fun selfManagedReady() = snapshot(HomeNotice.ReadyToStart)

    private fun snapshot(notice: HomeNotice?) {
        paparazzi.snapshot {
            PlainphoneTheme {
                HomeTestFrame {
                    HomeScreen(
                        HomeUiState("10:42", "Thursday 8 October", "Thu 8 Oct",
                            listOf(HomeApp("music", "Spotify"), HomeApp("chat", "Slack"),
                                HomeApp("camera", "Camera")), notice),
                        onEvent = {},
                    )
                }
            }
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(arrayOf(1.0f), arrayOf(2.0f))
    }
}
