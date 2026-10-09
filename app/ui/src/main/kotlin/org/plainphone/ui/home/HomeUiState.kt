/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.home

/** Display-ready values from lane D. Apps are already ordered; notice priority is upstream. */
data class HomeUiState(
    val time: String,
    val date: String,
    val shortDate: String,
    val apps: List<HomeApp> = emptyList(),
    val notice: HomeNotice? = null,
)

/** Opaque ID returned unchanged to the view-model; this module never launches a package. */
data class HomeApp(val id: String, val name: String)

sealed interface HomeNotice {
    data object NeedsAttention : HomeNotice
    data class TurnOffWaiting(val time: String) : HomeNotice
    data object PeopleChangeWaiting : HomeNotice
    data object ModeChangeWaiting : HomeNotice
    data class OpeningUp(val minutes: Int) : HomeNotice
    data object ReadyToStart : HomeNotice
    data class MonthlyCheck(val trustedPeople: String) : HomeNotice
    data object SelfManagedMonthlyCheck : HomeNotice
    data object AlreadyInProgress : HomeNotice
}

sealed interface HomeEvent {
    data object OpenPhone : HomeEvent
    data object OpenMessages : HomeEvent
    data object OpenMore : HomeEvent
    data object OpenNotice : HomeEvent
    data class OpenApp(val id: String) : HomeEvent
}
