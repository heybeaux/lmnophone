// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.trusted.*

/** Production mapper and the exact self-managed context used by PinAccessActivity; no authority effects. */
class PinSettingsProductionTalkBackTest {
    @get:Rule val compose = createComposeRule()
    @Test fun productionPinPagesRetainNamedSecretFieldsAndAnnounceErrors() {
        val page = mutableStateOf(PinPageState(PinPage.RECOVERY_PIN))
        val context = AdminContext(InstrumentationRegistry.getInstrumentation().targetContext
            .getString(org.plainphone.ui.R.string.current_phone_user))
        compose.setContent { PlainphoneTheme {
            PinScreen(pinUiState(page.value, context, "123456", "123456", "1234-ABCD", "1 minute", "", null), {})
        } }
        compose.onNodeWithTag("pin-input").assertContentDescriptionEquals("PIN")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.runOnIdle { page.value = PinPageState(PinPage.ENTER, wrong = true) }
        compose.onNodeWithText("That PIN isn't right. Try again.")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.runOnIdle { page.value = PinPageState(PinPage.RECOVERY) }
        compose.onNodeWithTag("recovery-input").assertContentDescriptionEquals("Recovery code")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.runOnIdle { page.value = PinPageState(PinPage.RECOVERY_ISSUED) }
        compose.onNodeWithText("1 2 3 4, A B C D").assertExists()
    }
    @Test fun productionMenuKeepsOnlyReachableRoutesAndEmergencyRemainsInMore() {
        val context = AdminContext(InstrumentationRegistry.getInstrumentation().targetContext
            .getString(org.plainphone.ui.R.string.current_phone_user))
        val more = mutableStateOf(false)
        compose.setContent { PlainphoneTheme {
            if (more.value) org.plainphone.ui.user.MoreScreen(org.plainphone.ui.user.MoreUiState(
                org.plainphone.ui.user.MoreMode.SelfManaged(10)), {})
            else AdminMenuScreen(pinMenuUiState(context, 2), {})
        } }
        listOf("Apps on this phone", "Requests from ${context.userName}", "Who gets requests", "Change PIN", "New recovery code").forEach {
            compose.onNode(hasText(it) and hasClickAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }
        compose.onNodeWithText("History").assertDoesNotExist()
        compose.runOnIdle { more.value = true }
        compose.onNodeWithContentDescription("Emergency 9-1-1", substring = true).assertHasClickAction()
    }
}
