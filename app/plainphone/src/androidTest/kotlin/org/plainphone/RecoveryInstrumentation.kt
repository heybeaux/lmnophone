// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.app.Activity
import android.app.ActivityManager
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import java.io.FileInputStream

/** Fresh private emulator only. Real screens and storage; no secrets in output, no calls. */
class RecoveryInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle) { super.onCreate(arguments); start() }
    private fun nodes(node: AccessibilityNodeInfo? = uiAutomation.rootInActiveWindow): List<AccessibilityNodeInfo> =
        if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    private fun find(label: String) = nodes().firstOrNull { it.text?.toString() == label || it.contentDescription?.toString() == label }
    private fun waitFor(label: String, test: () -> Boolean) {
        val end = System.currentTimeMillis() + 20_000
        while (!test()) { check(System.currentTimeMillis() < end) { "timed out: $label" }; Thread.sleep(150) }
    }
    private fun tap(label: String, scroll: Boolean = false) {
        if (scroll) waitFor("menu ready") { find(targetContext.getString(org.plainphone.ui.R.string.admin_done_self)) != null }
        if (scroll) for (i in 0 until 24) {
            val node = nodes().firstOrNull { it.isScrollable } ?: break
            if (!node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) break
            Thread.sleep(600)
        }
        repeat(if (scroll) 24 else 1) {
            if (find(label) == null && scroll) {
                nodes().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                Thread.sleep(600)
                scrollTrace += listOf("History", "Change PIN", "New recovery code", "Turn LMNOPhone off", "Your settings", "Apps on this phone").filter { find(it) != null }.joinToString(",")
            }
        }
        waitFor(label) { find(label) != null }
        fun clickable(): AccessibilityNodeInfo? {
            var node = find(label)
            while (node != null && !node.isClickable) node = node.parent
            return node?.takeIf { it.isEnabled }
        }
        waitFor("enabled $label") { clickable() != null }
        check(clickable()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { "not clickable: $label" }
        waitForIdleSync(); Thread.sleep(250)
    }
    private fun fields(vararg values: String) {
        repeat(12) {
            if (nodes().count { it.isEditable && it.isEnabled } != values.size) {
                nodes().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                Thread.sleep(600)
            }
        }
        waitFor("input fields") { nodes().count { it.isEditable && it.isEnabled } == values.size }
        nodes().filter { it.isEditable && it.isEnabled }.zip(values.toList()).forEach { (node, value) ->
            check(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            })) { "input rejected" }
        }
        waitForIdleSync(); Thread.sleep(350)
    }
    private fun shell(command: String): String = uiAutomation.executeShellCommand(command).use { fd -> FileInputStream(fd.fileDescriptor).bufferedReader().readText() }
    private var phase = "start"
    private val scrollTrace = mutableListOf<String>()
    private var footerBottom = Int.MAX_VALUE
    private val codes = mutableListOf<String>()
    private fun readCode(): String {
        waitFor("issued code") { find("Here is your new recovery code") != null }
        repeat(12) {
            if (nodes().none { it.text?.toString()?.matches(Regex("[0-9a-f]{4}(-[0-9a-f]{4}){7}")) == true }) {
                nodes().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                Thread.sleep(600)
            }
        }
        waitFor("code text") { nodes().any { it.text?.toString()?.matches(Regex("[0-9a-f]{4}(-[0-9a-f]{4}){7}")) == true } }
        val code = nodes().first { it.text?.toString()?.matches(Regex("[0-9a-f]{4}(-[0-9a-f]{4}){7}")) == true }.text.toString()
        codes += code
        val button = find("I've written it down") ?: error("missing acknowledge")
        val bounds = Rect(); button.getBoundsInScreen(bounds)
        check(bounds.bottom <= footerBottom) { "acknowledge overlaps navigation bar" }
        tap("I've written it down")
        waitFor("PIN after acknowledgment") { find("Your PIN") != null }
        check(nodes().none { it.text?.toString() == code }) { "code retained after acknowledgment" }
        return code
    }
    private fun recover(code: String, newPin: String) {
        tap(if (find("Use recovery code") != null) "Use recovery code" else "Forgot PIN? Use recovery code")
        waitFor("recovery entry") { find("Enter recovery code") != null }
        fields(code); tap("Continue")
        waitFor("new PIN") { find("Choose a new PIN") != null }
        fields(newPin, newPin); tap("Continue")
    }
    override fun onStart() {
        val result = Bundle()
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish"))
            val manager = targetContext.getSystemService(ActivityManager::class.java)
            val before = shell("dumpsys device_policy").lines().filter { it.contains("LockTaskPolicy") }
            check(before.isNotEmpty()) { "missing lock task inventory" }
            startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitFor("Home locked") { manager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED }
            Thread.sleep(2_000)
            val activity = startActivitySync(Intent(targetContext, PinAccessActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            check(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            runOnMainSync {
                val view = activity.window.decorView
                footerBottom = view.height - view.rootWindowInsets.getInsets(WindowInsets.Type.navigationBars()).bottom
            }
            waitFor("fresh enrollment") { find("Choose a new PIN") != null }
            phase = "enrollment"
            fields("111111", "111111"); tap("Continue")
            val first = readCode()
            phase = "menu-entry"
            fields("111111"); tap("Continue")
            tap("New recovery code", scroll = true)
            phase = "rotation"
            waitFor("fresh PIN for rotation") { find("Your PIN") != null }
            fields("999999"); tap("Continue")
            waitFor("wrong PIN") { find("That PIN isn't right. Try again.") != null }
            fields("111111"); tap("Continue")
            val second = readCode(); check(first != second)
            phase = "old-code"
            recover(first, "222222")
            waitFor("old code refused") { find("That code isn't right. Check it against what you wrote down, and try again.") != null }
            phase = "uppercase-recovery"
            fields(second.uppercase()); tap("Continue")
            fields("222222", "222222"); tap("Continue")
            val third = readCode(); check(third != second)
            fields("111111"); tap("Continue")
            waitFor("old PIN refused") { find("That PIN isn't right. Try again.") != null }
            fields("222222"); tap("Continue")
            phase = "change-pin-route"
            tap("Change PIN", scroll = true)
            recover(third, "333333")
            val fourth = readCode()
            phase = "PIN-lockout-recovery"
            repeat(5) { fields("999999"); tap("Continue") }
            waitFor("PIN waiting") { find("Too many tries") != null }
            recover(fourth, "444444")
            readCode()
            fields("444444"); tap("Continue")
            waitFor("menu after recovery") { find("Your settings") != null }
            check(manager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED)
            val after = shell("dumpsys device_policy").lines().filter { it.contains("LockTaskPolicy") }
            check(before == after) { "lock task floor changed" }
            val logs = shell("logcat -d")
            check(codes.none { logs.contains(it, true) || logs.contains(it.replace("-", ""), true) }) { "secret found in logcat" }
            result.putString("outcome", "PASS: enrollment, fresh PIN rotation, old code refused, uppercase recovery, old PIN refused, new PIN works, change-PIN recovery route, recovery during PIN lockout, navigation-bar clearance, FLAG_SECURE, acknowledged code removed, unchanged lock task list, zero code hits in logcat. No calls.")
            finish(Activity.RESULT_OK, result)
        } catch (e: Throwable) {
            // Never print values, hierarchy, exceptions or assertion objects that may contain secrets.
            result.putString("scrollTrace", scrollTrace.takeLast(24).joinToString("|"))
            result.putString("outcome", "FAIL at " + phase + ": " + e.javaClass.simpleName + ": " + (e.message?.takeIf { message -> codes.none { message.contains(it, true) } } ?: "redacted"))
            finish(Activity.RESULT_CANCELED, result)
        } finally { codes.clear() }
    }
}
