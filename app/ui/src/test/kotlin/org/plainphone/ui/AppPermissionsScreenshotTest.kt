// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.*
import com.android.resources.Density
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.trusted.*

@RunWith(Parameterized::class)
class AppPermissionsScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"), showSystemUi = false)
    private val context = AdminContext("this person")
    private val name = "Camera with a very long app name"
    private val rows = listOf(
        AppPermissionRow("android.permission-group.CAMERA", "Camera", PermissionStatus.Allowed),
        AppPermissionRow("android.permission-group.MICROPHONE", "Microphone", PermissionStatus.Denied),
        AppPermissionRow("android.permission-group.LOCATION", "Location", PermissionStatus.Default),
        AppPermissionRow("android.permission-group.CONTACTS", "Contacts", PermissionStatus.Unknown))
    private val choices = listOf(PermissionGroup.Camera, PermissionGroup.Microphone, PermissionGroup.Location, PermissionGroup.Contacts)
        .mapIndexed { index, group -> PermissionChoice(rows[index].id, group) }
    @Test fun page() = shot("Remove from this person's phone") {
        PreviewAppDetailScreen(context, name, "123.456.789", permissions = AppPermissionsUiState(rows), canChooseOnAdd = true) {}
    }
    @Test fun choices() = shot("Next", disabled = true) {
        PermissionsScreen(PermissionsUiState(context, name, choices, previewAdd = true)) {}
    }
    @Test fun chosen() = shot("Next") {
        PermissionsScreen(PermissionsUiState(context, name, choices.map { it.copy(decision = PermissionDecision.Deny) }, canContinue = true, previewAdd = true)) {}
    }
    @Test fun refused() = shot("Next", disabled = true, refused = true) {
        PermissionsScreen(PermissionsUiState(context, name, choices.mapIndexed { index, choice -> choice.copy(unavailable = index == 1) }, previewAdd = true)) {}
    }
    @Test fun empty() = shot("Remove from this person's phone") {
        PreviewAppDetailScreen(context, name, "1", permissions = AppPermissionsUiState(), canChooseOnAdd = true) {}
    }
    @Test fun unreadable() = shot("Remove from this person's phone") {
        PreviewAppDetailScreen(context, name, "1", permissions = AppPermissionsUiState(checkFailed = true), canChooseOnAdd = true) {}
    }
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun shot(primary: String, disabled: Boolean = false, refused: Boolean = false, content: @Composable () -> Unit) {
        var checked = false
        var scrollNode: SemanticsNode? = null
        var scrollToEnd = false
        var scrolled = false
        val compose = androidx.compose.ui.platform.ComposeView(paparazzi.context).apply { setContent {
            PlainphoneTheme { HomeTestFrame {
                val view = LocalView.current as ViewRootForTest
                Box(Modifier.onGloballyPositioned {
                    val nodes = view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                    fun text(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
                    val button = nodes.single { text(it) == primary && it.config.contains(SemanticsActions.OnClick) }
                    assertEquals(disabled, button.config.contains(SemanticsProperties.Disabled))
                    assertTrue("Primary is fully visible", button.boundsInRoot.height >= button.size.height - 1f)
                    assertTrue("Primary above navigation", button.boundsInRoot.bottom <= 592f)
                    if (refused) {
                        assertFalse(nodes.any { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { label -> label.startsWith("Microphone:") } })
                        assertTrue(nodes.any { text(it).startsWith("Android doesn't let LMNOPhone switch this on.") })
                    }
                    val scroll = nodes.single { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
                    scrollNode = scroll
                    val range = scroll.config[SemanticsProperties.VerticalScrollAxisRange]
                    // Both pinned and approved whole-page fallback must make the full action reachable.
                    assertTrue("Primary fully reachable above nav", button.positionInRoot.y + button.size.height - range.maxValue() <= 592f + 1f)
                    assertTrue("Primary fits the viewport", button.size.height < 560f)
                    view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false).forEach { node ->
                        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                        layouts.forEach { layout -> for (line in 0 until layout.lineCount) {
                            assertFalse("No ellipsis", layout.isLineEllipsized(line))
                            // Centered text can retain a paragraph offset after intrinsic sizing: compare the glyph span.
                            assertTrue("No horizontal clipping: ${layout.layoutInput.text.text}, right=${layout.getLineRight(line)}, width=${layout.size.width}", layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f)
                            assertTrue("No vertical clipping", layout.getLineBottom(line) <= layout.size.height + 1f)
                        } }
                    }
                    checked = true
                    if (scrollToEnd && !scrolled) {
                        scrolled = true
                        kotlinx.coroutines.runBlocking(object : androidx.compose.runtime.MonotonicFrameClock {
                            private var frame = 0L
                            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R { frame += 16_000_000L; return onFrame(frame) }
                        }) { scroll.config[SemanticsActions.ScrollByOffset].invoke(androidx.compose.ui.geometry.Offset(0f, range.maxValue())) }
                    }
                }) { content() }
            } }
        } }
        paparazzi.snapshot(compose, offsetMillis = 1_000)
        assertTrue(checked)
        scrollToEnd = true
        paparazzi.snapshot(compose, name = "bottom", offsetMillis = 1_000)
        assertTrue("Scrolled snapshot must actually scroll", scrolled)
        assertTrue(checkNotNull(scrollNode).config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)

    }
    companion object { @JvmStatic @Parameterized.Parameters(name = "scale={0}") fun cases() = listOf(1f, 1.5f, 2f) }
}
