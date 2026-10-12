// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.home.*
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.user.*

/** Bounds are measured from composed rows, including wrapped text, at 160 dpi. */
@RunWith(Parameterized::class)
class MenuSpacingScreenshotTest(private val scale: Float) {
    private val device = DeviceConfig.PIXEL_5.copy(screenWidth = 360, screenHeight = 640,
        xdpi = 160, ydpi = 160, density = Density.MEDIUM, fontScale = scale, locale = "en")
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device, showSystemUi = false,
        maxPercentDifference = 0.0)

    @Test fun homeRowsHaveEqualGaps() = shot(check = { nodes ->
        val rows = listOf("Camera", "Maps", "Messages", "Phone").map { nodes.row(it) }
        rows.forEach { assertTrue("Home target must be at least 56 dp", it.size.height >= 56) }
        val gaps = rows.zipWithNext { a, b -> b.positionInRoot.y - a.positionInRoot.y - a.size.height }
        assertEquals("App rows establish the 12 dp rhythm", 12f, gaps[0], 0.01f)
        gaps.drop(1).forEach { assertEquals("Every Home row has the same gap", gaps[0], it, 0.01f) }
        assertTrue(nodes.row("More").size.height >= 64)
    }) {
        HomeScreen(homeState(listOf(HomeApp("camera", "Camera"), HomeApp("maps", "Maps"))), {})
    }

    @Test fun moreTrustedRowsHaveEqualGaps() = more(MoreMode.Trusted(listOf("Sam", "Jo"), "Sam"),
        listOf("Phone settings", "Ask for an app", "For Sam and Jo"))

    @Test fun moreSelfManagedRowsHaveEqualGaps() = more(MoreMode.SelfManaged(10),
        listOf("Phone settings", "Add an app", "Your settings"))

    @Test fun longHomeLabelAndOverflowCounts() = shot(check = { nodes ->
        listOf("home-messages", "home-phone").forEach { tag ->
            val row = nodes.single { it.config.getOrElse(SemanticsProperties.TestTag) { "" } == tag }
            assertTrue(row.size.height >= 56)
            assertTrue("Communication rows stay visible", row.boundsInRoot.height == row.size.height.toFloat())
        }
        assertTrue(nodes.row("More").size.height >= 64)
        val spoken = nodes.flatMap { it.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() } }
        assertTrue(spoken.any { it.contains("100") && it.contains("unread") })
        assertTrue(spoken.any { it.contains("100") && it.contains("missed") })
    }) {
        HomeScreen(homeState(listOf(HomeApp("maps", "A long app name that wraps onto several lines")))
            .copy(missedCalls = 100, unreadTexts = 100), {})
    }

    @Test fun moreTrustedViewport() = shot {
        MoreScreen(MoreUiState(MoreMode.Trusted(listOf("Sam", "Jo"), "Sam")), {})
    }

    @Test fun moreSelfManagedViewport() = shot {
        MoreScreen(MoreUiState(MoreMode.SelfManaged(10)), {})
    }

    @Test fun moreLongTrustedNamesFullContent() {
        paparazzi.unsafeUpdateConfig(deviceConfig = device.copy(screenHeight = 1800))
        moreLongTrustedNames()
    }

    @Test fun moreLongTrustedNames() = shot {
        MoreScreen(MoreUiState(MoreMode.Trusted(listOf("Sam".repeat(8), "Jo".repeat(8)), "Sam".repeat(8))), {})
    }

    private fun more(mode: MoreMode, labels: List<String>) {
        // Full scrolling content for bounds checks, even at 200%; 640 dp fixtures cover the viewport.
        paparazzi.unsafeUpdateConfig(deviceConfig = device.copy(screenHeight = 1800))
        shot(check = { nodes ->
            val emergency = nodes.single { it.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                .any { label -> label.startsWith("Emergency") } }
            val rows = listOf(emergency) + labels.map { nodes.row(it) }
            rows.forEach { assertTrue("More target must be at least 64 dp", it.size.height >= 64) }
            val gaps = rows.zipWithNext { a, b -> b.positionInRoot.y - a.positionInRoot.y - a.size.height }
            gaps.forEach { assertEquals("More rows share one rhythm", gaps.last(), it, 0.01f) }
            val last = rows.last()
            assertTrue("About retains the section gap", nodes.row("About this phone").positionInRoot.y -
                last.positionInRoot.y - last.size.height >= 32f)
        }) { MoreScreen(MoreUiState(mode), {}) }
    }

    private fun homeState(apps: List<HomeApp>) = HomeUiState("10:42", "Thursday 8 October", "Thu 8 Oct", apps)
    private fun List<SemanticsNode>.row(label: String) = single { node ->
        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text == label }
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun shot(check: (List<SemanticsNode>) -> Unit = {}, content: @Composable () -> Unit) {
        var checked = false
        paparazzi.snapshot {
            PlainphoneTheme {
                val root = LocalView.current as ViewRootForTest
                Box(Modifier.onGloballyPositioned {
                    check(root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true))
                    checked = true
                }) { HomeTestFrame(content) }
            }
        }
        assertTrue("Layout assertions must run", checked)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(1f, 1.3f, 1.5f, 1.8f, 2f).map { arrayOf(it) }
    }
}
