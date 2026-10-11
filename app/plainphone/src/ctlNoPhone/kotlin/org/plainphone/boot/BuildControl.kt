// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import android.content.Intent

/** Negative control 1, never shipped: Home's Phone resolves to nothing (the gate's "broken click handler"). */
object BuildControl {
    val control: NegativeControl = object : NegativeControl {
        override val name = "no-phone"

        override fun phoneIntent(intent: Intent): Intent = intent.setPackage("org.plainphone.control.nothing")
    }
}
