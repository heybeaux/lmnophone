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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.user.*

@RunWith(Parameterized::class)
class PhoneSettingsWaitingScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0)
    @Test fun noWaitingChange() = shot(null)
    @Test fun waitingChange() = shot("change the trusted people")
    @Test fun longWaitingChange() = shot("hand Sam's place to Alex as a trusted person looking after this phone")

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun shot(change: String?) = paparazzi.snapshot {
        PlainphoneTheme {
            val view = LocalView.current as ViewRootForTest
            var event: PhoneSettingsEvent? = null
            Box(Modifier.onGloballyPositioned {
                val nodes = view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                val boxes = nodes.filter { it.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                    .any { label -> label.startsWith("A change is waiting,") } }
                assertEquals(if (change == null) 0 else 1, boxes.size)
                if (change != null) {
                    val box = boxes.single()
                    assertEquals(listOf("A change is waiting, $change. Opens the change."),
                        box.config[SemanticsProperties.ContentDescription])
                    assertTrue(box.boundsInRoot.height >= 56)
                    assertTrue(box.config.getOrElse(SemanticsProperties.Text) { emptyList() }.isEmpty())
                    assertTrue(box.config[SemanticsActions.OnClick].action!!.invoke())
                    assertEquals(PhoneSettingsEvent.OpenWaitingChange, event)
                }
            }) {
                HomeTestFrame {
                    PhoneSettingsScreen(PhoneSettingsUiState(pendingChange = change)) { event = it }
                }
            }
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(1f, 1.3f, 1.5f, 1.8f, 2f).map { arrayOf(it) }
    }
}
