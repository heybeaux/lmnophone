// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.core

/** Running time that wall-clock changes can't move. Windows and waits are measured on it. */
fun interface RunningClock {
    fun elapsedMillis(): Long
}
