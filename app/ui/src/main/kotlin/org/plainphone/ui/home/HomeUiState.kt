/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.home

import java.time.LocalDate

/** Display-ready values from lane D. Apps are already ordered; notice priority is upstream. */
data class HomeUiState(
    val time: String,
    val date: String,
    val shortDate: String,
    val apps: List<HomeApp> = emptyList(),
    val notice: HomeNotice? = null,
    /** Fresh, reliable host values only; null hides unavailable or switched-off counts. */
    val missedCalls: Int? = null,
    val unreadTexts: Int? = null,
    val showFamily: Boolean = false,
)

/** IDs should be unique. Duplicate IDs remain renderable and are returned unchanged to the host. */
data class HomeApp(val id: String, val name: String)

/** Supplied day facts; no clock is read here. UI owns relative-day wording and localization. */
data class HomeNoticeDay(val date: LocalDate, val daysFromToday: Int)

sealed interface HomeNotice {
    data object PreviewTurnOffClosed : HomeNotice
    data object NeedsAttention : HomeNotice
    data class TurnOffWaiting(val time: String, val day: HomeNoticeDay) : HomeNotice
    data object PeopleChangeWaiting : HomeNotice
    data object ModeChangeWaiting : HomeNotice
    data class OpeningUp(val minutes: Int) : HomeNotice
    data object ReadyToStart : HomeNotice
    data class MonthlyCheck(val trustedPeople: List<String>) : HomeNotice
    data object SelfManagedMonthlyCheck : HomeNotice
    data object AlreadyInProgress : HomeNotice
    data object Cancelling : HomeNotice
    /** device-lockdown: Android reported a system update waiting; LMNOPhone asked it to wait. */
    data object SoftwareUpdateWaiting : HomeNotice
}

sealed interface HomeEvent {
    data object OpenPhone : HomeEvent
    data object OpenMessages : HomeEvent
    data object OpenFamily : HomeEvent
    data object OpenMore : HomeEvent
    data object OpenNotice : HomeEvent
    data class OpenApp(val id: String) : HomeEvent
}
