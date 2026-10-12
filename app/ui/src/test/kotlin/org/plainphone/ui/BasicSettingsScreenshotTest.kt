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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.user.*

@RunWith(Parameterized::class)
class BasicSettingsScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0)
    @Test fun brightness() = brightnessShot(BrightnessUiState(50, false))
    @Test fun automatic() = brightnessShot(BrightnessUiState(50, true))
    @Test fun floor() = brightnessShot(BrightnessUiState(10, false))
    @Test fun failed() = brightnessShot(BrightnessUiState(50, false, true), "Brightness couldn't be changed.")
    @Test fun unavailable() = brightnessShot(BrightnessUiState(null, false, true), "Brightness couldn't be changed.")
    @Test fun charging() = batteryShot(BatteryUiState(64, true), "Battery: 64%", "Charging")
    @Test fun notCharging() = batteryShot(BatteryUiState(100, false), "Battery: 100%", "Not charging")
    @Test fun unknown() = batteryShot(BatteryUiState(null, null), "Battery level isn't available.")
    private fun brightnessShot(state: BrightnessUiState, vararg expected: String) = shot(*expected) { BrightnessScreen(state, {}) }
    private fun batteryShot(state: BatteryUiState, vararg expected: String) = shot(*expected) { BatteryScreen(state, {}) }
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun shot(vararg expected: String, content: @androidx.compose.runtime.Composable () -> Unit) = paparazzi.snapshot {
        PlainphoneTheme {
            val view = LocalView.current as ViewRootForTest
            Box(Modifier.onGloballyPositioned {
                val texts = view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false)
                    .flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } }.map { it.text }
                expected.forEach { assertTrue("Missing $it in $texts", it in texts) }
            }) { HomeTestFrame(content) }
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(1f, 1.3f, 1.5f, 1.8f, 2f).map { arrayOf(it) }
    }
}
