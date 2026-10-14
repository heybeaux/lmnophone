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
import org.plainphone.ui.requests.*
import org.plainphone.ui.trusted.*
import org.plainphone.ui.theme.PlainphoneTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.*

@RunWith(Parameterized::class)
class RequestRemovalScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"), showSystemUi = false)
    private val user = "Alex Jo Sam Alex Jo Sam Alex Jo Sam"
    private val request = "The bus times app for the ferry and the walking route home. ".repeat(5).take(280)
    private val state = RequestsUiState(historyOnly = true, userName = user,
        rows = listOf(RequestUiRow(request, "14 Oct 2026", RequestUiStatus.Sending, "r1")))

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun shot(state: RequestsUiState, action: String, expected: RequestEvent,
        confirmation: String = "Remove this request? ${state.userName} won't be told.") {
        var checked = false
        var event: RequestEvent? = null
        var scrolled = false
        val compose = androidx.compose.ui.platform.ComposeView(paparazzi.context).apply { setContent { PlainphoneTheme { HomeTestFrame {
            val view = LocalView.current as ViewRootForTest
            Box(Modifier.onGloballyPositioned {
                val nodes = view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                fun text(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
                val button = nodes.single { text(it) == action && it.config.contains(SemanticsActions.OnClick) }
                assertTrue("Touch target", button.size.height >= 48f)
                val scroll = nodes.single { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
                val range = scroll.config[SemanticsProperties.VerticalScrollAxisRange]
                assertTrue("Action reachable", button.positionInRoot.y + button.size.height - range.maxValue() <= 592f)
                assertTrue(button.config[SemanticsActions.OnClick].action!!.invoke())
                assertEquals(expected, event)
                if (state.removingId != null) {
                    val keep = nodes.single { text(it) == "Keep it" && it.config.contains(SemanticsActions.OnClick) }
                    assertTrue(keep.config[SemanticsActions.OnClick].action!!.invoke())
                    assertEquals(RequestEvent.Keep, event)
                    assertTrue(nodes.any { text(it) == confirmation })
                }
                view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false).forEach { node ->
                    val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                    layouts.forEach { layout -> for (line in 0 until layout.lineCount) {
                        assertFalse(layout.isLineEllipsized(line))
                        // Line bounds include trailing spaces and alignment offsets. Check the visible glyphs.
                        val end = layout.getLineEnd(line, visibleEnd = true)
                        if (end > layout.getLineStart(line)) assertTrue("Visible text fits",
                            layout.getBoundingBox(end - 1).right - layout.getLineLeft(line) <= layout.size.width + 1f)
                        assertTrue(layout.getLineBottom(line) <= layout.size.height + 1f)
                    } }
                }
                checked = true
                if (action == "Not now" && !scrolled) {
                    scrolled = true
                    scrollToEnd(scroll)
                }
                if (state.removeFailed) assertTrue(nodes.any { text(it) == "The change could not be saved. Try again." })
            }) { RequestsScreen(state) { event = it } }
        } } } }
        paparazzi.snapshot(compose, offsetMillis = 1_000)
        assertTrue(checked)
    }
    @Test fun longRequest() = shot(state, "Not now", RequestEvent.NotNow("r1"))
    @Test fun confirm() = shot(state.copy(removingId = "r1"), "Remove", RequestEvent.Remove)
    @Test fun productionFallbackConfirmation() = shot(state.copy(
        userName = paparazzi.context.getString(R.string.current_phone_user), removingId = "r1"),
        "Remove", RequestEvent.Remove, "Remove this request? The person using this phone won't be told.")
    @Test fun failedRemoval() = shot(state.copy(removingId = "r1", removeFailed = true), "Remove", RequestEvent.Remove)
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    @Test fun menuCount() {
        val notice = paparazzi.context.resources.getQuantityString(R.plurals.requests_needs_you, 1, 1, user)
        assertEquals("1 request from $user", notice)
        assertEquals("2 requests from $user", paparazzi.context.resources.getQuantityString(R.plurals.requests_needs_you, 2, 2, user))
        var scrolled = false
        val compose = androidx.compose.ui.platform.ComposeView(paparazzi.context).apply { setContent {
            PlainphoneTheme { HomeTestFrame {
                val view = LocalView.current as ViewRootForTest
                Box(Modifier.onGloballyPositioned {
                    val nodes = view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                    val scroll = nodes.single { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
                    val range = scroll.config[SemanticsProperties.VerticalScrollAxisRange]
                    val request = nodes.single { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == notice } }
                    assertTrue(request.config.contains(SemanticsActions.OnClick))
                    assertTrue(request.positionInRoot.y + request.size.height - range.maxValue() <= scroll.boundsInRoot.bottom + 1f)
                    if (!scrolled) { scrolled = true; scrollToEnd(scroll) }
                }) {
                    AdminMenuScreen(AdminMenuUiState(AdminContext(user), listOf(AdminMenuItem(AdminDestination.Requests, "1")),
                        listOf(AdminNotice("requests", notice))), {})
                }
            } }
        } }
        paparazzi.snapshot(compose, offsetMillis = 1_000)
        assertTrue(scrolled)
    }
    private fun scrollToEnd(scroll: SemanticsNode) {
        kotlinx.coroutines.runBlocking(object : androidx.compose.runtime.MonotonicFrameClock {
            private var frame = 0L
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R { frame += 16_000_000L; return onFrame(frame) }
        }) { scroll.config[SemanticsActions.ScrollByOffset].invoke(androidx.compose.ui.geometry.Offset(0f, 10000f)) }
    }
    companion object { @JvmStatic @Parameterized.Parameters(name = "scale={0}") fun cases() = listOf(1f, 1.3f, 1.5f, 2f) }
}
