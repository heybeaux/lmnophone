/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.trusted.*

/** Uses the emulator's actual IME and Android key events, not performTextInput. */
@RunWith(Parameterized::class)
class TaskKeyboardTest(private val stage: PinStage, private val twoPeople: Boolean) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun imeVisible() = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
        ?.isVisible(WindowInsetsCompat.Type.ime()) == true

    @Test fun keyboardKeepsLayoutFocusAndInput() {
        val state = mutableStateOf(PinUiState(AdminContext("Pat", "Alexandria Catherine Montgomery"), stage,
            otherName = if (twoPeople) "Christopher Alexander Worthington" else null))
        compose.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                PlainphoneTheme { Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                    PinScreen(state.value) { event -> state.value = when (event) {
                        is PinEvent.ValueChanged -> state.value.copy(value = event.value)
                        is PinEvent.ConfirmationChanged -> state.value.copy(confirmation = event.value)
                        else -> state.value
                    } }
                } }
            }
        }
        val pinned = compose.onAllNodesWithTag("task-pinned").fetchSemanticsNodes().isNotEmpty()
        val layoutTag = if (pinned) "task-pinned" else "task-whole-page"
        val tags = when (stage) {
            PinStage.Recovery -> listOf("recovery-input")
            PinStage.NewPin -> listOf("pin-input", "pin-confirm")
            else -> listOf("pin-input")
        }
        tags.forEach { tag ->
            compose.onNodeWithTag(tag).performScrollTo().performClick()
            compose.waitUntil(timeoutMillis = 5_000) { imeVisible() }
            // Allow the real IME inset animation to finish before checking its lasting state.
            InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 3_000)
            compose.onNodeWithTag(layoutTag).assertExists()
            compose.onNodeWithTag(tag).assertIsFocused()
            org.junit.Assert.assertTrue("IME must remain visible", imeVisible())
            InstrumentationRegistry.getInstrumentation().sendStringSync("123")
            compose.waitForIdle()
            assertEquals("123", if (tag == "pin-confirm") state.value.confirmation else state.value.value)
            compose.onNodeWithTag(tag).assertIsFocused()
            compose.onNodeWithTag(layoutTag).assertExists()
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "stage={0},two={1}")
        fun cases() = listOf(PinStage.Enter, PinStage.Recovery, PinStage.NewPin).flatMap { stage ->
            listOf(false, true).map { arrayOf<Any>(stage, it) }
        }
    }
}
