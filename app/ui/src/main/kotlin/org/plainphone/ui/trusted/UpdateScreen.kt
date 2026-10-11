/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.trusted

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource as s
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.plainphone.ui.R
import org.plainphone.ui.theme.*
import org.plainphone.ui.components.withoutBidiControls

@Composable
fun UpdateScreen(state: UpdateUiState, onEvent: (UpdateEvent) -> Unit) {
    val helper = state.helper.withoutBidiControls()
    val title = when (state.stage) {
        UpdateStage.Intro -> s(R.string.admin_update)
        UpdateStage.Address -> s(R.string.update_address_title, helper)
        UpdateStage.Getting -> s(R.string.update_getting)
        UpdateStage.Unreachable -> s(R.string.update_unreachable, helper)
        UpdateStage.TooBig -> s(R.string.update_too_big)
        UpdateStage.Invalid -> s(R.string.update_invalid, helper)
        UpdateStage.NotNewer -> s(R.string.update_not_newer)
        UpdateStage.Confirm -> s(R.string.update_confirm, state.version)
        UpdateStage.Installing -> s(R.string.update_installing)
        UpdateStage.Done -> s(R.string.update_done)
    }
    val primary = when (state.stage) {
        UpdateStage.Intro -> s(R.string.admin_next)
        UpdateStage.Address -> s(R.string.update_get)
        UpdateStage.Getting, UpdateStage.Installing -> null
        UpdateStage.Confirm -> s(R.string.update_install)
        UpdateStage.Done -> s(R.string.admin_done)
        else -> s(R.string.user_ok)
    }
    AdminFrame(state.context, title, primary, { onEvent(UpdateEvent.Continue) },
        enabled = !state.sending && (state.stage != UpdateStage.Address || state.canContinue)) {
        when (state.stage) {
            UpdateStage.Intro -> Words(s(R.string.update_intro, helper))
            UpdateStage.Address -> {
                Words(s(R.string.update_address_body, helper))
                val label = s(R.string.update_address)
                Text(label, style = PlainphoneType.Small)
                BasicTextField(state.address, { onEvent(UpdateEvent.AddressChanged(it)) }, enabled = !state.sending,
                    singleLine = true, textStyle = PlainphoneType.Body,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }
                        .border(PlainphoneSpace.Border, PlainphoneColors.Ink3, RoundedCornerShape(PlainphoneSpace.BoxRadius)).padding(PlainphoneSpace.S4))
            }
            UpdateStage.Getting -> { Words(s(R.string.update_getting_body)); CircularProgressIndicator(color = PlainphoneColors.Ink, trackColor = PlainphoneColors.Fill2) }
            UpdateStage.Confirm -> Words(s(R.string.update_confirm_body))
            else -> Unit
        }
    }
}
