// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.junit.Assert.*
import org.junit.Test

class AppRequestsTest {
    private class Memory : RequestStore {
        var book = RequestBook(); var fail = false
        override fun read() = book
        override fun write(value: RequestBook) { if (fail) error("disk unavailable"); book = value }
    }
    private class Port : RequestSmsPort {
        var calls = 0; var sentNumber = ""; var permitted = true; var signal = true; var throws = false
        override fun problem() = if (!permitted) RequestStatus.NoPermission else if (!signal) RequestStatus.NoSignal else null
        override fun parts(text: String) = listOf(text, "second part")
        override fun send(number: String, id: String, parts: List<String>) { calls++; sentNumber = number; if (throws) error("uncertain handover") }
    }
    private val store = Memory(); private val port = Port()
    private var now = 1000L
    private val engine = AppRequests(store, port, "process", { now }, { 10L })
    private fun setup() { engine.setRecipient(RequestRecipient("Sam", "555-0100")) }
    @Test fun removalKeepsQuotaAcrossRestartAndCannotBeResubmitted() {
        setup(); repeat(5) { engine.submit("r$it", "Bus app") }
        assertTrue(engine.remove("r0"))
        assertEquals(4, engine.view().requests.size)
        val restarted = AppRequests(store, port, "next", { now }, { 10L })
        assertEquals(RequestStatus.DailyLimit, restarted.submit("six", "Maps app").status)
        restarted.submit("r0", "Bus app")
        assertEquals(5, port.calls)
        assertFalse(restarted.view().requests.any { it.id == "r0" })
    }
    @Test fun removeWhileSendingIgnoresLateCallbacksAndPreservesOthers() {
        setup(); engine.submit("a", "Bus app"); engine.submit("b", "Maps app")
        assertTrue(engine.remove("a"))
        val removed = store.book
        assertFalse(engine.result("a", 0, RequestPart.Accepted))
        assertFalse(engine.result("a", 1, RequestPart.Failed))
        assertEquals(removed, store.book)
        assertEquals("b", engine.view().requests.single().id)
        assertTrue(engine.remove("a"))
    }
    @Test fun failedOrUnconfirmedRemovalDoesNotReportSuccess() {
        setup(); engine.submit("a", "Bus app"); store.fail = true
        assertFalse(engine.remove("a"))
        assertEquals("a", engine.view().requests.single().id)
        val ignored = object : RequestStore {
            override fun read() = store.book
            override fun write(value: RequestBook) = Unit
        }
        assertFalse(AppRequests(ignored, port, "next", { now }, { 10L }).remove("a"))
        var reads = 0
        val unreadable = object : RequestStore {
            override fun read(): RequestBook { if (reads++ > 0) error("read failed"); return store.book }
            override fun write(value: RequestBook) = Unit
        }
        assertFalse(AppRequests(unreadable, port, "next", { now }, { 10L }).remove("a"))
    }
    @Test fun noRecipientDoesNotSaveOrSend() {
        assertEquals(RequestStatus.NotSetUp, engine.submit("a", "Bus app").status)
        assertEquals(0, port.calls); assertTrue(store.book.requests.isEmpty())
    }
    @Test fun sentRequiresEveryPartAndDuplicateTapIsIgnored() {
        setup()
        assertEquals(RequestStatus.Sending, engine.submit("a", "Bus app").status)
        engine.submit("a", "Bus app")
        assertEquals(1, port.calls); assertEquals("555-0100", port.sentNumber)
        engine.result("a", 0, RequestPart.Accepted)
        assertEquals(RequestStatus.Sending, engine.view().requests.single().status)
        engine.result("a", 1, RequestPart.Accepted)
        assertEquals(RequestStatus.Sent, engine.view().requests.single().status)
        engine.result("a", 1, RequestPart.Failed)
        assertEquals(RequestStatus.Sent, engine.view().requests.single().status)
    }
    @Test fun airplaneModeIsNotSentAndStillSaved() {
        setup(); port.signal = false
        assertEquals(RequestStatus.NoSignal, engine.submit("a", "Bus app").status)
        assertEquals(0, port.calls); assertEquals("Bus app", store.book.requests.single().text)
    }
    @Test fun partialSendIsNeverClaimedNotSentOrSent() {
        setup(); engine.submit("a", "Bus app")
        engine.result("a", 0, RequestPart.Accepted); engine.result("a", 1, RequestPart.NoSignal)
        assertEquals(RequestStatus.NotConfirmed, engine.view().requests.single().status)
    }
    @Test fun restartAndTimeoutAreUnconfirmedAndNeverResend() {
        setup(); engine.submit("a", "Bus app")
        assertEquals(RequestStatus.NotConfirmed, AppRequests(store, port, "next", { now }, { 10L }).view().requests.single().status)
        now += AppRequests.UNANSWERED_MS + 1
        assertEquals(RequestStatus.NotConfirmed, engine.view().requests.single().status)
        assertEquals(1, port.calls)
        engine.result("a", 0, RequestPart.Accepted); engine.result("a", 1, RequestPart.Accepted)
        assertEquals(RequestStatus.Sent, engine.view().requests.single().status)
    }
    @Test fun quotaSurvivesNewEngineAndChangingRecipient() {
        setup(); repeat(5) { engine.submit("r$it", "Bus app") }
        engine.setRecipient(RequestRecipient("Jo", "555-0101"))
        val restarted = AppRequests(store, port, "next", { now }, { 10L })
        assertEquals(RequestStatus.DailyLimit, restarted.submit("six", "Maps app").status)
        assertEquals(5, port.calls)
    }
    @Test fun failedWritePreventsDispatchAndDamagedReadDoesNotResetQuota() {
        setup(); store.fail = true
        assertEquals(RequestStatus.StorageError, engine.submit("a", "Bus app").status)
        assertEquals(0, port.calls)
        val bad = object : RequestStore { override fun read(): RequestBook = error("damaged"); override fun write(value: RequestBook) = Unit }
        assertEquals(RequestStatus.StorageError, AppRequests(bad, port, "x", { 0L }, { 10L }).submit("a", "Bus app").status)
    }
    @Test fun throwingHandoverStaysUnconfirmedUntilLateCallbacks() {
        setup(); port.throws = true
        assertEquals(RequestStatus.NotConfirmed, engine.submit("a", "Bus app").status)
        assertEquals(RequestStatus.NotConfirmed, engine.view().requests.single().status)
        engine.result("a", 0, RequestPart.Accepted); engine.result("a", 1, RequestPart.Accepted)
        assertEquals(RequestStatus.Sent, engine.view().requests.single().status)
        assertEquals(1, port.calls)
    }
    @Test fun optionalNameAndInvalidInputs() {
        engine.setRecipient(RequestRecipient("", "555-0100"))
        assertTrue(engine.view().isSetUp)
        assertEquals(RequestStatus.Invalid, engine.submit("a", "123").status)
        assertEquals(RequestStatus.Invalid, engine.submit("b", "a".repeat(281)).status)
        assertThrows(IllegalArgumentException::class.java) { engine.setRecipient(RequestRecipient("Sam\u202e", "555-0100")) }
        assertThrows(IllegalArgumentException::class.java) { engine.setRecipient(RequestRecipient("Sam", "911")) }
        engine.setRecipient(null)
        assertEquals(RequestStatus.NotSetUp, engine.submit("c", "Bus app").status)
    }
}
