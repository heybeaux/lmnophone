// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.trusted.*

/** PLA-583: "{user}'s phone" copy says "this phone" when no name is set, and keeps a configured name. */
@RunWith(Parameterized::class)
class PhoneNameScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0)
    private val clock = InstalledApp("clock", "Clock", "")

    /** The activity's apps context: the fallback words as the user name, no trusted person name. */
    @Composable private fun productionContext() = AdminContext(stringResource(R.string.current_phone_user), null)
    @Composable private fun unnamedHelped() = AdminContext(stringResource(R.string.current_phone_user), "Jo")
    @Composable private fun AddConfirm(context: AdminContext) = AppReviewScreen(AppReviewUiState(context, clock,
        stage = AppReviewStage.Confirm, helper = stringResource(R.string.setup_helper), previewSubset = true)) {}
    private val menuItems = listOf(AdminMenuItem(AdminDestination.ChangePin), AdminMenuItem(AdminDestination.NewRecoveryCode))

    @Test fun addConfirmUnnamed() = shot("Add Clock to this phone?") { AddConfirm(productionContext()) }
    @Test fun addConfirmBlankName() = shot("Add Clock to this phone?") { AddConfirm(AdminContext("")) }
    @Test fun addConfirmNamed() = shot("Add Clock to Sam's phone?") { AddConfirm(AdminContext("Sam")) }
    /** Title only: this menu's body and Done button (trusted-person mode, not in this build) need their own copy. */
    @Test fun menuTitleUnnamed() = shot("Looking after this phone", wholeScreen = false) {
        AdminMenuScreen(AdminMenuUiState(unnamedHelped(), menuItems)) {}
    }
    @Test fun menuTitleNamed() = shot("Looking after Sam's phone") {
        AdminMenuScreen(AdminMenuUiState(AdminContext("Sam", "Jo"), menuItems)) {}
    }
    @Test fun pinBodyUnnamed() = shot("Enter your PIN to look after this phone.") {
        PinScreen(PinUiState(unnamedHelped(), PinStage.Enter)) {}
    }
    @Test fun pinBodyNamed() = shot("Enter your PIN to look after Sam's phone.") {
        PinScreen(PinUiState(AdminContext("Sam", "Jo"), PinStage.Enter)) {}
    }
    @Test fun turnOffIntroUnnamed() = shot("This makes this phone an ordinary Android phone again. Every app will open") {
        TurnOffScreen(TurnOffUiState(unnamedHelped(), TurnOffStage.Intro, helper = "Jo")) {}
    }
    @Test fun turnOffIntroNamed() = shot("This makes Sam's phone an ordinary Android phone again. Every app will open") {
        TurnOffScreen(TurnOffUiState(AdminContext("Sam", "Jo"), TurnOffStage.Intro, helper = "Jo")) {}
    }
    @Test fun turnOffSureUnnamed() = shot("LMNOPhone will be turned off on this phone.") {
        TurnOffScreen(TurnOffUiState(unnamedHelped(), TurnOffStage.FirstConfirmation, helper = "Jo")) {}
    }
    @Test fun turnOffSureNamed() = shot("LMNOPhone will be turned off on Sam's phone.") {
        TurnOffScreen(TurnOffUiState(AdminContext("Sam", "Jo"), TurnOffStage.FirstConfirmation, helper = "Jo")) {}
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun shot(expected: String, wholeScreen: Boolean = true, content: @Composable () -> Unit) {
        var checked = false
        paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
            val view = LocalView.current as ViewRootForTest
            Box(Modifier.onGloballyPositioned {
                val nodes = view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                val texts = nodes.flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
                val checkedTexts = if (wholeScreen) texts else texts.filter { expected in it }
                assertFalse("Fallback words used as a name: $checkedTexts", checkedTexts.any { "using this phone's" in it })
                val matching = nodes.filter { node -> node.config.getOrNull(SemanticsProperties.Text)
                    .orEmpty().any { expected in it.text } }
                assertEquals("Expected screen copy: $expected in $texts", 1, matching.size)
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                matching.single().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                assertTrue("No text layout for: $expected", layouts.isNotEmpty())
                layouts.forEach { assertFalse("Text overflow: ${it.layoutInput.text.text}", it.hasVisualOverflow) }
                checked = true
            }) { content() }
        } } }
        assertTrue(checked)
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(arrayOf(1f), arrayOf(1.3f), arrayOf(2f))
    }
}
