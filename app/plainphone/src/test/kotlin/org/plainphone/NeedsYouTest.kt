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
    private val requests = RequestView(true, "Jo", listOf(RequestDisplay("request-1", "Bus app", 1, RequestStatus.Sent)))
    private val copy = NeedsYouCopy({ "Waiting: $it" }, "LMNOPhone needs attention", { n, user -> "$n requests from $user" })
    private fun map(wait: WaitingNotice? = null, notice: HomeNotice? = null, view: RequestView? = null, user: String = "Alex") =
        pinMenuUiState(AdminContext(user), wait, notice, view, copy)

    @Test fun productionMapperEmptyAndEachRealSourceInOrder() {
        assertTrue(map().needsYou.isEmpty())
        assertEquals(listOf("waiting:change-1"), map(waiting).needsYou.map { it.id })
        assertEquals(listOf("attention"), map(notice = HomeNotice.NeedsAttention).needsYou.map { it.id })
        assertEquals(listOf("requests"), map(view = requests).needsYou.map { it.id })
        assertEquals(listOf("waiting:change-1", "attention", "requests"), map(waiting, HomeNotice.NeedsAttention, requests).needsYou.map { it.id })
        assertTrue(map(waiting.copy(phase = WaitingPhase.COMPLETED)).needsYou.isEmpty())
        assertTrue(map(view = requests.copy(available = false)).needsYou.isEmpty())
        assertTrue(map(view = requests.copy(requests = emptyList())).needsYou.isEmpty())
    }
    @Test fun requestCountUsesStoreAndPhoneUserNotRecipient() {
        val entries = RequestStatus.entries.mapIndexed { i, status -> RequestDisplay("r$i", "Bus app", 1, status) }
        assertEquals("${entries.size} requests from Alex", map(view = requests.copy(requests = entries)).needsYou.single().text)
        assertEquals("1 requests from the person using this phone", map(view = requests, user = "the person using this phone").needsYou.single().text)
        assertEquals("1 requests from Sam", map(view = requests, user = "Sam").needsYou.single().text)
    }
    @Test fun persistedRequestsAreReadWithoutSendingOrChangingSavedData() {
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
        repeat(2) { assertEquals("2 requests from Alex", map(view = engine.view()).needsYou.single().text) }
        assertEquals(book, store.read())
        assertEquals(0, writes)
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
        assertTrue(pin.contains("RequestEngine.get(this@PinAccessActivity).view()"))
        assertTrue(pin.contains("waitingMenu, homeNotice, menuRequests, needsYouCopy()"))
        assertTrue(pin.contains("is AdminMenuEvent.OpenNotice -> openNeedsYou(it.id)"))
        assertTrue(pin.contains("flow.useRecoveryFromApps()"))
        val main = java.io.File("src/main/kotlin/org/plainphone/MainActivity.kt").readText()
        assertTrue(main.contains("EXTRA_NEEDS_YOU_WAITING"))
        assertTrue(main.contains("EXTRA_NEEDS_YOU_ATTENTION"))
        assertTrue(main.contains("openWaitingChange(requestId)"))
    }
}
