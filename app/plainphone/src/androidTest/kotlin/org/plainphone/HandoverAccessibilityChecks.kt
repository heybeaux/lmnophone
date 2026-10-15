// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.app.Instrumentation
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.*

/** Assertions used on the actual PIN route; a standalone fixture is displaced by Home recovery. */
internal object HandoverAccessibilityChecks {
    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
        if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }

    fun verifyAndReturn(instrumentation: Instrumentation) {
        fun current() = nodes(instrumentation.uiAutomation.rootInActiveWindow)
        repeat(8) { current().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD); Thread.sleep(100) }
        assertTrue(current().any { it.text?.toString() == "Check this phone" && it.isHeading })
        val expected = listOf("LMNOPhone is in charge of this phone, OK", "Only approved apps open, OK",
            "Emergency calling, OK", "USB debugging, needs the helper:", "Wireless debugging, OK")
        val reached = mutableSetOf<String>()
        repeat(10) {
            val snapshot = current()
            for (label in expected) {
                val matches = snapshot.filter { it.contentDescription?.startsWith(label) == true }
                if (matches.isNotEmpty()) {
                    assertEquals("one spoken stop", 1, matches.size)
                    assertFalse(matches.single().isClickable)
                    assertTrue(nodes(matches.single()).drop(1).none { !it.text.isNullOrEmpty() || !it.contentDescription.isNullOrEmpty() })
                    reached += label
                }
            }
            assertTrue(snapshot.none { node -> listOf("✓", "›", "‹").any {
                node.text?.contains(it) == true || node.contentDescription?.contains(it) == true
            } })
            current().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            Thread.sleep(100)
        }
        assertEquals(expected.toSet(), reached)
        // Native key events, without Instrumentation.sendKeyDownUpSync's global idle wait.
        repeat(5) {
            if (current().none { it.text?.toString() == "Your settings" }) {
                for (code in listOf(KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ENTER)) {
                    val now = SystemClock.uptimeMillis()
                    assertTrue(instrumentation.uiAutomation.injectInputEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0), true))
                    assertTrue(instrumentation.uiAutomation.injectInputEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0), true))
                    Thread.sleep(150)
                }
            }
        }
        assertTrue(current().any { it.text?.toString() == "Your settings" })
        assertFalse(current().any { it.isEditable })
    }
}
