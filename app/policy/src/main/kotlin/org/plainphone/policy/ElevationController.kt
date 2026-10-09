// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.WindowKind

/** Self-managed mode: the owner opens a time-limited window for themselves. */
interface ElevationController {
    fun request(kind: WindowKind)

    fun end()
}
