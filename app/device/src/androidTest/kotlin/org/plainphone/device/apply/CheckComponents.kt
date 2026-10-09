// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.apply

import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** testOnly Device Owner for [DpmPortCheck]. */
class CheckAdmin : DeviceAdminReceiver()

/** No-op component whose enabled state is flipped after each unhide (mitigation B). */
class FlushMarker : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}

/** Empty Home target for the HOME stage. */
class CheckHome : Activity()
