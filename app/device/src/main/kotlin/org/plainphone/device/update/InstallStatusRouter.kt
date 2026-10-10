// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.update

import android.content.Intent
import android.content.pm.PackageInstaller

/**
 * Forwards PackageInstaller status broadcasts to the protocol's result routing (`onResult`), only
 * for terminal statuses: success, or one of the documented failures. `STATUS_PENDING_USER_ACTION`
 * is not an outcome and is never forwarded (and never mapped to failure); neither is an unknown code
 * or a broadcast without a session. Startup readback classifies anything not forwarded.
 * A status never authorizes or retries an install.
 */
class InstallStatusRouter(private val onResult: (sessionId: Int, success: Boolean) -> Unit) {
    fun route(intent: Intent): Boolean = route(
        intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1),
        intent.getIntExtra(PackageInstaller.EXTRA_STATUS, MISSING))

    /** True when the status was terminal and was forwarded. */
    fun route(sessionId: Int, status: Int): Boolean {
        if (sessionId < 0) return false
        val success = when (status) {
            PackageInstaller.STATUS_SUCCESS -> true
            in FAILURES -> false
            else -> return false
        }
        onResult(sessionId, success)
        return true
    }

    companion object {
        /** No status extra. */
        const val MISSING = Int.MIN_VALUE
        private val FAILURES = setOf(PackageInstaller.STATUS_FAILURE, PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_ABORTED, PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_STORAGE,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, PackageInstaller.STATUS_FAILURE_TIMEOUT)
    }
}
