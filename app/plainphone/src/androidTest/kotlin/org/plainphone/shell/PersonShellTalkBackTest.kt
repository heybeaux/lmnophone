// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.shell

import androidx.compose.runtime.mutableStateOf
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import java.time.ZonedDateTime
import java.util.Locale
import org.plainphone.core.Mode
import org.plainphone.counts.CommunicationCounts
import org.plainphone.policy.NoticeInput
import org.plainphone.ui.user.*

/** Runs the production ShellUiMapper AND shell count handoff, with no device effects. */
class PersonShellTalkBackTest {
    @get:Rule val compose = createComposeRule()
    @Test fun reliableCountsReachSpokenRowsAndDisappearWhenCleared() {
        val snapshot = PhoneSnapshot(ZonedDateTime.parse("2026-10-14T10:42:00Z"), "Alex", Mode.TRUSTED,
            emptyList(), 10, emptyList(), NoticeInput(Mode.TRUSTED))
        val counts = mutableStateOf(CommunicationCounts(2, 1))
        val steps = mutableListOf<Step>()
        compose.setContent {
            PlainphoneShell(state = ShellState(), snapshot = snapshot,
                mapper = ShellUiMapper(Locale.US, true), navigator = ShellNavigator({ snapshot }, "LMNOPhone"),
                counts = counts.value, about = AboutUiState("test", 1, null, emptyList(), emptyList()),
                isDeviceSecure = false, notice = null, onNotice = {}, settings = PhoneSettingsUiState(), onSettings = {},
                textSize = TextSizeUiState(), onTextSize = {}, wifi = WifiSettingsUiState(), wifiJoin = null,
                onWifiJoin = {}, onWifiShow = {}, onWifiSelect = {}, onWifiForget = {}, wifiForgetAsk = null,
                onWifiForgetAsk = {}, onWifiMessage = {}, bluetooth = BluetoothSettingsUiState(),
                discovery = BluetoothDiscoveryUiState(), onDiscovery = {}, onPairNew = {}, sound = SoundUiState(),
                onSound = {}, ringtone = RingtoneUiState(), onRingtone = {}, brightness = BrightnessUiState(50, false),
                battery = BatteryUiState(), onBrightness = {}, voice = VoiceUiState.ChoosePerson(emptyList()),
                onVoiceOpen = {}, onVoiceClose = {}, onVoice = {}, onStep = steps::add)
        }
        compose.onNodeWithContentDescription("Phone, 2 missed calls").assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("Messages, 1 unread text").assertHasClickAction().performClick()
        assertEquals(listOf(ShellEffect.OpenDialer, ShellEffect.OpenMessages), steps.map { it.effect })
        compose.onAllNodes(hasClickAction()).assertCountEquals(3)
        // The production shell and count mapper must also preserve keyboard reachability.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
        compose.onNodeWithContentDescription("Messages, 1 unread text")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
        compose.onNodeWithContentDescription("Phone, 2 missed calls").assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
        assertEquals(ShellEffect.OpenDialer, steps.last().effect)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
        compose.onNodeWithText("More").assertIsFocused()

        compose.runOnIdle { counts.value = CommunicationCounts(0, null) }
        compose.onNodeWithText("Phone").assertTextEquals("Phone").assertHasClickAction()
        compose.onNodeWithText("Messages").assertTextEquals("Messages").assertHasClickAction()
        compose.onNodeWithContentDescription("Phone, 2 missed calls").assertDoesNotExist()
        compose.onNodeWithContentDescription("Messages, 1 unread text").assertDoesNotExist()
    }
}
