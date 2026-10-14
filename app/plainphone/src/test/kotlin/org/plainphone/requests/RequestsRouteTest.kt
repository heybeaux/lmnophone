// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.requests

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.shell.*
import org.plainphone.ui.user.*
import org.plainphone.policy.*
import org.plainphone.ui.requests.*

class RequestsRouteTest {
    @Test fun historyMapsEverySavedRequestWithStableIdsAndUserSideCannotRemove() {
        val view = RequestView(false, "", (1..12).map { RequestDisplay("r$it", "Bus app $it", 10, RequestStatus.NoSignal) })
        val history = RequestsUiMapper.map(view, "", null, false, historyOnly = true)
        assertEquals(12, history.rows.size)
        assertEquals("r12", history.rows.last().id)
        assertTrue(history.historyOnly)
        assertFalse(RequestsUiMapper.map(view, "", null, false).historyOnly)
        val failed = RequestsUiMapper.map(view.copy(available = false), "", null, false, true)
        assertEquals(RequestUiStatus.StorageError, failed.result)
    }
    @Test fun recipientReadFailureIsDistinctFromSaveFailure() {
        val read = recipientFailure(RequestRecipientUiState(), changing = false)
        assertTrue(read.readFailed)
        assertFalse(read.failed)
        val save = recipientFailure(RequestRecipientUiState(confirming = true), changing = true)
        assertFalse(save.readFailed)
        assertTrue(save.failed)
        assertTrue(save.confirming)
    }
    @Test fun requestReadRunsOnResumeOutsideTheClockLoop() {
        val source = java.io.File("src/main/kotlin/org/plainphone/MainActivity.kt").readText()
        val refresh = source.substringAfter("LaunchedEffect(resumed, phone) {").substringBefore("LaunchedEffect(shell.state, phone)")
        assertTrue(refresh.contains("if (resumed)"))
        assertTrue(refresh.contains("RequestEngine.get(this@MainActivity).view()"))
        assertFalse(refresh.contains("while (true)"))
        val clock = source.substringAfter("LaunchedEffect(shell.state, phone)").substringBefore("val cancel = cancelScreen")
        assertFalse(clock.contains("RequestEngine"))
        assertTrue(source.contains("ShellEffect.OpenAppRequests -> Intent(this, org.plainphone.requests.RequestsActivity::class.java)"))
    }

    @Test fun productionMoreUsesRealRequestActivity() {
        val phone = PreviewPhone(PreviewPhone.empty())
        val nav = ShellNavigator(phone, "LMNOPhone", testBuild = true)
        assertEquals(ShellEffect.OpenAppRequests, nav.onMore(ShellState(), MoreEvent.AskForApp).effect)
    }
    @Test fun productionMapperUsesSetupFlagNotOptionalNameAndKeepsWholeText() {
        val view = RequestView(true, "", listOf(RequestDisplay("a", "Bus\napp", 10, RequestStatus.NotConfirmed)))
        val ui = RequestsUiMapper.map(view, "Bus app", null, false)
        assertTrue(ui.canSend); assertTrue(ui.isSetUp)
        assertEquals("Bus\napp", ui.rows.single().text)
        assertEquals(RequestUiStatus.NotConfirmed, ui.rows.single().status)
        assertFalse(RequestsUiMapper.map(RequestView(false, "", emptyList()), "Bus app", null, false).canSend)
    }
    @Test fun moreMapperUsesConfiguredFlagEvenWithNoName() {
        val mapper = ShellUiMapper(java.util.Locale.ENGLISH, true)
        assertTrue(mapper.more(PreviewPhone.empty().copy(requestsReady = true)).requestsReady)
        assertFalse(mapper.more(PreviewPhone.empty()).requestsReady)
    }
    @Test fun savedRecipientEnablesRequestsWithoutInventingPinAuthorityOrWaitRows() {
        val store = object : RequestStore {
            var book = RequestBook()
            override fun read() = book
            override fun write(value: RequestBook) { book = value }
        }
        val port = object : RequestSmsPort {
            override fun problem(): RequestStatus? = null
            override fun parts(text: String) = listOf(text)
            override fun send(number: String, id: String, parts: List<String>) = error("No send in mapper test")
        }
        val engine = AppRequests(store, port, "test", { 0L }, { 0L })
        for (recipient in listOf(null, RequestRecipient("Sam", "555-0100"), RequestRecipient("", "555-0100"), null)) {
            engine.setRecipient(recipient)
            val view = engine.view()
            val snapshot = PreviewPhone.empty().copy(requestsReady = view.available && view.isSetUp)
            val more = ShellUiMapper(java.util.Locale.ENGLISH, true).more(snapshot)
            val ask = RequestsUiMapper.map(view, "Bus app", null, false)
            assertEquals(recipient != null, more.requestsReady)
            assertEquals(more.requestsReady, ask.isSetUp)
            assertEquals(more.requestsReady, ask.canSend)
            // This branch renders PIN settings and Ask, never the self-managed wait rows.
            assertTrue(more.pinHolderOnly)
            assertTrue(snapshot.trustedPeople.isEmpty())
            val pin = org.plainphone.pinUiState(org.plainphone.PinPageState(org.plainphone.PinPage.RECOVERY_LOCKED),
                org.plainphone.ui.trusted.AdminContext(""), "", "", "", "30 seconds", "", null,
                helper = "the person who set up LMNOPhone on this phone")
            assertEquals("the person who set up LMNOPhone on this phone", pin.helper)
        }
    }
    @Test fun recipientWriteRequiresFreshPinAndLiveScreen() {
        var saved = 0; var checks = 0
        val guard = RecipientChange({ pin -> checks++; pin.concatToString() == "123456" }, { saved++ })
        assertFalse(guard.apply("000000".toCharArray(), RequestRecipient("Sam", "555-0100")) { true })
        assertFalse(guard.apply("123456".toCharArray(), null) { false })
        assertEquals(0, saved)
        val pin = "123456".toCharArray()
        assertTrue(guard.apply(pin, RequestRecipient("", "555-0100")) { true })
        assertTrue(pin.all { it == '\u0000' }); assertEquals(1, saved)
        assertTrue(guard.apply("123456".toCharArray(), null) { true })
        assertEquals(2, saved); assertEquals(3, checks)
    }
}
