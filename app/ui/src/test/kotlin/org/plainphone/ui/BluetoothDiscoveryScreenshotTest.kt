// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import androidx.compose.runtime.Composable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.user.*

@RunWith(Parameterized::class)
class BluetoothDiscoveryScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0)
    @Test fun looking() = shot { BluetoothDiscoveryScreen(BluetoothDiscoveryUiState(looking = true), {}) }
    @Test fun list() = shot { BluetoothDiscoveryScreen(BluetoothDiscoveryUiState(devices = listOf(
        NearbyBluetoothOption("1", "Alex speaker"), NearbyBluetoothOption("2", "Sam headphones"))), {}) }
    @Test fun longList() = shot { BluetoothDiscoveryScreen(BluetoothDiscoveryUiState(devices = listOf(
        NearbyBluetoothOption("1", "Sam and Jo's very long headphones device name"))), {}) }
    @Test fun none() = shot { BluetoothDiscoveryScreen(BluetoothDiscoveryUiState(), {}) }
    @Test fun error() = shot { BluetoothDiscoveryScreen(BluetoothDiscoveryUiState(failed = true), {}) }
    @Test fun device() = shot { BluetoothDeviceScreen("Sam headphones", {}) }
    @Test fun deviceLongName() = shot { BluetoothDeviceScreen("Sam and Jo's very long headphones device name", {}) }
    private fun shot(content: @Composable () -> Unit) = paparazzi.snapshot {
        PlainphoneTheme { HomeTestFrame { content() } }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(1f, 1.3f, 1.5f, 1.8f, 2f).map { arrayOf(it) }
    }
}
