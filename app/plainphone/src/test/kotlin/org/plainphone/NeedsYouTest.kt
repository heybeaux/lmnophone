// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.policy.*
import org.plainphone.ui.home.HomeNotice
import org.plainphone.ui.trusted.*

class NeedsYouTest {
    private val waiting = WaitingNotice("change-1", WaitingChangeKind.TURN_OFF, WaitingPhase.PENDING, 60000)
    private val copy = NeedsYouCopy({ "Waiting: $it" }, "LMNOPhone needs attention")
    private fun map(wait: WaitingNotice? = null, notice: HomeNotice? = null, user: String = "Alex") =
        pinMenuUiState(AdminContext(user), wait, notice, copy)

    @Test fun productionMapperEmptyAndEachRealSourceInOrder() {
        assertTrue(map().needsYou.isEmpty())
        assertEquals(listOf("waiting:change-1"), map(waiting).needsYou.map { it.id })
        assertEquals(listOf("attention"), map(notice = HomeNotice.NeedsAttention).needsYou.map { it.id })
        assertEquals(listOf("waiting:change-1", "attention"), map(waiting, HomeNotice.NeedsAttention).needsYou.map { it.id })
        assertTrue(map(waiting.copy(phase = WaitingPhase.COMPLETED)).needsYou.isEmpty())
    }
    @Test fun configuredNamesArePreservedWithoutInventingRequestNotices() {
        for (user in listOf("Alex", "Sam", "the person using this phone")) {
            assertEquals(user, map(user = user).context.userName)
            assertTrue(map(user = user).needsYou.isEmpty())
            assertTrue(map(user = user).items.any { it.destination == AdminDestination.Requests })
        }
    }
    @Test fun productionMapperCountsSavedRequestsAfterWaitingAndAttention() {
        val view = RequestView(true, "Jo", listOf(
            RequestDisplay("a", "Bus app", 1, RequestStatus.Sending),
            RequestDisplay("b", "Maps", 1, RequestStatus.NoSignal)))
        val copy = NeedsYouCopy({ "Waiting: $it" }, "Attention", { n, user -> "$n requests from $user" })
        val menu = pinMenuUiState(AdminContext("Alex"), waiting, HomeNotice.NeedsAttention, copy, view)
        assertEquals(listOf("waiting:change-1", "attention", "requests"), menu.needsYou.map { it.id })
        assertEquals("2 requests from Alex", menu.needsYou.last().text)
        assertEquals("2", menu.items.single { it.destination == AdminDestination.Requests }.detail)
        for (empty in listOf(view.copy(requests = emptyList()), view.copy(available = false))) {
            val hidden = pinMenuUiState(AdminContext("Alex"), null, null, copy, empty)
            assertTrue(hidden.needsYou.isEmpty())
            assertEquals("", hidden.items.single { it.destination == AdminDestination.Requests }.detail)
        }
    }
    @Test fun itemsHaveRealDestinationsAndStaleWaitCannotOpenReplacement() {
        assertEquals(NeedsYouTarget.Waiting("change-1"), needsYouTarget("waiting:change-1", waiting))
        assertEquals(NeedsYouTarget.Attention, needsYouTarget("attention", waiting))
        assertEquals(NeedsYouTarget.Requests, needsYouTarget("requests", waiting))
        assertNull(needsYouTarget("waiting:change-1", waiting.copy(requestId = "replacement")))
        assertNull(needsYouTarget("waiting:change-1", waiting.copy(phase = WaitingPhase.CANCELLED)))
        assertNull(needsYouTarget("unknown", waiting))
        assertEquals(WaitingStage.Committing, org.plainphone.boot.confirmStage("change-1", waiting.copy(phase = WaitingPhase.COMMITTING)))
    }
    @Test fun activityUsesProductionMapperRealSourcesAndRoutes() {
        val pin = java.io.File("src/main/kotlin/org/plainphone/PinAccessActivity.kt").readText()
        assertTrue(pin.contains("runtime.waitingNotice.collectAsState()"))
        assertTrue(pin.contains("runtime.notice.collectAsState()"))
        assertTrue(pin.contains("RequestEngine"))
        assertTrue(pin.contains("menuRequests"))
        assertTrue(pin.contains("AdminDestination.Requests -> openRequests()"))
        assertTrue(pin.contains("waitingMenu, homeNotice, needsYouCopy(), menuRequests"))
        assertTrue(pin.contains("is AdminMenuEvent.OpenNotice -> openNeedsYou(it.id)"))
        assertTrue(pin.contains("flow.useRecoveryFromApps()"))
        val main = java.io.File("src/main/kotlin/org/plainphone/MainActivity.kt").readText()
        assertTrue(main.contains("EXTRA_NEEDS_YOU_WAITING"))
        assertTrue(main.contains("EXTRA_NEEDS_YOU_ATTENTION"))
        assertTrue(main.contains("openWaitingChange(requestId)"))
    }
}
