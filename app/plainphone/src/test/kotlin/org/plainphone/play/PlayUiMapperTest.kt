// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.play

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.device.session.PlaySessionView
import org.plainphone.ui.trusted.*

class PlayUiMapperTest {
    @Test fun menuWaitRoundsUpAtMinuteBoundariesWithoutOverflow() {
        for ((millis, expected) in listOf(-1L to 1, 0L to 1, 1L to 1, 60000L to 1,
            60001L to 2, 600000L to 10, Long.MAX_VALUE to Int.MAX_VALUE)) {
            assertEquals(expected, playWaitMinutes(millis))
        }
    }

    @Test fun mapsRealWaitQuotaCountdownAndReadyWindow() {
        val context = AdminContext("Alex")
        val before = playOpeningUi(PlaySessionView(left = 1, limit = 2, waitMillis = 600000), context)
        assertEquals(10, before.durationMinutes); assertEquals(10, before.waitMinutes); assertEquals(1, before.left)
        assertEquals(OpeningStage.Before, before.stage); assertTrue(before.playDisclosure)
        assertEquals(OpeningStage.Countdown, playOpeningUi(PlaySessionView(countdownMillis = 60001), context).stage)
        assertEquals(2, playOpeningUi(PlaySessionView(countdownMillis = 60001), context).minutes)
        assertEquals(OpeningStage.Ready, playOpeningUi(PlaySessionView(readyMillis = 300000), context).stage)
        assertEquals(5, playOpeningUi(PlaySessionView(readyMillis = 300000), context).minutes)
    }
    @Test fun homeNoticeShowsCountdownAndStartWithoutOverridingWaitingOrAttention() {
        val view = PlaySessionView(countdownMillis = 60001, handbackReady = true)
        assertEquals(org.plainphone.ui.home.HomeNotice.OpeningUp(2), playHomeNotice(view, null))
        assertEquals(org.plainphone.ui.home.HomeNotice.ReadyToStart,
            playHomeNotice(view.copy(countdownMillis = null, readyMillis = 300000), null))
        for (base in listOf(org.plainphone.ui.home.HomeNotice.NeedsAttention, org.plainphone.ui.home.HomeNotice.PeopleChangeWaiting))
            assertEquals(base, playHomeNotice(view, base))
        assertEquals(org.plainphone.ui.home.HomeNotice.NeedsAttention, playHomeNotice(view.copy(handbackReady = false), null))
    }

    @Test fun retainedClosedGrantOffersDoneRatherThanAnotherCountdown() {
        assertTrue(playNeedsClosure(PlaySessionView(id = "grant", handbackReady = true)))
        assertFalse(playNeedsClosure(PlaySessionView(id = "wait", countdownMillis = 10)))
        assertFalse(playNeedsClosure(PlaySessionView(id = "ready", readyMillis = 10)))
        assertFalse(playNeedsClosure(PlaySessionView()))
    }

    @Test fun exhaustedQuotaSkipsBeforeButDoesNotHideAnExistingCountdown() {
        val context = AdminContext("Alex")
        assertEquals(OpeningStage.NoOpenings, playOpeningUi(PlaySessionView(left = 0, limit = 2), context).stage)
        for (left in listOf(1, 2)) assertEquals(OpeningStage.Before,
            playOpeningUi(PlaySessionView(left = left, limit = 2), context).stage)
        assertEquals(OpeningStage.Countdown,
            playOpeningUi(PlaySessionView(left = 0, countdownMillis = 1000), context).stage)
    }
    @Test fun refusedStartIsNotFailedHandbackEvenWithRetainedGrant() {
        val ready = PlaySessionView(handbackReady = true)
        assertNull(playSessionStage(ready, failed = true))
        assertNull(playSessionStage(ready.copy(id = "grant"), failed = true))
        assertEquals(SessionStage.NotReady, playSessionStage(ready.copy(handbackReady = false), failed = true))
        assertEquals(SessionStage.Checking, playSessionStage(ready, failed = true, busy = true))
        assertEquals(SessionStage.Restored, playSessionStage(ready, returned = true))
    }

    @Test fun productionApprovalMapperShowsTheGrantNotice() {
        val ui = playApprovalListUi(AdminContext("Alex"), listOf(HiddenAppRow("test.app", "Test app")))
        assertTrue(ui.showGrantNotice)
        assertEquals(1, ui.apps.size)
    }

}
