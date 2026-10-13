// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.voice

import android.content.Context
import org.plainphone.device.voice.VoicePerson

/** Release: real sending is off until the family list (slice 2b) supplies people with numbers. */
object VoiceSendBuild {
    @Suppress("UNUSED_PARAMETER")
    fun people(context: Context): List<VoicePerson>? = null
}
