// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice.replies

import java.security.MessageDigest

/**
 * One received picture message (MMS) from the phone's inbox, read-only (PLA-527). [from] is the sender's
 * address as the messaging app stored it. Never logged: [toString] says nothing about it.
 */
data class MmsInboxMessage(val id: Long, val dateMs: Long, val from: String?, val parts: List<MmsPart>) {
    override fun toString() = "MmsInboxMessage(redacted)"
}

/** A part of [MmsInboxMessage]: its provider row id and declared content type. */
data class MmsPart(val id: Long, val contentType: String?) {
    override fun toString() = "MmsPart(redacted)"
}

/**
 * A family person's voice reply that can be played. [key] is opaque ([ReplyKey]); [partId] is the provider's
 * part row, played straight from the messaging store and never copied. Never logged.
 */
data class ReplyClip(val key: String, val personId: String, val dateMs: Long, val partId: Long, val contentType: String) {
    override fun toString() = "ReplyClip(redacted)"
}

/** Person id to that person's unplayed clips, oldest first, in family-list order. Never logged. */
class ReplyClips(private val clips: Map<String, List<ReplyClip>>) : Map<String, List<ReplyClip>> by clips {
    override fun toString() = "ReplyClips(redacted)"
    override fun equals(other: Any?) = clips == other
    override fun hashCode() = clips.hashCode()
}

/**
 * The opaque name a reply is remembered by in LMNOPhone's own storage: a salted SHA-256 of the provider's
 * message id and date, cut to 32 hex characters. It holds no phone number, and the ids can't be read back.
 */
object ReplyKey {
    private const val SALT = "lmnophone.voice-reply.v1"

    fun of(messageId: Long, dateMs: Long): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$SALT:$messageId:$dateMs".toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    val PATTERN = Regex("[0-9a-f]{32}")
}
