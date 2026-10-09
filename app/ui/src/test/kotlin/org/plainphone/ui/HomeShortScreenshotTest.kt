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

/** Below-minimum stress fixtures exercise two-line, half-row and whole-page fallbacks. */
@RunWith(Parameterized::class)
class HomeShortScreenshotTest(private val height: Int) {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 360, screenHeight = height,
            xdpi = 160, ydpi = 160, density = Density.MEDIUM, fontScale = 2f, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0,
    )
    @Test fun fallback() = paparazzi.snapshot {
        PlainphoneTheme {
            HomeTestFrame {
                HomeScreen(HomeUiState("10:42", "Thursday 8 October", "Thu 8 Oct",
                    (1..8).map { HomeApp("app-$it", "App $it") }, HomeNotice.ModeChangeWaiting), {})
            }
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "height={0}")
        fun heights() = listOf(arrayOf(560), arrayOf(540), arrayOf(480))
    }
}
