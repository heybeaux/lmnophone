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
import org.plainphone.ui.home.*
import org.plainphone.ui.requests.*
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.user.*

/** Synthetic UI only: callbacks record intent, never send or open a dialer. */
@OptIn(ExperimentalTestApi::class)
class PersonTalkBackTest {
    @get:Rule val compose = createComposeRule()
    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
    private val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
    private val person = VoicePersonUi("sam", "Sam")
    private fun mount(content: @Composable () -> Unit) {
        compose.setContent { PlainphoneTheme { Box(Modifier.size(360.dp, 560.dp)) { content() } } }
    }
    private fun row(text: String) = compose.onNode(hasText(text) and hasClickAction())
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    private fun noGlyphs() {
        compose.onAllNodes(SemanticsMatcher("Spoken decorative glyph") { node ->
            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text in listOf("›", "‹", "▶", "✓") }
        }).assertCountEquals(0)
    }
    @Test fun settingsRowsHaveWordsOnlyAndTitleIsHeading() {
        mount { PhoneSettingsScreen(PhoneSettingsUiState(pendingChange = "change the trusted people"), {}) }
        compose.onNode(hasText("Phone settings") and heading).assertExists()
        listOf("Wi-Fi", "Bluetooth", "Brightness", "Sound", "Text size", "Battery").forEach { row(it).assertTextEquals(it) }
        compose.onNodeWithContentDescription("A change is waiting, change the trusted people. Opens the change.")
            .assertHasClickAction().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        noGlyphs()
    }
    @Test fun soundRingtoneIsOneStopWithoutArrow() {
        mount { SoundScreen(SoundUiState(ringtoneTitle = "Chime"), {}) }
        row("Ringtone").assertTextEquals("Ringtone", "Chime")
        compose.onNode(hasText("Sound") and heading).assertExists()
        noGlyphs()
    }
    @Test fun voiceSentReadsWordsAndAnnouncesOneResult() {
        val state = mutableStateOf<VoiceUiState>(VoiceUiState.Sending(person))
        mount { VoicePracticeScreen(state.value, {}) }
        compose.runOnIdle { state.value = VoiceUiState.Sent(person) }
        compose.onNode(hasText("Sent to Sam") and heading and polite).assertExists()
        compose.onAllNodes(polite).assertCountEquals(1)
        row("Done").assertTextEquals("Done")
        noGlyphs()
    }
    @Test fun appRequestResultAnnouncesWithoutMark() {
        val state = mutableStateOf(RequestsUiState(isSetUp = true, busy = true, result = RequestUiStatus.Sending))
        mount { RequestsScreen(state.value, {}) }
        compose.runOnIdle { state.value = state.value.copy(busy = false, result = RequestUiStatus.Sent) }
        compose.onNode(heading and polite).assertExists()
        compose.onAllNodes(polite).assertCountEquals(1)
    }
    @Test fun legacyReceiptKeepsVisibleTickOutOfSpeech() {
        mount { AskForAppScreen(AskForAppUiState("Sam", requests = listOf(
            AppRequestRow("one", "Maps", "Today", AppRequestStatus.Delivered("Sam")),
            AppRequestRow("two", "Music", "Today", AppRequestStatus.Added))), {}) }
        compose.onNodeWithText("Sam's phone got it").assertExists()
        compose.onNodeWithText("Added").assertExists()
        noGlyphs()
    }
    @Test fun homeNoticeFamilyAndCountsAreSingleActions() {
        val events = mutableListOf<HomeEvent>()
        mount { HomeScreen(HomeUiState("10:42", "Thursday 8 October", "Thu 8 Oct",
            listOf(HomeApp("camera", "Camera")), HomeNotice.Cancelling,
            missedCalls = 2, unreadTexts = 1, showFamily = true), events::add) }
        row("Camera").assertTextEquals("Camera")
        compose.onNodeWithContentDescription("Phone, 2 missed calls").assertHasClickAction()
        compose.onNodeWithContentDescription("Messages, 1 unread text").assertHasClickAction()
        compose.onNodeWithTag("home-family").assertHasClickAction()
        compose.onNodeWithTag("home-notice").assert(polite).performClick()
        assertEquals(listOf(HomeEvent.OpenNotice), events)
        compose.onAllNodes(hasClickAction()).assertCountEquals(6)
        noGlyphs()
    }
    @Test fun moreEmergencyAndNavigationKeepOneActionEach() {
        val events = mutableListOf<MoreEvent>()
        mount { MoreScreen(MoreUiState(MoreMode.Trusted(emptyList(), ""), voicePractice = true, pinHolderOnly = true), events::add) }
        compose.onNode(hasText("More") and heading).assertExists()
        row("Phone settings").assertExists()
        row("Ask for an app").assertExists()
        compose.onNode(hasContentDescription("Emergency 9-1-1. Opens the dialer with 9-1-1 typed in.") and hasClickAction()).performClick()
        assertEquals(listOf(MoreEvent.OpenEmergencyDialer), events)
        noGlyphs()
    }
    @Test fun settingsFamilyAndAboutMatrixAtLargeText() {
        data class Page(val title: String, val last: String, val content: @Composable () -> Unit)
        val pages = listOf(
            Page("Phone settings", "Show missed calls and unread texts on Home") { PhoneSettingsScreen(PhoneSettingsUiState(), {}) },
            Page("Wi-Fi", "Alex network") { WifiSettingsScreen(WifiSettingsUiState(nearby = emptyList(), choices = listOf(WifiNearbyUi("a", "Alex network"))), {}) },
            Page("Bluetooth", "Pair a new device") { BluetoothSettingsScreen(BluetoothSettingsUiState(true, emptyList(), false), {}) },
            Page("Pair a new device", "Headphones") { BluetoothDiscoveryScreen(BluetoothDiscoveryUiState(devices = listOf(NearbyBluetoothOption("a", "Headphones"))), {}) },
            Page("Brightness", "Done") { BrightnessScreen(BrightnessUiState(50, false), {}) },
            Page("Sound", "Done") { SoundScreen(SoundUiState(ringtoneTitle = "Chime"), {}) },
            Page("Ringtone", "Done") { RingtoneScreen(RingtoneUiState(listOf(RingtoneOption("a", "Chime")), "a", canSave = true), {}) },
            Page("Text size", "Done") { TextSizeScreen(TextSizeUiState("150", true, true), {}) },
            Page("Battery", "‹ Phone settings") { BatteryScreen(BatteryUiState(50), {}) },
            Page("LMNOPhone", "OK") { AboutScreen(AboutUiState("test", 1, null, emptyList(), emptyList()), {}) },
            Page("Ask for an app", "OK") { RequestsScreen(RequestsUiState(), {}) },
            Page("Who do you want to talk to?", "Sam") { VoicePracticeScreen(VoiceUiState.ChoosePerson(listOf(person), fromHome = true, practice = false), {}) },
            Page("Sam", "Record") { VoicePracticeScreen(VoiceUiState.Person(person), {}) },
        )
        val page = mutableStateOf(pages.first())
        val scale = mutableStateOf(1.3f)
        mount { DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale.value)) { key(page.value.title) { page.value.content() } } }
        for (font in listOf(1.3f, 1.5f, 2f)) for (next in pages) {
            compose.runOnIdle { scale.value = font; page.value = next }
            compose.onNode(hasText(next.title) and heading).assertExists()
            val last = compose.onNode(hasText(next.last) and hasClickAction())
            last.performScrollTo().assertIsDisplayed()
            val bounds = last.getUnclippedBoundsInRoot()
            assertTrue("${next.title} at $font: last action below safe area", bounds.bottom <= 560.dp && bounds.top >= 0.dp)
            noGlyphs()
        }
    }
    @Test fun ringtoneSelectionIsSpokenAsState() {
        mount { RingtoneScreen(RingtoneUiState(listOf(RingtoneOption("a", "Chime"), RingtoneOption("b", "Bell")), "a", canSave = true), {}) }
        compose.onNodeWithText("Chime").assertIsSelected().assertTextEquals("Chime")
        compose.onNodeWithText("Bell").assertIsNotSelected()
        noGlyphs()
    }
}
