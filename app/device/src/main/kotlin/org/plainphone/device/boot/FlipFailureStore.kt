// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.boot

import org.plainphone.core.PackageName

/**
 * Unresolved mitigation-B flip failures (PLA-241 finding 4).
 * @property names packages whose flip failed. One stays here across the locked and unlocked passes,
 *   across emergency repair and across a process restart, until a later flip for it reads back or the
 *   trusted person acknowledges it. A later pass with no new unhide clears nothing.
 * @property unknown the saved record exists but could not be read (PLA-264 finding 3), so a failure may
 *   be missing from [names]. Only an acknowledgement clears it; a later flip proves nothing about it.
 */
data class FlipFailures(val names: Set<PackageName> = emptySet(), val unknown: Boolean = false) {
    val needsAttention: Boolean get() = unknown || names.isNotEmpty()

    companion object {
        val NONE = FlipFailures()
        val UNKNOWN = FlipFailures(unknown = true)
    }
}

interface FlipFailureStore {
    /**
     * [FlipFailures.NONE] only when nothing was ever saved or the last save was a deliberate empty one.
     * A record that exists but cannot be read is [FlipFailures.unknown], never an older copy. The
     * caller also treats a throw as unknown.
     */
    fun read(): FlipFailures
    /** True when durably saved. False is not acknowledged; the caller keeps its in-memory copy and retries. */
    fun save(state: FlipFailures): Boolean
}

/** For tests and for a build without a device-protected directory: lives as long as the process. */
class InMemoryFlipFailureStore : FlipFailureStore {
    @Volatile private var state = FlipFailures.NONE
    override fun read() = state
    override fun save(state: FlipFailures): Boolean { this.state = state; return true }
}
