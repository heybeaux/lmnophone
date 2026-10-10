/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.trusted

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.plainphone.ui.theme.PlainphoneTheme

@Preview(widthDp = 360, heightDp = 640, fontScale = 1f)
@Preview(widthDp = 360, heightDp = 640, fontScale = 2f)
private annotation class MinimumScreen
private val previewContext = AdminContext("Pat", "Sam")
private val previewApp = InstalledApp("example", "Bus times", "Example Apps")
@MinimumScreen @Composable private fun WhoPreview() = PlainphoneTheme {
    WhoAreYouScreen(WhoAreYouUiState(listOf(TrustedPersonChoice("one", "Sam"), TrustedPersonChoice("two", "Jo"))), {})
}
@MinimumScreen @Composable private fun PinPreview() = PlainphoneTheme { PinScreen(PinUiState(previewContext), {}) }
@MinimumScreen @Composable private fun MenuPreview() = PlainphoneTheme {
    AdminMenuScreen(AdminMenuUiState(previewContext, listOf(AdminMenuItem(AdminDestination.AddApp), AdminMenuItem(AdminDestination.Apps))), {})
}
@MinimumScreen @Composable private fun ReviewPreview() = PlainphoneTheme { AppReviewScreen(AppReviewUiState(previewContext, previewApp, helper = "Jo"), {}) }
@MinimumScreen @Composable private fun PermissionsPreview() = PlainphoneTheme {
    PermissionsScreen(PermissionsUiState(previewContext, previewApp.name, listOf(PermissionChoice("mic", PermissionGroup.Microphone))), {})
}
@MinimumScreen @Composable private fun AppsPreview() = PlainphoneTheme { AppsOnPhoneScreen(AppsOnPhoneUiState(previewContext, listOf(AppOnPhone(previewApp))), {}) }
@MinimumScreen @Composable private fun RequestsPreview() = PlainphoneTheme {
    TrustedRequestsScreen(TrustedRequestsUiState(previewContext, listOf(TrustedRequest("one", "The bus times app", "Thursday", true))), {})
}
@MinimumScreen @Composable private fun PeoplePreview() = PlainphoneTheme {
    TrustedPeopleScreen(TrustedPeopleUiState(previewContext, listOf(TrustedPersonChoice("one", "Sam")), listOf(PeopleAction.Add, PeopleAction.HandOver)), {})
}
@MinimumScreen @Composable private fun PeopleChangePreview() = PlainphoneTheme {
    PeopleChangeScreen(PeopleChangeUiState(previewContext, PeopleAction.Add, listOf("Sam"), newName = "Jo"), {})
}
@MinimumScreen @Composable private fun WaitingPreview() = PlainphoneTheme {
    WaitingChangeScreen(WaitingChangeUiState(previewContext, WaitingStage.Details, "turn Plainphone off", listOf("Sam"),
        "Sam", "Thursday", "10:40", "10:40 tomorrow", canSkip = true), {})
}
@MinimumScreen @Composable private fun SessionPreview() = PlainphoneTheme { SessionScreen(SessionUiState(previewContext, SessionStage.Play), {}) }
@MinimumScreen @Composable private fun AppDetailPreview() = PlainphoneTheme { AppDetailScreen(AppDetailUiState(previewContext, previewApp), {}) }
@MinimumScreen @Composable private fun UpdatePreview() = PlainphoneTheme { UpdateScreen(UpdateUiState(previewContext, UpdateStage.Intro, "Jo"), {}) }
@MinimumScreen @Composable private fun OpeningPreview() = PlainphoneTheme { OpeningScreen(OpeningUiState(AdminContext("Pat"), OpeningStage.Countdown, OpeningKind.Play), {}) }
@MinimumScreen @Composable private fun WaitsPreview() = PlainphoneTheme {
    WaitsAndLimitsScreen(WaitsAndLimitsUiState(AdminContext("Pat"), "10 minutes", 2, false, true, true, true, true), {})
}
@MinimumScreen @Composable private fun HistoryPreview() = PlainphoneTheme {
    HistoryScreen(HistoryUiState(previewContext, listOf(HistoryEntry("Thursday", "Added Bus times", HistoryAuthority.Pin, "Sam"))), {})
}
@MinimumScreen @Composable private fun OffPreview() = PlainphoneTheme {
    TurnOffScreen(TurnOffUiState(previewContext, TurnOffStage.Intro, listOf("Sam", "Jo"), "Jo"), {})
}
