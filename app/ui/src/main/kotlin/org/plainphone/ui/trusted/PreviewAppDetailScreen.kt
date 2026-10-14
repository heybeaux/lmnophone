// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.trusted

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource as s
import org.plainphone.ui.R
import org.plainphone.ui.components.withoutBidiControls

enum class PermissionStatus { Allowed, Denied, Default, Unknown, Mixed }
data class AppPermissionRow(val id: String, val label: String, val status: PermissionStatus)
data class AppPermissionsUiState(val rows: List<AppPermissionRow> = emptyList(), val checkFailed: Boolean = false)

/** Display only: no permission edit action in self-managed mode. */
@Composable
fun PreviewAppDetailScreen(context: AdminContext, app: String, version: String, onBack: () -> Unit = {}, permissions: AppPermissionsUiState? = null, canChooseOnAdd: Boolean = false, onRemove: () -> Unit) {
    AdminFrame(context, app.withoutBidiControls(), s(R.string.app_detail_remove, context.userName.withoutBidiControls()), onRemove,
        secondary = s(R.string.admin_back), onSecondary = onBack) {
        Words(s(R.string.app_preview_version, version.withoutBidiControls()))
        permissions?.let { AppPermissionLines(app, it) }
        if (canChooseOnAdd) Words(s(R.string.app_self_permissions_later, app.withoutBidiControls()))
    }
}

@Composable
fun PreviewAppsFailureScreen(context: AdminContext, onBack: () -> Unit) {
    AdminFrame(context, s(R.string.admin_apps), s(R.string.admin_back), onBack) {
        Words(s(R.string.family_error))
    }
}

@Composable
fun AppPermissionLines(app: String, state: AppPermissionsUiState) {
    Heading(s(R.string.app_detail_permissions, app.withoutBidiControls()))
    if (state.checkFailed) Words(s(R.string.app_permissions_unknown))
    else if (state.rows.isEmpty()) Words(s(R.string.app_permissions_none, app.withoutBidiControls()))
    else state.rows.forEach { row ->
        val label = permissionLabel(row.id, row.label)
        val status = s(when (row.status) {
            PermissionStatus.Allowed -> R.string.app_permissions_allowed
            PermissionStatus.Denied -> R.string.app_permissions_denied
            PermissionStatus.Default -> R.string.app_permissions_default
            PermissionStatus.Unknown -> R.string.app_permissions_unknown
            PermissionStatus.Mixed -> R.string.app_permissions_mixed
        })
        Words(s(R.string.app_permissions_line, label, status))
    }
}

@Composable
fun permissionLabel(id: String, fallback: String): String = when (id) {
    "android.permission-group.CAMERA" -> s(R.string.permission_camera)
    "android.permission-group.MICROPHONE" -> s(R.string.permission_microphone)
    "android.permission-group.LOCATION" -> s(R.string.permission_location)
    "android.permission-group.CONTACTS" -> s(R.string.permission_contacts)
    "android.permission-group.PHONE" -> s(R.string.permission_phone)
    "android.permission-group.CALL_LOG" -> s(R.string.permission_callhistory)
    "android.permission-group.SMS" -> s(R.string.permission_sms)
    "android.permission-group.CALENDAR" -> s(R.string.permission_calendar)
    "android.permission-group.NEARBY_DEVICES" -> s(R.string.permission_nearby)
    "android.permission-group.NOTIFICATIONS" -> s(R.string.permission_notifications)
    else -> fallback.withoutBidiControls()
}

@Composable
fun AppPermissionFailureScreen(context: AdminContext, app: String, groups: List<AppPermissionRow>,
    readback: AppPermissionsUiState, retry: () -> Unit) {
    AdminFrame(context, s(R.string.app_permissions_failed_title, app.withoutBidiControls()), s(R.string.app_permissions_retry), retry) {
        if (groups.isEmpty()) Words(s(R.string.app_permissions_failed))
        groups.forEach { Words(s(R.string.app_permissions_failed_group, permissionLabel(it.id, it.label))) }
        AppPermissionLines(app, readback)
    }
}
