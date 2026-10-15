// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
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
    private fun key(key: Key) = compose.onNode(isFocused()).performKeyInput { pressKey(key) }
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
}
