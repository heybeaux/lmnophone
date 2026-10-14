// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.plainphone.ui.home.*
import org.plainphone.ui.user.*
import org.plainphone.ui.theme.PlainphoneTheme

@OptIn(ExperimentalTestApi::class)
class CommunicationCountsTest {
    @get:Rule val compose = createComposeRule()
    private val base = HomeUiState("10:42", "Thursday 8 October", "Thu 8 Oct",
        listOf(HomeApp("camera", "Camera")))
    @Test fun countVisibilityPluralsAndOneControlPerRow() {
        val state = mutableStateOf(base)
        val events = mutableListOf<HomeEvent>()
        compose.setContent { PlainphoneTheme { Box(Modifier.size(360.dp, 560.dp)) {
            HomeScreen(state.value, events::add)
        } } }
        listOf<Int?>(null, 0, 1, 3, 4, 99, 100).forEach { count ->
            compose.runOnIdle { state.value = base.copy(missedCalls = count, unreadTexts = count); events.clear() }
            val positive = count != null && count > 0
            val phone = if (positive) compose.onNodeWithContentDescription("Phone, $count missed ${if(count == 1) "call" else "calls"}") else compose.onNodeWithText("Phone")
            val messages = if (positive) compose.onNodeWithContentDescription("Messages, $count unread ${if(count == 1) "text" else "texts"}") else compose.onNodeWithText("Messages")
            phone.assertHasClickAction().performClick()
            messages.assertHasClickAction().performClick()
            compose.onNodeWithText("Camera").assertTextEquals("Camera").performClick()
            assertEquals(listOf(HomeEvent.OpenPhone, HomeEvent.OpenMessages, HomeEvent.OpenApp("camera")), events)
            compose.onAllNodes(hasClickAction()).assertCountEquals(4)
            listOf("home-phone", "home-messages").forEach { tag ->
                val texts = compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)) and
                    SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                    .fetchSemanticsNodes().map { node ->
                        val results = mutableListOf<TextLayoutResult>()
                        node.config[SemanticsActions.GetTextLayoutResult].action!!(results)
                        results.single().layoutInput.text.text
                    }
                assertEquals(if (positive) 2 else 1, texts.size)
                if (positive) assertEquals(if (count!! > 99) "99+" else count.toString(), texts.last())
            }
        }
    }
    @Test fun largeTextMatrixKeepsBothRowsAndTheirTextUnclipped() {
        data class Case(val scale: Float, val notice: Boolean, val count: Int?)
        val cases = listOf(1f, 1.3f, 1.5f, 1.8f, 2f).flatMap { scale ->
            listOf(false, true).flatMap { notice -> listOf<Int?>(null, 100).map { Case(scale, notice, it) } }
        }
        val current = mutableStateOf(cases.first())
        compose.setContent {
            val case = current.value
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(case.scale)) {
                PlainphoneTheme { Box(Modifier.size(360.dp, 560.dp)) {
                    HomeScreen(base.copy(notice = if(case.notice) HomeNotice.ModeChangeWaiting else null,
                        missedCalls = case.count, unreadTexts = case.count), {})
                } }
            }
        }
        cases.forEach { case ->
            compose.runOnIdle { current.value = case }
            compose.onNodeWithTag("home-whole-page").assertDoesNotExist()
            listOf("home-phone", "home-messages").forEach { tag ->
                val row = compose.onNodeWithTag(tag).assertIsDisplayed()
                val bounds = row.getUnclippedBoundsInRoot()
                assertTrue("$case $tag", bounds.top >= 0.dp && bounds.bottom <= 560.dp && bounds.bottom - bounds.top >= 56.dp)
                val textNodes = compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)) and
                    SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                assertTrue(textNodes.fetchSemanticsNodes().isNotEmpty())
                repeat(textNodes.fetchSemanticsNodes().size) { index ->
                    val results = mutableListOf<TextLayoutResult>()
                    textNodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                    // FlowRow can retain a full-width paragraph while Text reports its tight
                    // measured width. Check rendered line extents, not that unused paragraph width.
                    results.forEach { result ->
                        assertFalse("$case: vertical overflow", result.didOverflowHeight)
                        repeat(result.lineCount) { line ->
                            assertFalse("$case: ellipsized text", result.isLineEllipsized(line))
                            assertTrue("$case: line outside text bounds",
                                result.getLineLeft(line) >= 0f &&
                                result.getLineRight(line) <= result.size.width + 0.5f &&
                                result.getLineBottom(line) <= result.size.height + 0.5f)
                        }
                    }
                    val tb = textNodes[index].getUnclippedBoundsInRoot()
                    assertTrue("$case: text outside row", tb.left >= bounds.left && tb.right <= bounds.right && tb.top >= bounds.top && tb.bottom <= bounds.bottom)
                }
            }
        }
    }
    @Test fun settingsLargeTextCanRevealEachStatusWithoutTextOverflow() {
        val scale = mutableStateOf(1f)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale.value)) {
                PlainphoneTheme { Box(Modifier.size(360.dp, 560.dp)) {
                    PhoneSettingsScreen(PhoneSettingsUiState(true, true, true, unsupportedApps = true), {})
                } }
            }
        }
        listOf(1f, 1.3f, 1.5f, 1.8f, 2f).forEach { value ->
            compose.runOnIdle { scale.value = value }
            listOf("Show missed calls and unread texts on Home",
                "Counts texts and picture messages. Chat (RCS) messages and other apps aren't supported, so they may be missing from the number.",
                "LMNOPhone keeps permission to check calls and texts after you turn this off. It never reads what they say.",
                "LMNOPhone can't read missed calls right now", "LMNOPhone can't read unread texts right now",
                "Counts work only with the phone's standard Phone and Messages apps.")
                .forEach { label ->
                    val node = compose.onNodeWithText(label, useUnmergedTree = true)
                    node.performScrollTo().assertIsDisplayed()
                    val result = mutableListOf<TextLayoutResult>()
                    node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) }
                    assertTrue("$value: $label", result.all { !it.hasVisualOverflow })
                    val bounds = node.getUnclippedBoundsInRoot()
                    assertTrue(bounds.top >= 0.dp && bounds.bottom <= 560.dp)
                }
        }
    }

    @Test fun switchUsesHostStateAndMoreRoutesWithoutPin() {
        val state = mutableStateOf(PhoneSettingsUiState())
        val events = mutableListOf<PhoneSettingsEvent>()
        val settings = mutableStateOf(false)
        compose.setContent { PlainphoneTheme { Box(Modifier.size(360.dp, 560.dp)) {
            if (settings.value) PhoneSettingsScreen(state.value, events::add)
            else MoreScreen(MoreUiState(MoreMode.SelfManaged(1))) {
                if (it == MoreEvent.OpenPhoneSettings) settings.value = true
            }
        } } }
        compose.onNodeWithText("Phone settings").performClick()
        val switch = compose.onNodeWithText("Show missed calls and unread texts on Home")
        switch.assertIsOff().performClick().assertIsOff()
        assertEquals(listOf(PhoneSettingsEvent.ShowCommunicationCountsChanged(true)), events)
        compose.runOnIdle { state.value = PhoneSettingsUiState(true, true, true) }
        switch.assertIsOn().performClick()
        assertEquals(PhoneSettingsEvent.ShowCommunicationCountsChanged(false), events.last())
        compose.onNodeWithText("LMNOPhone can't read missed calls right now").assertExists()
        compose.onNodeWithText("LMNOPhone can't read unread texts right now").assertExists()
        compose.runOnIdle { state.value = PhoneSettingsUiState(false, true, true) }
        compose.onNodeWithText("LMNOPhone can't read", substring = true).assertDoesNotExist()
        compose.onNodeWithText("More").performClick()
        assertEquals(PhoneSettingsEvent.Back, events.last())
    }
}
