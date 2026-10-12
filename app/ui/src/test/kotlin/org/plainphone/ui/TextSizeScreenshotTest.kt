// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.user.*

@RunWith(Parameterized::class)
class TextSizeScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0)
    @Test fun confirmed() = shot(TextSizeUiState("130", true, true))
    @Test fun permissionMissing() = shot(TextSizeUiState("100", false, false, true))
    @Test fun unavailable() = shot(TextSizeUiState(null, false, false, true))
    @Test fun upperLimit() = shot(TextSizeUiState("200", true, false))
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun shot(state: TextSizeUiState) = paparazzi.snapshot {
        PlainphoneTheme {
            val view = LocalView.current as ViewRootForTest
            Box(Modifier.onGloballyPositioned {
                val nodes = view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                for ((label, enabled) in listOf("Smaller text" to state.canDecrease, "Bigger text" to state.canIncrease)) {
                    val node = nodes.single { label in it.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() } }
                    assertEquals(!enabled, node.config.contains(SemanticsProperties.Disabled))
                    assertTrue(node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.isEmpty())
                }
                val text = nodes.flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } }.map { it.text }
                assertTrue("Done" in text)
                assertTrue("This is how big your text will be." in text)
                assertEquals(state.failed, "Text size couldn't be changed." in text)
                state.percent?.let { assertTrue("$it%" in text) }
            }) { HomeTestFrame { TextSizeScreen(state, {}) } }
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(1f, 1.3f, 1.5f, 1.8f, 2f).map { arrayOf(it) }
    }
}
