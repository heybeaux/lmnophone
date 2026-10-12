// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.user

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.plainphone.ui.R
import org.plainphone.ui.components.TaskScreen
import org.plainphone.ui.components.trustedPeopleText
import org.plainphone.ui.components.withoutBidiControls
import org.plainphone.ui.theme.PlainphoneType

/** Display data only. The host supplies the installed version and preview identities. */
data class AboutUiState(val versionName: String, val trustedPeople: List<String>, val helper: String, val productName: String,
    val isDeviceSecure: Boolean = false)

sealed interface AboutEvent {
    data object Ok : AboutEvent
}

/** U8. No helper phone number or verified SOS capability exists in this preview. */
@Composable
fun AboutScreen(state: AboutUiState, onEvent: (AboutEvent) -> Unit) {
    val product = state.productName
    val everyone = trustedPeopleText(state.trustedPeople, everyone = true)
    TaskScreen(stringResource(R.string.more_about), stringResource(R.string.user_ok),
        { onEvent(AboutEvent.Ok) }) {
        Text(stringResource(R.string.about_test_build, state.versionName), style = PlainphoneType.Body)
        Text(stringResource(R.string.about_intro, product), style = PlainphoneType.Body)
        if (everyone != null) {
            if (state.trustedPeople.size > 1) {
                Text(stringResource(R.string.about_equals, everyone), style = PlainphoneType.Body)
            }
            Text(stringResource(if (state.trustedPeople.size > 1) R.string.about_choices_multiple else R.string.about_choices, everyone), style = PlainphoneType.Body)
            Text(stringResource(R.string.about_privacy, product, everyone, state.helper.withoutBidiControls()),
                style = PlainphoneType.Body)
            Text(stringResource(if (state.trustedPeople.size > 1) R.string.about_texts_multiple else R.string.about_texts, product, everyone), style = PlainphoneType.Body)
            Text(stringResource(R.string.about_history), style = PlainphoneType.Body)
        }
        Text(stringResource(R.string.about_emergency_title), style = PlainphoneType.Row,
            modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.about_emergency_phone), style = PlainphoneType.Body)
        Text(stringResource(R.string.about_emergency_more), style = PlainphoneType.Body)
        if (state.isDeviceSecure) {
            Text(stringResource(R.string.about_emergency_lock), style = PlainphoneType.Body)
        }
    }
}
