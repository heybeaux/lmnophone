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
    @Test fun productionMoreUsesRealRequestActivity() {
        val phone = PreviewPhone(PreviewPhone.empty())
        val nav = ShellNavigator(phone, phone, "LMNOPhone", testBuild = true)
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
