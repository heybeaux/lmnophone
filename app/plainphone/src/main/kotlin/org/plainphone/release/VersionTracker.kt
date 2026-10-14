// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.release

data class ObservedVersions(val current: Long, val previous: Long?)
interface VersionStore {
    fun read(): ObservedVersions?
    /** Atomically save both fields and wait for the durable result. */
    fun commit(value: ObservedVersions): Boolean
}
class VersionTracker(private val store: VersionStore) {
    fun observe(current: Long): ObservedVersions {
        val old = runCatching { store.read() }.getOrNull()
        if (old?.current == current) return old
        val next = ObservedVersions(current, old?.current?.takeIf { it > 0 })
        return if (runCatching { store.commit(next) }.getOrDefault(false)) next
            else ObservedVersions(current, null)
    }
}
