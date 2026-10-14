// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.voice

import android.content.Context
import java.io.File
import org.plainphone.device.voice.VoiceNumbers

/** Only saved Family recipients can send. Old practice markers no longer enable sending. */
object VoiceSendBuild {
    fun marker(context: Context) = File(context.noBackupFilesDir, "test-voice-send")
    fun numbers(context: Context): VoiceNumbers? = null
    fun familySend(): Boolean = true
}
