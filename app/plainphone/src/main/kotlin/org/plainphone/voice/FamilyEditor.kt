// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.voice

import org.plainphone.device.voice.*
import org.plainphone.policy.FamilyRules
import org.plainphone.ui.trusted.*
import java.util.UUID

/** UI-thread editor. The activity supplies live PIN-menu authority; pause revokes it and clears fields. */
class FamilyEditor(private val store: FamilyStore, context: AdminContext,
    private val authorized: () -> Boolean, private val grantMic: () -> Boolean,
    private val hear: (String) -> Unit, private val leave: () -> Unit,
    private val changed: (FamilyUiState) -> Unit) {
    var state = FamilyUiState(context)
        private set
    private var id: String? = null
    fun open() { if (authorized()) refresh() }
    private fun publish(next: FamilyUiState) { state = next; changed(next) }
    private fun refresh() {
        try {
            val list = store.read()
            id = null
            publish(FamilyUiState(state.context, enabled = list.enabled,
                people = list.people.map { FamilyPersonRow(it.id, it.name) }, canContinue = true,
                canAdd = list.people.size < FamilyRules.MAX_PEOPLE))
        } catch (_: Exception) { publish(FamilyUiState(state.context, failed = true)) }
    }
    fun event(event: FamilyEvent) {
        if (!authorized()) return
        try {
            when (event) {
                FamilyEvent.Back -> when (state.stage) {
                    FamilyStage.List -> leave()
                    FamilyStage.Number -> publish(state.copy(stage = FamilyStage.Name, canContinue = FamilyRules.validName(state.name) && FamilyRules.validName(spoken())))
                    FamilyStage.Picture -> publish(state.copy(stage = FamilyStage.Number))
                    FamilyStage.Confirm -> publish(state.copy(stage = FamilyStage.Picture))
                    else -> refresh()
                }
                FamilyEvent.Enable -> if (state.stage == FamilyStage.List && !state.enabled) {
                    check(grantMic()); store.setEnabled(true); refresh()
                }
                FamilyEvent.Add -> if (state.stage == FamilyStage.List && state.enabled && state.canAdd) {
                    id = UUID.randomUUID().toString().replace("-", "")
                    publish(state.copy(stage = FamilyStage.Name, name = "", spokenName = "", number = "", editing = false, canContinue = false))
                }
                is FamilyEvent.Edit -> if (state.stage == FamilyStage.List) {
                    val person = store.read().people.firstOrNull { it.id == event.id } ?: return
                    id = person.id
                    publish(state.copy(stage = FamilyStage.Name, name = person.name, spokenName = person.spokenName,
                        number = person.number, editing = true, canContinue = true))
                }
                is FamilyEvent.Name -> if (state.stage == FamilyStage.Name) updateName(event.value.take(80), state.spokenName)
                is FamilyEvent.SpokenName -> if (state.stage == FamilyStage.Name) updateName(state.name, event.value.take(80))
                is FamilyEvent.Number -> if (state.stage == FamilyStage.Number) publish(state.copy(number = event.value.take(25),
                    canContinue = FamilyRules.validNumber(event.value), failed = false))
                FamilyEvent.Hear -> if (state.stage == FamilyStage.Name && FamilyRules.validName(spoken())) hear(spoken())
                FamilyEvent.AskRemove -> if (state.editing && state.stage == FamilyStage.Name) publish(state.copy(stage = FamilyStage.Remove, canContinue = true))
                FamilyEvent.AskTurnOff -> if (state.enabled && state.stage == FamilyStage.List) publish(state.copy(stage = FamilyStage.TurnOff, canContinue = true))
                FamilyEvent.Continue -> if (state.canContinue) when (state.stage) {
                    FamilyStage.Name -> publish(state.copy(stage = FamilyStage.Number, canContinue = FamilyRules.validNumber(state.number)))
                    FamilyStage.Number -> publish(state.copy(stage = FamilyStage.Picture))
                    FamilyStage.Picture -> publish(state.copy(stage = FamilyStage.Confirm))
                    FamilyStage.Confirm -> { store.save(FamilyPerson(id ?: return, state.name.trim(), spoken(), state.number.trim())); refresh() }
                    FamilyStage.Remove -> { store.remove(id ?: return); refresh() }
                    FamilyStage.TurnOff -> { store.setEnabled(false); refresh() }
                    FamilyStage.List -> Unit
                }
            }
        } catch (_: Exception) { publish(state.copy(failed = true)) }
    }
    private fun spoken() = state.spokenName.ifBlank { state.name }.trim()
    private fun updateName(name: String, spoken: String) = publish(state.copy(name = name, spokenName = spoken,
        canContinue = FamilyRules.validName(name) && FamilyRules.validName(spoken.ifBlank { name }), failed = false))
}
