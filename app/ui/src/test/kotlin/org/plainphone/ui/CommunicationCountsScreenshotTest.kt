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
import org.plainphone.ui.home.*
import org.plainphone.ui.user.*
import org.plainphone.ui.theme.PlainphoneTheme

@RunWith(Parameterized::class)
class CommunicationCountsScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0)
    @Test fun home() {
        listOf(false, true).forEach { notice ->
            listOf<Int?>(null, 0, 1, 99, 100).forEach { count ->
                paparazzi.snapshot(name = "notice_${notice}_count_$count") {
                    PlainphoneTheme { HomeTestFrame {
                        HomeScreen(HomeUiState("10:42", "Thursday 8 October", "Thu 8 Oct",
                            listOf(HomeApp("camera", "Camera")),
                            if (notice) HomeNotice.ModeChangeWaiting else null,
                            missedCalls = count, unreadTexts = count), {})
                    } }
                }
            }
        }
    }
    @Test fun settings() {
        listOf(PhoneSettingsUiState(), PhoneSettingsUiState(showCommunicationCounts = true),
            PhoneSettingsUiState(true, missedCallsUnavailable = true, unreadTextsUnavailable = true))
            .forEachIndexed { index, state ->
                paparazzi.snapshot(name = "settings_$index") {
                    PlainphoneTheme { HomeTestFrame { PhoneSettingsScreen(state, {}) } }
                }
            }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(1f, 1.3f, 1.5f, 1.8f, 2f).map { arrayOf(it) }
    }
}
