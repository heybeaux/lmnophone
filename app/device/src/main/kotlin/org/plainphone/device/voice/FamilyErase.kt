// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import java.io.File
import java.io.IOException
import org.plainphone.device.voice.replies.ReplyPlayedStore
import org.plainphone.device.voice.send.VoiceSendChannel

/**
 * Everything LMNOPhone keeps about one family person besides the list entry, under [root] (noBackupFilesDir):
 * the unsent message in every format and any partial take, the send record (PLA-513, build-47 observation) and
 * which replies were heard (PLA-527). [FamilyStore] calls [erase] before removing the person, turning voice
 * messages off or changing the number; a failure aborts that change. Never touches the practice.
 */
class FamilyErase(private val root: File) {
    /** Quiet when nothing is stored. @throws IOException if anything remains. */
    fun erase(personId: String) {
        val drafts = File(root, VoiceSendChannel.Family.draftDir)
        VoiceFormat.entries.forEach { format ->
            val store = VoiceDraftStore(drafts, format) { 0L }
            delete(store.file(personId)); delete(store.takeFile(personId))
        }
        val records = File(root, VoiceSendChannel.Family.recordDir)
        delete(File(records, "$personId.send")); delete(File(records, "$personId.send.tmp"))
        ReplyPlayedStore(File(root, ReplyPlayedStore.DIR)).erase(personId)
    }

    private fun delete(file: File) {
        if (file.exists() && !file.delete() && file.exists()) throw IOException("could not erase family record")
    }
}
