// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.home.*
import org.plainphone.ui.theme.PlainphoneTheme

@RunWith(Parameterized::class)
class NeedsAttentionScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 360, screenHeight = 640,
            xdpi = 160, ydpi = 160, density = Density.MEDIUM, fontScale = scale, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0,
    )
    @Test fun noPending() = shot(null)
    @Test fun turnOff() = shot(PendingChange(PendingChangeKind.TurnOff("21:15", tomorrow)))
    @Test fun laterTurnOff() = shot(PendingChange(PendingChangeKind.TurnOff("11:59 PM",
        HomeNoticeDay(LocalDate.of(2026, 10, 22), 11))))
    @Test fun people() = shot(PendingChange(PendingChangeKind.People))
    @Test fun setup() = shot(PendingChange(PendingChangeKind.Setup))
    @Test fun committingTurnOff() = shot(PendingChange(PendingChangeKind.TurnOff("21:15", tomorrow), committing = true))
    @Test fun committingPeople() = shot(PendingChange(PendingChangeKind.People, committing = true))
    private fun shot(pending: PendingChange?) = paparazzi.snapshot {
        PlainphoneTheme { HomeTestFrame { NeedsAttentionScreen(NeedsAttentionUiState(pending), {}) } }
    }
    companion object {
        private val tomorrow = HomeNoticeDay(LocalDate.of(2026, 10, 12), 1)
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(arrayOf(1f), arrayOf(1.3f), arrayOf(1.5f), arrayOf(1.8f), arrayOf(2f))
    }
}
