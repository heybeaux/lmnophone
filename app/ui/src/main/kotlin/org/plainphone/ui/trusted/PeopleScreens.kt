/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.trusted

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource as s
import org.plainphone.ui.R
import org.plainphone.ui.components.*

@Composable
fun TrustedPeopleScreen(state: TrustedPeopleUiState, onEvent: (TrustedPeopleEvent) -> Unit) {
    val user = state.context.userName.withoutBidiControls()
    val name = state.context.trustedPersonName.orEmpty().withoutBidiControls()
    val other = state.otherName.orEmpty().withoutBidiControls()
    AdminFrame(state.context, s(R.string.admin_people), secondary = s(R.string.admin_back), onSecondary = { onEvent(TrustedPeopleEvent.Back) }) {
        Words(if (state.people.size == 1) s(R.string.admin_person_body, state.people.first().name.withoutBidiControls(), user) else s(R.string.admin_people_body, user))
        state.people.forEach { person -> ScreenBox { Heading(person.name.withoutBidiControls()); if (person.contact.isNotEmpty()) Words(person.contact) } }
        state.actions.forEach { action ->
            val title = when (action) {
                PeopleAction.Add -> s(R.string.admin_people_add)
                PeopleAction.HandOver -> s(R.string.admin_people_handover)
                PeopleAction.Remove -> s(R.string.admin_people_remove, other)
                PeopleAction.ToSelfManaged -> s(R.string.admin_people_self, user)
                PeopleAction.ToTrusted -> s(R.string.admin_switch)
                PeopleAction.Ready -> s(R.string.admin_people_ready)
            }
            val detail = when (action) {
                PeopleAction.Add -> s(R.string.admin_takes_text, name)
                PeopleAction.Remove -> s(R.string.admin_takes_text, other)
                else -> s(R.string.admin_takes_wait)
            }
            ActionRow(title, detail) { onEvent(TrustedPeopleEvent.Open(action)) }
        }
    }
}
@Composable
fun PeopleChangeScreen(state: PeopleChangeUiState, onEvent: (PeopleChangeEvent) -> Unit) {
    val user = state.context.userName.withoutBidiControls()
    val new = state.newName.withoutBidiControls()
    val other = state.otherName.withoutBidiControls()
    val everyone = trustedPeopleText(state.everyone, everyone = true).orEmpty()
    val title = when (state.action) {
        PeopleAction.Add -> s(R.string.admin_people_add_title)
        PeopleAction.HandOver -> s(R.string.admin_people_hand_title)
        PeopleAction.Remove -> s(R.string.admin_remove_title, other)
        PeopleAction.ToSelfManaged -> s(R.string.admin_self_title, user)
        PeopleAction.ToTrusted -> s(R.string.admin_trusted_title)
        PeopleAction.Ready -> s(R.string.admin_people_ready)
    }
    val ready = state.action == PeopleAction.Ready
    AdminFrame(state.context, title, s(if (ready) R.string.admin_start_wait else R.string.admin_continue),
        { onEvent(PeopleChangeEvent.Continue) }, enabled = !state.sending,
        secondary = s(if (ready) R.string.admin_dont_start else R.string.admin_not_now), secondaryEnabled = !state.sending,
        onSecondary = { onEvent(PeopleChangeEvent.NotNow) }) {
        Words(when (state.action) {
            PeopleAction.Add -> s(R.string.admin_people_add_body, new, user)
            PeopleAction.HandOver -> s(R.string.admin_people_hand_body, new)
            PeopleAction.Remove -> s(R.string.admin_people_remove_body, other, user)
            PeopleAction.ToSelfManaged -> s(R.string.admin_self_body, user, everyone)
            PeopleAction.ToTrusted -> s(R.string.admin_trusted_body)
            PeopleAction.Ready -> s(R.string.admin_people_ready_body, state.change.withoutBidiControls(), new)
        })
        if (state.action in listOf(PeopleAction.Add, PeopleAction.HandOver, PeopleAction.Remove)) Words(s(R.string.admin_people_wait, everyone))
    }
}
