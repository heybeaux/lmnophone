// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.plainphone.ui.requests.*
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.trusted.*

@OptIn(ExperimentalTestApi::class)
class PinSettingsTalkBackTest {
    @get:Rule val compose = createComposeRule()
    private val context = AdminContext("")
    private val camera = InstalledApp("camera", "Camera", "")
    private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
    private val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
    private fun show(content: @Composable () -> Unit) = compose.setContent {
        PlainphoneTheme { Box(Modifier.size(360.dp, 560.dp)) { content() } }
    }
    private fun action(label: String) = compose.onNode(hasText(label) and hasClickAction()).assert(button)
    private fun noGlyphs() {
        listOf("›", "‹", "✓").forEach { glyph ->
            compose.onAllNodes(hasText(glyph, substring = true)).assertCountEquals(0)
            compose.onAllNodes(hasContentDescription(glyph, substring = true)).assertCountEquals(0)
        }
    }
    @Test fun entryRecoveryAndChangeFieldsHaveVisibleNamesAndPasswords() {
        val state = mutableStateOf(PinUiState(context, value = "123456"))
        show { PinScreen(state.value, {}) }
        fun field(tag: String, label: String) {
            compose.onNodeWithTag(tag).assertContentDescriptionEquals(label)
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)).assert(hasSetTextAction())
        }
        field("pin-input", "PIN")
        compose.runOnIdle { state.value = state.value.copy(stage = PinStage.NewPin, confirmation = "123456") }
        field("pin-input", "PIN")
        field("pin-confirm", "Type it again")
        compose.onNodeWithText("Choose a new PIN").assert(heading)
        compose.runOnIdle { state.value = state.value.copy(stage = PinStage.Recovery, value = "ABCD1234") }
        field("recovery-input", "Recovery code")
        compose.onNodeWithTag("recovery-input").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("••••••••")))
    }
    @Test fun wrongPinAndWaitHaveOnePoliteMessage() {
        val state = mutableStateOf(PinUiState(context))
        show { PinScreen(state.value, {}) }
        compose.onAllNodes(polite).assertCountEquals(0)
        compose.runOnIdle { state.value = state.value.copy(wrongPin = true) }
        compose.onNodeWithText("That PIN isn't right. Try again.").assert(polite)
        compose.onAllNodes(polite).assertCountEquals(1)
        compose.runOnIdle { state.value = state.value.copy(sending = true) }
        compose.onAllNodes(polite).assertCountEquals(1)
        compose.runOnIdle { state.value = state.value.copy(stage = PinStage.Locked, wait = "1 minute") }
        compose.onNode(hasText("Wait 1 minute", substring = true)).assert(polite)
        compose.onAllNodes(polite).assertCountEquals(1)
        compose.onNodeWithTag("pin-input").assertDoesNotExist()
    }
    @Test fun recoveryErrorsAndPinChangeResultsArePolite() {
        val state = mutableStateOf(PinUiState(context, PinStage.Recovery, wrongRecoveryCode = true))
        val changed = mutableStateOf<Boolean?>(null)
        show { changed.value?.let { PinChangeResultScreen(context, it) {} } ?: PinScreen(state.value, {}) }
        compose.onNode(hasText("That code isn't right", substring = true)).assert(polite)
        compose.runOnIdle { state.value = state.value.copy(stage = PinStage.RecoveryLocked, wait = "1 minute") }
        compose.onAllNodes(polite).assertCountEquals(1)
        compose.runOnIdle { state.value = state.value.copy(stage = PinStage.NewPin, mismatch = true) }
        compose.onAllNodes(polite).assertCountEquals(1)
        listOf(true, false).forEach { result ->
            compose.runOnIdle { changed.value = result }
            compose.onAllNodes(polite).assertCountEquals(1)
            action("OK")
        }
    }
    @Test fun menuRowsAndNeedsYouAreSingleButtons() {
        val events = mutableListOf<AdminMenuEvent>()
        show { AdminMenuScreen(AdminMenuUiState(context,
            listOf(AdminMenuItem(AdminDestination.Apps), AdminMenuItem(AdminDestination.Requests, "2"),
                AdminMenuItem(AdminDestination.RequestRecipient), AdminMenuItem(AdminDestination.ChangePin),
                AdminMenuItem(AdminDestination.NewRecoveryCode)), listOf(AdminNotice("requests", "2 requests"))), events::add) }
        compose.onNode(hasText("Your settings") and heading).assertExists()
        compose.onNodeWithText("Needs you").assert(heading)
        listOf("Apps on this phone", "Requests from ", "Who gets requests", "Change PIN", "New recovery code", "2 requests").forEach { label ->
            compose.onAllNodes(hasText(label) and hasClickAction()).assertCountEquals(1)
            action(label).performScrollTo().performClick()
        }
        assertEquals(6, events.size)
        noGlyphs()
    }
    @Test fun appRowsReorderNamesAndPositionsStayTogether() {
        val rows = mutableStateOf(listOf(AppOnPhone(camera, canMoveDown = true),
            AppOnPhone(InstalledApp("maps", "Maps", ""), canMoveUp = true)))
        show { AppsOnPhoneScreen(AppsOnPhoneUiState(context, rows.value, canAddHidden = true)) {
            if (it == AppsOnPhoneEvent.MoveDown("camera")) rows.value = rows.value.reversed()
        } }
        action("Camera").assertTextEquals("Camera").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1 of 2"))
        compose.onNodeWithContentDescription("Move Camera down").assert(button).performScrollTo().performClick()
        action("Camera").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2 of 2"))
        compose.onNodeWithContentDescription("Move Camera up").assert(button)
        action("Add a hidden app").performScrollTo().assertIsDisplayed()
        noGlyphs()
    }
    @Test fun appDetailsHiddenListAndFailuresHaveHeadingsAndActions() {
        val page = mutableIntStateOf(0)
        show { when(page.intValue) {
            0 -> PreviewAppDetailScreen(context, "Camera", "1", permissions = AppPermissionsUiState(checkFailed = true), onRemove = {})
            1 -> PreviewHiddenAppsScreen(context, listOf(HiddenAppRow("camera", "Camera")), false, false, {}, {})
            2 -> PreviewHiddenAppsScreen(context, emptyList(), true, false, {}, {})
            3 -> PreviewAppsFailureScreen(context) {}
            else -> AppPermissionFailureScreen(context, "Camera", emptyList(), AppPermissionsUiState(), {})
        } }
        compose.onNodeWithText("Camera").assert(heading)
        compose.onNodeWithText("What Camera can use").assert(heading)
        compose.onNodeWithText("Couldn't check").assert(polite)
        action("Remove Camera")
        compose.runOnIdle { page.intValue = 1 }
        action("Camera").assertTextEquals("Camera")
        noGlyphs()
        (2..4).forEach { index ->
            compose.runOnIdle { page.intValue = index }
            assertTrue(compose.onAllNodes(polite).fetchSemanticsNodes().isNotEmpty())
            assertTrue(compose.onAllNodes(heading).fetchSemanticsNodes().isNotEmpty())
        }
    }
    @Test fun selectedPermissionSpeaksChoiceWithoutTickOrDuplicateText() {
        show { PermissionsScreen(PermissionsUiState(context, "Camera", listOf(
            PermissionChoice("camera", PermissionGroup.Camera, decision = PermissionDecision.Allow)), previewAdd = true), {}) }
        compose.onNodeWithText("What can Camera use?").assert(heading)
        compose.onNodeWithContentDescription("Camera: Allow").assert(button).assertIsSelected()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        compose.onNodeWithContentDescription("Camera: Don't allow").assert(button).assertIsNotSelected()
        noGlyphs()
    }
    @Test fun appReviewWarningConfirmAndRemoveHaveOneActionStop() {
        val stage = mutableStateOf(AppReviewStage.Review)
        show { AppReviewScreen(AppReviewUiState(context, camera, stage = stage.value, previewSubset = true), {}) }
        listOf(AppReviewStage.Review to "Next", AppReviewStage.Unreviewed to "Add anyway",
            AppReviewStage.Confirm to "Add Camera", AppReviewStage.Remove to "Remove").forEach { (next, label) ->
            compose.runOnIdle { stage.value = next }
            action(label).assertIsDisplayed()
            assertTrue(compose.onAllNodes(heading).fetchSemanticsNodes().isNotEmpty())
            noGlyphs()
        }
    }
    @Test fun savedRequestRemovalAnnouncesFailureAndKeepsBothActions() {
        val state = mutableStateOf(RequestsUiState(historyOnly = true, rows = listOf(RequestUiRow("Bus times", "Thursday", RequestUiStatus.NotConfirmed, "one"))))
        val events = mutableListOf<RequestEvent>()
        show { RequestsScreen(state.value, events::add) }
        compose.onNodeWithText("Requests").assert(heading)
        action("Not now").performScrollTo().performClick()
        assertEquals(listOf(RequestEvent.NotNow("one")), events)
        compose.runOnIdle { state.value = state.value.copy(removingId = "one", removeFailed = true) }
        compose.onNodeWithText("The change could not be saved. Try again.").assert(polite)
        action("Keep it").performClick()
        action("Remove").performClick()
        noGlyphs()
    }
    @Test fun recipientNamesFieldsFailuresAndNavigation() {
        val state = mutableStateOf(RequestRecipientUiState(editing = true))
        show { RequestRecipientScreen(state.value, {}) }
        compose.onNodeWithText("Who gets requests").assert(heading)
        listOf("Name (optional)", "Phone number").forEach { label ->
            compose.onNode(hasContentDescription(label) and hasSetTextAction()).assertExists()
        }
        compose.runOnIdle { state.value = state.value.copy(failed = true) }
        compose.onNodeWithText("The change could not be saved. Try again.").assert(polite)
        compose.runOnIdle { state.value = RequestRecipientUiState(readFailed = true) }
        compose.onNodeWithText("Who gets requests could not be read. Check again later.").assert(polite)
        action("OK")
    }
    @Test fun issuedCodeIsSpelledOnceAndNotAutomaticallyAnnounced() {
        show { PinScreen(PinUiState(context, PinStage.RecoveryIssued, recoveryCode = "1234-ABCD"), {}) }
        compose.onNodeWithText("1 2 3 4, A B C D").assertExists()
        compose.onAllNodes(hasText("1 2 3 4, A B C D")).assertCountEquals(1)
        compose.onAllNodes(polite).assertCountEquals(0)
        action("I've written it down")
    }
}
