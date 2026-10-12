// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

import org.junit.Assert.*
import org.junit.Test

class WifiControllerTest {
    private class Port : WifiPort {
        var facts = WifiFacts(true, true, "\"AndroidWifi\"", emptyList())
        var fail = false
        override fun read(): WifiFacts { if (fail) error("unreadable"); return facts }
    }
    @Test fun connectedNamed() {
        val c = WifiController(Port()); c.refresh()
        assertTrue(c.state.connected); assertEquals("AndroidWifi", c.state.network)
    }
    @Test fun connectedUnnamed() {
        for (name in listOf(null, "<unknown ssid>", "\"<unknown ssid>\"", "", "\"\"")) {
            val p = Port().apply { facts = facts.copy(ssid = name) }
            val c = WifiController(p); c.refresh()
            assertTrue(c.state.connected); assertNull(c.state.network)
        }
    }
    @Test fun disconnectedDoesNotKeepOldName() {
        val p = Port(); val c = WifiController(p); c.refresh()
        p.facts = p.facts.copy(connected = false); c.refresh()
        assertFalse(c.state.connected); assertNull(c.state.network)
    }
    @Test fun wifiOffClearsConnectionAndList() {
        val p = Port(); val c = WifiController(p); c.refresh()
        p.facts = p.facts.copy(enabled = false, nearby = listOf(WifiNetwork("Sam", -20)))
        c.refresh(); assertFalse(c.state.enabled); assertFalse(c.state.connected)
        assertNull(c.state.network); assertNull(c.state.nearby)
    }
    @Test fun unreadableScanKeepsKnownConnectionButDropsOldList() {
        val p = Port().apply { facts = facts.copy(nearby = listOf(WifiNetwork("Sam", -20))) }
        val c = WifiController(p); c.refresh()
        p.facts = p.facts.copy(nearby = null); c.refresh()
        assertTrue(c.state.connected); assertEquals("AndroidWifi", c.state.network); assertNull(c.state.nearby)
    }
    @Test fun strongestFirstDedupeHiddenAndLimit() {
        val p = Port().apply { facts = facts.copy(nearby =
            (1..14).map { WifiNetwork("Network $it", -it) } +
            listOf(WifiNetwork("Network 14", 0), WifiNetwork("", 10), WifiNetwork("<unknown ssid>", 10))) }
        val c = WifiController(p); c.refresh()
        assertEquals(listOf("Network 14") + (1..9).map { "Network $it" }, c.state.nearby)
    }
    @Test fun quotePairRemovedButEmbeddedQuotesPreserved() {
        val p = Port().apply { facts = facts.copy(ssid = "\"Sam's \"Wi-Fi\"\"",
            nearby = listOf(WifiNetwork("\"Jo\"", -20), WifiNetwork("Jo", -30))) }
        val c = WifiController(p); c.refresh()
        assertEquals("Sam's \"Wi-Fi\"", c.state.network); assertEquals(listOf("Jo"), c.state.nearby)
    }
    @Test fun failureClearsStaleFacts() {
        val p = Port(); val c = WifiController(p); c.refresh(); p.fail = true; c.refresh()
        assertNull(c.state.network); assertNull(c.state.nearby)
    }
    @Test fun controlCharactersCannotReorderOrSplitLabels() {
        val p = Port().apply { facts = facts.copy(ssid = "\"Sam\u202e\nWi-Fi\"") }
        val c = WifiController(p); c.refresh(); assertEquals("SamWi-Fi", c.state.network)
    }
}
