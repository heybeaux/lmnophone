// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import android.content.Context
import java.io.File

object AndroidFamilyStore {
    private fun credentialContext(context: Context): Context {
        // The app does not opt into defaultToDeviceProtectedStorage. Reject an accidentally supplied DP context.
        require(!context.isDeviceProtectedStorage) { "family requires credential storage" }
        return context
    }
    fun drafts(context: Context) = File(credentialContext(context).noBackupFilesDir, "voice-family-drafts")
    fun open(context: Context): FamilyStore {
        val privateContext = credentialContext(context)
        // Removal, turning off and a number change erase the drafts, send record and heard replies (PLA-527).
        return FamilyStore(File(privateContext.noBackupFilesDir, "voice-family"), FamilyErase(privateContext.noBackupFilesDir)::erase)
    }
}
