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

@RunWith(Parameterized::class)
class ProductionHelperScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0)
    private val context = AdminContext("the person using this phone")
    private val helper = "the person who set up LMNOPhone on this phone"

    @Test fun recoveryFallback() = shot("If the code is lost, call $helper.") {
        PinScreen(PinUiState(context, PinStage.RecoveryLocked, wait = "30 seconds")) {}
    }
    @Test fun recoveryConfiguredHelper() = shot("If the code is lost, call Jo.") {
        PinScreen(PinUiState(context, PinStage.RecoveryLocked, wait = "30 seconds", helper = "Jo")) {}
    }
    @Test fun turnOffIntro() = shot("To use LMNOPhone again later, $helper has to set it up again with a computer.") {
        TurnOffScreen(TurnOffUiState(context, TurnOffStage.Intro, helper = helper, testBuild = true)) {}
    }
    @Test fun turnOffLastCheck() = shot("Once it's off, only $helper can turn it back on, using a computer.") {
        TurnOffScreen(TurnOffUiState(context, TurnOffStage.LastConfirmation, helper = helper, testBuild = true)) {}
    }
    @Test fun turnOffFailed() = shot("Call $helper and show this screen.") {
        TurnOffScreen(TurnOffUiState(context, TurnOffStage.Failed, helper = helper,
            stepDescription = "showing hidden apps again", testBuild = true)) {}
    }
    @Test fun appReview() = shot("To ask them to, tell $helper.") {
        AppReviewScreen(AppReviewUiState(context, InstalledApp("maps", "Maps", ""),
            helper = helper, previewSubset = true)) {}
    }
    @Test fun appUnreviewed() = shot("ask $helper to get it checked first.") {
        AppReviewScreen(AppReviewUiState(context, InstalledApp("maps", "Maps", ""),
            stage = AppReviewStage.Unreviewed, helper = helper, previewSubset = true)) {}
    }
    @Test fun familyMicError() = shot("Try again, or ask $helper.") {
        FamilyScreen(FamilyUiState(context, enabled = true, micFailed = true, helperName = helper)) {}
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun shot(expected: String, content: @Composable () -> Unit) {
        var checked = false
        paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
            val view = LocalView.current as ViewRootForTest
            Box(Modifier.onGloballyPositioned {
                val nodes = view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                val help = nodes.single { node -> node.config.getOrNull(SemanticsProperties.Text)
                    .orEmpty().any { expected in it.text } }
                val scroll = nodes.single { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
                val range = scroll.config[SemanticsProperties.VerticalScrollAxisRange]
                assertTrue("Helper text must be reachable by scrolling",
                    help.positionInRoot.y + help.size.height - range.maxValue() <= scroll.boundsInRoot.bottom + 1f)
                listOf(help).forEach { node ->
                    val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                    layouts.forEach { assertFalse("Text overflow: ${it.layoutInput.text.text}; size=${it.size}; lastBottom=${it.getLineBottom(it.lineCount - 1)}", it.hasVisualOverflow) }
                }
                checked = true
            }) { content() }
        } } }
        assertTrue(checked)
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(arrayOf(1f), arrayOf(2f))
    }
}
