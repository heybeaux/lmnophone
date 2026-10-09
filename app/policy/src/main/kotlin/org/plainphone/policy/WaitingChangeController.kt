// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

/** The 24-hour waits (turning off, trusted-people changes, mode switches) and cancel. */
interface WaitingChangeController {
    fun request(change: String)

    fun cancel(change: String)
}
