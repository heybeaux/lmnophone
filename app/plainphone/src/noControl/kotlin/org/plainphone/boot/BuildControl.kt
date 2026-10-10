// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

/** Debug and release builds: no negative control. */
object BuildControl {
    val control: NegativeControl = NegativeControl.None
}
