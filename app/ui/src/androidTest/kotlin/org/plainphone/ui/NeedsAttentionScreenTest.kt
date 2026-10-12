// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.plainphone.ui.home.*
import org.plainphone.ui.theme.PlainphoneTheme

@OptIn(ExperimentalTestApi::class)
class NeedsAttentionScreenTest {
    @get:Rule val compose = createComposeRule()
    private val state = mutableStateOf(NeedsAttentionUiState())
    private val scale = mutableStateOf(2f)
    private val events = mutableListOf<NeedsAttentionEvent>()
    private val kinds = listOf(
        PendingChangeKind.TurnOff("21:15", HomeNoticeDay(LocalDate.of(2026, 10, 12), 1)),
        PendingChangeKind.TurnOff("11:59 PM", HomeNoticeDay(LocalDate.of(2026, 10, 22), 11)),
        PendingChangeKind.People, PendingChangeKind.Setup,
    )

    private fun show() = compose.setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale.value)) {
            PlainphoneTheme {
                // 360 x 640 dp with the module's 32 dp status + 48 dp navigation reserve.
                Box(Modifier.size(360.dp, 560.dp)) {
                    NeedsAttentionScreen(state.value, events::add)
                }
            }
        }
    }

    @Test fun cancelAndOkAreFullyVisibleWithoutScrollingAcrossScalesAndKinds() {
        show()
        for (fontScale in listOf(1f, 1.3f, 1.5f, 1.8f, 2f)) for (kind in kinds) {
            compose.runOnIdle {
                scale.value = fontScale
                state.value = NeedsAttentionUiState(PendingChange(kind))
                events.clear()
            }
            compose.onNodeWithTag("task-pinned").assertExists()
            for (label in listOf("Cancel this change", "OK")) {
                val node = compose.onNodeWithText(label).assertIsDisplayed().fetchSemanticsNode()
                assertTrue("$label clipped at $fontScale for $kind",
                    node.boundsInRoot.height >= node.layoutInfo.coordinates.size.height - 1)
                compose.onNodeWithText(label).performClick()
            }
            assertEquals(listOf(NeedsAttentionEvent.CancelChange, NeedsAttentionEvent.Back), events)
        }
    }

    @Test fun hostTransitionToCommittingRemovesCancelAndWaitingPromise() {
        show()
        for (kind in kinds) {
            compose.runOnIdle { state.value = NeedsAttentionUiState(PendingChange(kind)) }
            compose.onNodeWithText("Cancel this change").assertIsDisplayed()
            compose.runOnIdle { state.value = NeedsAttentionUiState(PendingChange(kind, committing = true)) }
            compose.onNodeWithText("Cancel this change").assertDoesNotExist()
            compose.onNodeWithText("Tap to cancel", substring = true).assertDoesNotExist()
            compose.onNodeWithText("Already in progress").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("OK").assertIsDisplayed().performClick()
        }
        assertEquals(List(kinds.size) { NeedsAttentionEvent.Back }, events)
    }

    @Test fun noPendingShowsReassuranceAndOnlyBack() {
        show()
        compose.onNodeWithText("LMNOPhone needs attention").assertIsDisplayed()
        compose.onNodeWithText("Calls, texts and Emergency still work.").assertIsDisplayed()
        compose.onNodeWithText("Cancel this change").assertDoesNotExist()
        compose.onNodeWithText("Already in progress").assertDoesNotExist()
        compose.onNodeWithText("OK").assertIsDisplayed().performClick()
        assertEquals(listOf(NeedsAttentionEvent.Back), events)
    }
}
