// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*

data class ClockRecord(val proven: RunningTime, val checkpoint: BootTime)

/** Lane D stores this tuple atomically with a checksum in device-protected storage. */
interface ClockStateStore {
    /** Missing, damaged, or invalid tuples return null. */
    fun read(): ClockRecord?
    /** Compare the durable record/revision. False grants no time authority and is not acknowledged; the next reading reads again. */
    fun compareAndSet(expected: ClockRecord?, replacement: ClockRecord): Boolean
}

/** Call at least every five minutes and before every operation transition or quota debit. */
class ProvenRunningClock(
    private val store: ClockStateStore,
    private val bootTime: () -> BootTime?,
    /** Epoch identifiers must never be reused, including after an interrupted repair. */
    private val newEpoch: () -> String,
) : RunningClock {
    @Synchronized
    override fun checkpoint(): ClockReading? {
        val now = bootTime() ?: return null
        val before = store.read()
        val next = advance(before, now) ?: return null
        if (!store.compareAndSet(before, next)) return null
        return ClockReading(next.proven, next.checkpoint)
    }

    private fun advance(before: ClockRecord?, now: BootTime): ClockRecord? {
        if (before == null || (before.checkpoint.bootId == now.bootId && now.elapsedMillis < before.checkpoint.elapsedMillis)) {
            return repair(before, now)
        }
        // On a different boot, credit only time since that boot started; never the uncertain gap.
        val delta = if (before.checkpoint.bootId == now.bootId) now.elapsedMillis - before.checkpoint.elapsedMillis else now.elapsedMillis
        val total = try { Math.addExact(before.proven.totalMillis, delta) } catch (_: ArithmeticException) { return repair(before, now) }
        return ClockRecord(RunningTime(before.proven.epoch, total), now)
    }

    private fun repair(before: ClockRecord?, now: BootTime): ClockRecord? {
        val epoch = newEpoch()
        if (epoch.isBlank() || epoch == before?.proven?.epoch) return null
        // Anchor at *now*, not the beginning of a potentially 30-hour boot.
        return ClockRecord(RunningTime(epoch, 0), now)
    }
}
