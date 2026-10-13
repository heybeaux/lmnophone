// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.runtime.Composable
import com.android.resources.Density
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.trusted.*

/** PLA-494 Change PIN pages. Synthetic names only; PIN fields are masked. */
@RunWith(Parameterized::class)
class ChangePinScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0)
    private val alex = AdminContext("Alex")
    @Test fun oldPinWithWaitingChange() = shot { PinScreen(PinUiState(alex, PinStage.Enter, value = "123456",
        canContinue = true, pendingChange = "Turn LMNOPhone off")) {} }
    @Test fun newPin() = shot { PinScreen(PinUiState(alex, PinStage.NewPin, value = "1234567", confirmation = "1234567", canContinue = true)) {} }
    @Test fun mismatch() = shot { PinScreen(PinUiState(alex, PinStage.NewPin, mismatch = true)) {} }
    @Test fun changed() = shot { PinChangeResultScreen(alex, true) {} }
    @Test fun failed() = shot { PinChangeResultScreen(alex, false) {} }
    private fun shot(content: @Composable () -> Unit) = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame { content() } } }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(1f, 1.3f, 1.5f, 1.8f, 2f).map { arrayOf(it) }
    }
}
