// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

/** Only our two optional Home count permissions. No revocation: that would kill Home. */
class CommunicationCountPermissions(private val context: Context, private val admin: ComponentName) {
    fun grant() {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        for (permission in listOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_SMS)) {
            try {
                // A true return is not availability. The host checks permission AND a successful query.
                dpm.setPermissionGrantState(admin, context.packageName, permission,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            } catch (_: Exception) { /* The host's readback reports this source unavailable. */ }
        }
    }
}
