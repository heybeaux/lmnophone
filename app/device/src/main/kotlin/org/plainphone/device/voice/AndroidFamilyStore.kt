// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import android.content.Context
import java.io.File
import java.io.IOException

object AndroidFamilyStore {
    private fun credentialContext(context: Context): Context {
        // The app does not opt into defaultToDeviceProtectedStorage. Reject an accidentally supplied DP context.
        require(!context.isDeviceProtectedStorage) { "family requires credential storage" }
        return context
    }
    fun drafts(context: Context) = File(credentialContext(context).noBackupFilesDir, "voice-family-drafts")
    fun open(context: Context): FamilyStore {
        val privateContext = credentialContext(context)
        return FamilyStore(File(privateContext.noBackupFilesDir, "voice-family")) { id ->
            // Erase every supported format, including a partial take, without looking at message contents.
            VoiceFormat.entries.forEach { format ->
                val store = VoiceDraftStore(drafts(privateContext), format, System::currentTimeMillis)
                listOf(store.file(id), store.takeFile(id)).forEach { file ->
                    if (file.exists() && !file.delete()) throw IOException("could not erase family draft")
                }
            }
        }
    }
}
