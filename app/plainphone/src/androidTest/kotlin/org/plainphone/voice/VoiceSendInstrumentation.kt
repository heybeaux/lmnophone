// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.voice

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.Bundle
import org.plainphone.MainActivity
import org.plainphone.ShellViewModel
import org.plainphone.device.voice.send.VoiceSendEngine
import org.plainphone.shell.ShellRoute
import org.plainphone.shell.ShellState
import org.plainphone.ui.user.VoiceUiEvent
import org.plainphone.ui.user.VoiceUiState

/**
 * Historical emulator harness for the retired practice-send route (PLA-499). The VOICE_SEND hook
 * no longer exists; this harness cannot enable sending. It records a fixture take, then calls the same
 * handler the Review screen's Send button calls, exactly once, and waits for the answer. Nothing else sends.
 * The `outcome` line names the screen and the stored reason; it never holds the number.
 */
class VoiceSendInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle) { super.onCreate(arguments); start() }

    private fun host(a: MainActivity): VoicePracticeHost {
        val field = MainActivity::class.java.getDeclaredField("voice\$delegate").apply { isAccessible = true }
        return (field.get(a) as Lazy<*>).value as VoicePracticeHost
    }

    private fun shell(a: MainActivity): ShellViewModel {
        val field = MainActivity::class.java.getDeclaredField("shell\$delegate").apply { isAccessible = true }
        return (field.get(a) as Lazy<*>).value as ShellViewModel
    }

    private fun <T : Any> waitFor(what: String, ms: Long = 20_000, read: () -> T?): T {
        val end = System.currentTimeMillis() + ms
        while (true) {
            var v: T? = null
            runOnMainSync { v = read() }
            v?.let { return it }
            check(System.currentTimeMillis() < end) { "timed out: $what" }
            Thread.sleep(250)
        }
    }

    override fun onStart() {
        val result = Bundle()
        val notes = mutableListOf<String>()
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "emulator only" }
            val app = targetContext.applicationContext
            val activity = startActivitySync(Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            waitForIdleSync()
            runOnMainSync {
                host(activity).store.delete("sam")
                VoiceSendEngine.get(app).forget("sam")
                shell(activity).state = ShellState(listOf(ShellRoute.Home, ShellRoute.More, ShellRoute.VoicePractice))
                host(activity).open()
            }
            val choose = waitFor("people list") { host(activity).ui as? VoiceUiState.ChoosePerson }
            notes += "choose practice=${choose.practice}"
            runOnMainSync { host(activity).onEvent(VoiceUiEvent.Choose("sam")); host(activity).onEvent(VoiceUiEvent.Record) }
            Thread.sleep(3_000)
            runOnMainSync { host(activity).onEvent(VoiceUiEvent.Stop) }
            val review = waitFor("Review") { host(activity).ui as? VoiceUiState.Review }
            val bytes = host(activity).store.file("sam").length()
            notes += "review canSend=${review.canSend} practice=${review.practice} take=$bytes bytes"
            check(review.canSend) { "Send not offered" }
            runOnMainSync { host(activity).onEvent(VoiceUiEvent.Send) }
            val first = waitFor("after Send") { host(activity).ui.takeIf { it !is VoiceUiState.Review } }
            notes += "after Send: ${first.javaClass.simpleName}"
            val end = waitFor("answer", ms = 330_000) {
                host(activity).ui.takeIf { it is VoiceUiState.Sent || it is VoiceUiState.NotSent }
            }
            val stored = VoiceSendEngine.get(app).view("sam")
            val draftKept = host(activity).store.draft("sam") != null
            notes += "answer: ${end.javaClass.simpleName}" +
                ((end as? VoiceUiState.NotSent)?.let { " problem=${it.problem}" } ?: "") +
                " stored=${stored?.status} failures=${stored?.failures} draftKept=$draftKept"
            runOnMainSync { activity.finish() }
            result.putString("outcome", "PASS " + notes.joinToString("; "))
            finish(Activity.RESULT_OK, result)
        } catch (e: Throwable) {
            result.putString("outcome", "FAIL: ${e.javaClass.simpleName}: ${e.message} | " + notes.joinToString("; "))
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
