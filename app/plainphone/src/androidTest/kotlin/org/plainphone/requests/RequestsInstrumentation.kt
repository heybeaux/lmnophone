// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.requests

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import org.plainphone.device.requests.RequestEngine
import org.plainphone.policy.*

/** Explicit emulator-only airplane check. Fixed fictional number, never any existing contact. */
class RequestsInstrumentation : Instrumentation() {
    private var readOnly = false
    private var uiSend = false
    override fun onCreate(arguments: Bundle) { super.onCreate(arguments); readOnly = arguments.getString("readOnly") == "true"; uiSend = arguments.getString("uiSend") == "true"; start() }
    private fun waitNode(predicate: (android.view.accessibility.AccessibilityNodeInfo) -> Boolean): android.view.accessibility.AccessibilityNodeInfo {
        fun find(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
            if (node == null) return null
            if (predicate(node)) return node
            repeat(node.childCount) { find(node.getChild(it))?.let { found -> return found } }
            return null
        }
        repeat(40) { find(uiAutomation.rootInActiveWindow)?.let { return it }; Thread.sleep(250) }
        error("request UI node unavailable")
    }
    override fun onStart() {
        val output = Bundle()
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish"))
            check(Settings.Global.getInt(targetContext.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1)
            val engine = RequestEngine.get(targetContext)
            if (!readOnly) {
                check(engine.view().requests.isEmpty() && engine.recipient() == null)
                engine.setRecipient(RequestRecipient("Sam", "555-0100"))
                val outcome = engine.submit("airplane-test", "The bus times app")
                check(outcome.status == RequestStatus.NoSignal)
            }
            check(engine.recipient() == RequestRecipient("Sam", "555-0100"))
            val row = engine.view().requests.first { it.id == "airplane-test" }
            check(row.text == "The bus times app" && row.status == RequestStatus.NoSignal)
            val activity = startActivitySync(Intent(targetContext, RequestsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(RequestsActivity.HISTORY, !uiSend))
            waitForIdleSync()
            if (uiSend) {
                val before = engine.view().requests.size
                val field = waitNode { it.contentDescription?.toString() == "Your message" && it.isEditable }
                check(field.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply { putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Maps for walking") }))
                val send = waitNode { it.text?.toString() == "Send" && it.isClickable && it.isEnabled }
                check(send.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
                waitNode { it.text?.toString() == "Not sent" }
                check(engine.view().requests.size == before + 1)
                check(engine.view().requests.first().status == RequestStatus.NoSignal)
            } else waitNode { it.text?.toString() == "Not sent" }
            Thread.sleep(500)
            uiAutomation.takeScreenshot().let { bitmap ->
                java.io.FileOutputStream(java.io.File(targetContext.cacheDir, "requests-proof.png")).use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            output.putString("outcome", "PASS airplane mode; Not sent/NoSignal; saved requests and recipient read back; readOnly=$readOnly; uiSend=$uiSend")
            finish(Activity.RESULT_OK, output)
        } catch (e: Throwable) {
            output.putString("outcome", "FAIL " + e.javaClass.simpleName)
            finish(Activity.RESULT_CANCELED, output)
        }
    }
}
