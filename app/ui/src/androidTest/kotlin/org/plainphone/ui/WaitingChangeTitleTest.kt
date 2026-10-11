// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.trusted.*

@OptIn(ExperimentalTestApi::class)
class WaitingChangeTitleTest {
    @get:Rule val compose = createComposeRule()

    private fun checkDetails(context: AdminContext?, turningOff: Boolean, title: String) {
        val events = mutableListOf<WaitingChangeEvent>()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                PlainphoneTheme {
                    Box(Modifier.size(360.dp, 560.dp)) {
                        WaitingChangeScreen(WaitingChangeUiState(
                            context = context, stage = WaitingStage.Details,
                            turningOff = turningOff,
                            change = if (turningOff) "turn LMNOPhone off" else "add Jo as a trusted person",
                            everyone = listOf("Sam"), starterName = "Sam",
                            day = "Thursday", startedAt = "10:40", happensAt = "10:40 tomorrow"
                        ), events::add)
                    }
                }
            }
        }
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(if (title == "A change is waiting") "LMNOPhone will turn off" else "A change is waiting")
            .assertDoesNotExist()
        compose.onNodeWithText("Cancel this change").assertIsDisplayed().performClick()
        assertEquals(listOf(WaitingChangeEvent.Cancel), events)
    }

    @Test fun userPendingTurnOffUsesU9aTitle() =
        checkDetails(null, true, "LMNOPhone will turn off")

    @Test fun otherUserChangeKeepsGenericTitle() =
        checkDetails(null, false, "A change is waiting")

    @Test fun trustedPendingTurnOffKeepsM3dTitle() =
        checkDetails(AdminContext("Alex", "Sam"), true, "A change is waiting")

    @Test fun selfManagedSettingsKeepGenericTitle() =
        checkDetails(AdminContext("Alex"), true, "A change is waiting")
}
