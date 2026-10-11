// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.content.Intent

/** [launch] returns false when no activity can handle the action and optional URI. */
internal fun openEmergencyDialer(launch: (action: String, uri: String?) -> Boolean) {
    if (!launch(MainActivity.EMERGENCY_DIAL, null)) {
        // Open the keypad with the number filled in; never place a call.
        launch(Intent.ACTION_DIAL, "tel:911")
    }
}
