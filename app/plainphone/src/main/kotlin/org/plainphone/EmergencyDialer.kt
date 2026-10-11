// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

/** [launch] returns false when no activity can handle the action and optional URI. */
internal fun openEmergencyDialer(launch: (action: String, uri: String?) -> Boolean) {
    launch(MainActivity.EMERGENCY_DIAL, null)
}
