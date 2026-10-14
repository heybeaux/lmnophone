/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.trusted

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource as s
import org.plainphone.ui.R
import org.plainphone.ui.components.*
import org.plainphone.ui.theme.*

@Composable
fun WhoAreYouScreen(state: WhoAreYouUiState, onEvent: (WhoAreYouEvent) -> Unit) {
    AdminFrame(null, s(R.string.admin_who), secondary = s(R.string.admin_back), onSecondary = { onEvent(WhoAreYouEvent.Back) }) {
        state.people.forEach { person -> ActionRow(person.name.withoutBidiControls()) { onEvent(WhoAreYouEvent.Choose(person.id)) } }
    }
}
@Composable
fun PinScreen(state: PinUiState, onEvent: (PinEvent) -> Unit) {
    val name = (state.context.trustedPersonName ?: state.context.userName).withoutBidiControls()
    val other = state.otherName?.withoutBidiControls()
    val self = state.context.trustedPersonName == null
    val title = when (state.stage) {
        PinStage.Enter -> if (self) s(R.string.admin_self_pin_title) else s(R.string.admin_pin_title, name)
        PinStage.Locked -> if (other != null) s(R.string.admin_pin_locked_named, name) else s(R.string.admin_pin_locked)
        PinStage.Recovery -> if (other != null) s(R.string.admin_recovery_named, name) else s(R.string.admin_recovery_title)
        PinStage.RecoveryLocked -> if (other != null) s(R.string.admin_code_locked_named, name) else s(R.string.admin_code_locked)
        PinStage.NewPin -> s(R.string.admin_new_pin)
        PinStage.RecoveryIssued -> s(R.string.admin_new_code)
    }
    val primary = when (state.stage) {
        PinStage.Locked -> s(R.string.admin_recovery)
        PinStage.RecoveryLocked -> s(if (state.recoveryLockout == RecoveryLockoutKind.PinAvailable) R.string.admin_enter_pin else R.string.user_ok)
        PinStage.RecoveryIssued -> s(R.string.admin_code_written)
        else -> s(R.string.admin_continue)
    }
    AdminFrame(state.context, title, primary, {
        onEvent(when (state.stage) { PinStage.Locked -> PinEvent.UseRecoveryCode;
            PinStage.RecoveryLocked -> if (state.recoveryLockout == RecoveryLockoutKind.PinAvailable) PinEvent.EnterPin else PinEvent.Ok; PinStage.RecoveryIssued -> PinEvent.Ok; else -> PinEvent.Continue })
    }, enabled = !state.sending && (state.canContinue || state.stage == PinStage.Locked || state.stage == PinStage.RecoveryLocked || state.stage == PinStage.RecoveryIssued),
        secondary = when (state.stage) {
            PinStage.Enter -> s(R.string.admin_pin_forgot)
            PinStage.Locked -> s(if (other != null) R.string.admin_choose_other else R.string.user_ok)
            PinStage.RecoveryLocked -> if (other != null) s(R.string.admin_choose_other) else if (state.recoveryLockout == RecoveryLockoutKind.PinAvailable) s(R.string.user_ok) else null
            else -> null
        }, secondaryEnabled = !state.sending,
        onSecondary = { onEvent(if (state.stage == PinStage.Enter) PinEvent.UseRecoveryCode else if (other != null) PinEvent.ChooseSomeoneElse else PinEvent.Ok) }) {
        when (state.stage) {
            PinStage.Enter -> {
                Words(if (self) s(R.string.admin_self_pin_body) else s(R.string.admin_pin_body, state.context.userName.withoutBidiControls()))
                if (state.freshExplanation) Words(s(R.string.admin_pin_fresh))
                state.pendingChange?.let { Words(s(if (self) R.string.admin_recovery_pending_self else R.string.admin_recovery_pending, it.withoutBidiControls())) }
                if (state.wrongPin) Words(s(R.string.admin_pin_wrong))
                SecretField(s(R.string.admin_pin_label), state.value, "pin-input", true, !state.sending) { onEvent(PinEvent.ValueChanged(it)) }
            }
            PinStage.Locked -> Words(if (other == null) s(R.string.admin_pin_wait, state.wait)
                else s(R.string.admin_pin_wait_two, state.wait, name, other))
            PinStage.Recovery -> {
                Words(s(R.string.admin_recovery_body))
                if (state.wrongRecoveryCode) Words(s(R.string.admin_recovery_wrong))
                if (self) Words(s(R.string.admin_recovery_self))
                state.pendingChange?.let { Words(s(if (self) R.string.admin_recovery_pending_self else R.string.admin_recovery_pending, it.withoutBidiControls())) }
                SecretField(s(R.string.admin_recovery_label), state.value, "recovery-input", false, !state.sending) { onEvent(PinEvent.ValueChanged(it)) }
                Words(s(R.string.admin_recovery_google))
            }
            PinStage.RecoveryLocked -> {
                Words(s(R.string.admin_code_wait, state.wait))
                Words(when (state.recoveryLockout) {
                    RecoveryLockoutKind.PinAvailable -> if (other == null) s(R.string.admin_code_pin_available) else s(R.string.admin_code_named_pin_available, name)
                    RecoveryLockoutKind.PinWaiting -> if (other == null) s(R.string.admin_code_pin_waiting, state.pinWait) else s(R.string.admin_code_named_pin_waiting, name, state.pinWait)
                    RecoveryLockoutKind.Skip -> s(R.string.admin_code_skip_waiting)
                })
                other?.let { Words(s(R.string.admin_code_other_available, it)) }
                Words(s(R.string.admin_code_help, state.helper.withoutBidiControls().ifBlank { s(R.string.setup_helper) }))
            }
            PinStage.NewPin -> {
                if (state.mismatch) Words(s(R.string.admin_pin_mismatch))
                SecretField(s(R.string.admin_pin_label), state.value, "pin-input", true, !state.sending) { onEvent(PinEvent.ValueChanged(it)) }
                SecretField(s(R.string.admin_pin_again), state.confirmation, "pin-confirm", true, !state.sending) { onEvent(PinEvent.ConfirmationChanged(it)) }
            }
            PinStage.RecoveryIssued -> {
                Words(if (self) s(R.string.admin_code_self) else s(R.string.admin_code_body, state.context.userName.withoutBidiControls()))
                ScreenBox { Words(state.recoveryCode) }
                Words(s(R.string.admin_recovery_google))
                other?.let { Words(s(R.string.admin_code_other, it)) }
            }
        }
    }
}

