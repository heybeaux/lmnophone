// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.voice

import android.content.Context
import java.io.File
import org.plainphone.device.voice.VoiceNumbers
import org.plainphone.device.voice.send.VoiceNumber

/**
 * Test build only: real sending is off unless adb set a number for Sam with TestHook `VOICE_SEND`
 * (shell-only). The number lives only in this app's no-backup storage on the test phone: never in source,
 * logs or evidence. Read when the voice screen's host is created, so a change needs the app restarted.
 * This marker is for the practice only; the family list has its own gate, [familySend].
 */
object VoiceSendBuild {
    fun marker(context: Context) = File(context.noBackupFilesDir, "test-voice-send")

    /** Null: real sending is off and every screen stays practice. Otherwise only Sam has a number. */
    fun numbers(context: Context): VoiceNumbers? {
        val number = try { marker(context).takeIf { it.isFile }?.readText()?.trim() } catch (_: Exception) { null }
        if (number == null || VoiceNumber.normalize(number) == null) return null
        return VoiceNumbers { personId -> number.takeIf { personId == "sam" } }
    }

    /**
     * Test build (PLA-513, build 47): real sending is on for every family person, to the number saved for them in
     * the family list. The debug build type is the whole gate: there is no marker. Release returns false.
     */
    fun familySend(): Boolean = true
}
