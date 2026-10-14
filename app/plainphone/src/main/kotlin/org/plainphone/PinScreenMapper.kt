// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import org.plainphone.ui.trusted.*

/** Production presentation mapping; no credential or Android state is read here. */
fun pinUiState(page: PinPageState, context: AdminContext, value: String, confirmation: String,
    recoveryCode: String, wait: String, pinWait: String, pendingChange: String?, helper: String = ""): PinUiState = PinUiState(
    context = context,
    stage = when (page.page) {
        PinPage.SETUP, PinPage.CHANGE_NEW, PinPage.RECOVERY_NEW -> PinStage.NewPin
        PinPage.LOCKED -> PinStage.Locked
        PinPage.RECOVERY -> PinStage.Recovery
        PinPage.RECOVERY_LOCKED -> PinStage.RecoveryLocked
        PinPage.RECOVERY_ISSUED -> PinStage.RecoveryIssued
        else -> PinStage.Enter
    },
    value = value, confirmation = confirmation,
    canContinue = if (page.page == PinPage.RECOVERY) value.isNotBlank()
        else value.length >= 6 && value.all { it in '0'..'9' } && when (page.page) {
            PinPage.SETUP -> value == confirmation
            PinPage.CHANGE_NEW, PinPage.RECOVERY_NEW -> confirmation.isNotEmpty()
            else -> true
        },
    sending = page.busy, wrongPin = page.wrong && page.page != PinPage.RECOVERY,
    wrongRecoveryCode = page.wrong && page.page == PinPage.RECOVERY,
    mismatch = page.mismatch, wait = wait, pinWait = pinWait, helper = helper,
    pendingChange = if (page.page in setOf(PinPage.CHANGE_OLD, PinPage.RECOVERY)) pendingChange else null,
    recoveryLockout = if (page.pinWaitMillis > 0) RecoveryLockoutKind.PinWaiting else RecoveryLockoutKind.PinAvailable,
    recoveryCode = if (page.page == PinPage.RECOVERY_ISSUED) recoveryCode else "",
)

fun pinMenuUiState(context: AdminContext) = AdminMenuUiState(context, AdminDestination.entries.map { AdminMenuItem(it) })
