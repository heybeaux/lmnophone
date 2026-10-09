/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.plainphone.ui.theme.PlainphoneColors

@Composable
internal fun HomeTestFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(PlainphoneColors.Paper)
        .padding(top = 32.dp, bottom = 48.dp)) { content() }
}
