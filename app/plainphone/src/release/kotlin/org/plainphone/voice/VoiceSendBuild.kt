// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.voice

import android.content.Context
import org.plainphone.device.voice.VoiceNumbers

/** Release: real sending is off until the family list (slice 2b) supplies numbers. */
object VoiceSendBuild {
    @Suppress("UNUSED_PARAMETER")
    fun numbers(context: Context): VoiceNumbers? = null
}
