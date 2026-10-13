// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.voice

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.os.Process
import java.io.File
import org.plainphone.MainActivity
import org.plainphone.PlainphoneAdminReceiver
import org.plainphone.device.voice.AndroidMicAccess
import org.plainphone.device.voice.AndroidVoiceRecorder
import org.plainphone.device.voice.VoiceFormat
import org.plainphone.device.voice.VoiceRecorder
import org.plainphone.ui.user.VoiceUiEvent
import org.plainphone.ui.user.VoiceUiState

/**
 * Emulator only, Device Owner set by adb first (PLA-457). Runs in the app process:
 *  1. the Device Owner grants RECORD_AUDIO to itself and it reads back granted (no prompt);
 *  2. the AAC .m4a default and the AMR fallback each write a real file with a valid header;
 *  3. MainActivity.onStop stops a live practice recording: the microphone is released and the
 *     take is kept or dropped (never left recording).
 * Results go to the `outcome` line; nothing is sent anywhere.
 */
class VoicePracticeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle) { super.onCreate(arguments); start() }

    override fun onStart() {
        val result = Bundle()
        val notes = mutableListOf<String>()
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "emulator only" }
            val app = targetContext
            val admin = ComponentName(app, PlainphoneAdminReceiver::class.java)
            val audio = app.getSystemService(AudioManager::class.java)
            fun micOpen() = audio.activeRecordingConfigurations.isNotEmpty()

            val mic = AndroidMicAccess(app, admin)
            val before = mic.granted()
            check(mic.selfGrant()) { "self-grant did not read back granted (before=$before)" }
            notes += "grant before=$before after=true"

            for (format in VoiceFormat.entries) {
                val file = File(app.cacheDir, "check.${format.extension}").apply { delete() }
                val events = mutableListOf<String>()
                val listener = object : VoiceRecorder.Listener {
                    override fun onMaxLength() { events += "max" }
                    override fun onError() { events += "error" }
                    override fun onSilenced() { events += "silenced" }
                    override fun onStartFailed() { events += "startFailed" }
                }
                val recorder = AndroidVoiceRecorder(app)
                runOnMainSync { recorder.start(file, format, 60_000, listener) }
                Thread.sleep(2_500)
                check(micOpen()) { "${format.name}: microphone not open while recording ($events)" }
                var kept = false
                runOnMainSync { kept = recorder.stop() }
                Thread.sleep(300)
                check(!micOpen()) { "${format.name}: microphone still open after stop" }
                check(kept && file.length() > 0 && format.looksRecorded(file)) {
                    "${format.name}: kept=$kept bytes=${file.length()} header=${format.looksRecorded(file)}"
                }
                val ms = MediaMetadataRetriever().run {
                    try { setDataSource(file.path); extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) }
                    finally { release() }
                }
                notes += "${format.name} bytes=${file.length()} durationMs=$ms events=$events"
                file.delete()
            }

            val activity = startActivitySync(Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            waitForIdleSync()
            val field = MainActivity::class.java.getDeclaredField("voice\$delegate").apply { isAccessible = true }
            val host = (field.get(activity) as Lazy<*>).value as VoicePracticeHost
            host.store.delete("sam")
            runOnMainSync { host.open(); host.onEvent(VoiceUiEvent.Choose("sam")); host.onEvent(VoiceUiEvent.Record) }
            Thread.sleep(2_500)
            val during = host.ui
            check(during is VoiceUiState.Recording && micOpen()) { "not recording before onStop: $during mic=${micOpen()}" }
            runOnMainSync { callActivityOnStop(activity) }
            Thread.sleep(500)
            val after = host.ui
            check(!micOpen()) { "microphone still open after onStop ($after)" }
            check(after !is VoiceUiState.Recording) { "still shows Recording after onStop" }
            val draft = host.store.file("sam")
            notes += "onStop: ${after.javaClass.simpleName} draftBytes=${draft.length()} " +
                "header=${VoiceFormat.AacM4a.looksRecorded(draft)}"
            if (after is VoiceUiState.Review) check(draft.length() > 0 && VoiceFormat.AacM4a.looksRecorded(draft))
            host.store.delete("sam")
            check(!draft.exists())
            runOnMainSync { activity.finish() }

            result.putString("outcome", "PASS uid=${Process.myUid()} " + notes.joinToString("; "))
            finish(Activity.RESULT_OK, result)
        } catch (e: Throwable) {
            result.putString("outcome", "FAIL: ${e.javaClass.simpleName}: ${e.message} | " + notes.joinToString("; "))
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
