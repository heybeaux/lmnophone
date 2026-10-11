/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.trusted.*

class TaskActionPresenceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun stillStoppingRemeasuresWhenOkAppearsUnderTheSameTitle() {
        val stage = mutableStateOf(WaitingStage.Stopping)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                // Short display makes the new footer cross the fit boundary at 200%.
                PlainphoneTheme { Box(Modifier.size(360.dp, 300.dp)) {
                    WaitingChangeScreen(WaitingChangeUiState(AdminContext("Pat", "Alexandria Catherine Montgomery"),
                        stage.value, "hand over", helper = "Jo"), {})
                } }
            }
        }
        compose.onNodeWithTag("task-pinned").assertExists()
        compose.onNodeWithText("OK").assertDoesNotExist()
        compose.runOnIdle { stage.value = WaitingStage.StillStopping }
        compose.onNodeWithTag("task-whole-page").assertExists()
        compose.onNodeWithText("OK").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { stage.value = WaitingStage.Stopping }
        compose.onNodeWithTag("task-pinned").assertExists()
        compose.onNodeWithText("OK").assertDoesNotExist()
    }
}
