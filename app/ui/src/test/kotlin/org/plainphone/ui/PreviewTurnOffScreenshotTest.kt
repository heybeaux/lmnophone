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
import org.plainphone.ui.home.*

@RunWith(Parameterized::class)
class PreviewTurnOffScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0)
    @Test fun intro() = turnOff(TurnOffStage.Intro)
    @Test fun firstConfirmation() = turnOff(TurnOffStage.FirstConfirmation)
    @Test fun lastConfirmation() = turnOff(TurnOffStage.LastConfirmation)
    @Test fun selfManagedReleaseConfirmation() = turnOff(TurnOffStage.FirstConfirmation, AdminContext("Alex"), false)
    @Test fun supervisedPreviewConfirmation() = turnOff(TurnOffStage.FirstConfirmation, AdminContext("Alex", "Jo"), true)
    @Test fun supervisedReleaseConfirmation() = turnOff(TurnOffStage.FirstConfirmation, AdminContext("Alex", "Jo"), false)
    @Test fun selfManagedReleaseLastCheck() = turnOff(TurnOffStage.LastConfirmation, AdminContext("Alex"), false)
    @Test fun supervisedReleaseLastCheck() = turnOff(TurnOffStage.LastConfirmation, AdminContext("Alex", "Jo"), false)
    @Test fun freshPin() = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        PinScreen(PinUiState(AdminContext("Alex"), PinStage.Enter)) {}
    } } }
    @Test fun started() = waiting(WaitingStage.Started)
    @Test fun busy() = waiting(WaitingStage.Another)
    @Test fun saveFailed() = waiting(WaitingStage.SaveFailed)
    @Test fun maturityNotice() = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        HomeScreen(HomeUiState("10:42", "Monday 12 October", "Mon 12 Oct", emptyList(), HomeNotice.PreviewTurnOffClosed)) {}
    } } }
    private fun turnOff(stage: TurnOffStage, context: AdminContext = AdminContext("Alex"), testBuild: Boolean = true) = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        TurnOffScreen(TurnOffUiState(context, stage, helper = "Sam", testBuild = testBuild)) {}
    } } }
    private fun waiting(stage: WaitingStage) = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        WaitingChangeScreen(WaitingChangeUiState(AdminContext("Alex"), stage, "Turn LMNOPhone off", happensAt = "10:42",
            happensAtDay = HomeNoticeDay(java.time.LocalDate.of(2026, 10, 13), 1))) {}
    } } }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(1f, 1.3f, 1.5f, 1.8f, 2f).map { arrayOf(it) }
    }
}
