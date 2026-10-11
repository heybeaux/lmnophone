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
    // Read and written only on the same dispatcher that runs every policy pass.
    private var lastPassLocked: Boolean? = null

    fun recordPass(locked: Boolean) {
        check(dispatcher.onDispatcher)
        lastPassLocked = locked
    }

    fun request(done: () -> Unit = {}) = dispatcher.post {
        try {
            // Check here, not on the receiver/activity thread: an earlier queued boot/start pass
            // may already have done the full work. A failed full pass keeps its usual retry paths;
            // it must not create a resume -> reapply -> resume loop.
            if (lastPassLocked != false && isUserUnlocked()) reapply()
        } finally {
            done()
        }
    }
}
