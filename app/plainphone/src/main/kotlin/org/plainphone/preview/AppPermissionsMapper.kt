// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.preview

import org.plainphone.device.approval.AppPermissionRead
import org.plainphone.policy.PermissionReadback
import org.plainphone.ui.trusted.*

fun appPermissionsUi(read: AppPermissionRead?): AppPermissionsUiState {
    if (read == null) return AppPermissionsUiState(checkFailed = true)
    return AppPermissionsUiState(read.snapshot.groups.map { group ->
        val states = group.permissions.map { read.states[it] ?: PermissionReadback.UNKNOWN }.toSet()
        val status = when {
            states.isEmpty() || PermissionReadback.UNKNOWN in states -> PermissionStatus.Unknown
            states.size > 1 -> PermissionStatus.Mixed
            else -> when (states.single()) {
                PermissionReadback.GRANTED -> PermissionStatus.Allowed
                PermissionReadback.DENIED -> PermissionStatus.Denied
                PermissionReadback.DEFAULT -> PermissionStatus.Default
                PermissionReadback.UNKNOWN -> PermissionStatus.Unknown
            }
        }
        AppPermissionRow(group.id, group.label, status)
    })
}
