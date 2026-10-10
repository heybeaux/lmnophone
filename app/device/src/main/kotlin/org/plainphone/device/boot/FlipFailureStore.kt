// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.boot

import org.plainphone.core.PackageName

/**
 * Unresolved mitigation-B flip failures (PLA-241 finding 4). A package stays here across the locked
 * and unlocked passes, across emergency repair and across a process restart, until a later flip for
 * it reads back or the trusted person acknowledges it. A later pass with no new unhide clears nothing.
 */
interface FlipFailureStore {
    fun read(): Set<PackageName>
    /** True when durably saved. False is not acknowledged; the caller keeps its in-memory copy. */
    fun save(names: Set<PackageName>): Boolean
}

/** For tests and for a build without a device-protected directory: lives as long as the process. */
class InMemoryFlipFailureStore : FlipFailureStore {
    @Volatile private var names = emptySet<PackageName>()
    override fun read() = names
    override fun save(names: Set<PackageName>): Boolean { this.names = names; return true }
}
