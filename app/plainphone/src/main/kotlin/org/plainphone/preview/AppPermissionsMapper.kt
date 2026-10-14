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

fun appPermissionChoicesUi(state: PreviewAppsState, context: AdminContext): PermissionsUiState {
    val read = state.permissions
    val choices = read?.snapshot?.groups.orEmpty().map { group ->
        org.plainphone.ui.trusted.PermissionChoice(group.id, permissionCategory(group.id),
            decision = state.choices[group.id]?.let { if (it == org.plainphone.policy.PermissionChoice.ALLOW) PermissionDecision.Allow else PermissionDecision.Deny },
            unavailable = group.id in state.unavailable, label = group.label)
    }
    return PermissionsUiState(context, state.selectedHidden?.name.orEmpty(), choices,
        canContinue = read != null && choices.all { it.decision != null || it.unavailable },
        sending = state.busy, checkFailed = read == null, previewAdd = true)
}
private fun permissionCategory(id: String): PermissionGroup? = when (id) {
    "android.permission-group.CAMERA" -> PermissionGroup.Camera
    "android.permission-group.MICROPHONE" -> PermissionGroup.Microphone
    "android.permission-group.LOCATION" -> PermissionGroup.Location
    "android.permission-group.CONTACTS" -> PermissionGroup.Contacts
    "android.permission-group.PHONE" -> PermissionGroup.Phone
    "android.permission-group.CALL_LOG" -> PermissionGroup.CallHistory
    "android.permission-group.SMS" -> PermissionGroup.Sms
    "android.permission-group.CALENDAR" -> PermissionGroup.Calendar
    "android.permission-group.NEARBY_DEVICES" -> PermissionGroup.Nearby
    "android.permission-group.STORAGE", "android.permission-group.READ_MEDIA_VISUAL", "android.permission-group.READ_MEDIA_AURAL" -> PermissionGroup.Media
    "android.permission-group.NOTIFICATIONS" -> PermissionGroup.Notifications
    "android.permission-group.ACTIVITY_RECOGNITION", "android.permission-group.SENSORS", "android.permission-group.HEALTH" -> PermissionGroup.Health
    else -> null
}
