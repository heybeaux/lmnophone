// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

import org.junit.Assert.*
import org.junit.Test

class RingtoneControllerTest {
    private class Port : RingtonePort {
        var current: String? = "b"
        var tones = listOf(RingtoneChoice("b", "Beta"), RingtoneChoice("a", "alpha"))
        var allowed = true
        var failWrite = false
        var failRead = false
        var failReadAfterWrite = false
        var ignoreWrite = false
        var failList = false
        var failTitle = false
        val events = mutableListOf<String>()
        override fun choices(): List<RingtoneChoice> { check(!failList); return tones }
        override fun read(): String? { check(!failRead); return current }
        override fun title(uri: String): String? { check(!failTitle); return tones.find { it.id == uri }?.title }
        override fun canWrite() = allowed
        override fun write(uri: String) {
            events += "write:$uri"; check(!failWrite)
            if (!ignoreWrite) current = uri
            if (failReadAfterWrite) failRead = true
        }
        override fun preview(uri: String) { events += "play:$uri" }
        override fun stopPreview() { events += "stop" }
    }
    @Test fun currentInListIsSelectedAndTitlesAreSortedIgnoringCase() {
        val c = RingtoneController(Port()); c.onOpen()
        assertEquals(listOf("a", "b"), c.state.choices.map { it.id })
        assertEquals("b", c.state.selectedId); assertFalse(c.state.currentUnlisted)
        assertEquals("Beta", c.currentTitle())
    }
    @Test fun unlistedCurrentStaysWithoutSelectionOrWrite() {
        val p = Port().apply { current = "custom" }; val c = RingtoneController(p); c.onOpen()
        assertNull(c.state.selectedId); assertTrue(c.state.currentUnlisted)
        assertTrue(c.done()); assertEquals("custom", p.current)
        assertFalse(p.events.any { it.startsWith("write:") })
    }
    @Test fun noDefaultHasNoTitleOrSelection() {
        val p = Port().apply { current = null }; val c = RingtoneController(p); c.onOpen()
        assertNull(c.currentTitle()); assertNull(c.state.selectedId)
        assertTrue(c.state.currentUnlisted); assertTrue(c.done()); assertNull(p.current)
    }
    @Test fun emptyListDoesNotChangeCurrent() {
        val p = Port().apply { tones = emptyList() }; val c = RingtoneController(p); c.onOpen()
        assertTrue(c.state.choices.isEmpty()); assertNull(c.state.selectedId)
        c.select("a"); assertTrue(c.done()); assertEquals("b", p.current)
    }
    @Test fun selectedChoicePreviewsThenSavesOnlyAfterDoneAndReadback() {
        val p = Port(); val c = RingtoneController(p); c.onOpen(); p.events.clear()
        c.select("a"); assertEquals(listOf("stop", "play:a"), p.events)
        assertEquals("b", p.current); assertEquals("a", c.state.selectedId)
        assertTrue(c.done()); assertEquals("a", p.current); assertEquals("alpha", c.currentTitle())
        assertEquals(listOf("stop", "play:a", "stop", "write:a"), p.events)
    }
    @Test fun unchangedSelectionDoesNotWrite() {
        val p = Port(); val c = RingtoneController(p); c.onOpen(); c.select("b")
        assertTrue(c.done()); assertFalse(p.events.any { it.startsWith("write:") })
    }
    @Test fun writeFailureAndMismatchStayOnChooser() {
        for (throwing in listOf(true, false)) {
            val p = Port().apply { failWrite = throwing; ignoreWrite = !throwing }
            val c = RingtoneController(p); c.onOpen(); c.select("a")
            assertFalse(c.done()); assertTrue(c.state.failed); assertEquals("Beta", c.currentTitle())
        }
    }
    @Test fun readbackFailureStaysAndDoesNotReportRequestedTitle() {
        val p = Port().apply { failReadAfterWrite = true }; val c = RingtoneController(p)
        c.onOpen(); c.select("a"); assertFalse(c.done()); assertTrue(c.state.failed)
        assertNull(c.currentTitle())
    }
    @Test fun noGrantStillPreviewsButCannotSave() {
        val p = Port().apply { allowed = false }; val c = RingtoneController(p); c.onOpen()
        assertFalse(c.state.canSave); assertTrue(c.state.failed)
        c.select("a"); assertTrue("play:a" in p.events); assertFalse(c.done())
        assertFalse(p.events.any { it.startsWith("write:") })
    }
    @Test fun lostGrantIsRecheckedBeforeWrite() {
        val p = Port(); val c = RingtoneController(p); c.onOpen(); c.select("a"); p.allowed = false
        assertFalse(c.done()); assertFalse(c.state.canSave)
        assertFalse(p.events.any { it.startsWith("write:") })
    }
    @Test fun nextPreviewStopsPreviousAndLeavingStopsWithoutSaving() {
        val p = Port(); val c = RingtoneController(p); c.onOpen(); p.events.clear()
        c.select("a"); c.select("b"); c.onLeave()
        assertEquals(listOf("stop", "play:a", "stop", "play:b", "stop"), p.events)
        c.select("a"); assertEquals("stop", p.events.last()); assertFalse(c.done())
    }
    @Test fun listAndReadFailuresAreContainedAndTitleFailureUsesFallback() {
        val p = Port().apply { failList = true }; val c = RingtoneController(p); c.onOpen()
        assertTrue(c.state.failed); assertTrue(c.state.choices.isEmpty()); assertFalse(c.state.canSave)
        p.failList = false; p.failRead = true; c.onOpen()
        assertTrue(c.state.failed); assertFalse(c.state.canSave)
        p.failRead = false; p.failTitle = true; assertNull(c.currentTitle())
    }
}
