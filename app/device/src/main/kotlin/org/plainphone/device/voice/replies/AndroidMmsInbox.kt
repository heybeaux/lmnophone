// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice.replies

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager

/**
 * The phone's MMS inbox, read with READ_SMS only (PLA-458 spike, PLA-527). LMNOPhone isn't the SMS app and never
 * writes here. Only downloaded received messages (m-retrieve-conf, m_type 132) that have an audio part: one query
 * for the newest inbox rows, one for every audio part, and one sender lookup per message with audio. Never throws
 * and never logs: an unreadable store reads as empty.
 */
class AndroidMmsInbox(context: Context) {
    private val app = context.applicationContext

    fun read(): List<MmsInboxMessage> = try { query() } catch (_: Exception) { emptyList() }

    /** The phone's own country rules for comparing a national number with an international one. Null: unknown. */
    fun e164(number: String): String? = try {
        val telephony = app.getSystemService(TelephonyManager::class.java)
        val country = telephony?.networkCountryIso?.takeIf { it.isNotBlank() } ?: telephony?.simCountryIso
        country?.takeIf { it.isNotBlank() }?.let { PhoneNumberUtils.formatNumberToE164(number, it.uppercase()) }
    } catch (_: Exception) { null }

    private fun query(): List<MmsInboxMessage> {
        if (app.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val resolver = app.contentResolver
        val inbox = LinkedHashMap<Long, Long>()
        resolver.query(MMS, arrayOf("_id", "date"), "msg_box = 1 AND m_type = 132", null, "date DESC")?.use { c ->
            val id = c.getColumnIndexOrThrow("_id")
            val date = c.getColumnIndexOrThrow("date")
            // The provider stores seconds.
            while (c.moveToNext() && inbox.size < MAX_MESSAGES) inbox[c.getLong(id)] = c.getLong(date) * 1_000
        }
        if (inbox.isEmpty()) return emptyList()
        val parts = HashMap<Long, MutableList<MmsPart>>()
        resolver.query(PARTS, arrayOf("_id", "mid", "ct"), "ct LIKE 'audio/%'", null, null)?.use { c ->
            val id = c.getColumnIndexOrThrow("_id")
            val mid = c.getColumnIndexOrThrow("mid")
            val ct = c.getColumnIndexOrThrow("ct")
            while (c.moveToNext()) {
                val message = c.getLong(mid)
                if (message in inbox) parts.getOrPut(message) { mutableListOf() } += MmsPart(c.getLong(id), c.getString(ct))
            }
        }
        return inbox.mapNotNull { (id, dateMs) ->
            val audio = parts[id] ?: return@mapNotNull null
            MmsInboxMessage(id, dateMs, sender(id), audio)
        }
    }

    private fun sender(messageId: Long): String? =
        app.contentResolver.query(Uri.parse("content://mms/$messageId/addr"), arrayOf("address"), "type = 137", null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    companion object {
        private val MMS: Uri = Uri.parse("content://mms")
        private val PARTS: Uri = Uri.parse("content://mms/part")
        /** Enough for the latest five from each of the family list's people. */
        const val MAX_MESSAGES = 100
        fun partUri(partId: Long): Uri = Uri.parse("content://mms/part/$partId")
    }
}
