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
class RecoveryPreviewScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"), showSystemUi = false, maxPercentDifference = 0.0)
    private val context = AdminContext("Alex")
    private fun shot(state: PinUiState) = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame { PinScreen(state) {} } } }
    @Test fun entry() = shot(PinUiState(context, PinStage.Recovery))
    @Test fun wrong() = shot(PinUiState(context, PinStage.Recovery, wrongRecoveryCode = true))
    @Test fun pinAvailable() = shot(PinUiState(context, PinStage.RecoveryLocked, wait = "30 seconds", helper = "Sam"))
    @Test fun pinWaiting() = shot(PinUiState(context, PinStage.RecoveryLocked, wait = "1 hour", pinWait = "1 minute", helper = "Sam", recoveryLockout = RecoveryLockoutKind.PinWaiting))
    @Test fun issued() = shot(PinUiState(context, PinStage.RecoveryIssued, recoveryCode = "0123-4567-89ab-cdef-0123-4567-89ab-cdef"))
    @Test fun newPin() = shot(PinUiState(context, PinStage.NewPin))
    @Test fun newCodeMenu() = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        AdminMenuScreen(AdminMenuUiState(context, listOf(AdminMenuItem(AdminDestination.NewRecoveryCode)))) {}
    } } }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(arrayOf(1f), arrayOf(2f))
    }
}
