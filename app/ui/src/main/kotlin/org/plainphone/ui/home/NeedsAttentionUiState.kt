// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.home

/** Host-selected display state. The host owns clocks, change status and cancellation. */
data class NeedsAttentionUiState(val pending: PendingChange? = null)

data class PendingChange(val kind: PendingChangeKind, val committing: Boolean = false)

sealed interface PendingChangeKind {
    /** Both values come from the same host date snapshot, as on Home. */
    data class TurnOff(val time: String, val day: HomeNoticeDay) : PendingChangeKind
    data object People : PendingChangeKind
    data object Setup : PendingChangeKind
}

sealed interface NeedsAttentionEvent {
    data object Back : NeedsAttentionEvent
    /** Open WaitingChangeScreen at ConfirmCancel with a null (PIN-free) context. */
    data object CancelChange : NeedsAttentionEvent
}
