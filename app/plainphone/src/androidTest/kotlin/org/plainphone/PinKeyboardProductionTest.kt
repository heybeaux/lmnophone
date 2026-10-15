// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.trusted.*

/** Uses the actual PinAccessActivity presentation mapper; no credential store or authority effects. */
class PinKeyboardProductionTest {
    @get:Rule val compose = createComposeRule()
    private val context = AdminContext("")
    private fun key(code: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code)
        compose.waitForIdle()
    }
    @Test fun everyProductionPinEntryUsesTheButtonGateForEnter() {
        val page = mutableStateOf(PinPageState(PinPage.ENTER))
        val value = mutableStateOf("")
        val confirmation = mutableStateOf("")
        val events = mutableListOf<PinEvent>()
        compose.setContent { PlainphoneTheme { Box(Modifier.size(360.dp, 560.dp)) {
            PinScreen(pinUiState(page.value, context, value.value, confirmation.value, "", "", "", null), events::add)
        } } }
        for (next in listOf(PinPage.ENTER, PinPage.CHANGE_OLD, PinPage.RECOVERY_PIN,
            PinPage.CHANGE_NEW, PinPage.RECOVERY_NEW, PinPage.RECOVERY, PinPage.SETUP)) {
            for (input in listOf("", "123", "123456")) {
                compose.runOnIdle {
                    page.value = PinPageState(next)
                    value.value = input
                    confirmation.value = input
                    events.clear()
                }
                val tag = when (next) {
                    PinPage.RECOVERY -> "recovery-input"
                    PinPage.SETUP, PinPage.CHANGE_NEW, PinPage.RECOVERY_NEW -> "pin-confirm"
                    else -> "pin-input"
                }
                val field = compose.onNodeWithTag(tag)
                field.performScrollTo().performClick()
                key(KeyEvent.KEYCODE_ENTER)
                val enabled = pinUiState(page.value, context, input, input, "", "", "", null).canContinue
                compose.runOnIdle { assertEquals("$next: Enter matches mapper eligibility", if (enabled) listOf(PinEvent.Continue) else emptyList(), events); events.clear() }
                val button = compose.onNode(hasText("Continue") and hasClickAction())
                if (enabled) button.performClick() else button.assertIsNotEnabled()
                compose.runOnIdle { assertEquals(if (enabled) listOf(PinEvent.Continue) else emptyList(), events) }
            }
        }
    }
    @Test fun productionPinMenuRowsAndDoneAllReceiveTabFocus() {
        compose.setContent { PlainphoneTheme { AdminMenuScreen(pinMenuUiState(context), {}) } }
        val expected = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.id }.toSet()
        key(KeyEvent.KEYCODE_TAB)
        compose.onAllNodes(hasClickAction())[0].performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        val reached = mutableSetOf<Int>()
        repeat(expected.size + 2) {
            compose.onAllNodes(isFocused()).fetchSemanticsNodes().forEach { reached += it.id }
            key(KeyEvent.KEYCODE_TAB)
        }
        assertTrue("Every production menu row and Done", reached.containsAll(expected))
    }
}
