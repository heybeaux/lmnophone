// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

/** Retains actions/names and receipt-time coverage; events during a pass queue the next one. */
internal class PackageEventBurst(
    private val post: (() -> Unit) -> Unit,
    private val run: (String, Boolean) -> Unit,
    private val hasSnapshot: () -> Boolean = { true },
) {
    private val pending = mutableSetOf<String>()
    private var maySkip = true
    fun add(reason: String) {
        // Take this before locking/queuing: a snapshot completed later cannot cover this event.
        val capturedAtReceipt = hasSnapshot()
        val enqueue = synchronized(pending) {
            val empty = pending.isEmpty()
            pending += reason
            maySkip = maySkip && capturedAtReceipt
            empty
        }
        if (enqueue) post {
            val (reasonToRun, eligible) = synchronized(pending) {
                val next = pending.singleOrNull() ?: "package-burst"
                val eligible = maySkip
                pending.clear()
                maySkip = true
                next to eligible
            }
            run(reasonToRun, eligible)
        }
    }
}
