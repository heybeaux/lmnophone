// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

import org.junit.Assert.*
import org.junit.Test

class BluetoothDiscoveryControllerTest {
    private class Port : BluetoothDiscoveryPort {
        var ready = true
        var starts = 0
        var stops = 0
        var succeeds = true
        var throws = false
        var callback: (BluetoothDiscoveryEvent) -> Unit = {}
        override fun available() = ready
        override fun start(onEvent: (BluetoothDiscoveryEvent) -> Unit): Boolean {
            starts++; callback = onEvent
            if (throws) throw SecurityException()
            return succeeds
        }
        override fun stop() { stops++ }
        fun found(id: String, name: String?, bonded: Boolean = false, audio: Boolean = false) =
            callback(BluetoothDiscoveryEvent.Found(id, name, bonded, audio))
    }
    @Test fun startFailureDiscardsResultsAndStops() {
        val p = Port().apply { succeeds = false }; val c = BluetoothDiscoveryController(p)
        c.open(); assertTrue(c.state.failed); assertFalse(c.state.looking); assertTrue(p.stops > 0)
    }
    @Test fun missingPermissionOrBluetoothOffDoesNotStart() {
        val p = Port().apply { ready = false }; val c = BluetoothDiscoveryController(p)
        c.open(); assertTrue(c.state.failed); assertEquals(0, p.starts)
    }
    @Test fun adapterExceptionIsUnavailable() {
        val c = BluetoothDiscoveryController(Port().apply { throws = true })
        c.open(); assertTrue(c.state.failed)
    }
    @Test fun filtersBondedUnnamedDuplicateAndSortsCaseInsensitively() {
        val p = Port(); val c = BluetoothDiscoveryController(p); c.open()
        p.found("1", "Sam"); p.found("2", "alex"); p.found("3", "Jo", true)
        p.found("4", null); p.found("5", " \u202E\n"); p.found("1", "Sam")
        assertEquals(listOf("alex", "Sam"), c.state.devices.map { it.name })
        assertTrue(c.state.looking)
    }
    @Test fun keepsTheAudioClassPerDeviceAndTheLatestReportWins() {
        val p = Port(); val c = BluetoothDiscoveryController(p); c.open()
        p.found("1", "Sam", audio = true); p.found("2", "Keys"); p.found("3", "Jo", audio = true)
        p.found("3", "Jo", audio = false)
        assertEquals(listOf("Jo" to false, "Keys" to false, "Sam" to true),
            c.state.devices.map { it.name to it.audio })
    }
    @Test fun sortsBeforeCappingAtTwenty() {
        val p = Port(); val c = BluetoothDiscoveryController(p); c.open()
        (30 downTo 1).forEach { p.found("$it", "Sam ${it.toString().padStart(2, '0')}") }
        assertEquals(20, c.state.devices.size)
        assertEquals("Sam 01", c.state.devices.first().name)
        assertEquals("Sam 20", c.state.devices.last().name)
    }
    @Test fun endWithNoneAndLookAgain() {
        val p = Port(); val c = BluetoothDiscoveryController(p); c.open()
        p.callback(BluetoothDiscoveryEvent.Finished)
        assertFalse(c.state.looking); assertFalse(c.state.failed); assertTrue(c.state.devices.isEmpty())
        c.open(); assertTrue(c.state.looking); assertEquals(2, p.starts)
    }
    @Test fun endWithSomeKeepsResultsUntilNextLook() {
        val p = Port(); val c = BluetoothDiscoveryController(p); c.open(); p.found("1", "Sam")
        p.callback(BluetoothDiscoveryEvent.Finished)
        assertEquals(1, c.state.devices.size); assertFalse(c.state.looking)
        c.open(); assertTrue(c.state.devices.isEmpty())
    }
    @Test fun offMidScanClearsNamesAndStops() {
        val p = Port(); val c = BluetoothDiscoveryController(p); c.open(); p.found("1", "Sam")
        p.callback(BluetoothDiscoveryEvent.Unavailable)
        assertTrue(c.state.failed); assertFalse(c.state.looking); assertTrue(c.state.devices.isEmpty())
        p.found("2", "Jo"); assertTrue(c.state.devices.isEmpty())
    }
    @Test fun leaveStopsAndIgnoresLateEventsIncludingAfterReopen() {
        val p = Port(); val c = BluetoothDiscoveryController(p); c.open(); val old = p.callback
        c.close(); assertTrue(p.stops > 0); assertTrue(c.state.devices.isEmpty())
        old(BluetoothDiscoveryEvent.Found("1", "Sam", false)); assertTrue(c.state.devices.isEmpty())
        c.open(); old(BluetoothDiscoveryEvent.Finished); assertTrue(c.state.looking)
    }
    @Test fun bondedOrNowUnnamedDeviceIsRemoved() {
        val p = Port(); val c = BluetoothDiscoveryController(p); c.open(); p.found("1", "Sam")
        p.found("1", "Sam", true); assertTrue(c.state.devices.isEmpty())
        p.found("2", "Jo"); p.found("2", null); assertTrue(c.state.devices.isEmpty())
    }
    @Test fun pauseStopsAndResumeStartsANewSearch() {
        val p = Port(); val c = BluetoothDiscoveryController(p); c.open(); val old = p.callback
        p.found("1", "Sam"); p.callback(BluetoothDiscoveryEvent.Finished)
        c.pause()
        assertTrue(c.paused); assertTrue(p.stops > 0); assertTrue(c.state.devices.isEmpty())
        old(BluetoothDiscoveryEvent.Found("2", "Jo", false)); assertTrue(c.state.devices.isEmpty())
        c.resume()
        assertFalse(c.paused); assertEquals(2, p.starts); assertTrue(c.state.looking); assertFalse(c.state.failed)
        // The paused search's callbacks stay retired after the new search starts.
        old(BluetoothDiscoveryEvent.Finished); assertTrue(c.state.looking)
        p.found("3", "Al"); assertEquals(listOf("Al"), c.state.devices.map { it.name })
    }
    @Test fun resumeAfterLeavingThePageStartsNothing() {
        val p = Port(); val c = BluetoothDiscoveryController(p); c.open(); c.pause(); c.close()
        assertFalse(c.paused); c.resume(); assertEquals(1, p.starts); assertFalse(c.state.looking)
        val q = Port(); val d = BluetoothDiscoveryController(q); d.pause(); d.resume(); assertEquals(0, q.starts)
    }
    @Test fun resumeWithoutPauseKeepsTheRunningSearch() {
        val p = Port(); val c = BluetoothDiscoveryController(p); c.open(); p.found("1", "Sam")
        c.resume(); assertEquals(1, p.starts); assertEquals(1, c.state.devices.size)
    }
    @Test fun resumeAfterAFailureTriesAgain() {
        val p = Port().apply { ready = false }; val c = BluetoothDiscoveryController(p); c.open()
        assertTrue(c.state.failed); c.pause(); p.ready = true; c.resume()
        assertTrue(c.state.looking); assertFalse(c.state.failed); assertEquals(1, p.starts)
    }
}
