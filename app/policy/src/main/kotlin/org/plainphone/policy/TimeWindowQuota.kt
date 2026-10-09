// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.WindowKind

/** Generic "open for a few minutes" quota. Web window and Wi-Fi sign-in stay behind an off flag. */
interface TimeWindowQuota {
    fun canOpen(kind: WindowKind): Boolean

    fun open(kind: WindowKind)
}
