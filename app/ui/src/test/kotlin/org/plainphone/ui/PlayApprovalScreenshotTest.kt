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
class PlayApprovalScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"), showSystemUi = false)
    private val context = AdminContext("the person using this phone")
    @Test fun candidates() {
        paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
            PlayApprovalListScreen(PlayApprovalListUiState(context,
                listOf(HiddenAppRow("a", "Camera and photos"), HiddenAppRow("b", "Notes")), showGrantNotice = true), {}, {})
        } } }
    }
    @Test fun expired() {
        paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
            PlayApprovalListScreen(PlayApprovalListUiState(context, emptyList(), expired = true, showGrantNotice = true), {}, {})
        } } }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}") fun scales() = listOf(arrayOf(1f), arrayOf(2f))
    }
}
