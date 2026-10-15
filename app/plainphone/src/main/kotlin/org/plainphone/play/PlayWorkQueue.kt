// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.play

import org.plainphone.policy.SessionEnd

/** Coalesces pending ticks and gives closes precedence over a tick not yet started.
 * All effects still run as indivisible tasks on the common policy dispatcher.
 * A running policy operation is never interrupted or bypassed by a second writer.
 */
class PlayWorkQueue(
    private val post: (() -> Unit) -> Unit,
    private val tickNow: () -> Unit,
    private val endNow: (SessionEnd) -> Unit,
) {
    private val lock = Any()
    private var posted = false
    private val ticks = mutableListOf<() -> Unit>()
    private val ends = mutableListOf<Pair<SessionEnd, () -> Unit>>()

    fun tick(done: () -> Unit) = synchronized(lock) { ticks += done; schedule() }
    fun end(reason: SessionEnd, done: () -> Unit) = synchronized(lock) { ends += reason to done; schedule() }

    private fun schedule() {
        if (posted) return
        posted = true
        post(::drain)
    }
    private fun drain() {
        val (closing, pulsing) = synchronized(lock) {
            val batch = ends.toList() to ticks.toList()
            ends.clear(); ticks.clear()
            batch
        }
        try {
            // Closing already observes and publishes the current state. No redundant tick follows it.
            if (closing.isEmpty()) tickNow()
            else for ((reason, done) in closing) try { endNow(reason) } finally { done() }
        } finally {
            try { pulsing.forEach { it() } } finally {
                synchronized(lock) {
                    posted = false
                    if (ends.isNotEmpty() || ticks.isNotEmpty()) schedule()
                }
            }
        }
    }
}
