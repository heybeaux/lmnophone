// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.trusted.*

/** Tall supplemental captures show the complete 2.0 body; compact captures still cover scrolling. */
class PlayFullBodyScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 2200, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = 2f, locale = "en"), showSystemUi = false)
    private val context = AdminContext("the person using this phone")
    @Test fun before() {
        paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
            OpeningScreen(OpeningUiState(context, OpeningStage.Before, OpeningKind.Play,
                durationMinutes = 10, playDisclosure = true)) {}
        } } }
    }
    @Test fun notReady() {
        paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
            SessionScreen(SessionUiState(context, SessionStage.NotReady,
                helper = "the person who set up this phone", canShowDetails = false)) {}
        } } }
    }
    @Test fun exhaustedQuotaUsesSingularAndPlural() {
        val res = paparazzi.context.resources
        assertTrue(res.getQuantityString(R.plurals.opening_none_body, 1, 1, "later").startsWith("You've used your 1 opening."))
        assertTrue(res.getQuantityString(R.plurals.opening_none_body, 2, 2, "later").startsWith("You've used your 2 openings."))
    }
    @Test fun countsUseSingularAndPluralInBeforeAndReady() {
        val res = paparazzi.context.resources
        for (left in listOf(1, 2)) {
            val expected = if (left == 1) "This is your last opening today." else "2 openings left"
            assertTrue(res.getQuantityString(R.plurals.opening_before_body, left, 10, "Play", 10, left).contains(expected))
            assertTrue(res.getQuantityString(R.plurals.opening_ready_body, left, 5, left).contains(expected))
            assertTrue(res.getQuantityString(R.plurals.opening_ready_body_one, left, left).contains(expected))
        }
    }
}
