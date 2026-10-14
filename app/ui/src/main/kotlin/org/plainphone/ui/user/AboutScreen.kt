// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.user

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.plainphone.ui.R
import org.plainphone.ui.components.TaskScreen
import org.plainphone.ui.components.ScreenLink
import org.plainphone.ui.theme.PlainphoneType

data class ReleaseNoteUi(val version: String, val changes: List<String>)
data class AboutUiState(val versionName: String, val versionCode: Long, val latest: ReleaseNoteUi?,
    val sincePrevious: List<ReleaseNoteUi>, val older: List<ReleaseNoteUi>,
    val isDeviceSecure: Boolean = false, val showOlder: Boolean = false)
sealed interface AboutEvent { data object Ok : AboutEvent }

@Composable
fun AboutScreen(state: AboutUiState, onEvent: (AboutEvent) -> Unit) {
    var older by rememberSaveable(state.versionCode) { mutableStateOf(state.showOlder) }
    TaskScreen(stringResource(R.string.about_product_name), stringResource(R.string.user_ok),
        { onEvent(AboutEvent.Ok) }) {
        Text(stringResource(R.string.about_version, state.versionName, state.versionCode), style = PlainphoneType.Body)
        ScreenLink(stringResource(if (older) R.string.about_latest else R.string.about_older), { older = !older })
        if (older) {
            AboutHeading(stringResource(R.string.about_older))
            state.older.forEach { ReleaseText(it) }
        } else {
            AboutHeading(stringResource(R.string.about_new))
            if (state.latest == null) Text(stringResource(R.string.about_notes_missing), style = PlainphoneType.Body)
            else state.latest.changes.forEach { Text(it, style = PlainphoneType.Body) }
            if (state.sincePrevious.isNotEmpty()) {
                AboutHeading(stringResource(R.string.about_since))
                state.sincePrevious.forEach { ReleaseText(it) }
            }
        }
        AboutHeading(stringResource(R.string.about_help))
        Text(stringResource(R.string.about_limitations), style = PlainphoneType.Body)
        Text(stringResource(R.string.about_voice_retention), style = PlainphoneType.Body)
        Text(stringResource(R.string.about_privacy_current), style = PlainphoneType.Body)
        Text(stringResource(R.string.lexend_notice), style = PlainphoneType.Small)
        AboutHeading(stringResource(R.string.about_emergency_title))
        Text(stringResource(R.string.about_emergency_phone), style = PlainphoneType.Body)
        Text(stringResource(R.string.about_emergency_more), style = PlainphoneType.Body)
        if (state.isDeviceSecure) Text(stringResource(R.string.about_emergency_lock), style = PlainphoneType.Body)
    }
}
@Composable private fun AboutHeading(text: String) = Text(text, style = PlainphoneType.Row,
    modifier = Modifier.semantics { heading() })
@Composable private fun ReleaseText(note: ReleaseNoteUi) {
    AboutHeading(stringResource(R.string.about_release, note.version))
    note.changes.forEach { Text(it, style = PlainphoneType.Body) }
}
