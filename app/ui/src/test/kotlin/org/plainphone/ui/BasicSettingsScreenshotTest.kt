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
    @Test fun automatic() = shot(forbidden = listOf("50%")) { BrightnessScreen(BrightnessUiState(50, true), {}) }
    @Test fun manualPercentageReturns() = brightnessShot(BrightnessUiState(50, false), "50%")
    @Test fun charged() = batteryShot(BatteryUiState(100, status = BatteryStatus.Charged), "Charged")
    @Test fun sound() = shot("Ring volume", "Media volume", "Done", labels = soundLabels) { SoundScreen(SoundUiState(VolumeUiState(4, 1, 7), VolumeUiState(7, 0, 15)), {}) }
    @Test fun soundFailed() = shot("Volume couldn't be changed.") { SoundScreen(SoundUiState(VolumeUiState(4, 1, 7), VolumeUiState(7, 0, 15), true), {}) }
    @Test fun soundMediaFailed() = shot("Volume couldn't be changed.") {
        SoundScreen(SoundUiState(VolumeUiState(4, 1, 7), VolumeUiState(7, 0, 15), mediaFailed = true), {})
    }
    @Test fun soundSilent() = shot("Ring volume can't be changed while the phone is on silent or vibrate.",
        labels = soundLabels, disabled = listOf("Ring volume", "Lower ring volume", "Raise ring volume"),
        enabled = listOf("Media volume", "Lower media volume", "Raise media volume")) {
        SoundScreen(SoundUiState(VolumeUiState(0, 1, 7), VolumeUiState(7, 0, 15), ringAdjustable = false), {})
    }
    private val soundLabels = listOf("Lower ring volume", "Raise ring volume", "Lower media volume", "Raise media volume", "Ring volume", "Media volume")
    @Test fun moreSettingsCopy() = shot("Wi-Fi, brightness, sound, text size, battery") { MoreScreen(MoreUiState(MoreMode.Trusted(listOf("Sam"), "Sam")), {}) }
    @Test fun floor() = brightnessShot(BrightnessUiState(10, false))
    @Test fun failed() = brightnessShot(BrightnessUiState(50, false, true), "Brightness couldn't be changed.")
    @Test fun unavailable() = brightnessShot(BrightnessUiState(null, false, true), "Brightness couldn't be changed.")
    @Test fun charging() = batteryShot(BatteryUiState(64, true), "Battery: 64%", "Charging")
    @Test fun notCharging() = batteryShot(BatteryUiState(100, false), "Battery: 100%", "Not charging")
    @Test fun unknown() = batteryShot(BatteryUiState(null, null), "Battery level isn't available.")
    private fun brightnessShot(state: BrightnessUiState, vararg expected: String) = shot(*expected, labels = listOf("Lower brightness", "Raise brightness", "Brightness")) { BrightnessScreen(state, {}) }
    private fun batteryShot(state: BatteryUiState, vararg expected: String) = shot(*expected) { BatteryScreen(state, {}) }
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun shot(vararg expected: String, forbidden: List<String> = emptyList(), labels: List<String> = emptyList(), disabled: List<String> = emptyList(), enabled: List<String> = emptyList(), content: @androidx.compose.runtime.Composable () -> Unit) = paparazzi.snapshot {
        PlainphoneTheme {
            val view = LocalView.current as ViewRootForTest
            Box(Modifier.onGloballyPositioned {
                val nodes = view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false)
                for (label in labels) {
                    val matching = nodes.filter { label in it.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() } }
                    assertTrue("Expected exactly one spoken label: $label", matching.size == 1)
                    if (label in disabled) assertTrue("Must be disabled: $label", matching.single().config.contains(SemanticsProperties.Disabled))
                    if (label in enabled) assertTrue("Must be enabled: $label", !matching.single().config.contains(SemanticsProperties.Disabled))
                }
                val merged = view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                for (label in labels.filter { it.startsWith("Lower ") || it.startsWith("Raise ") }) {
                    val button = merged.single { label in it.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() } }
                    assertTrue("Glyph must not create a second spoken node: $label",
                        button.config.getOrElse(SemanticsProperties.Text) { emptyList() }.isEmpty())
                }
                val texts = nodes
                    .flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } }.map { it.text }
                expected.forEach { assertTrue("Missing $it in $texts", it in texts) }
                forbidden.forEach { assertTrue("Unexpected $it in $texts", it !in texts) }
            }) { HomeTestFrame(content) }
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(1f, 1.3f, 1.5f, 1.8f, 2f).map { arrayOf(it) }
    }
}
