// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.voice

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.plainphone.MainActivity
import org.plainphone.ShellViewModel
import org.plainphone.device.voice.VoicePracticeSession
import org.plainphone.shell.ShellRoute
import org.plainphone.shell.ShellState
import org.plainphone.ui.user.VoiceUiEvent
import org.plainphone.ui.user.VoiceUiNotice
import org.plainphone.ui.user.VoiceUiState

/**
 * Emulator only, Device Owner set by adb first (PLA-479). Runs in the app process:
 *  1. on Review with a kept take, MainActivity.recreate() comes back on Review for the same person and take;
 *  2. recording, a real text-size change (font_scale 1.0 -> 1.3) stops the recorder, releases the microphone
 *     and comes back on Review with "Recording stopped." and the take. font_scale is put back to 1.0.
 * Results go to the `outcome` line; nothing is sent anywhere.
 */
class VoiceRecreateInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle) { super.onCreate(arguments); start() }

    private val resumed = LinkedBlockingQueue<MainActivity>()
    private val stops = java.util.Collections.synchronizedList(mutableListOf<String>())

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
            Thread.sleep(200)
        }
    }

    private fun session(a: MainActivity): VoicePracticeSession {
        val field = VoicePracticeHost::class.java.getDeclaredField("session").apply { isAccessible = true }
        return field.get(host(a)) as VoicePracticeSession
    }

    /**
     * The route's DisposableEffect opens the host only when the shell composes. On this emulator the home
     * gate stays at "checking" (no ready session), so the test calls the same open() the effect calls.
     */
    private fun opened(a: MainActivity, notes: MutableList<String>) {
        runOnMainSync {
            if (!session(a).showing) { host(a).open(); notes += "open() called by the test (gate not ready)" }
        }
    }

    private fun shellCommand(cmd: String) {
        uiAutomation.executeShellCommand(cmd).close()
    }

    private fun next(old: MainActivity): MainActivity {
        val end = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < end) {
            val a = resumed.poll(500, TimeUnit.MILLISECONDS) ?: continue
            if (a !== old) return a
        }
        error("MainActivity was not recreated")
    }

    override fun onStart() {
        val result = Bundle()
        val notes = mutableListOf<String>()
        val app = targetContext.applicationContext as Application
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) { if (a is MainActivity) resumed.add(a) }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityPaused(a: Activity) { if (a is MainActivity) stops += "paused place=${host(a).place()}" }
            override fun onActivityPrePaused(a: Activity) { if (a is MainActivity) stops += "prePaused place=${host(a).place()}" }
            override fun onActivityPreStopped(a: Activity) { if (a is MainActivity) stops += "preStopped place=${host(a).place()} changing=${a.isChangingConfigurations}" }
            override fun onActivityStopped(a: Activity) {
                if (a is MainActivity) stops += "stopped changing=${a.isChangingConfigurations} place=${shell(a).voicePlace} ui=${host(a).ui.javaClass.simpleName}"
            }
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "emulator only" }
            val audio = app.getSystemService(AudioManager::class.java)
            fun micOpen() = audio.activeRecordingConfigurations.isNotEmpty()
            shellCommand("settings put system font_scale 1.0")

            var activity = startActivitySync(Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            waitForIdleSync()
            resumed.clear()
            runOnMainSync {
                host(activity).store.delete("sam")
                shell(activity).state = ShellState(listOf(ShellRoute.Home, ShellRoute.More, ShellRoute.VoicePractice))
            }
            opened(activity, notes)
            waitFor("practice people list") { host(activity).ui as? VoiceUiState.ChoosePerson }
            runOnMainSync { host(activity).onEvent(VoiceUiEvent.Choose("sam")); host(activity).onEvent(VoiceUiEvent.Record) }
            Thread.sleep(2_500)
            runOnMainSync { host(activity).onEvent(VoiceUiEvent.Stop) }
            val review = waitFor("Review before recreate") { host(activity).ui as? VoiceUiState.Review }
            val draft = host(activity).store.draft("sam") ?: error("no take kept")
            val bytes = host(activity).store.file("sam").length()
            notes += "before: Review person=${review.person.name} take=${draft.savedAtMs} bytes=$bytes"

            // 1. Recreate on Review.
            val old = activity
            runOnMainSync { old.recreate() }
            activity = next(old)
            val a1 = activity
            opened(a1, notes)
            val back = waitFor("Review after recreate") { (host(a1).ui as? VoiceUiState.Review)?.takeIf { shell(a1).state.current == ShellRoute.VoicePractice } }
            check(back.person.id == "sam") { "other person after recreate: ${back.person}" }
            check(host(a1).store.draft("sam") == draft && host(a1).store.file("sam").length() == bytes) { "take changed" }
            check(!back.playing)
            notes += "recreate: Review person=${back.person.name} sameTake=true notice=${back.notice}"

            // 2. Recording, then a real text-size change.
            runOnMainSync { host(a1).onEvent(VoiceUiEvent.RecordAgain) }
            Thread.sleep(2_500)
            check(host(a1).ui is VoiceUiState.Recording && micOpen()) { "not recording before font change: ${host(a1).ui} mic=${micOpen()}" }
            shellCommand("settings put system font_scale 1.3")
            activity = next(a1)
            val a2 = activity
            opened(a2, notes)
            val stopped = waitFor("Review after font change") { host(a2).ui as? VoiceUiState.Review }
            Thread.sleep(500)
            check(!micOpen()) { "microphone still open after font change" }
            check(stopped.person.id == "sam") { "other person after font change: ${stopped.person}" }
            check(stopped.notice == VoiceUiNotice.Interrupted) { "notice=${stopped.notice}" }
            check(host(a2).ui is VoiceUiState.Review) { "recording again by itself: ${host(a2).ui}" }
            val kept = host(a2).store.draft("sam") ?: error("take lost")
            notes += "font 1.3 while recording: Review person=${stopped.person.name} notice=${stopped.notice} " +
                "mic=${micOpen()} take=${kept.savedAtMs} bytes=${host(a2).store.file("sam").length()} fontScale=${a2.resources.configuration.fontScale}"

            shellCommand("settings put system font_scale 1.0")
            activity = next(a2)
            val a3 = activity
            opened(a3, notes)
            val last = waitFor("Review after font restore") { host(a3).ui as? VoiceUiState.Review }
            notes += "font 1.0: Review person=${last.person.name} fontScale=${a3.resources.configuration.fontScale}"
            runOnMainSync { host(a3).store.delete("sam") }
            runOnMainSync { a3.finish() }

            result.putString("outcome", "PASS " + notes.joinToString("; "))
            finish(Activity.RESULT_OK, result)
        } catch (e: Throwable) {
            shellCommand("settings put system font_scale 1.0")
            result.putString("outcome", "FAIL: ${e.javaClass.simpleName}: ${e.message} | " + notes.joinToString("; ") + " | " + stops)
            finish(Activity.RESULT_CANCELED, result)
        } finally {
            app.unregisterActivityLifecycleCallbacks(callbacks)
        }
    }
}
