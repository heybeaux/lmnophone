// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import androidx.compose.ui.semantics.*
import org.plainphone.ui.home.*
import org.plainphone.ui.user.*
import org.plainphone.ui.requests.*
import org.junit.Rule
import org.junit.Test
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.trusted.*

@OptIn(ExperimentalTestApi::class)
class KeyboardAccessTest {
    @get:Rule val compose = createComposeRule()
    private val context = AdminContext("")
    private fun show(content: @Composable () -> Unit) = compose.setContent {
        PlainphoneTheme { Box(Modifier.size(360.dp, 560.dp)) { content() } }
    }
    private fun key(key: Key) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(key.nativeKeyCode)
        compose.waitForIdle()
    }
    private fun action(text: String) = compose.onNode(hasText(text) and hasClickAction())

    @Test fun enterAndImeSubmitExactlyLikeContinueAndRespectHostEligibility() {
        val state = mutableStateOf(PinUiState(context, value = "123456", canContinue = true))
        val events = mutableListOf<PinEvent>()
        show { PinScreen(state.value, events::add) }
        val field = compose.onNodeWithTag("pin-input")
        field.performClick()
        key(Key.Enter)
        compose.runOnIdle { assertEquals(listOf(PinEvent.Continue), events); events.clear() }
        field.performImeAction()
        compose.runOnIdle { assertEquals(listOf(PinEvent.Continue), events); events.clear() }
        action("Continue").performClick()
        compose.runOnIdle { assertEquals(listOf(PinEvent.Continue), events); events.clear() }
        for (value in listOf("", "123", "123456")) {
            compose.runOnIdle { state.value = state.value.copy(value = value, canContinue = false) }
            field.performClick()
            key(Key.Enter)
            field.performImeAction()
            compose.runOnIdle { assertEquals(emptyList<PinEvent>(), events) }
        }
    }
    @Test fun tabFromPinReachesContinueThenForgotAndEnterActivates() {
        val events = mutableListOf<PinEvent>()
        show { PinScreen(PinUiState(context, value = "123456", canContinue = true), events::add) }
        compose.onNodeWithTag("pin-input").performClick()
        key(Key.Tab)
        action("Continue").assertIsFocused()
        key(Key.Enter)
        compose.runOnIdle { assertEquals(listOf(PinEvent.Continue), events) }
        key(Key.Tab)
        action("Forgot PIN? Use recovery code").assertIsFocused()
        key(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(listOf(PinEvent.Continue, PinEvent.UseRecoveryCode), events) }
    }
    @Test fun newPinNextMovesToConfirmationAndDoneUsesHostGate() {
        val state = mutableStateOf(PinUiState(context, PinStage.NewPin, value = "123456", confirmation = "123456", canContinue = true))
        val events = mutableListOf<PinEvent>()
        show { PinScreen(state.value, events::add) }
        compose.onNodeWithTag("pin-input").performClick().performImeAction()
        compose.onNodeWithTag("pin-confirm").assertIsFocused()
        key(Key.Enter)
        compose.runOnIdle { assertEquals(listOf(PinEvent.Continue), events); events.clear(); state.value = state.value.copy(canContinue = false) }
        compose.onNodeWithTag("pin-confirm").performImeAction()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }
    @Test fun recipientNextAndDoneRespectSaveEligibility() {
        val state = mutableStateOf(RequestRecipientUiState(editing = true, name = "Al", number = "555-0100", canSave = true))
        val events = mutableListOf<RecipientEvent>()
        show { RequestRecipientScreen(state.value, events::add) }
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performScrollTo().performClick().performImeAction()
        fields[1].assertIsFocused()
        key(Key.Enter)
        compose.runOnIdle { assertEquals(listOf(RecipientEvent.Continue), events); events.clear(); state.value = state.value.copy(canSave = false) }
        fields[1].performImeAction()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }
    @Test fun familyNameNextAndNumberDoneRespectContinueEligibility() {
        val state = mutableStateOf(FamilyUiState(context, FamilyStage.Name, name = "Al", spokenName = "Al", canContinue = true))
        val events = mutableListOf<FamilyEvent>()
        show { FamilyScreen(state.value, events::add) }
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performScrollTo().performClick().performImeAction()
        fields[1].assertIsFocused()
        fields[1].performImeAction()
        compose.runOnIdle { assertEquals(listOf(FamilyEvent.Continue), events); events.clear(); state.value = state.value.copy(canContinue = false) }
        fields[1].performImeAction()
        compose.runOnIdle { assertTrue(events.isEmpty()); state.value = state.value.copy(stage = FamilyStage.Number, number = "555-0100", canContinue = true) }
        compose.onNode(hasSetTextAction()).performScrollTo().performClick()
        key(Key.Enter)
        compose.runOnIdle { assertEquals(listOf(FamilyEvent.Continue), events) }
    }
    @Test fun wifiDoneUsesConnectAndBusyCannotConnectAgain() {
        val state = mutableStateOf(WifiJoinUiState("Test network", WifiJoinKind.Personal, password = "test-only"))
        val events = mutableListOf<WifiJoinEvent>()
        show { WifiJoinScreen(state.value, events::add) }
        compose.onNode(hasSetTextAction()).performClick().performImeAction()
        compose.runOnIdle { assertEquals(listOf(WifiJoinEvent.Connect), events); events.clear(); state.value = state.value.copy(joining = true) }
        key(Key.Enter)
        compose.runOnIdle { assertFalse(events.contains(WifiJoinEvent.Connect)) }
    }
    @Test fun updateGoUsesContinueAndCannotBypassDisabledButton() {
        val state = mutableStateOf(UpdateUiState(context, stage = UpdateStage.Address, helper = "Alex", canContinue = true))
        val events = mutableListOf<UpdateEvent>()
        show { UpdateScreen(state.value, events::add) }
        compose.onNode(hasSetTextAction()).performScrollTo().performClick().performImeAction()
        compose.runOnIdle { assertEquals(listOf(UpdateEvent.Continue), events); events.clear(); state.value = state.value.copy(canContinue = false) }
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }
    @Test fun enterInEitherRequestMessageAddsNewlineWithoutSending() {
        val legacy = mutableStateOf(false)
        val request = mutableStateOf(RequestsUiState(isSetUp = true, message = "Maps", canSend = true))
        val oldRequest = mutableStateOf(AskForAppUiState("Al", message = "Maps", canSend = true))
        var sends = 0
        show {
            if (legacy.value) AskForAppScreen(oldRequest.value) {
                when (it) {
                    is AskForAppEvent.MessageChanged -> oldRequest.value = oldRequest.value.copy(message = it.value)
                    AskForAppEvent.Send -> sends++
                    else -> Unit
                }
            } else RequestsScreen(request.value) {
                when (it) {
                    is RequestEvent.Message -> request.value = request.value.copy(message = it.text)
                    RequestEvent.Send -> sends++
                    else -> Unit
                }
            }
        }
        for (old in listOf(false, true)) {
            compose.runOnIdle { legacy.value = old }
            compose.onNode(hasSetTextAction()).performScrollTo().performClick()
            key(Key.Enter)
            compose.runOnIdle {
                assertEquals(0, sends)
                assertTrue((if (old) oldRequest.value.message else request.value.message).contains('\n'))
            }
        }
    }

    /** Native keys exercise touch-to-keyboard input mode, scroll-to-focus and button activation.
     * All callbacks are inert event collectors; even the emergency row cannot open a dialer. */
    @Test fun tabAndDpadReachEveryEnabledActionAcrossProductionScreens() {
        data class Page(val name: String, val content: @Composable ((Any) -> Unit) -> Unit)
        val person = VoicePersonUi("al", "Al")
        val pages = listOf(
            Page("home") { e -> HomeScreen(HomeUiState("10:42", "Thursday 8 October", "Thu 8 Oct", listOf(HomeApp("camera", "Camera")), missedCalls = 2, unreadTexts = 1, showFamily = true), e) },
            Page("more") { e -> MoreScreen(MoreUiState(MoreMode.Trusted(emptyList(), ""), voicePractice = true, pinHolderOnly = true), e) },
            Page("settings") { e -> PhoneSettingsScreen(PhoneSettingsUiState(), e) },
            Page("about") { e -> AboutScreen(AboutUiState("test", 62, null, emptyList(), emptyList()), e) },
            Page("ask") { e -> RequestsScreen(RequestsUiState(isSetUp = true, message = "Maps", canSend = true), e) },
            Page("family") { e -> VoicePracticeScreen(VoiceUiState.ChoosePerson(listOf(person), fromHome = true, practice = false), e) },
            Page("person") { e -> VoicePracticeScreen(VoiceUiState.Person(person, openMessages = true, listen = VoiceUiListen.Listen), e) },
            Page("pin-menu") { e -> AdminMenuScreen(AdminMenuUiState(context, listOf(AdminMenuItem(AdminDestination.Apps), AdminMenuItem(AdminDestination.RequestRecipient), AdminMenuItem(AdminDestination.ChangePin), AdminMenuItem(AdminDestination.NewRecoveryCode))), e) },
            Page("apps") { e -> AppsOnPhoneScreen(AppsOnPhoneUiState(context, listOf(AppOnPhone(InstalledApp("camera", "Camera", ""))), canAddHidden = true), e) },
            Page("recovery") { e -> PinScreen(PinUiState(context, PinStage.Recovery, value = "test-code", canContinue = true), e) },
            Page("issued") { e -> PinScreen(PinUiState(context, PinStage.RecoveryIssued, recoveryCode = "TEST-CODE"), e) },
            Page("change-result") { e -> PinChangeResultScreen(context, true) { e(PinEvent.Ok) } },
            Page("recipient") { e -> RequestRecipientScreen(RequestRecipientUiState(editing = true, name = "Al", number = "555-0100", canSave = true), e) },
            Page("family-settings") { e -> FamilyScreen(FamilyUiState(context, enabled = true, people = listOf(FamilyPersonRow("al", "Al"))), e) },
        )
        val current = mutableIntStateOf(0)
        val events = mutableListOf<Any>()
        show { key(current.intValue) { pages[current.intValue].content(events::add) } }
        for ((index, page) in pages.withIndex()) {
            compose.runOnIdle { current.intValue = index; events.clear() }
            val enabledAction = hasClickAction() and isEnabled() and SemanticsMatcher.keyNotDefined(SemanticsProperties.EditableText)
            val expectedOrder = compose.onAllNodes(enabledAction).fetchSemanticsNodes().map { it.id }
            val expected = expectedOrder.toSet()
            for (direction in listOf(Key.Tab, Key.DirectionDown)) {
                // Enter keyboard mode first. Focus the first item without a click or effect.
                key(Key.Tab)
                compose.onAllNodes(enabledAction)[0].performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                val reached = linkedSetOf<Int>()
                repeat(expected.size * 3 + 3) {
                    compose.onAllNodes(isFocused()).fetchSemanticsNodes().forEach { reached += it.id }
                    if (!reached.containsAll(expected)) {
                        // Multi-line editors reserve arrows for cursor movement; Tab leaves them.
                        val editing = compose.onAllNodes(isFocused() and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
                        key(if (editing && page.name == "ask") Key.Tab else direction)
                    }
                }
                assertTrue("${page.name} $direction unreachable actions: " + compose.onAllNodes(enabledAction).fetchSemanticsNodes().filter { it.id !in reached }.map { it.config }, reached.containsAll(expected))
                if (direction == Key.Tab) assertEquals("${page.name}: Tab order", expectedOrder, reached.filter { it in expected })
            }
            // Activate every reachable action with both activation keys. No business effects run.
            for (nodeId in expected) {
                val node = compose.onNode(SemanticsMatcher("id=$nodeId") { it.id == nodeId })
                node.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                node.assertIsFocused()
                val before = events.size
                key(Key.Enter)
                if (page.name != "about") assertEquals("${page.name}: Enter", before + 1, events.size)
                key(Key.DirectionCenter)
                if (page.name != "about") assertEquals("${page.name}: centre", before + 2, events.size)
            }
        }
    }

}
