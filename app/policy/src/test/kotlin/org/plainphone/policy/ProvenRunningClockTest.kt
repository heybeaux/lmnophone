// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class ProvenRunningClockTest {
    private val hour = 3_600_000L
    private var boot: BootTime? = BootTime("boot", 0)
    private var epochs = 0
    private val store = MemoryClockStore()
    private fun clock() = ProvenRunningClock(store, { boot }, { "epoch-${++epochs}" })

    @Test fun `repeated process restarts never count an interval twice`() {
        assertEquals(0, clock().checkpoint()!!.proven.totalMillis)
        repeat(10) { n ->
            boot = BootTime("boot", (n + 1) * 1000L)
            assertEquals((n + 1) * 1000L, clock().checkpoint()!!.proven.totalMillis)
            assertEquals((n + 1) * 1000L, clock().checkpoint()!!.proven.totalMillis)
        }
    }

    @Test fun `off time and lost checkpoint gap are never credited`() {
        val start = clock().checkpoint()!!.proven
        boot = BootTime("boot", 10 * hour); clock().checkpoint()
        // Reboot after an uncheckpointed interval and arbitrary time switched off.
        boot = BootTime("new-boot", 2 * hour)
        val restarted = clock().checkpoint()!!
        assertEquals(12 * hour, restarted.proven.since(start))
        boot = BootTime("new-boot", 14 * hour)
        assertEquals(24 * hour, clock().checkpoint()!!.proven.since(start))
    }

    @Test fun `damaged counter in long boot anchors now and old records need fresh day`() {
        val old = clock().checkpoint()!!.proven
        boot = BootTime("boot", 30 * hour); store.record = null
        val repaired = clock().checkpoint()!!
        assertEquals(0, repaired.proven.totalMillis)
        assertNotEquals(old.epoch, repaired.proven.epoch)
        assertEquals(0, repaired.proven.since(old))
        boot = BootTime("boot", 54 * hour)
        assertEquals(24 * hour, clock().checkpoint()!!.proven.since(old))
    }

    @Test fun `interrupted repair does not credit earlier boot or reuse epoch`() {
        val old = clock().checkpoint()!!.proven
        boot = BootTime("boot", 30 * hour); store.record = null
        val repaired = clock().checkpoint()!!
        assertEquals(repaired, clock().checkpoint())
        store.record = null
        val twice = clock().checkpoint()!!
        assertNotEquals(twice.proven.epoch, repaired.proven.epoch)
        assertEquals(0, twice.proven.since(old))
        assertEquals(0, twice.proven.since(repaired.proven))
    }

    @Test fun `failed repair write closes authority until durable checkpoint`() {
        boot = BootTime("boot", 30 * hour); store.fail = true
        assertNull(clock().checkpoint()); assertNull(store.record)
        store.fail = false
        assertEquals(0, clock().checkpoint()!!.proven.totalMillis)
    }

    @Test fun `same boot backwards elapsed repairs conservatively`() {
        clock().checkpoint(); boot = BootTime("boot", hour); val previous = clock().checkpoint()!!
        boot = BootTime("boot", 50)
        val repaired = clock().checkpoint()!!
        assertEquals(0, repaired.proven.totalMillis)
        assertNotEquals(previous.proven.epoch, repaired.proven.epoch)
    }

    @Test fun `unreadable boot id closes authority and does not fabricate a boot`() {
        clock().checkpoint(); val old = store.record
        boot = null
        assertNull(clock().checkpoint()); assertSame(old, store.record)
    }

    @Test fun `failed ordinary checkpoint cannot double count retry`() {
        clock().checkpoint(); boot = BootTime("boot", hour); store.fail = true
        assertNull(clock().checkpoint())
        store.fail = false
        assertEquals(hour, clock().checkpoint()!!.proven.totalMillis)
        assertEquals(hour, clock().checkpoint()!!.proven.totalMillis)
    }

    @Test fun `overflow repairs instead of wrapping or prematurely expiring waits`() {
        store.record = ClockRecord(RunningTime("old", Long.MAX_VALUE - 5), BootTime("boot", 0))
        boot = BootTime("boot", 10)
        assertEquals(0, clock().checkpoint()!!.proven.totalMillis)
    }

    private class MemoryClockStore : ClockStateStore {
        var record: ClockRecord? = null
        var fail = false
        override fun read(): ClockRecord? = record
        override fun compareAndSet(expected: ClockRecord?, replacement: ClockRecord): Boolean {
            if (fail || record !== expected) return false
            record = replacement; return true
        }
    }
}
