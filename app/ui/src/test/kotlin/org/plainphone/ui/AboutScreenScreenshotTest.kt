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
import org.plainphone.ui.user.AboutScreen
import org.plainphone.ui.user.AboutUiState

@RunWith(Parameterized::class)
class AboutScreenScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 360, screenHeight = 640,
            xdpi = 160, ydpi = 160, density = Density.MEDIUM, fontScale = scale, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0,
    )

    @Test fun oneTrustedPerson() = shot(listOf("Sam"))
    @Test fun twoTrustedPeople() = shot(listOf("Sam", "Jo"))
    @Test fun longTrustedNames() = shot(listOf("Sam ".repeat(8).trim(), "Jo ".repeat(8).trim()))

    private fun shot(people: List<String>) = paparazzi.snapshot {
        PlainphoneTheme {
            HomeTestFrame { AboutScreen(AboutUiState("0.1.0-preview-18", people, "Sam"), {}) }
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(arrayOf(1f), arrayOf(1.3f), arrayOf(1.5f), arrayOf(1.8f), arrayOf(2f))
    }
}
