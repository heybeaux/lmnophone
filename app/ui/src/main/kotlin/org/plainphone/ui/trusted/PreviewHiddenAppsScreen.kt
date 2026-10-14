// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.trusted

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource as s
import org.plainphone.ui.R
import org.plainphone.ui.components.withoutBidiControls

data class HiddenAppRow(val id: String, val name: String, val icon: ImageBitmap? = null)

@Composable
fun PreviewHiddenAppsScreen(context: AdminContext, apps: List<HiddenAppRow>, unavailable: Boolean,
    busy: Boolean, onOpen: (String) -> Unit, onBack: () -> Unit) {
    AdminFrame(context, s(R.string.admin_hidden_app), secondary = s(R.string.admin_back), onSecondary = onBack) {
        if (unavailable) StatusWords(s(R.string.app_hidden_unavailable))
        else if (!busy && apps.isEmpty()) Words(s(R.string.app_hidden_empty))
        else apps.forEach { app ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp),
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })) }
                Box(Modifier.weight(1f)) { ActionRow(app.name.withoutBidiControls(), enabled = !busy) { onOpen(app.id) } }
            }
        }
    }
}
