/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.trusted.*
import org.plainphone.ui.user.*

class SliceThreeCompletionTest {
    @get:Rule val compose = createComposeRule()
    private val self = AdminContext("Pat")
    private val longName = "Alexandria Catherine Montgomery"
    private fun show(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
            PlainphoneTheme { Box(Modifier.size(360.dp, 560.dp)) { content() } }
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(instrumentation.targetContext.cacheDir, name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    @Test fun longTitleMovesActionsAndBandIntoOneScroll() {
        show { PinScreen(PinUiState(AdminContext("Pat", longName), PinStage.Locked,
            wait = "1 minute", otherName = "Christopher Alexander Worthington"), {}) }
        val band = compose.onNodeWithText("For $longName")
        band.assertIsDisplayed()
        capture("long-pin-top.png")
        compose.onNodeWithText("Use recovery code").assertIsNotDisplayed()
        compose.onNodeWithText("Use recovery code").performScrollTo().assertIsDisplayed()
        band.assertIsNotDisplayed()
        capture("long-pin-actions.png")
        compose.onNodeWithText("Too many tries for $longName's PIN").performScrollTo().assertIsDisplayed()
    }
    @Test fun ordinaryScreenPinsActionsAndRechecksWhenFontSizeChanges() {
        val scale = mutableStateOf(1f)
        val state = mutableStateOf(PinUiState(AdminContext("Pat", longName), PinStage.Locked,
            wait = "1 minute", otherName = "Christopher Alexander Worthington"))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale.value)) {
                PlainphoneTheme { Box(Modifier.size(360.dp, 560.dp)) { PinScreen(state.value, {}) } }
            }
        }
        compose.onNodeWithTag("task-pinned").assertExists()
        compose.onNodeWithText("Use recovery code").assertIsDisplayed()
        compose.runOnIdle { scale.value = 2f }
        compose.onNodeWithTag("task-whole-page").assertExists()
        compose.onNodeWithText("Use recovery code").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(sending = true) }
        compose.onNodeWithTag("task-whole-page").assertExists()
        compose.onNodeWithText("Use recovery code").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("For $longName").assertIsNotDisplayed()
        compose.runOnIdle { state.value = state.value.copy(stage = PinStage.Recovery) }
        compose.onNodeWithText("For $longName").assertIsDisplayed()
    }
    @Test fun selfManagedPinHasItsOwnTitleAndBody() {
        show { PinScreen(PinUiState(self), {}) }
        compose.onNodeWithText("Your PIN").assertExists()
        compose.onNodeWithText("Enter your PIN to make changes on this phone.").assertExists()
    }
    @Test fun selfManagedPermissionsHaveNoDirectEditRoute() {
        val page = mutableStateOf(false)
        val app = InstalledApp("bus", "Bus times", "Example")
        show {
            if (!page.value) AppDetailScreen(AppDetailUiState(self, app), {})
            else AppReviewScreen(AppReviewUiState(self, app, stage = AppReviewStage.Remove), {})
        }
        compose.onNodeWithText("Change what Bus times can use").assertDoesNotExist()
        compose.onNodeWithText("To change these, remove Bus times, then add it again with \"Add a hidden app\" and choose again.").assertExists()
        compose.onNodeWithText("Remove Bus times").assertExists()
        compose.runOnIdle { page.value = true }
        compose.onNodeWithText("Bus times will disappear from the home screen straight away. Nothing is deleted: if you add it again with \"Add a hidden app\", your things in it will still be there.").assertExists()
    }
    @Test fun stuckSelfManagedHandoverKeepsCurrentAuthorityInTheCopy() {
        val state = mutableStateOf(WaitingChangeUiState(self, WaitingStage.Stuck, "switch to Jo looking after this phone", newName = "Jo\u202E", canStop = true))
        show { WaitingChangeScreen(state.value, {}) }
        compose.onNodeWithText("Until this works, nothing changes: you still look after this phone", substring = true).assertExists()
        compose.runOnIdle { state.value = state.value.copy(stage = WaitingStage.StopConfirm) }
        compose.onNodeWithText("LMNOPhone puts reset protection back for your Google account. Jo's PIN and details are thrown away, and you keep looking after this phone.").assertExists()
        compose.runOnIdle { state.value = state.value.copy(stage = WaitingStage.Stopped) }
        compose.onNodeWithText("Stopped. Nothing changed. You still look after this phone.").assertExists()
    }
}
