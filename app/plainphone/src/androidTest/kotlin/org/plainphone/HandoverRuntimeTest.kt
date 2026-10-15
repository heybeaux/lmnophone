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
        var node = find(label)
        while (node != null && !node.isClickable) node = node.parent
        check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        instrumentation.waitForIdleSync()
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
        val runtime = PolicyRuntime.get(ctx)
        var report: HandoverReport? = null
        val read = CountDownLatch(1)
        runtime.checkPhone { report = it; read.countDown() }
        check(read.await(20, TimeUnit.SECONDS))
        val actual = checkNotNull(report)
        val status = actual.items.entries.joinToString { "${it.key}=${it.value}" }
        instrumentation.sendStatus(0, Bundle().apply { putString("handoverReadback", status) })
        val plan = org.plainphone.preview.Preview2Device(ctx).plan(false)
        val port = org.plainphone.device.DpmPort(ctx,
            android.content.ComponentName(ctx, PlainphoneAdminReceiver::class.java),
            android.content.ComponentName(ctx.packageName, "org.plainphone.boot.FlushMarker"))
        val floorFailures = plan.neverHide.filter { port.packageState(it)?.let { s -> s.hidden || s.suspended || !s.enabled } != false }
        val route = org.plainphone.device.AndroidHandoverReads(ctx, port).emergencyRoute(plan)
        instrumentation.sendStatus(0, Bundle().apply { putString("handoverDiagnostic", "floor=$floorFailures route=$route") })
        for (action in listOf("com.android.phone.EmergencyDialer.DIAL", "android.intent.action.DIAL_EMERGENCY")) {
            val pm = ctx.packageManager
            val flags = android.content.pm.PackageManager.MATCH_SYSTEM_ONLY or android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES or
                android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS or android.content.pm.PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
            for (r in pm.queryIntentActivities(Intent(action), flags)) {
                val a = r.activityInfo
                val n = org.plainphone.core.PackageName(a.packageName)
                instrumentation.sendStatus(0, Bundle().apply { putString("handoverComponent", "${a.name} manifest=${a.enabled} override=${pm.getComponentEnabledSetting(android.content.ComponentName(a.packageName,a.name))} floor=${n in plan.neverHide} lock=${n in plan.lockTaskPackages} state=${port.packageState(n)}") })
            }
        }
        assertEquals(HandoverState.OK, actual.items[HandoverItem.OWNER])
        assertEquals(HandoverState.OK, actual.items[HandoverItem.APPROVED_APPS])
        assertEquals(HandoverState.OK, actual.items[HandoverItem.EMERGENCY])
        assertEquals(HandoverState.NEEDS_HELPER, actual.items[HandoverItem.USB_DEBUGGING])
        assertFalse(actual.ready)
        val activity = instrumentation.startActivitySync(Intent(ctx, PinAccessActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        // This test deliberately refuses a configured phone instead of guessing an existing PIN.
        await("fresh PIN setup required") { find("Type it again") != null }
        val random = SecureRandom()
        val pin = CharArray(8) { ('0'.code + random.nextInt(10)).toChar() }
        try {
            fields(pin.concatToString(), 2); tap("Continue")
            await("recovery acknowledgement") { find("I've written it down") != null }
            tap("I've written it down")
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
            tap("Done")
            await("returned to menu") { find("Your settings") != null }
            assertFalse(nodes().any { it.isEditable }) // Done did not request another PIN.
        } finally { pin.fill('\u0000'); instrumentation.runOnMainSync { activity.finish() } }
    }
}
