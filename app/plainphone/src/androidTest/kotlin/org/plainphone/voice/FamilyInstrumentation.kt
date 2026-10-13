// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.voice

import android.app.Activity
import android.app.ActivityManager
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import org.plainphone.MainActivity
import org.plainphone.PlainphoneAdminReceiver
import org.plainphone.device.voice.*
import org.plainphone.ui.trusted.*

/** Private synthetic emulator only. Never taps a send control or calls any number. */
class FamilyInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle) { super.onCreate(arguments); start() }
    private fun waitFor(label: String, read: () -> Boolean) {
        val end = System.currentTimeMillis() + 20_000
        while (!read()) { check(System.currentTimeMillis() < end) { "timed out: $label" }; Thread.sleep(200) }
    }
    private fun find(label: String, node: AccessibilityNodeInfo? = uiAutomation.rootInActiveWindow): AccessibilityNodeInfo? {
        node ?: return null
        if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
        for (i in 0 until node.childCount) find(label, node.getChild(i))?.let { return it }
        return null
    }
    private fun tap(label: String) {
        waitFor(label) { find(label) != null }
        var node = find(label)
        while (node != null && !node.isClickable) node = node.parent
        check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { "not clickable: $label" }
        waitForIdleSync()
    }
    private fun shot(name: String) {
        Thread.sleep(350)
        val image = uiAutomation.takeScreenshot() ?: error("screenshot unavailable")
        File(targetContext.filesDir, "family-$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    override fun onStart() {
        val result = Bundle()
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "emulator only" }
            check(!targetContext.isDeviceProtectedStorage)
            val store = AndroidFamilyStore.open(targetContext)
            check(store.read().people.all { it.id == "testsam" }) { "requires empty synthetic family store" }
            store.read().people.forEach { store.remove(it.id) }
            store.save(FamilyPerson("testsam", "Sam", "Sam", "555-0100"))
            check(AndroidMicAccess(targetContext, ComponentName(targetContext, PlainphoneAdminReceiver::class.java)).selfGrant())
            store.setEnabled(true)
            val intent = FamilyMessages.intent(store.recipient("testsam")!!)
            check(intent.action == Intent.ACTION_SENDTO)
            check(intent.`package` == "com.google.android.apps.messaging")
            check(intent.data?.scheme == "smsto" && intent.data?.schemeSpecificPart == "555-0100")
            check(intent.extras == null && intent.flags == 0)
            startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitFor("Family on production Home") { find("Family, voice messages") != null }
            shot("home")
            tap("Family, voice messages")
            waitFor("people list") { find("Who do you want to talk to?") != null }
            shot("people")
            tap("Sam")
            waitFor("Messages action") { find(targetContext.getString(org.plainphone.ui.R.string.family_messages, "Sam")) != null }
            shot("person")
            val activityManager = targetContext.getSystemService(ActivityManager::class.java)
            check(activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED)
            tap(targetContext.getString(org.plainphone.ui.R.string.family_messages, "Sam"))
            waitFor("Google Messages foreground") { uiAutomation.rootInActiveWindow?.packageName?.toString() == "com.google.android.apps.messaging" }
            waitFor("selected conversation") { find("555-0100") != null }
            Thread.sleep(1200) // Wait for the conversation frame, not its transient launch activity.
            check(uiAutomation.rootInActiveWindow?.packageName?.toString() == "com.google.android.apps.messaging")
            check(activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED)
            shot("messages")
            repeat(3) {
                if (uiAutomation.rootInActiveWindow?.packageName?.toString() != targetContext.packageName) {
                    sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); Thread.sleep(500)
                }
            }
            waitFor("Back returns to LMNOPhone") { uiAutomation.rootInActiveWindow?.packageName?.toString() == targetContext.packageName }
            check(activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED)
            // Verify the production erasure adapter, including unfinished takes and both formats.
            VoiceFormat.entries.forEach { format ->
                val drafts = VoiceDraftStore(AndroidFamilyStore.drafts(targetContext), format, System::currentTimeMillis)
                drafts.prepareTake("testsam").writeText("synthetic unfinished take")
                drafts.file("testsam").writeText("synthetic draft")
            }
            store.remove("testsam")
            check(AndroidFamilyStore.drafts(targetContext).listFiles().orEmpty().isEmpty())
            result.putString("outcome", "PASS: CE family; explicit SENDTO; production Home→Family→person→Messages; lock task before/during/after; Back returns; all-format draft removal. No send or call.")
            finish(Activity.RESULT_OK, result)
        } catch (e: Throwable) {
            result.putString("outcome", "FAIL: ${e.javaClass.simpleName}: ${e.message}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
