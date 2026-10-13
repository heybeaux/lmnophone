// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import java.io.File
import java.io.IOException

/**
 * Codec and container; the host maps this to MediaRecorder settings. Default (Lead, PLA-457):
 * AAC-LC mono in an MPEG-4 `.m4a` file, which plays on most phones. AMR-NB in a raw `.amr` file
 * (the classic MMS voice format) stays as the fallback. [header] is the magic at [magicOffset].
 */
enum class VoiceFormat(val extension: String, val mimeType: String, val channels: Int,
    val header: ByteArray, val magicOffset: Int, val sampleRate: Int, val bitRate: Int, val bytesPerSecond: Int) {
    /** 24 kbit/s is 3000 bytes a second of audio; 352 more allow for the growing MP4 sample tables (PLA-499). */
    AacM4a("m4a", "audio/mp4", 1, "ftyp".toByteArray(Charsets.US_ASCII), 4, 44_100, 24_000, 3_352),
    /** 12.2 kbit/s is 1525 bytes a second plus one frame header byte in 32. */
    AmrNb("amr", "audio/amr", 1, "#!AMR\n".toByteArray(Charsets.US_ASCII), 0, 8_000, 12_200, 1_600);

    /** True when [file] has this format's magic and holds at least one byte of audio after it. */
    fun looksRecorded(file: File): Boolean = try {
        val end = magicOffset + header.size
        file.length() > end + (if (this == AacM4a) 4 else 0) && file.inputStream().use { input ->
            val start = ByteArray(end)
            input.read(start) == end && start.copyOfRange(magicOffset, end).contentEquals(header)
        }
    } catch (_: IOException) { false }
}

/**
 * One practice draft per person, as `<personId>.<extension>` in [dir]. The host passes an app-private,
 * credential-encrypted, no-backup directory (Context.noBackupFilesDir), so drafts are unreadable
 * before the first unlock and never leave the phone in a backup.
 *
 * Retention: a draft is kept until it is deleted (or, in a later slice, sent), and is pruned
 * [RETENTION_MS] (7 days) after it was saved. A new take is written to `<personId>.take.<extension>` and
 * replaces the draft only once it is complete (stopped, at least 1 s, valid header), so an interrupted take never destroys the old draft.
 */
open class VoiceDraftStore(val dir: File, private val format: VoiceFormat, private val clock: () -> Long) {
    constructor(dir: File, clock: () -> Long) : this(dir, VoiceFormat.AacM4a, clock)

    fun file(personId: String): File = File(dir, "${checked(personId)}.${format.extension}")
    fun takeFile(personId: String): File = File(dir, "${checked(personId)}.take.${format.extension}")

    /** Makes sure the directory exists before the recorder opens a take. */
    fun prepareTake(personId: String): File {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("voice draft folder unavailable")
        return takeFile(personId).also { it.delete() }
    }

    fun draft(personId: String): VoiceDraft? {
        val f = file(personId)
        return if (f.isFile && format.looksRecorded(f)) VoiceDraft(personId, f.lastModified()) else null
    }

    /** Replaces the draft with the finished take. Null (and no take left behind) if the take is unusable. */
    open fun keepTake(personId: String): VoiceDraft? {
        val take = takeFile(personId)
        if (!format.looksRecorded(take)) { take.delete(); return null }
        val now = clock()
        take.setLastModified(now)
        if (!take.renameTo(file(personId))) { take.delete(); throw IOException("could not keep draft") }
        return VoiceDraft(personId, now)
    }

    fun discardTake(personId: String) { takeFile(personId).delete() }

    fun delete(personId: String) { takeFile(personId).delete(); file(personId).delete() }

    /** Removes leftover takes and drafts older than [RETENTION_MS]. Never throws. */
    fun prune() {
        val files = dir.listFiles() ?: return
        val now = clock()
        for (f in files) {
            val take = f.name.endsWith(".take.${format.extension}")
            if (take || now - f.lastModified() > RETENTION_MS) f.delete()
        }
    }

    private fun checked(personId: String): String {
        require(personId.matches(Regex("[a-z0-9]{1,32}"))) { "not a practice person id" }
        return personId
    }

    companion object {
        const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    }
}
