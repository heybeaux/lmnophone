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
class PlayLastOpeningScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 1800, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"), showSystemUi = false)
    @Test fun lastOpening() {
        for (stage in listOf(OpeningStage.Before, OpeningStage.Ready, OpeningStage.NoOpenings)) {
            paparazzi.snapshot(stage.name) { PlainphoneTheme { HomeTestFrame {
                OpeningScreen(OpeningUiState(AdminContext(""), stage, OpeningKind.Play,
                    left = if (stage == OpeningStage.NoOpenings) 0 else 1, limit = 1,
                    durationMinutes = 10, minutes = 5, playDisclosure = true, time = "10:00")) {}
            } } }
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "scale={0}") fun scales() = listOf(arrayOf(1f), arrayOf(2f))
    }
}
