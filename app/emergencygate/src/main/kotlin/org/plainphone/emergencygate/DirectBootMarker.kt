// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.emergencygate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Never invoked. A Direct Boot aware component lets `am instrument` run before the first unlock (API 35). */
class DirectBootMarker : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
