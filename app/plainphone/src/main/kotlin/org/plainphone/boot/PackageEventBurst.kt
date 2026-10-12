// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

/** Retains all actions/names until dispatch; broadcasts arriving during a pass queue the next one. */
internal class PackageEventBurst(private val post: (() -> Unit) -> Unit, private val run: (String) -> Unit) {
    private val pending = mutableSetOf<String>()
    fun add(reason: String) {
        val enqueue = synchronized(pending) {
            val empty = pending.isEmpty()
            pending += reason
            empty
        }
        if (enqueue) post {
            val reasonToRun = synchronized(pending) {
                val next = pending.singleOrNull() ?: "package-burst"
                pending.clear()
                next
            }
            run(reasonToRun)
        }
    }
}
