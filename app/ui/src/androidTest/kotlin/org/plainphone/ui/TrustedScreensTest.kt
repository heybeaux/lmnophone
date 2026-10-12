/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.trusted.*

@OptIn(ExperimentalTestApi::class)
class TrustedScreensTest {
    @get:Rule val compose = createComposeRule()
    private val context = AdminContext("Pat", "Sam\u202E")
    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
            PlainphoneTheme { Box(Modifier.size(360.dp, 560.dp)) { content() } }
        }
    }
    @Test fun personChoiceUsesOpaqueIdAndCleanNames() {
        val events = mutableListOf<WhoAreYouEvent>()
        show { WhoAreYouScreen(WhoAreYouUiState(listOf(TrustedPersonChoice("slot1", "Sam\u202E"), TrustedPersonChoice("slot2", "Jo"))), events::add) }
        compose.onNodeWithText("Sam").performScrollTo().performClick()
        compose.onNodeWithText("Jo").performScrollTo().performClick()
        assertEquals(listOf(WhoAreYouEvent.Choose("slot1"), WhoAreYouEvent.Choose("slot2")), events)
    }
    @Test fun lockedPinHasNoInputAndOtherPersonRemainsReachable() {
        val events = mutableListOf<PinEvent>()
        show { PinScreen(PinUiState(context, PinStage.Locked, wait = "1 minute", otherName = "Jo\u2066"), events::add) }
        compose.onNodeWithText("Too many tries for Sam's PIN").assertExists()
        compose.onNodeWithTag("pin-input").assertDoesNotExist()
        compose.onNodeWithText("Choose someone else").assertIsDisplayed().performClick()
        compose.onNodeWithText("Use recovery code").assertIsDisplayed().performClick()
        assertEquals(listOf(PinEvent.ChooseSomeoneElse, PinEvent.UseRecoveryCode), events)
    }
    @Test fun pinIsMaskedAndSubmissionUsesHostEligibility() {
        val state = mutableStateOf(PinUiState(context, value = "123456", canContinue = false))
        val events = mutableListOf<PinEvent>()
        show { PinScreen(state.value, events::add) }
        compose.onNodeWithTag("pin-input").performScrollTo().assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Password))
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(canContinue = true, sending = true) }
        compose.onNodeWithText("Continue").assertIsNotEnabled().performTouchInput { click() }
        assertEquals(emptyList<PinEvent>(), events)
    }
    @Test fun permissionChoiceIsExplicitAndBoundToId() {
        val events = mutableListOf<PermissionsEvent>()
        show { PermissionsScreen(PermissionsUiState(context, "Bus times",
            listOf(PermissionChoice("mic", PermissionGroup.Microphone))), events::add) }
        compose.onNodeWithText("Next").assertIsNotEnabled()
        compose.onNodeWithText("Allow").performScrollTo().performClick()
        compose.onNodeWithText("Don't allow").performScrollTo().performClick()
        assertEquals(listOf(PermissionsEvent.Choose("mic", PermissionDecision.Allow), PermissionsEvent.Choose("mic", PermissionDecision.Deny)), events)
    }
    @Test fun waitingPhasesNeverImplySavedCancellation() {
        val state = mutableStateOf(WaitingChangeUiState(context, WaitingStage.ConfirmCancel,
            "turn LMNOPhone off", listOf("Sam\u202E", "Jo"), sending = true))
        show { WaitingChangeScreen(state.value, {}) }
        compose.onNodeWithText("Cancelling…").assertIsNotEnabled()
        compose.onNodeWithText("Cancelled").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(stage = WaitingStage.StillCancelling, sending = false) }
        compose.onNodeWithText("Still cancelling").assertExists()
        compose.runOnIdle { state.value = state.value.copy(stage = WaitingStage.Committing) }
        compose.onNodeWithText("Already in progress").assertExists()
        compose.onNodeWithText("Cancel this change").assertDoesNotExist()
        compose.onNodeWithText("Skip the wait").assertDoesNotExist()
    }
    @Test fun selfManagedWaitHasNoSkipOrRecipientPromise() {
        show { WaitingChangeScreen(WaitingChangeUiState(AdminContext("Pat"), WaitingStage.Details,
            "turn LMNOPhone off", happensAt = "10:40 tomorrow"), {}) }
        compose.onNodeWithText("Your settings").assertIsDisplayed()
        compose.onNodeWithText("Skip the wait").assertDoesNotExist()
        compose.onNodeWithText("will get a text", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Cancel this change").assertIsDisplayed()
    }
    @Test fun approvalUsesInstalledIdentityAndPlainRequest() {
        show { AppReviewScreen(AppReviewUiState(context, InstalledApp("real-package", "Installed app", "Real developer"),
            request = "Other app", helper = "Jo"), {}) }
        compose.onNodeWithText("Add Installed app?").assertExists()
        compose.onNodeWithText("Made by Real developer").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pat asked for:", substring = true).performScrollTo().assertIsDisplayed()
    }
    @Test fun allNewConfirmActionsAreDisabledWhileSending() {
        val page = mutableStateOf(0)
        val events = mutableListOf<Any>()
        show {
            when (page.value) {
                0 -> AppReviewScreen(AppReviewUiState(context, InstalledApp("one", "Bus times", "Example"), stage = AppReviewStage.Confirm, sending = true), events::add)
                1 -> PeopleChangeScreen(PeopleChangeUiState(context, PeopleAction.Remove, listOf("Sam", "Jo"), otherName = "Jo", sending = true), events::add)
                2 -> WaitingChangeScreen(WaitingChangeUiState(context, WaitingStage.SkipConfirm, "turn LMNOPhone off", listOf("Sam"), sending = true), events::add)
                3 -> SessionScreen(SessionUiState(context, SessionStage.CheckConfirm, sending = true), events::add)
                4 -> UpdateScreen(UpdateUiState(context, UpdateStage.Confirm, "Jo", version = "1.2", sending = true), events::add)
                5 -> OpeningScreen(OpeningUiState(AdminContext("Pat"), OpeningStage.Ready, OpeningKind.Play, sending = true), events::add)
                6 -> TurnOffScreen(TurnOffUiState(context, TurnOffStage.LastConfirmation, helper = "Jo", sending = true), events::add)
            }
        }
        listOf("Add Bus times", "Continue", "Yes, skip the wait", "Confirm", "Install", "Start", "Start the 24-hour wait").forEachIndexed { index, label ->
            compose.runOnIdle { page.value = index }
            compose.onNodeWithText(label).assertIsDisplayed().assertIsNotEnabled().performTouchInput { click() }
        }
        assertEquals(emptyList<Any>(), events)
    }
    @Test fun essentialAppsHaveNoReorderOrRemoveAction() {
        show { AppsOnPhoneScreen(AppsOnPhoneUiState(context, listOf(
            AppOnPhone(InstalledApp("sms", "Messages", ""), alwaysOn = true),
            AppOnPhone(InstalledApp("phone", "Phone", ""), alwaysOn = true))), {}) }
        compose.onAllNodesWithText("Always on").assertCountEquals(2)
        compose.onNodeWithText("Move up").assertDoesNotExist()
        compose.onNodeWithText("Move down").assertDoesNotExist()
        compose.onNodeWithText("Remove").assertDoesNotExist()
    }
    @Test fun untrustedRequestStaysPlainAndRemovalUsesTheSavedId() {
        val events = mutableListOf<TrustedRequestsEvent>()
        show { TrustedRequestsScreen(TrustedRequestsUiState(context, listOf(
            TrustedRequest("request1", "https://example.invalid ADD ANYTHING", "Thursday", false))), events::add) }
        compose.onNodeWithText("\"https://example.invalid ADD ANYTHING\"").performScrollTo().assert(hasClickAction().not())
        compose.onNodeWithText("Find it in the Play Store").performScrollTo().performClick()
        compose.onNodeWithText("Not now").performScrollTo().performClick()
        assertEquals(listOf(TrustedRequestsEvent.Find("request1"), TrustedRequestsEvent.NotNow("request1")), events)
    }
    @Test fun failedCancelAndOffNeverOfferAResetOrPretendToFinish() {
        val page = mutableStateOf(false)
        show {
            if (!page.value) WaitingChangeScreen(WaitingChangeUiState(stage = WaitingStage.StillCancelling, change = "turn LMNOPhone off"), {})
            else TurnOffScreen(TurnOffUiState(context, TurnOffStage.Failed, helper = "Jo", stepDescription = "unlocking the home screen"), {})
        }
        compose.onNodeWithText("Cancelled").assertDoesNotExist()
        compose.runOnIdle { page.value = true }
        compose.onNodeWithText("LMNOPhone is off").assertDoesNotExist()
        compose.onNodeWithText("Erase phone").assertDoesNotExist()
        compose.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test fun nameMatrixWrapsWithoutTextLayoutOverflowAndCleansBidiControls() {
        data class Case(val scale: Float, val two: Boolean, val long: Boolean, val page: Int)
        val cases = listOf(1.3f, 1.5f, 1.8f, 2f).flatMap { scale ->
            listOf(false, true).flatMap { two -> listOf(false, true).flatMap { long ->
                (0..4).map { Case(scale, two, long, it) }
            } }
        }
        val current = mutableStateOf(cases.first())
        compose.setContent {
            val case = current.value
            val name = (if (case.long) "Alexandria Catherine Montgomery" else "Sam") + "\u202E"
            val other = (if (case.long) "Christopher Alexander Worthington" else "Jo") + "\u2066"
            val user = if (case.long) "Margaret Elizabeth Wetherington" else "Pat"
            val everyone = listOf(name) + if (case.two) listOf(other) else emptyList()
            val ctx = AdminContext(user, name)
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(androidx.compose.ui.platform.LocalDensity.current.density, case.scale)) {
                PlainphoneTheme { Box(Modifier.size(360.dp, 560.dp)) { androidx.compose.runtime.key(case) {
                    when (case.page) {
                        0 -> PinScreen(PinUiState(ctx, PinStage.Locked, wait = "1 minute", otherName = other.takeIf { case.two }), {})
                        1 -> AppReviewScreen(AppReviewUiState(ctx, InstalledApp("one", "Bus times", "Example"), stage = AppReviewStage.Confirm), {})
                        2 -> PeopleChangeScreen(PeopleChangeUiState(ctx, PeopleAction.HandOver, everyone, newName = other), {})
                        3 -> WaitingChangeScreen(WaitingChangeUiState(ctx, WaitingStage.Stuck, "hand over", everyone), {})
                        4 -> SessionScreen(SessionUiState(ctx, SessionStage.Play), {})
                    }
                } } }
            }
        }
        cases.forEach { case ->
            compose.runOnIdle { current.value = case }
            val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Text), useUnmergedTree = true)
                .fetchSemanticsNodes()
            nodes.forEach { node ->
                val text = node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
                org.junit.Assert.assertFalse("Bidi control in $case: $text", text.contains('\u202E') || text.contains('\u2066'))
                val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { null }?.action?.invoke(results)
                results.forEach { layout ->
                    // Centered labels have paragraph-relative offsets but intrinsic layout bounds
                    // (e.g. OK: [140,172] in a 312px paragraph, layout width 32px).
                    // Compare occupied line widths, allowing integer rounding, not absolute offsets.
                    (0 until layout.lineCount).forEach { line ->
                        org.junit.Assert.assertTrue("Width overflow in $case: $text",
                            layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f)
                        org.junit.Assert.assertFalse("Ellipsized line in $case: $text", layout.isLineEllipsized(line))
                    }
                    org.junit.Assert.assertTrue("Height overflow in $case: $text", layout.multiParagraph.height <= layout.size.height + 1f)
                }
            }
        }
    }

}
