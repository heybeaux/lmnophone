// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import org.plainphone.device.boot.RestrictionFilter

/** Release build: every restriction the engine asks for. */
object BuildRestrictions {
    val filter = RestrictionFilter.NONE
}
