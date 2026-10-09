// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.plainphone.R
import org.plainphone.ui.theme.PlainphoneColors
import org.plainphone.ui.theme.PlainphoneSpace
import org.plainphone.ui.theme.PlainphoneType

/** Test builds: shown for any action whose feature isn't wired yet. Nothing was changed or sent. */
@Composable
fun NotInBuildScreen(onOk: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PlainphoneSpace.S6),
        verticalArrangement = Arrangement.spacedBy(PlainphoneSpace.S4),
    ) {
        BasicText(stringResource(R.string.not_in_build_title), style = PlainphoneType.Title)
        BasicText(stringResource(R.string.not_in_build_body), style = PlainphoneType.Body)
        Box(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .background(PlainphoneColors.Ink, RoundedCornerShape(PlainphoneSpace.BoxRadius))
                .clickable(role = Role.Button, onClick = onOk).padding(PlainphoneSpace.S4),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(stringResource(R.string.not_in_build_ok), style = PlainphoneType.Button.copy(color = PlainphoneColors.Paper, textAlign = TextAlign.Center))
        }
    }
}
