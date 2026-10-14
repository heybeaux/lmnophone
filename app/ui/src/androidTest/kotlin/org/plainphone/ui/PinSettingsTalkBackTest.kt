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
    @Test fun wrongPinHasOnePoliteMessage() {
        val state = mutableStateOf(PinUiState(context))
        show { PinScreen(state.value, {}) }
        compose.onAllNodes(polite).assertCountEquals(0)
        compose.runOnIdle { state.value = state.value.copy(wrongPin = true) }
        compose.onNodeWithText("That PIN isn't right. Try again.").assert(polite)
        compose.onAllNodes(polite).assertCountEquals(1)
        compose.runOnIdle { state.value = state.value.copy(sending = true) }
        compose.onAllNodes(polite).assertCountEquals(1)
    }
    @Test fun tickingWaitsKeepOnlyTheStableTitlePolite() {
        val state = mutableStateOf(PinUiState(context))
        show { PinScreen(state.value, {}) }
        val liveRegion = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)
        val contexts = listOf(context to null, AdminContext("Alex", "Sam") to "Jo")
        contexts.forEach { (admin, other) ->
            listOf(PinStage.Locked, PinStage.RecoveryLocked).forEach { stage ->
                listOf("45 seconds" to "44 seconds", "0:45" to "0:44").forEach { (before, after) ->
                    compose.runOnIdle {
                        state.value = PinUiState(admin, stage, wait = before, pinWait = before,
                            otherName = other, recoveryLockout = RecoveryLockoutKind.PinWaiting)
                    }
                    fun checkCountdown(wait: String) {
                        compose.onAllNodes(hasText(wait, substring = true), useUnmergedTree = true)
                            .fetchSemanticsNodes().also { assertTrue(it.isNotEmpty()) }.forEach { node ->
                                assertFalse(node.config.contains(SemanticsProperties.LiveRegion))
                            }
                        compose.onAllNodes(liveRegion).assertCountEquals(1)
                        compose.onNode(polite and heading).assertExists()
                        compose.onNodeWithTag("pin-input").assertDoesNotExist()
                        compose.onNodeWithTag("recovery-input").assertDoesNotExist()
                    }
                    checkCountdown(before)
                    val title = compose.onNode(polite and heading).fetchSemanticsNode()
                    compose.runOnIdle { state.value = state.value.copy(wait = after, pinWait = after) }
                    checkCountdown(after)
                    compose.onAllNodes(hasText(before, substring = true)).assertCountEquals(0)
                    val afterTitle = compose.onNode(polite and heading).fetchSemanticsNode()
                    assertEquals(title.id, afterTitle.id)
                    assertEquals(title.config[SemanticsProperties.Text], afterTitle.config[SemanticsProperties.Text])
                }
            }
        }
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
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2 of 2"))
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
    @Test fun completedAppChangesAnnounceTheirResultOnce() {
        val stage = mutableStateOf(AppReviewStage.Confirm)
        show { AppReviewScreen(AppReviewUiState(context, camera, stage = stage.value, previewSubset = true), {}) }
        compose.onAllNodes(polite).assertCountEquals(0)
        listOf(AppReviewStage.Added, AppReviewStage.Removed).forEach { result ->
            compose.runOnIdle { stage.value = result }
            compose.onAllNodes(polite and heading).assertCountEquals(1)
            action("OK")
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
        compose.runOnIdle { state.value = RequestRecipientUiState(name = "Sam", number = "555-0100") }
        compose.onNodeWithText("Sam").assertExists()
        action("Change")
        action("Remove")
        noGlyphs()
    }
    @Test fun issuedCodeIsSpelledOnceAndNotAutomaticallyAnnounced() {
        show { PinScreen(PinUiState(context, PinStage.RecoveryIssued, recoveryCode = "1234-ABCD"), {}) }
        compose.onNodeWithText("1 2 3 4, A B C D").assertExists()
        compose.onAllNodes(hasText("1 2 3 4, A B C D")).assertCountEquals(1)
        compose.onAllNodes(polite).assertCountEquals(0)
        action("I've written it down")
    }
    @Test fun allChangedPathsFitAtLargeTextAndLastActionClearsNavigation() {
        data class Page(val name: String, val last: String, val content: @Composable () -> Unit)
        val pages = listOf(
            Page("enter", "Continue") { PinScreen(PinUiState(context), {}) },
            Page("wrong", "Continue") { PinScreen(PinUiState(context, wrongPin = true), {}) },
            Page("wait", "Use recovery code") { PinScreen(PinUiState(context, PinStage.Locked, wait = "1 minute"), {}) },
            Page("recovery", "Continue") { PinScreen(PinUiState(context, PinStage.Recovery, wrongRecoveryCode = true), {}) },
            Page("recovery-wait", "Enter PIN instead") { PinScreen(PinUiState(context, PinStage.RecoveryLocked, wait = "1 minute"), {}) },
            Page("new-pin", "Continue") { PinScreen(PinUiState(context, PinStage.NewPin, mismatch = true), {}) },
            Page("code", "I've written it down") { PinScreen(PinUiState(context, PinStage.RecoveryIssued, recoveryCode = "1234-ABCD-5678-EFGH-9012-IJKL-3456-MNOP"), {}) },
            Page("change-result", "OK") { PinChangeResultScreen(context, false) {} },
            Page("menu", "Done – back to the home screen") { AdminMenuScreen(AdminMenuUiState(context,
                listOf(AdminMenuItem(AdminDestination.Apps), AdminMenuItem(AdminDestination.Requests),
                    AdminMenuItem(AdminDestination.RequestRecipient), AdminMenuItem(AdminDestination.ChangePin),
                    AdminMenuItem(AdminDestination.NewRecoveryCode)), listOf(AdminNotice("request", "2 requests"))), {}) },
            Page("apps", "Go back") { AppsOnPhoneScreen(AppsOnPhoneUiState(context,
                listOf(AppOnPhone(camera), AppOnPhone(InstalledApp("phone", "Phone", ""), alwaysOn = true)), canAddHidden = true), {}) },
            Page("details", "Remove Camera") { PreviewAppDetailScreen(context, "Camera", "1", permissions = AppPermissionsUiState(checkFailed = true), onRemove = {}) },
            Page("hidden", "Go back") { PreviewHiddenAppsScreen(context, listOf(HiddenAppRow("camera", "Camera")), false, false, {}, {}) },
            Page("hidden-error", "Go back") { PreviewHiddenAppsScreen(context, emptyList(), true, false, {}, {}) },
            Page("remove-failed", "Go back") { PreviewAppsFailureScreen(context) {} },
            Page("review", "Next") { AppReviewScreen(AppReviewUiState(context, camera, previewSubset = true), {}) },
            Page("warning", "Add anyway") { AppReviewScreen(AppReviewUiState(context, camera, stage = AppReviewStage.Unreviewed, previewSubset = true), {}) },
            Page("confirm", "Add Camera") { AppReviewScreen(AppReviewUiState(context, camera, stage = AppReviewStage.Confirm, previewSubset = true), {}) },
            Page("added", "OK") { AppReviewScreen(AppReviewUiState(context, camera, stage = AppReviewStage.Added, previewSubset = true), {}) },
            Page("removed", "OK") { AppReviewScreen(AppReviewUiState(context, camera, stage = AppReviewStage.Removed, previewSubset = true), {}) },
            Page("hidden-result", "OK") { AppReviewScreen(AppReviewUiState(context, camera, stage = AppReviewStage.Hidden, previewSubset = true), {}) },
            Page("remove", "Remove") { AppReviewScreen(AppReviewUiState(context, camera, stage = AppReviewStage.Remove, previewSubset = true), {}) },
            Page("permissions", "Next") { PermissionsScreen(PermissionsUiState(context, "Camera", listOf(PermissionChoice("camera", PermissionGroup.Camera, decision = PermissionDecision.Allow)), previewAdd = true), {}) },
            Page("permission-failed", "Choose again") { AppPermissionFailureScreen(context, "Camera", emptyList(), AppPermissionsUiState(), {}) },
            Page("requests", "OK") { RequestsScreen(RequestsUiState(historyOnly = true, rows = listOf(RequestUiRow("Bus times", "Thursday", RequestUiStatus.NotConfirmed, "one"))), {}) },
            Page("request-remove", "Remove") { RequestsScreen(RequestsUiState(historyOnly = true, removingId = "one", removeFailed = true), {}) },
            Page("recipient", "Set up") { RequestRecipientScreen(RequestRecipientUiState(), {}) },
            Page("recipient-saved", "Change") { RequestRecipientScreen(RequestRecipientUiState(name = "Sam", number = "555-0100"), {}) },
            Page("recipient-edit", "Continue") { RequestRecipientScreen(RequestRecipientUiState(editing = true), {}) },
            Page("recipient-confirm", "Save") { RequestRecipientScreen(RequestRecipientUiState(confirming = true, name = "Sam", number = "555-0100"), {}) },
            Page("recipient-remove", "Remove") { RequestRecipientScreen(RequestRecipientUiState(removing = true), {}) },
            Page("recipient-error", "OK") { RequestRecipientScreen(RequestRecipientUiState(readFailed = true), {}) }
        )
        val current = mutableIntStateOf(0)
        val scale = mutableFloatStateOf(1.3f)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale.floatValue)) {
                PlainphoneTheme { Box(Modifier.size(360.dp, 560.dp)) {
                    key(current.intValue, scale.floatValue) { pages[current.intValue].content() }
                } }
            }
        }
        listOf(1.3f, 1.5f, 2f).forEach { fontScale -> pages.forEachIndexed { index, page ->
            compose.runOnIdle { scale.floatValue = fontScale; current.intValue = index }
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                .fetchSemanticsNodes().forEach { node ->
                    val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                    results.forEach { result ->
                        // Paragraph alignment uses the available width; compare the line span with the
                        // wrap-content box, allowing its integer-pixel rounding.
                        assertFalse("${page.name} at $fontScale: height overflow", result.didOverflowHeight)
                        (0 until result.lineCount).forEach { line ->
                            assertFalse("${page.name}: ellipsis", result.isLineEllipsized(line))
                            assertTrue("${page.name} at $fontScale: ${result.layoutInput.text} right=${result.getLineRight(line)} width=${result.size.width}",
                                result.getLineRight(line) - result.getLineLeft(line) <= result.size.width + 1f)
                        }
                    }
                }
            assertTrue("${page.name}: heading", compose.onAllNodes(heading).fetchSemanticsNodes().isNotEmpty())
            noGlyphs()
            val lastBody = when (page.name) {
                "menu" -> action("New recovery code")
                "apps" -> action("Add a hidden app")
                "hidden" -> action("Camera")
                "permissions" -> compose.onNodeWithContentDescription("Camera: Don't allow")
                "recipient-edit" -> compose.onNode(hasContentDescription("Phone number") and hasSetTextAction())
                else -> null
            }
            lastBody?.performScrollTo()?.assertIsDisplayed()
            val last = action(page.last)
            if (!last.isDisplayed()) last.performScrollTo()
            last.assertIsDisplayed()
            val bounds = last.fetchSemanticsNode().boundsInRoot
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue("${page.name}: action fits safe viewport", bounds.bottom <= root.bottom)
            // Test host reserves the same 80 dp for system bars as the 360 x 640 goldens.
            val density = compose.density.density
            assertTrue("${page.name}: footer below spare navigation boundary", last.fetchSemanticsNode().boundsInWindow.bottom < 2280)
            println("PIN_LAYOUT ${page.name} scale=$fontScale bottomDp=${bounds.bottom / density} safeBottomDp=${root.bottom / density} bottomWindowPx=${last.fetchSemanticsNode().boundsInWindow.bottom}")
        } }
    }

}
