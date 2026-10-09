// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.storage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Never invoked. Its Direct Boot aware declaration lets the check start before the first unlock. */
class DirectBootMarker : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {}
}
