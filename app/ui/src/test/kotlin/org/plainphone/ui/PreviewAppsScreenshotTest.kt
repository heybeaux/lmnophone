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

@RunWith(Parameterized::class)
class PreviewAppsScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"), showSystemUi = false, maxPercentDifference = 0.0)
    private val context = AdminContext("Alex", "Sam")
    private val app = InstalledApp("calculator", "Calculator with a very long app name", "")
    @Test fun list() = shot { AppsOnPhoneScreen(AppsOnPhoneUiState(context, listOf(
        AppOnPhone(app, canMoveDown = true), AppOnPhone(InstalledApp("camera", "Camera", ""), canMoveUp = true),
        AppOnPhone(InstalledApp("sms", "Messages", ""), alwaysOn = true),
        AppOnPhone(InstalledApp("phone", "Phone", ""), alwaysOn = true)))) {} }
    @Test fun detail() = shot { PreviewAppDetailScreen(context, app.name, "123.456.789") {} }
    @Test fun removeConfirm() = shot { AppReviewScreen(AppReviewUiState(context, app, stage = AppReviewStage.Remove)) {} }
    private fun shot(content: @Composable () -> Unit) = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame { content() } } }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(1f, 1.3f, 1.5f, 1.8f, 2f).map { arrayOf(it) }
    }
}
