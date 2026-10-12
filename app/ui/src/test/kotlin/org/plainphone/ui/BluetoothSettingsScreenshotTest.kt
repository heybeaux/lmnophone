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
import androidx.compose.ui.semantics.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.user.*

@RunWith(Parameterized::class)
class BluetoothSettingsScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0)
    @Test fun paired() = shot(BluetoothSettingsUiState(true, listOf("Alex speaker", "Jo keyboard", "Sam headphones"), false))
    @Test fun unnamed() = shot(BluetoothSettingsUiState(true, listOf(null), false))
    @Test fun noDevices() = shot(BluetoothSettingsUiState(true, emptyList(), false))
    @Test fun off() = shot(BluetoothSettingsUiState(false, listOf("Hidden synthetic device"), false))
    @Test fun failure() = shot(BluetoothSettingsUiState(true, listOf("Hidden synthetic device"), true))
    @Test fun longNames() = shot(BluetoothSettingsUiState(true, listOf("Sam and Jo's very long headphones device name", "Alex speaker"), false))
    @Test fun tenDevices() = shot(BluetoothSettingsUiState(true, (1..10).map { "Device $it" }, false))
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun shot(state: BluetoothSettingsUiState) = paparazzi.snapshot {
        PlainphoneTheme {
            val view = LocalView.current as ViewRootForTest
            Box(Modifier.onGloballyPositioned {
                val nodes = view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                val text = nodes.flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } }.map { it.text }
                assertTrue("Bluetooth" in text)
                assertTrue("Pairing a new device comes in a later test build." in text)
                assertEquals(!state.readFailed && !state.enabled, "Bluetooth is off." in text)
                assertEquals(state.readFailed, "Bluetooth status can't be read right now." in text)
                assertEquals(!state.readFailed && state.enabled, "Paired with:" in text)
                assertEquals(!state.readFailed && state.enabled && state.paired.isEmpty(), "No paired devices." in text)
                assertFalse("Hidden synthetic device" in text)
                if (!state.readFailed && state.enabled) state.paired.forEach {
                    assertTrue((it ?: "Unnamed device") in text)
                }
                assertEquals(1, nodes.count { it.config.contains(SemanticsActions.OnClick) })
            }) { HomeTestFrame { BluetoothSettingsScreen(state, {}) } }
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(1f, 1.3f, 1.5f, 1.8f, 2f).map { arrayOf(it) }
    }
}
