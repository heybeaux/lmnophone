// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.voice

import android.content.Context
import org.plainphone.device.voice.VoiceNumbers

/** Release: real sending is off, for the family list and the practice alike (PLA-513). */
object VoiceSendBuild {
    @Suppress("UNUSED_PARAMETER")
    fun numbers(context: Context): VoiceNumbers? = null

    fun familySend(): Boolean = false
}
