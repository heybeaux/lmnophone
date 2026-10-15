// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.home.HomeApp
import org.plainphone.ui.home.HomeScreen
import org.plainphone.ui.home.HomeUiState
import org.plainphone.ui.theme.PlainphoneTheme

/** Production Home in a 360 × 640 viewport, reserving 80 dp for system bars. */
@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class HomeTraversalTest(private val scale: Float) {
    @get:Rule val compose = createComposeRule()
    private val apps = listOf("Calendar", "Music", "Maps", "Camera")
    private val actions = apps + listOf("Messages", "Phone", "More")
    private lateinit var focus: FocusManager

    private fun home() {
        compose.setContent {
            focus = LocalFocusManager.current
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale)) {
                PlainphoneTheme {
                    Box(Modifier.size(360.dp, 560.dp)) {
                        HomeScreen(HomeUiState("10:42", "Thursday 8 October", "Thu 8 Oct",
                            apps.map { HomeApp(it, it) }), {})
                    }
                }
            }
        }
        compose.onNodeWithText("Camera").assertIsDisplayed()
        if (scale == 2f) {
            compose.onNodeWithTag("home-apps").assert(SemanticsMatcher("apps overflow") {
                val range = it.config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
                range.maxValue() > 0f
            })
        }
    }

    @Test fun keyboardNextFollowsDrawnOrderIncludingOffscreenApps() {
        home()
        compose.onNodeWithTag("home-apps").performScrollToNode(hasText(apps.first()))
        compose.onNodeWithText(apps.first()).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        actions.forEachIndexed { index, label ->
            compose.onNodeWithText(label).assertIsFocused().assertIsDisplayed()
            if (index < actions.lastIndex) compose.runOnIdle {
                assertTrue("Next after $label", focus.moveFocus(FocusDirection.Next))
            }
        }
        // Shift-Tab uses the same order in reverse, including lazy-list scrolling.
        actions.asReversed().drop(1).forEach { label ->
            compose.runOnIdle { assertTrue(focus.moveFocus(FocusDirection.Previous)) }
            compose.onNodeWithText(label).assertIsFocused().assertIsDisplayed()
        }
    }

    @Test fun androidAccessibilityTraversalFollowsDrawnOrderAfterScrolling() {
        home()
        // Inspect both ends of the overflowing list, not just initially composed rows.
        listOf(apps.last(), apps.first()).forEach { anchor ->
            compose.onNodeWithTag("home-apps").performScrollToNode(hasText(anchor))
            compose.waitForIdle()
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.waitForIdle(100, 3_000)
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            fun collect(node: AccessibilityNodeInfo) {
                nodes += node
                for (i in 0 until node.childCount) node.getChild(i)?.let(::collect)
            }
            collect(requireNotNull(automation.rootInActiveWindow))
            fun label(node: AccessibilityNodeInfo): String? {
                node.text?.toString()?.takeIf { it in actions }?.let { return it }
                for (i in 0 until node.childCount) node.getChild(i)?.let { child ->
                    label(child)?.let { return it }
                }
                return null
            }
            val controls = nodes.filter { it.isVisibleToUser && it.isClickable && label(it) in actions }
            assertTrue("Expected apps and pinned controls in Android tree", controls.size >= 4)
            val drawn = controls.sortedBy {
                android.graphics.Rect().also(it::getBoundsInScreen).top
            }
            assertEquals(actions.filter { label -> controls.any { label(it) == label } },
                drawn.map { label(it) })
            // Compose exports TalkBack ordering through Android traversalBefore/After.
            // Follow those edges, with platform child order only for unrelated nodes.
            val remaining = nodes.toMutableList()
            val ordered = mutableListOf<AccessibilityNodeInfo>()
            while (remaining.isNotEmpty()) {
                val next = remaining.firstOrNull { candidate ->
                    remaining.none { other -> other != candidate &&
                        (other.traversalBefore == candidate || candidate.traversalAfter == other) }
                }
                assertNotNull("Accessibility traversal must not cycle", next)
                ordered += next!!
                remaining.remove(next)
            }
            assertEquals("TalkBack order at scale $scale, anchor $anchor",
                drawn.map { label(it) },
                ordered.filter { it in controls }.map { label(it) })
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(1f, 2f).map { arrayOf(it) }
    }
}
