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
import org.plainphone.ui.trusted.*

@RunWith(Parameterized::class)
class PlayPreviewScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"), showSystemUi = false)
    private val context = AdminContext("the person using this phone")
    @Test fun opening() {
        for (stage in listOf(OpeningStage.Before, OpeningStage.Countdown, OpeningStage.Ready)) {
            paparazzi.snapshot(stage.name) { PlainphoneTheme { HomeTestFrame {
                OpeningScreen(OpeningUiState(context, stage, OpeningKind.Play, durationMinutes = 10,
                    minutes = if (stage == OpeningStage.Countdown) 10 else 5, playDisclosure = true)) { }
            } } }
        }
    }
    @Test fun refused() {
        paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
            OpeningScreen(OpeningUiState(context, OpeningStage.OpenFailed, OpeningKind.Play)) {}
        } } }
    }
    @Test fun closure() {
        for (stage in listOf(SessionStage.Checking, SessionStage.Restored, SessionStage.NotReady)) {
            paparazzi.snapshot(stage.name) { PlainphoneTheme { HomeTestFrame {
                SessionScreen(SessionUiState(context, stage, helper = "the person who set up this phone", canShowDetails = false)) { }
            } } }
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}") fun scales() = listOf(arrayOf(1f), arrayOf(2f))
    }
}
