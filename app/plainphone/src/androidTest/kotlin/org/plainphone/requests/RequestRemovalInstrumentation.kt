// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.requests

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.time.LocalDate
import org.plainphone.device.requests.RequestEngine
import org.plainphone.device.requests.RequestFileStore
import org.plainphone.policy.*

/** Private emulator only. Airplane mode and a synthetic recipient; never uses a live contact. */
class RequestRemovalInstrumentation : Instrumentation() {
    private var restart = false
    override fun onCreate(arguments: Bundle) {
        super.onCreate(arguments); restart = arguments.getString("restart") == "true"; start()
    }
    private fun node(text: String): AccessibilityNodeInfo? {
        fun find(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (n == null) return null
            if (n.text?.toString() == text) return n
            repeat(n.childCount) { find(n.getChild(it))?.let { match -> return match } }
            return null
        }
        return find(uiAutomation.rootInActiveWindow)
    }
    private fun waitText(text: String): AccessibilityNodeInfo {
        repeat(40) {
            node(text)?.let { return it }
            fun scroll(n: AccessibilityNodeInfo?): Boolean {
                if (n == null) return false
                if (n.isScrollable && n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
                repeat(n.childCount) { if (scroll(n.getChild(it))) return true }
                return false
            }
            if (it > 5) scroll(uiAutomation.rootInActiveWindow)
            Thread.sleep(100)
        }
        error("UI text missing")
    }
    private fun click(text: String) {
        var target: AccessibilityNodeInfo? = waitText(text)
        while (target != null && !target.isClickable) target = target.parent
        check(target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        waitForIdleSync(); Thread.sleep(250)
    }
    override fun onStart() {
        val out = Bundle(); var step = "preflight"
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish"))
            check(Settings.Global.getInt(targetContext.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1)
            val engine = RequestEngine.get(targetContext)
            val file = File(targetContext.noBackupFilesDir, "app-requests")
            if (restart) {
                step = "restart"
                check(engine.view().requests.isEmpty())
                check(engine.submit("after-restart", "Maps app").status == RequestStatus.DailyLimit)
                check(!file.readBytes().toString(Charsets.ISO_8859_1).contains("The ferry bus app"))
            } else {
                step = "seed"
                RequestFileStore(file).write(RequestBook(RequestRecipient("Jo", "555-0100")))
                repeat(5) { check(engine.submit("r$it", "The ferry bus app $it").status == RequestStatus.NoSignal) }
                (1..4).forEach { check(engine.remove("r$it")) }
                step = "history"
                val activity = startActivitySync(Intent(targetContext, RequestsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(RequestsActivity.HISTORY, true))
                waitText("The ferry bus app 0")
                step = "not-now"
                click("Not now")
                step = "keep"
                waitText("Remove this request? the person using this phone won't be told.")
                click("Keep it")
                check(engine.view().requests.single().id == "r0")
                step = "remove"
                click("Not now"); click("Remove")
                waitText("No requests saved on this phone.")
                check(engine.view().requests.isEmpty())
                check(engine.submit("six", "Maps app").status == RequestStatus.DailyLimit)
                check(!engine.result("r0", 0, RequestPart.Accepted))
                check(!file.readBytes().toString(Charsets.ISO_8859_1).contains("The ferry bus app"))
                check(RequestFileStore(file).read().removed.count { it.day == LocalDate.now().toEpochDay() } == 5)
                step = "user"
                runOnMainSync { activity.finish() }
                val user = startActivitySync(Intent(targetContext, RequestsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                waitText("Your message")
                check(node("The ferry bus app 0") == null && node("Not now") == null)
                runOnMainSync { user.finish() }
            }
            out.putString("outcome", "PASS removal/keep; text erased; quota retained; callbacks ignored; user list clear; restart=$restart")
            finish(Activity.RESULT_OK, out)
        } catch (e: Throwable) {
            out.putString("outcome", "FAIL ${e.javaClass.simpleName} step=$step")
            finish(Activity.RESULT_CANCELED, out)
        }
    }
}
