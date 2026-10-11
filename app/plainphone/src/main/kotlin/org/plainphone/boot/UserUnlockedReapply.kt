// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import org.plainphone.device.SerialDispatcher

/** Runs the missing full pass after Direct Boot, on the runtime's existing dispatcher. */
internal class UserUnlockedReapply(
    private val dispatcher: SerialDispatcher,
    private val isUserUnlocked: () -> Boolean,
    private val reapply: () -> Unit,
) {
    fun recordPass(locked: Boolean) = Unit

    fun request(done: () -> Unit = {}) = dispatcher.post {
        // The existing runtime has no early-unlock trigger yet.
        done()
    }
}
