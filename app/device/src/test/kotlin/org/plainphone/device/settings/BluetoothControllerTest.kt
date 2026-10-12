// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

import org.junit.Assert.*
import org.junit.Test

class BluetoothControllerTest {
    private class Port : BluetoothPort {
        var facts = BluetoothFacts(BluetoothAdapterState.On, listOf("Sam headphones", "alex speaker", "Jo keyboard"))
        var failure: Exception? = null
        override fun read(): BluetoothFacts { failure?.let { throw it }; return facts }
    }
    @Test fun onSortsNamesIgnoringCase() {
        val c = BluetoothController(Port()); c.refresh()
        assertTrue(c.state.enabled)
        assertEquals(listOf("alex speaker", "Jo keyboard", "Sam headphones"), c.state.paired)
    }
    @Test fun onWithNoDevices() {
        val p = Port().apply { facts = facts.copy(paired = emptyList()) }
        val c = BluetoothController(p); c.refresh()
        assertTrue(c.state.enabled); assertEquals(emptyList<String?>(), c.state.paired)
    }
    @Test fun unnamedDevicesRetainOneRowEach() {
        val p = Port().apply { facts = facts.copy(paired = listOf(null, "", "   ")) }
        val c = BluetoothController(p); c.refresh()
        assertEquals(listOf(null, null, null), c.state.paired)
    }
    @Test fun sortsBeforeTakingTenAndKeepsDuplicateNames() {
        val p = Port().apply { facts = facts.copy(paired = (1..12).reversed().map { "Device %02d".format(it) } + "Device 01") }
        val c = BluetoothController(p); c.refresh()
        assertEquals(listOf("Device 01") + (1..9).map { "Device %02d".format(it) }, c.state.paired)
    }
    @Test fun allOtherStatesClearOldList() {
        for (state in listOf(BluetoothAdapterState.Off, BluetoothAdapterState.TurningOn,
            BluetoothAdapterState.TurningOff, BluetoothAdapterState.Absent)) {
            val p = Port(); val c = BluetoothController(p); c.refresh()
            p.facts = p.facts.copy(adapter = state); c.refresh()
            assertFalse(c.state.enabled); assertTrue(c.state.paired.isEmpty()); assertFalse(c.state.readFailed)
        }
    }
    @Test fun failureAndMissingGrantClearOldListThenRecover() {
        for (failure in listOf(SecurityException(), IllegalStateException())) {
            val p = Port(); val c = BluetoothController(p); c.refresh()
            p.failure = failure; c.refresh()
            assertTrue(c.state.readFailed); assertFalse(c.state.enabled); assertTrue(c.state.paired.isEmpty())
            p.failure = null; c.refresh(); assertFalse(c.state.readFailed); assertEquals(3, c.state.paired.size)
        }
    }
    @Test fun removesControlsFromDisplayNames() {
        val p = Port().apply { facts = facts.copy(paired = listOf("Sam\u202e\n speaker", "\u200b")) }
        val c = BluetoothController(p); c.refresh()
        assertEquals(listOf("Sam speaker", null), c.state.paired)
    }
}
