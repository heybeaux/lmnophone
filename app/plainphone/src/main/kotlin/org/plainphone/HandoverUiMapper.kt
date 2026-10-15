// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import org.plainphone.policy.*
import org.plainphone.ui.trusted.*

/** Production mapper; no fixture supplies a state to the installed screen. */
fun handoverUiState(report: HandoverReport) = HandoverUiState(report.items.map { (item, status) ->
    HandoverCheckRow(when (item) {
        HandoverItem.OWNER -> HandoverCheckKind.OWNER
        HandoverItem.APPROVED_APPS -> HandoverCheckKind.APPROVED_APPS
        HandoverItem.EMERGENCY -> HandoverCheckKind.EMERGENCY
        HandoverItem.USB_DEBUGGING -> HandoverCheckKind.USB_DEBUGGING
        HandoverItem.WIRELESS_DEBUGGING -> HandoverCheckKind.WIRELESS_DEBUGGING
    }, when (status) {
        HandoverState.OK -> HandoverCheckState.OK
        HandoverState.NEEDS_HELPER -> HandoverCheckState.NEEDS_HELPER
        HandoverState.COULD_NOT_CHECK -> HandoverCheckState.COULD_NOT_CHECK
    })
}, report.needsHelper)
