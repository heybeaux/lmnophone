// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.requests.*
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.trusted.*

@RunWith(Parameterized::class)
class CopyPassThreeTest(private val scale: Float) {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalTestApi::class)
    @Test fun selfManagedRemoveLabelAndConfirmationAgree() {
        val context = AdminContext("the person using this phone")
        val app = InstalledApp("camera", "Camera", "")
        var confirm by mutableStateOf(false)
        var continued = false
        compose.setContent { DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale)) {
            PlainphoneTheme { Box(Modifier.size(360.dp, 560.dp)) {
                if (confirm) AppReviewScreen(AppReviewUiState(context, app,
                    stage = AppReviewStage.Remove, previewSubset = true)) {
                    if (it == AppReviewEvent.Continue) continued = true
                }
                else PreviewAppDetailScreen(context, app.name, "1") { confirm = true }
            } }
        } }
        // Text semantics are the spoken label; no separate description overrides the app name.
        compose.onNodeWithText("Remove Camera").assertHasClickAction().assertIsDisplayed().performClick()
        compose.onNodeWithText("Remove Camera?").assertExists()
        compose.onNodeWithText("Remove").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, continued) }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun notSetUpKeepsHistoryAndReturnsWithoutSending() {
        val events = mutableListOf<RequestEvent>()
        compose.setContent { DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale)) {
            PlainphoneTheme { Box(Modifier.size(360.dp, 560.dp)) {
                RequestsScreen(RequestsUiState(rows = listOf(RequestUiRow("Bus times app", "14 Oct", RequestUiStatus.Sent))), events::add)
            } }
        } }
        compose.onAllNodesWithText("Not set up on this phone yet.").assertCountEquals(1)
        compose.onAllNodesWithText("No one is set to get requests. This phone’s PIN holder can set it up in More, then PIN settings, then Who gets requests.").assertCountEquals(1)
        compose.onNodeWithText("Bus times app").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sent").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Send").assertDoesNotExist()
        compose.onNodeWithText("OK").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(RequestEvent.Back), events) }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "scale={0}") fun scales() = listOf(1f, 1.5f, 2f)
    }
}
