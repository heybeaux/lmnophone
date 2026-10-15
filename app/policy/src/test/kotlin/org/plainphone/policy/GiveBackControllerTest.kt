// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class GiveBackControllerTest {
    private val person = TrustedPersonId("holder")
    private val op = WaitingOperation(WaitingIdentity("off", "code"), person, 0, 0, Mode.TRUSTED,
        RunningTime("epoch", 0), WaitingPhase.COMMITTING, mapOf(person to 0L))
    private val actions = GiveBackKind.entries.map { GiveBackAction(it, "test.app", "test.permission") }
    private val store = Store(WaitingState(1, op))
    private val port = Port()
    private fun run(retry: Boolean = false) = GiveBackController(store, port).run("off", BatteryState(80, true), retry)

    @Test fun `inventory is durable before first effect and owner is removed last`() {
        assertEquals(GiveBackOutcome.OFF, run().outcome)
        assertEquals(actions, port.writes)
        assertEquals("remove", port.events.last())
        assertEquals(WaitingPhase.COMPLETED, store.state!!.operation!!.phase)
        assertTrue(port.reads.containsAll(actions))
    }

    @Test fun `every failed readback is journaled counted and retains owner until explicit retry`() {
        for (action in actions) {
            reset(); port.fail = action
            val result = run()
            assertTrue(port.faultFired)
            assertEquals(GiveBackOutcome.FAILED, result.outcome)
            assertEquals(1, result.remaining)
            assertTrue(port.owner)
            assertEquals(GiveBackStatus.FAILED, store.state!!.giveBack!!.entries.single { it.action == action }.status)
            port.fail = null
            assertEquals(GiveBackOutcome.FAILED, run().outcome)
            assertEquals(GiveBackOutcome.OFF, run(retry = true).outcome)
        }
    }

    @Test fun `all failures are counted and none can remove owner`() {
        port.failAll = true
        assertEquals(actions.size, run().remaining)
        assertTrue(port.faultFired)
        assertTrue(port.owner)
    }

    @Test fun `crash after each saved result resumes at next unfinished item`() {
        for (index in actions.indices) {
            reset(); store.crashAfter = index + 2 // inventory, then one checkpoint per action
            assertFailsWith<SimulatedCrash> { run() }
            assertTrue(store.crashFired)
            assertEquals(actions.take(index + 1), port.writes)
            store.crashAfter = null
            assertEquals(GiveBackOutcome.OFF, run().outcome)
            assertEquals(actions, port.writes)
        }
    }

    @Test fun `unacknowledged journal never permits the next effect`() {
        store.failSave = true
        assertEquals(GiveBackOutcome.NOT_READY, run().outcome)
        assertTrue(port.writes.isEmpty())
        assertTrue(port.owner)
    }

    @Test fun `failed result checkpoint stops before the next physical item`() {
        store.failAt = 2 // inventory saved, then first result write fails
        assertEquals(GiveBackOutcome.NOT_READY, run().outcome)
        assertEquals(actions.take(1), port.writes)
        assertTrue(port.owner)
    }

    @Test fun `throwing write at every item still records failure and retains owner`() {
        for (action in actions) {
            reset(); port.throwAt = action
            assertEquals(1, run().remaining)
            assertTrue(port.faultFired)
            assertTrue(port.owner)
            assertEquals(GiveBackStatus.FAILED, store.state!!.giveBack!!.entries.single { it.action == action }.status)
        }
    }

    @Test fun `missing pending wrong or cancelled record never restores`() {
        for (phase in listOf(WaitingPhase.PENDING, WaitingPhase.CANCELLED)) {
            store.state = WaitingState(1, op.withPhase(phase))
            assertEquals(GiveBackOutcome.IGNORED, run().outcome)
        }
        store.state = null
        assertEquals(GiveBackOutcome.NOT_READY, run().outcome)
        assertTrue(port.writes.isEmpty())
    }

    @Test fun `low or unknown battery stops before any unhide`() {
        assertEquals(GiveBackOutcome.LOW_BATTERY,
            GiveBackController(store, port).run("off", BatteryState(4, false)).outcome)
        assertTrue(port.writes.isEmpty())
    }

    @Test fun `changed final readback and missing successors retain ownership`() {
        port.home = false
        assertEquals(GiveBackOutcome.FAILED, run().outcome)
        assertTrue(port.owner)
        port.home = true; port.dialer = false
        assertEquals(GiveBackOutcome.FAILED, run(true).outcome)
        assertTrue(port.owner)
        port.dialer = true
        assertEquals(GiveBackOutcome.OFF, run(true).outcome)
    }

    @Test fun `ownership lost after clear crash completes on restart without privileged writes`() {
        port.crashOnRemove = true
        assertFailsWith<SimulatedCrash> { run() }
        assertFalse(port.owner)
        val before = port.writes.size
        port.crashOnRemove = false
        assertEquals(GiveBackOutcome.OFF, run().outcome)
        assertEquals(before, port.writes.size)
    }

    private fun reset() {
        store.state = WaitingState(1, op); store.saves = 0; store.crashFired = false
        port.owner = true; port.writes.clear(); port.reads.clear(); port.events.clear(); port.faultFired = false
    }
    private class SimulatedCrash : Error()
    private inner class Store(var state: WaitingState?) : WaitingChangeStore {
        var saves = 0; var failAt: Int? = null; var crashAfter: Int? = null; var crashFired = false; var failSave = false
        override fun read() = state
        override fun compareAndSet(expected: WaitingState, replacement: WaitingState): Boolean {
            if (failSave || saves + 1 == failAt || state !== expected) return false
            state = replacement; saves++
            if (saves == crashAfter) { crashFired = true; throw SimulatedCrash() }
            return true
        }
    }
    private inner class Port : GiveBackPort {
        var owner = true; var fail: GiveBackAction? = null; var throwAt: GiveBackAction? = null; var failAll = false; var faultFired = false
        var home = true; var dialer = true; var crashOnRemove = false
        val writes = mutableListOf<GiveBackAction>(); val reads = mutableListOf<GiveBackAction>()
        val events = mutableListOf<String>()
        override fun isDeviceOwner() = owner
        override fun inventory() = actions
        override fun restore(action: GiveBackAction) {
            assertNotNull(store.state!!.giveBack)
            assertTrue(store.state!!.giveBack!!.entries.any { it.action == action })
            val entries = store.state!!.giveBack!!.entries
            val index = entries.indexOfFirst { it.action == action }
            assertTrue(entries.take(index).all { it.status != GiveBackStatus.PENDING })
            writes += action
            if (throwAt == action) { faultFired = true; throw IllegalStateException("injected DPM write failure") }
        }
        override fun readBack(action: GiveBackAction): Boolean {
            reads += action
            if (failAll || action == fail) { faultFired = true; return false }
            return true
        }
        override fun anotherHomeResolves() = home
        override fun systemDialerResolves() = dialer
        override fun removeDeviceOwner() { events += "remove"; owner = false; if (crashOnRemove) throw SimulatedCrash() }
    }
}
