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
    @Test fun savedRequestHistoryDoesNotPopulateNeedsYou() {
        val book = RequestBook(RequestRecipient("Jo", "555-0100"), listOf(
            RequestEntry("saved-1", "Bus app", 1, 0, "old", listOf(RequestPart.Accepted)),
            RequestEntry("saved-2", "Maps", 1, 0, "old", listOf(RequestPart.NoSignal))))
        var writes = 0
        val store = object : RequestStore {
            override fun read() = book
            override fun write(value: RequestBook) { writes++ }
        }
        val sms = object : RequestSmsPort {
            override fun problem(): RequestStatus? = error("No SMS access for menu")
            override fun parts(text: String): List<String> = error("No SMS access for menu")
            override fun send(number: String, id: String, parts: List<String>) = error("Never send from menu")
        }
        val engine = AppRequests(store, sms, "current", { 10L }, { 1L })
        assertEquals(2, engine.view().requests.size)
        // Menu mapping has no request-store input or access. Saved history is not a to-do list.
        repeat(2) {
            assertTrue(map().needsYou.isEmpty())
            assertEquals(listOf("waiting:change-1", "attention"), map(waiting, HomeNotice.NeedsAttention).needsYou.map { it.id })
        }
        assertEquals(book, store.read())
        assertEquals(0, writes)
    }
    @Test fun itemsHaveRealDestinationsAndStaleWaitCannotOpenReplacement() {
        assertEquals(NeedsYouTarget.Waiting("change-1"), needsYouTarget("waiting:change-1", waiting))
        assertEquals(NeedsYouTarget.Attention, needsYouTarget("attention", waiting))
        assertNull(needsYouTarget("requests", waiting))
        assertNull(needsYouTarget("waiting:change-1", waiting.copy(requestId = "replacement")))
        assertNull(needsYouTarget("waiting:change-1", waiting.copy(phase = WaitingPhase.CANCELLED)))
        assertNull(needsYouTarget("unknown", waiting))
        assertEquals(WaitingStage.Committing, org.plainphone.boot.confirmStage("change-1", waiting.copy(phase = WaitingPhase.COMMITTING)))
    }
    @Test fun activityUsesProductionMapperRealSourcesAndRoutes() {
        val pin = java.io.File("src/main/kotlin/org/plainphone/PinAccessActivity.kt").readText()
        assertTrue(pin.contains("runtime.waitingNotice.collectAsState()"))
        assertTrue(pin.contains("runtime.notice.collectAsState()"))
        assertFalse(pin.contains("RequestEngine"))
        assertFalse(pin.contains("menuRequests"))
        assertTrue(pin.contains("AdminDestination.Requests -> openRequests()"))
        assertTrue(pin.contains("waitingMenu, homeNotice, needsYouCopy()"))
        assertTrue(pin.contains("is AdminMenuEvent.OpenNotice -> openNeedsYou(it.id)"))
        assertTrue(pin.contains("flow.useRecoveryFromApps()"))
        val main = java.io.File("src/main/kotlin/org/plainphone/MainActivity.kt").readText()
        assertTrue(main.contains("EXTRA_NEEDS_YOU_WAITING"))
        assertTrue(main.contains("EXTRA_NEEDS_YOU_ATTENTION"))
        assertTrue(main.contains("openWaitingChange(requestId)"))
    }
}