/** Change PIN result (PLA-494). A failure doesn't promise which PIN works: an unacknowledged write may have landed. */
@Composable
fun PinChangeResultScreen(context: AdminContext, changed: Boolean, onOk: () -> Unit) {
    AdminFrame(context, s(R.string.admin_change_pin), s(R.string.user_ok), onOk) {
        Words(s(if (changed) R.string.admin_pin_changed else R.string.admin_pin_change_failed))
    }
}

@Composable internal fun destinationTitle(destination: AdminDestination, user: String) = s(when (destination) {
    AdminDestination.VoiceMessages -> R.string.family_title
    AdminDestination.AddApp -> R.string.more_add; AdminDestination.HiddenApp -> R.string.admin_hidden_app
    AdminDestination.Apps -> R.string.admin_apps; AdminDestination.Requests -> R.string.admin_requests
    AdminDestination.Updates -> R.string.admin_updates; AdminDestination.Settings -> R.string.admin_settings
    AdminDestination.Limits -> R.string.admin_limits; AdminDestination.People -> R.string.admin_people
    AdminDestination.History -> R.string.admin_history; AdminDestination.Waits -> R.string.admin_waits
    AdminDestination.UpdatePlainphone -> R.string.admin_update; AdminDestination.ChangePin -> R.string.admin_change_pin
    AdminDestination.NewRecoveryCode -> R.string.admin_new_recovery_code
    AdminDestination.SwitchMode -> R.string.admin_switch; AdminDestination.TurnOff -> R.string.admin_turn_off
}, user)

@Composable
fun AdminMenuScreen(state: AdminMenuUiState, onEvent: (AdminMenuEvent) -> Unit) {
    val self = state.context.trustedPersonName == null
    val user = state.context.userName.withoutBidiControls()
    AdminFrame(state.context, if (self) s(R.string.more_your_settings) else s(R.string.admin_menu_title, user),
        if (self) s(R.string.admin_done_self) else s(R.string.admin_done_home, user), { onEvent(AdminMenuEvent.Done) }) {
        Words(if (self) s(R.string.admin_menu_self) else s(R.string.admin_menu_body, user))
        state.openings?.let { Words(it.withoutBidiControls()) }
        if (state.needsYou.isNotEmpty()) ScreenBox {
            Heading(s(R.string.admin_needs))
            state.needsYou.forEach { notice -> ActionRow(notice.text.withoutBidiControls()) { onEvent(AdminMenuEvent.OpenNotice(notice.id)) } }
        }
        state.items.forEach { item -> ActionRow(destinationTitle(item.destination, user), item.detail.withoutBidiControls()) { onEvent(AdminMenuEvent.Open(item.destination)) } }
    }
}
