// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

/**
 * device-lockdown, "Lock task must actually be running" (PLA-307 finding 2). A re-apply that reads
 * back does not by itself put the phone in lock task: if Plainphone's process died and an unapproved
 * app reached the front before it restarted, Home is not resumed and nothing calls `startLockTask()`.
 * So after every unlocked pass that reports ready, this reads the live state and, when lock task is
 * not running, brings Home forward. Home then enters lock task itself, through the KEYGUARD gate
 * ([PolicyRuntime.mayEnterLockTask]). A Device Owner may start an activity from the background.
 *
 * A locked pass never opens Home (the full plan has not run), and a pass that is not ready leaves Home
 * on its attention screen. A failed read counts as not running; a failed start never throws.
 */
class LockTaskRecovery(
    private val lockTaskRunning: () -> Boolean,
    private val bringHomeForward: () -> Unit,
) {
    /** True when Home was brought forward. */
    fun afterPass(locked: Boolean, ready: Boolean): Boolean {
        if (locked || !ready) return false
        val running = try {
            lockTaskRunning()
        } catch (e: RuntimeException) {
            false
        }
        if (running) return false
        return try {
            bringHomeForward()
            true
        } catch (e: RuntimeException) {
            false
        }
    }
}
