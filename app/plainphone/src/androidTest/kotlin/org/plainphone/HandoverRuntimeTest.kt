// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.plainphone.boot.PolicyRuntime
import org.plainphone.policy.*

/** New private emulator only. Enrolls a generated test PIN; never prints or captures credentials. */
class HandoverRuntimeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun nodes(node: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow): List<AccessibilityNodeInfo> =
        if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    private fun find(label: String) = nodes().firstOrNull { it.text?.toString() == label || it.contentDescription?.toString() == label }
    private fun await(label: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20000
        while (!condition()) { check(System.currentTimeMillis() < deadline) { label }; Thread.sleep(100) }
    }
    private fun tap(label: String, scroll: Boolean = false) {
        if (scroll) repeat(20) {
            if (find(label) == null) {
                nodes().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                Thread.sleep(250)
            }
        }
        await("missing control") { find(label) != null }
        fun clickable(): AccessibilityNodeInfo? {
            var node = find(label)
            while (node != null && !node.isClickable) node = node.parent
            return node?.takeIf { it.isEnabled }
        }
        await("control not enabled") { clickable() != null }
        check(clickable()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        instrumentation.waitForIdleSync()
        Thread.sleep(200)
    }
    private fun fields(secret: String, count: Int) {
        await("missing fields") { nodes().count { it.isEditable && it.isEnabled } == count }
        nodes().filter { it.isEditable && it.isEnabled }.forEach {
            check(it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, secret)
            }))
        }
        instrumentation.waitForIdleSync()
    }
    @Test fun freshEmulatorShowsActualReadbackThroughPinMenu() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "private emulator only" }
        val ctx = instrumentation.targetContext
        // Let normal Home startup/recovery finish before opening a PIN-owned page.
        instrumentation.startActivitySync(Intent(ctx, MainActivity::class.java)
            .addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("Home lock task") { ctx.getSystemService(android.app.ActivityManager::class.java).lockTaskModeState ==
            android.app.ActivityManager.LOCK_TASK_MODE_LOCKED }
        val runtime = PolicyRuntime.get(ctx)
        var report: HandoverReport? = null
        val read = CountDownLatch(1)
        runtime.checkPhone { report = it; read.countDown() }
        check(read.await(20, TimeUnit.SECONDS))
        val actual = checkNotNull(report)
        val status = actual.items.entries.joinToString { "${it.key}=${it.value}" }
        instrumentation.sendStatus(0, Bundle().apply { putString("handoverReadback", status) })
        assertEquals(HandoverState.OK, actual.items[HandoverItem.OWNER])
        assertEquals(HandoverState.OK, actual.items[HandoverItem.APPROVED_APPS])
        assertEquals(HandoverState.OK, actual.items[HandoverItem.EMERGENCY])
        assertEquals(HandoverState.NEEDS_HELPER, actual.items[HandoverItem.USB_DEBUGGING])
        assertFalse(actual.ready)
        val random = SecureRandom()
        val pin = CharArray(8) { ('0'.code + random.nextInt(10)).toChar() }
        // Arrange fresh synthetic credentials through the real enrollment API. No value is logged.
        val enrolled = CountDownLatch(1)
        var created = false
        runtime.enrollPin(pin.copyOf()) { result ->
            if (result is org.plainphone.device.authority.PinEnrollmentResult.Created) {
                result.code.clear(); created = true
            }
            enrolled.countDown()
        }
        check(enrolled.await(20, TimeUnit.SECONDS) && created) { "fresh emulator enrollment required" }
        val activity = instrumentation.startActivitySync(Intent(ctx, PinAccessActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            fields(pin.concatToString(), 1); tap("Continue")
            tap("Check this phone", scroll = true)
            await("readback screen") { nodes().any { it.contentDescription?.startsWith("LMNOPhone is in charge of this phone, OK") == true } }
            // Capture only this read-only page. Credential screens retain FLAG_SECURE.
            instrumentation.runOnMainSync { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            Thread.sleep(250)
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                File(ctx.cacheDir, "handover-top.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            repeat(4) {
                nodes().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                Thread.sleep(250)
            }
            await("USB readback") { nodes().any { it.contentDescription?.startsWith("USB debugging, needs the helper:") == true } }
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                File(ctx.cacheDir, "handover-bottom.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            instrumentation.runOnMainSync { activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            HandoverAccessibilityChecks.verifyAndReturn(instrumentation)
            await("returned to menu") { find("Your settings") != null }
            assertFalse(nodes().any { it.isEditable }) // Done did not request another PIN.
        } finally { pin.fill('\u0000'); instrumentation.runOnMainSync { activity.finish() } }
    }
}
