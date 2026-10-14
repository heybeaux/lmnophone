// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.voice

import android.content.Context
import org.plainphone.device.voice.VoiceNumbers

/** Only saved Family recipients can send. Old practice markers no longer enable sending. */
object VoiceSendBuild {
    fun numbers(context: Context): VoiceNumbers? = null
    fun familySend(): Boolean = true
}
