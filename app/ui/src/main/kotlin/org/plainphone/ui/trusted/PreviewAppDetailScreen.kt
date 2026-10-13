// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.trusted

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource as s
import org.plainphone.ui.R
import org.plainphone.ui.components.withoutBidiControls

/** Authorized T5 preview subset. Omitted permissions and dates are not placeholder promises. */
@Composable
fun PreviewAppDetailScreen(context: AdminContext, app: String, version: String, onRemove: () -> Unit) {
    AdminFrame(context, app.withoutBidiControls(), s(R.string.app_detail_remove, context.userName.withoutBidiControls()), onRemove) {
        Words(s(R.string.app_preview_version, version.withoutBidiControls()))
    }
}

@Composable
fun PreviewAppsFailureScreen(context: AdminContext, onBack: () -> Unit) {
    AdminFrame(context, s(R.string.admin_apps), s(R.string.admin_back), onBack) {
        Words(s(R.string.family_error))
    }
}
