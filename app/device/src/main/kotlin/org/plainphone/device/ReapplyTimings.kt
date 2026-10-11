// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

/** Per-pass elapsed milliseconds, used only on the serialized policy dispatcher.
 * Labels are fixed code names, never package names. Nested intervals are inclusive;
 * repeated intervals (such as emergency repair) accumulate. Failed work is timed too.
 */
class ReapplyTimings(private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val elapsed = linkedMapOf<String, Long>()

    fun reset() = elapsed.clear()

    fun <T> measure(label: String, work: () -> T): T {
        val start = elapsedMillis()
        try {
            return work()
        } finally {
            elapsed[label] = (elapsed[label] ?: 0) + (elapsedMillis() - start)
        }
    }

    fun summary(): String = elapsed.entries.joinToString(",") { "${it.key}=${it.value}" }
}
