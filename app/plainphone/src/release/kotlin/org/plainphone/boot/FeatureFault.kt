// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import android.content.Context

/** Release: there is no feature fault switch. */
object FeatureFault {
    @Suppress("UNUSED_PARAMETER")
    fun on(context: Context): Boolean = false
}
