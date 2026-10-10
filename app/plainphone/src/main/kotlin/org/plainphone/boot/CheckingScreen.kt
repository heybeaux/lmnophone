// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.plainphone.R
import org.plainphone.ui.theme.PlainphoneColors
import org.plainphone.ui.theme.PlainphoneSpace
import org.plainphone.ui.theme.PlainphoneType

/** The neutral state before the re-apply reads back: no apps, only Phone and Emergency. */
@Composable
fun CheckingScreen(onPhone: () -> Unit, onEmergency: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(PlainphoneColors.Paper).safeDrawingPadding().padding(PlainphoneSpace.S6),
        verticalArrangement = Arrangement.spacedBy(PlainphoneSpace.S4),
    ) {
        BasicText(stringResource(R.string.checking_title, stringResource(R.string.app_name)), style = PlainphoneType.Title)
        BasicText(stringResource(R.string.checking_body), style = PlainphoneType.Body, modifier = Modifier.weight(1f))
        Action(stringResource(R.string.checking_phone), onPhone)
        Action(stringResource(R.string.checking_emergency), onEmergency)
    }
}

@Composable
private fun Action(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        Modifier.fillMaxWidth().background(PlainphoneColors.Ink, shape)
            .clickable(role = Role.Button, onClick = onClick).heightIn(min = 56.dp).padding(PlainphoneSpace.S4),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = PlainphoneType.Button.copy(color = PlainphoneColors.Paper))
    }
}
