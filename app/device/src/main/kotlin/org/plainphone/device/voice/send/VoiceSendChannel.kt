// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice.send

/**
 * Which drafts a send comes from. Each channel has its own drafts and its own send records, so a practice
 * person and a family person with the same id never share a send, and a confirmed Sent erases a draft only in
 * the channel that sent it. The key travels in the sentIntent, so a late answer after a restart finds the
 * right drafts. Folders are under noBackupFilesDir (credential-encrypted, never backed up).
 */
enum class VoiceSendChannel(val key: String, val draftDir: String, val recordDir: String) {
    /** The practice drafts (VoicePracticeHost). */
    Practice("practice", "voice-practice", "voice-send"),
    /** The family drafts (PLA-500, AndroidFamilyStore.drafts). */
    Family("family", "voice-family-drafts", "voice-send-family");

    companion object {
        /** No key means Practice: sends handed over before channels existed carry none. Unknown keys are refused. */
        fun fromExtra(key: String?): VoiceSendChannel? =
            if (key == null) Practice else entries.firstOrNull { it.key == key }
    }
}
