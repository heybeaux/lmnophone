/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui

import androidx.compose.runtime.Composable
import org.plainphone.ui.trusted.*

data class TrustedFixture(val name: String, val hasName: Boolean = true, val content: @Composable () -> Unit)
fun trustedFixtures(two: Boolean = true, long: Boolean = false): List<TrustedFixture> {
    val name = if (long) "Alexandria Catherine Montgomery" else "Sam"
    val other = if (long) "Christopher Alexander Worthington" else "Jo"
    val user = if (long) "Margaret Elizabeth Wetherington" else "Pat"
    val people = listOf(TrustedPersonChoice("one", name)) + if (two) listOf(TrustedPersonChoice("two", other)) else emptyList()
    val context = AdminContext(user, name)
    val app = InstalledApp("bus", if (long) "Regional Transit Journey Planner" else "Bus times", "Example Apps")
    val everyone = people.map { it.name }
    val change = "add $other as a trusted person"
    return buildList {
        add(TrustedFixture("who") { WhoAreYouScreen(WhoAreYouUiState(people), {}) })
        PinStage.entries.forEach { stage ->
            add(TrustedFixture("pin_$stage") { PinScreen(PinUiState(context, stage, wait = "1 minute",
                otherName = other.takeIf { two }, pendingChange = change, freshExplanation = true,
                recoveryCode = "EXAMPLE-CODE-NOT-A-SECRET"), {}) })
        }
        add(TrustedFixture("pin_wrong") { PinScreen(PinUiState(context, wrongPin = true), {}) })
        add(TrustedFixture("pin_sending") { PinScreen(PinUiState(context, sending = true, canContinue = true), {}) })
        add(TrustedFixture("menu") { AdminMenuScreen(AdminMenuUiState(context,
            AdminDestination.entries.filter { it != AdminDestination.HiddenApp && it != AdminDestination.Waits && it != AdminDestination.SwitchMode }
                .map { AdminMenuItem(it) }, listOf(AdminNotice("wait", "Waiting: $change"))), {}) })
        add(TrustedFixture("self_menu", false) { AdminMenuScreen(AdminMenuUiState(AdminContext(user),
            AdminDestination.entries.filter { it != AdminDestination.Requests && it != AdminDestination.People }.map { AdminMenuItem(it) },
            openings = "2 of 2 openings left"), {}) })
        AppReviewStage.entries.forEach { stage ->
            add(TrustedFixture("app_$stage") { AppReviewScreen(AppReviewUiState(context, app, stage = stage,
                helper = other, request = "The ferry bus app", allowed = "Notifications", denied = "Location"), {}) })
        }
        add(TrustedFixture("app_older") { AppReviewScreen(AppReviewUiState(context, app,
            WebReview.Older("Help pages can show websites.", "1.0", "2.0")), {}) })
        add(TrustedFixture("app_checked") { AppReviewScreen(AppReviewUiState(context, app,
            WebReview.Checked("Help pages can show websites.", "2.0")), {}) })
        add(TrustedFixture("permissions") { PermissionsScreen(PermissionsUiState(context, app.name,
            PermissionGroup.entries.map { PermissionChoice(it.name, it) }), {}) })
        add(TrustedFixture("permission_choices") { PermissionsScreen(PermissionsUiState(context, app.name,
            listOf(PermissionChoice("camera", PermissionGroup.Camera, decision = PermissionDecision.Allow),
                PermissionChoice("mic", PermissionGroup.Microphone, decision = PermissionDecision.Deny),
                PermissionChoice("location", PermissionGroup.Location, unavailable = true)), canContinue = true), {}) })
        add(TrustedFixture("apps") { AppsOnPhoneScreen(AppsOnPhoneUiState(context, listOf(AppOnPhone(app, canMoveDown = true),
            AppOnPhone(InstalledApp("sms", "Messages", ""), alwaysOn = true),
            AppOnPhone(InstalledApp("phone", "Phone", ""), alwaysOn = true))), {}) })
        add(TrustedFixture("requests") { TrustedRequestsScreen(TrustedRequestsUiState(context,
            listOf(TrustedRequest("one", "https://example.invalid is plain request text", "Thursday 8 Oct, 10:40", true))), {}) })
        add(TrustedFixture("request_remove") { TrustedRequestsScreen(TrustedRequestsUiState(context, emptyList(), removeRequestId = "one"), {}) })
        add(TrustedFixture("people") { TrustedPeopleScreen(TrustedPeopleUiState(context, people,
            listOf(if (two) PeopleAction.Remove else PeopleAction.Add, PeopleAction.HandOver, PeopleAction.ToSelfManaged), other), {}) })
        PeopleAction.entries.forEach { action ->
            add(TrustedFixture("people_$action") { PeopleChangeScreen(PeopleChangeUiState(context, action, everyone, other, other, change), {}) })
        }
        WaitingStage.entries.forEach { stage ->
            add(TrustedFixture("waiting_$stage") { WaitingChangeScreen(WaitingChangeUiState(context, stage,
                change, everyone, name, "Thursday", "10:40", "10:40 tomorrow", other, other, user,
                canSkip = true, canStop = true), {}) })
        }
        add(TrustedFixture("user_wait") { WaitingChangeScreen(WaitingChangeUiState(stage = WaitingStage.Details,
            change = "turn Plainphone off", everyone = everyone, starterName = name, day = "Thursday",
            startedAt = "10:40", happensAt = "10:40 tomorrow", turningOff = true), {}) })
        add(TrustedFixture("self_wait", false) { WaitingChangeScreen(WaitingChangeUiState(AdminContext(user), WaitingStage.Details,
            "turn Plainphone off", happensAt = "10:40 tomorrow"), {}) })
        add(TrustedFixture("cancel_saving") { WaitingChangeScreen(WaitingChangeUiState(stage = WaitingStage.ConfirmCancel,
            change = change, everyone = everyone, sending = true), {}) })
        TurnOffStage.entries.forEach { stage ->
            add(TrustedFixture("turn_off_$stage") { TurnOffScreen(TurnOffUiState(context, stage, everyone, other,
                step = 3, total = 12, stepDescription = "unlocking the home screen"), {}) })
        }
        AppDetailStage.entries.forEach { stage ->
            add(TrustedFixture("detail_$stage") { AppDetailScreen(AppDetailUiState(context, app, stage, "2.0", "8 October", "9 October", other), {}) })
        }
        UpdateStage.entries.forEach { stage ->
            add(TrustedFixture("update_$stage") { UpdateScreen(UpdateUiState(context, stage, other, version = "1.2"), {}) })
        }
        OpeningStage.entries.forEach { stage ->
            add(TrustedFixture("opening_$stage") { OpeningScreen(OpeningUiState(AdminContext(user), stage, OpeningKind.App,
                appName = app.name, time = "10:40 tomorrow"), {}) })
        }
        add(TrustedFixture("waits_limits", false) { WaitsAndLimitsScreen(WaitsAndLimitsUiState(AdminContext(user), "10 minutes", 2,
            false, true, true, true, true), {}) })
        add(TrustedFixture("history") { HistoryScreen(HistoryUiState(context, listOf(HistoryEntry("Thursday 8 October, 10:40",
            "Added Bus times", HistoryAuthority.Pin, name, everyone.map { HistoryReceipt(it, ReceiptStatus.Waiting) }))), {}) })
        SessionStage.entries.forEach { stage ->
            add(TrustedFixture("session_$stage") { SessionScreen(SessionUiState(context, stage, other,
                dueDate = "8 November", apps = listOf(CheckApp("sms", "Messages"), CheckApp("play", "Google Play services", true)),
                restoredLines = listOf("The texting app was changed, so Plainphone put Messages back.")), {}) })
        }
    }
}
