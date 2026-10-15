// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.view.KeyEvent
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.plainphone.policy.*
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.trusted.*

class HandoverAccessibilityTest {
    @get:Rule val compose = createComposeRule()
    @Test fun productionMappedItemsAreSingleStopsAndDoneWorksWithTabAndEnter() {
        var done = false
        val ui = handoverUiState(HandoverCheck.read { when (it) {
            HandoverItem.USB_DEBUGGING -> false
            HandoverItem.WIRELESS_DEBUGGING -> null
            else -> true
        } })
        compose.setContent { PlainphoneTheme { HandoverScreen(ui, { done = true }) } }
        compose.onNodeWithText("Check this phone").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        for (row in ui.items) {
            compose.onNodeWithTag("handover-${row.kind.name}").performScrollTo()
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).assertHasNoClickAction()
        }
        compose.onNodeWithContentDescription("USB debugging, needs the helper:", substring = true).assertExists()
        compose.onNodeWithContentDescription("Wireless debugging, Couldn't check:", substring = true).assertExists()
        listOf("✓", "›", "‹").forEach {
            compose.onAllNodes(hasText(it, substring = true)).assertCountEquals(0)
            compose.onAllNodes(hasContentDescription(it, substring = true)).assertCountEquals(0)
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        repeat(4) {
            if (!done) { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB); compose.waitForIdle()
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER); compose.waitForIdle() }
        }
        assertTrue(done)
    }
}
