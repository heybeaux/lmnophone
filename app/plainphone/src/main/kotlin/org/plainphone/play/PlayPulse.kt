// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.play

import org.plainphone.device.session.PlaySessionView

/** Main-thread timer owner. Its completion callback must also return to the main thread. */
class PlayPulse(
    private val delay: (Runnable) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val tick: (() -> Unit) -> Unit,
) {
    private var needed = false
    private var queued = false
    private var running = false
    private val pulse = Runnable { fire() }
    fun update(view: PlaySessionView) {
        // An outstanding grant with failed readback still needs its restrictive retry.
        needed = view.id != null || view.countdownMillis != null || view.readyMillis != null ||
            view.active || view.approvalAvailable
        if (!needed && queued) { cancel(pulse); queued = false }
        schedule()
    }
    private fun schedule() {
        if (needed && !queued && !running) { queued = true; delay(pulse) }
    }
    private fun fire() {
        queued = false
        if (!needed || running) return
        running = true
        tick { running = false; schedule() }
    }
}
