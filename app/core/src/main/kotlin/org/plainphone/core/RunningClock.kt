// SPDX-License-Identifier: Apache-2.0
package org.plainphone.core

/** Proven running time. Wall time is never an authority input. */
data class RunningTime(val epoch: String, val totalMillis: Long) {
    init { require(epoch.isNotBlank() && totalMillis >= 0) }

    /** An older epoch is conservatively re-anchored at current epoch zero. */
    fun since(start: RunningTime): Long =
        if (epoch == start.epoch) (totalMillis - start.totalMillis).coerceAtLeast(0) else totalMillis
}

data class BootTime(val bootId: String, val elapsedMillis: Long) {
    init { require(bootId.isNotBlank() && elapsedMillis >= 0) }
}

data class ClockReading(val proven: RunningTime, val boot: BootTime)

/** Lane D serializes and durably checkpoints this before every state transition and debit. */
fun interface RunningClock {
    /** Null means the reading could not be proven/persisted: no time-based authority may open. */
    fun checkpoint(): ClockReading?
}
