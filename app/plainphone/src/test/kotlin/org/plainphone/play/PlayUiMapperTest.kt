// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.play

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.device.session.PlaySessionView
import org.plainphone.ui.trusted.*

class PlayUiMapperTest {
    @Test fun mapsRealWaitQuotaCountdownAndReadyWindow() {
        val context = AdminContext("Alex")
        val before = playOpeningUi(PlaySessionView(left = 1, limit = 2, waitMillis = 600000), context)
        assertEquals(10, before.durationMinutes); assertEquals(10, before.waitMinutes); assertEquals(1, before.left)
        assertEquals(OpeningStage.Before, before.stage)
        assertEquals(OpeningStage.Countdown, playOpeningUi(PlaySessionView(countdownMillis = 60001), context).stage)
        assertEquals(2, playOpeningUi(PlaySessionView(countdownMillis = 60001), context).minutes)
        assertEquals(OpeningStage.Ready, playOpeningUi(PlaySessionView(readyMillis = 300000), context).stage)
        assertEquals(5, playOpeningUi(PlaySessionView(readyMillis = 300000), context).minutes)
    }
}
