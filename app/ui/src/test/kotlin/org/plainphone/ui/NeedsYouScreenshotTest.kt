// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.Assert.*
import org.plainphone.ui.trusted.*
import org.plainphone.ui.theme.PlainphoneTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.*

@RunWith(Parameterized::class)
class NeedsYouScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"), showSystemUi = false)

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun shot(count: Int, user: String = "Alex", requestOnly: Boolean = false) {
        val notices = listOf(AdminNotice("waiting:a", "Waiting: turn LMNOPhone off"),
            AdminNotice("attention", "LMNOPhone needs attention"), AdminNotice("requests", "3 requests from $user")).let { if (requestOnly) it.takeLast(1) else it.take(count) }
        val state = AdminMenuUiState(AdminContext(user), AdminDestination.entries.map { AdminMenuItem(it) }, notices)
        var checked = false
        var scrolled = false
        val compose = androidx.compose.ui.platform.ComposeView(paparazzi.context).apply { setContent {
        PlainphoneTheme { HomeTestFrame {
            val view = LocalView.current as ViewRootForTest
            Box(Modifier.onGloballyPositioned {
                val nodes = view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                fun text(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
                val done = nodes.single { text(it) == "Done – back to the home screen" && it.config.contains(SemanticsActions.OnClick) }
                val scroll = nodes.single { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
                val range = scroll.config[SemanticsProperties.VerticalScrollAxisRange]
                assertTrue("Done stays fully visible", done.boundsInRoot.height >= done.size.height - 1f)
                assertTrue("Done above navigation bar", done.boundsInRoot.bottom <= 592f)
                assertTrue("Content ends above Done", scroll.boundsInRoot.bottom <= done.boundsInRoot.top)
                assertEquals(count > 0, nodes.any { text(it) == "Needs you" })
                notices.forEach { notice ->
                    val row = nodes.single { text(it) == notice.text }
                    assertTrue("Notice has a tap action", row.config.contains(SemanticsActions.OnClick))
                    assertTrue("Whole notice reachable", row.positionInRoot.y + row.size.height - range.maxValue() <= scroll.boundsInRoot.bottom + 1f)
                    assertTrue("No horizontal overflow", row.size.width <= scroll.size.width)
                }
                view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false).forEach { node ->
                    val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                    layouts.forEach { layout ->
                        // Compose rounds integer layout bounds; allow the final fractional pixel.
                        for (line in 0 until layout.lineCount) {
                            assertFalse("Text must not ellipsize", layout.isLineEllipsized(line))
                            assertTrue("Text fits horizontally", layout.getLineRight(line) <= layout.size.width + 1f)
                            assertTrue("Text fits vertically", layout.getLineBottom(line) <= layout.size.height + 1f)
                        }
                    }
                }
                checked = true
                if (requestOnly && !scrolled) {
                    val row = nodes.single { text(it) == notices.single().text }
                    val offset = row.positionInRoot.y - scroll.positionInRoot.y
                    scrolled = true
                    kotlinx.coroutines.runBlocking(object : androidx.compose.runtime.MonotonicFrameClock {
                        private var frame = 0L
                        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                            frame += 16_000_000L
                            return onFrame(frame)
                        }
                    }) { scroll.config[SemanticsActions.ScrollByOffset].invoke(androidx.compose.ui.geometry.Offset(0f, offset)) }
                    assertTrue("Long request is scrolled into view", range.value() > 0f)
                }
            }) { AdminMenuScreen(state, {}) }
        } } } }
        paparazzi.snapshot(compose, offsetMillis = 1_000)
        assertTrue(checked)
        if (requestOnly) assertTrue(scrolled)
    }
    @Test fun empty() = shot(0)
    @Test fun one() = shot(1)
    @Test fun three() = shot(3)
    @Test fun longConfiguredName() = shot(1, "Alex Jo Sam Alex Jo Sam Alex Jo Sam", requestOnly = true)
    companion object { @JvmStatic @Parameterized.Parameters(name = "scale={0}") fun cases() = listOf(1f, 1.5f, 2f) }
}
