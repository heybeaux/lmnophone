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

/** What a PIN settings row opens. Rows without one in this build are not shown, so no row opens the not-in-build page. */
enum class PinMenuTarget { Family, Play, AddHiddenApp, Apps, Requests, RequestRecipient, ChangePin, NewRecoveryCode, TurnOff }

fun pinMenuTarget(destination: AdminDestination): PinMenuTarget? = when (destination) {
    AdminDestination.VoiceMessages -> PinMenuTarget.Family
    AdminDestination.HiddenApp -> PinMenuTarget.AddHiddenApp
    AdminDestination.Apps -> PinMenuTarget.Apps
    AdminDestination.Requests -> PinMenuTarget.Requests
    AdminDestination.RequestRecipient -> PinMenuTarget.RequestRecipient
    AdminDestination.ChangePin -> PinMenuTarget.ChangePin
    AdminDestination.NewRecoveryCode -> PinMenuTarget.NewRecoveryCode
    AdminDestination.TurnOff -> PinMenuTarget.TurnOff
    AdminDestination.AddApp -> PinMenuTarget.Play
    AdminDestination.Updates, AdminDestination.Settings, AdminDestination.Limits,
    AdminDestination.People, AdminDestination.History, AdminDestination.Waits, AdminDestination.UpdatePlainphone,
    AdminDestination.SwitchMode -> null
}

fun pinMenuUiState(context: AdminContext, requestCount: Int? = null) = AdminMenuUiState(context, AdminDestination.entries.filter { it != AdminDestination.RequestRecipient && pinMenuTarget(it) != null }.flatMap { if (it == AdminDestination.Requests) listOf(AdminMenuItem(it, requestCount?.takeIf { count -> count > 0 }?.toString().orEmpty()), AdminMenuItem(AdminDestination.RequestRecipient)) else listOf(AdminMenuItem(it)) })
