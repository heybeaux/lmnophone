// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.trusted

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource as s
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import org.plainphone.ui.R
import org.plainphone.ui.theme.PlainphoneSpace

enum class HandoverCheckKind { OWNER, APPROVED_APPS, EMERGENCY, USB_DEBUGGING, WIRELESS_DEBUGGING }
enum class HandoverCheckState { OK, NEEDS_HELPER, COULD_NOT_CHECK }
data class HandoverCheckRow(val kind: HandoverCheckKind, val state: HandoverCheckState)
data class HandoverUiState(val items: List<HandoverCheckRow>, val needsHelper: Int)

/** A read-only result, with one spoken stop per item and a single return action. */
@Composable
fun HandoverScreen(state: HandoverUiState?, onDone: () -> Unit) {
    AdminFrame(null, s(R.string.handover_title), s(R.string.admin_done), onDone) {
        if (state == null) Words(s(R.string.handover_checking)) else {
            Words(if (state.needsHelper == 0) s(R.string.handover_partial_ok) else
                pluralStringResource(R.plurals.handover_not_ready, state.needsHelper, state.needsHelper))
            for (row in state.items) {
                val title = s(when (row.kind) {
                    HandoverCheckKind.OWNER -> R.string.handover_owner
                    HandoverCheckKind.APPROVED_APPS -> R.string.handover_apps
                    HandoverCheckKind.EMERGENCY -> R.string.handover_emergency
                    HandoverCheckKind.USB_DEBUGGING -> R.string.handover_usb
                    HandoverCheckKind.WIRELESS_DEBUGGING -> R.string.handover_wireless
                })
                val status = s(when (row.state) {
                    HandoverCheckState.OK -> R.string.handover_ok
                    HandoverCheckState.NEEDS_HELPER -> R.string.handover_needs_helper
                    HandoverCheckState.COULD_NOT_CHECK -> R.string.handover_unknown
                })
                val detail = when (row.state) {
                    HandoverCheckState.OK -> ""
                    HandoverCheckState.COULD_NOT_CHECK -> s(R.string.handover_unknown_help)
                    HandoverCheckState.NEEDS_HELPER -> s(when (row.kind) {
                        HandoverCheckKind.OWNER -> R.string.handover_owner_help
                        HandoverCheckKind.APPROVED_APPS -> R.string.handover_apps_help
                        HandoverCheckKind.EMERGENCY -> R.string.handover_emergency_help
                        HandoverCheckKind.USB_DEBUGGING -> R.string.handover_usb_help
                        HandoverCheckKind.WIRELESS_DEBUGGING -> R.string.handover_wireless_help
                    })
                }
                val spoken = if (row.state == HandoverCheckState.NEEDS_HELPER)
                    s(R.string.handover_spoken_needs_helper, title, detail) else s(if (detail.isEmpty()) R.string.handover_spoken_ok else R.string.handover_spoken_help,
                    title, status, detail)
                Column(Modifier.fillMaxWidth().testTag("handover-${row.kind.name}")
                    .clearAndSetSemantics { contentDescription = spoken },
                    verticalArrangement = Arrangement.spacedBy(PlainphoneSpace.S1)) {
                    Heading(title)
                    Words(status)
                    if (detail.isNotEmpty()) Words(detail)
                }
            }
        }
        Heading(s(R.string.handover_not_checked))
        listOf(R.string.handover_boot, R.string.handover_reset, R.string.handover_browser,
            R.string.handover_updates, R.string.handover_sweep, R.string.handover_text).forEach { Words(s(it)) }
    }
}
