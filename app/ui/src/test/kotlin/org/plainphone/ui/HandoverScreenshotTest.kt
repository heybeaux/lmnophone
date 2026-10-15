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
class HandoverScreenshotTest(private val scale: Float, private val height: Int) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = height, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"), showSystemUi = false)
    @Test fun mixedResults() {
        val items = HandoverCheckKind.entries.map { kind -> HandoverCheckRow(kind, when (kind) {
            HandoverCheckKind.USB_DEBUGGING -> HandoverCheckState.NEEDS_HELPER
            HandoverCheckKind.WIRELESS_DEBUGGING -> HandoverCheckState.COULD_NOT_CHECK
            else -> HandoverCheckState.OK
        }) }
        paparazzi.snapshot { PlainphoneTheme { HomeTestFrame { HandoverScreen(HandoverUiState(items, 2), {}) } } }
    }
    @Test fun allCheckedItemsOkStillDoesNotSayReady() {
        paparazzi.snapshot { PlainphoneTheme { HomeTestFrame { HandoverScreen(HandoverUiState(
            HandoverCheckKind.entries.map { HandoverCheckRow(it, HandoverCheckState.OK) }, 0), {}) } } }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "scale={0},height={1}") fun scales() =
            listOf(1f, 1.3f, 1.5f, 1.8f, 2f).map { arrayOf<Any>(it, 640) } + listOf(arrayOf<Any>(2f, 3400))
    }
}
