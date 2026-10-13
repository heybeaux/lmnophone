// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice.send

import org.plainphone.device.voice.mms.InvalidHeaderValueException
import org.plainphone.device.voice.mms.pdu.EncodedStringValue
import org.plainphone.device.voice.mms.pdu.PduBody
import org.plainphone.device.voice.mms.pdu.PduComposer
import org.plainphone.device.voice.mms.pdu.PduHeaders
import org.plainphone.device.voice.mms.pdu.PduPart
import org.plainphone.device.voice.mms.pdu.SendReq

/** A phone number reduced to what MMS addresses: an optional leading `+` and 3 to 20 digits. */
object VoiceNumber {
    private val FORMATTING = setOf(' ', '-', '.', '(', ')', '\t', '\u00A0')
    private val NUMBER = Regex("\\+?[0-9]{3,20}")

    /** Null for anything that is not one phone number. Never logs the input. */
    fun normalize(input: String): String? {
        val stripped = input.filterNot { it in FORMATTING }
        return stripped.takeIf { NUMBER.matches(it) }
    }
}

/**
 * Builds the m-send-req that SmsManager.sendMultimediaMessage hands to the carrier (PLA-458 transport spike):
 * multipart/mixed with one AAC `.m4a` part, the To address and no text or SMIL part. No From header: the
 * composer writes the insert-address token and the carrier fills in this phone's number.
 */
object VoiceMmsPdu {
    const val CONTENT_TYPE = "application/vnd.wap.multipart.mixed"
    const val PART_TYPE = "audio/mp4"
    const val PART_NAME = "voice.m4a"

    /** @throws IllegalArgumentException for a bad number or an empty recording. */
    fun sendRequest(number: String, audio: ByteArray, dateSeconds: Long): ByteArray {
        val to = requireNotNull(VoiceNumber.normalize(number)) { "not a phone number" }
        require(audio.isNotEmpty()) { "empty recording" }
        val req = SendReq()
        req.setContentType(CONTENT_TYPE.toByteArray(Charsets.US_ASCII))
        req.setTo(EncodedStringValue.encodeStrings(arrayOf(to)))
        req.setDate(dateSeconds)
        req.setMessageClass(PduHeaders.MESSAGE_CLASS_PERSONAL_STR.toByteArray(Charsets.US_ASCII))
        try {
            req.setPriority(PduHeaders.PRIORITY_NORMAL)
            req.setDeliveryReport(PduHeaders.VALUE_NO)
            req.setReadReport(PduHeaders.VALUE_NO)
        } catch (e: InvalidHeaderValueException) {
            throw IllegalStateException("fixed header refused", e)
        }
        val part = PduPart().apply {
            setContentType(PART_TYPE.toByteArray(Charsets.US_ASCII))
            setName(PART_NAME.toByteArray(Charsets.US_ASCII))
            setContentLocation(PART_NAME.toByteArray(Charsets.US_ASCII))
            setContentId("<$PART_NAME>".toByteArray(Charsets.US_ASCII))
            setData(audio)
        }
        req.body = PduBody().apply { addPart(part) }
        req.messageSize = audio.size.toLong()
        val pdu = try { PduComposer(req).make() } catch (e: RuntimeException) {
            throw IllegalStateException("could not compose the message", e)
        }
        return checkNotNull(pdu) { "could not compose the message" }
    }
}

/**
 * A small, bounds-checked WAP-230/OMA MMS decoder: enough to check our own m-send-req and to read the
 * Response-Status of the carrier's m-send-conf. Walks the headers by their encoding, never by searching bytes.
 */
object MmsPduReader {
    const val MESSAGE_TYPE_SEND_REQ = 0x80
    const val MESSAGE_TYPE_SEND_CONF = 0x81

    data class Part(val contentType: String, val name: String?, val contentLocation: String?, val contentId: String?,
        val data: ByteArray) {
        override fun equals(other: Any?) = other is Part && contentType == other.contentType && name == other.name &&
            contentLocation == other.contentLocation && contentId == other.contentId && data.contentEquals(other.data)
        override fun hashCode() = contentType.hashCode() * 31 + data.contentHashCode()
    }

    data class Pdu(val messageType: Int, val to: List<String>, val contentType: String?, val parts: List<Part>)

    private const val H_MESSAGE_TYPE = 0x0C
    private const val H_TRANSACTION_ID = 0x18
    private const val H_MMS_VERSION = 0x0D
    private const val H_TO = 0x17
    private const val H_CONTENT_TYPE = 0x04
    private const val H_RESPONSE_STATUS = 0x12
    private const val P_NAME = 0x05
    private const val P_CONTENT_ID = 0x40
    private const val P_CONTENT_LOCATION = 0x0E
    private val WELL_KNOWN_TYPES = mapOf(0x03 to "text/plain", 0x23 to "application/vnd.wap.multipart.mixed",
        0x33 to "application/vnd.wap.multipart.related")

    /** @throws IllegalArgumentException when [pdu] is not a well-formed MMS PDU. */
    fun read(pdu: ByteArray): Pdu {
        val c = Cursor(pdu, 0, pdu.size)
        require(c.octet() == 0x80 or H_MESSAGE_TYPE) { "the first header must be the message type" }
        val type = c.octet()
        val to = mutableListOf<String>()
        while (c.hasMore()) {
            val field = c.octet()
            require(field >= 0x80) { "application headers are not expected here" }
            when (field and 0x7F) {
                H_TO -> to += c.encodedString()
                H_CONTENT_TYPE -> {
                    val contentType = c.contentType().first
                    return Pdu(type, to, contentType, c.multipart())
                }
                else -> c.skipValue()
            }
        }
        return Pdu(type, to, null, emptyList())
    }

    /**
     * The Response-Status octet of an m-send-conf (0x80 is Ok), or null when [pdu] is missing, damaged,
     * not an m-send-conf, or has no Response-Status. Never throws.
     */
    fun sendConfStatus(pdu: ByteArray?): Int? {
        if (pdu == null || pdu.isEmpty()) return null
        return try {
            val c = Cursor(pdu, 0, pdu.size)
            if (c.octet() != (0x80 or H_MESSAGE_TYPE) || c.octet() != MESSAGE_TYPE_SEND_CONF) return null
            var status: Int? = null
            var transaction = false
            var version = false
            while (c.hasMore()) {
                val field = c.octet()
                if (field < 0x80) return null
                when (field and 0x7F) {
                    H_MESSAGE_TYPE -> return null
                    H_TRANSACTION_ID -> {
                        if (transaction || c.text().isEmpty()) return null
                        transaction = true
                    }
                    H_MMS_VERSION -> {
                        if (version || c.octet() !in 0x90..0x93) return null
                        version = true
                    }
                    H_RESPONSE_STATUS -> {
                        if (status != null) return null
                        status = c.octet().takeIf { it >= 0x80 } ?: return null
                    }
                    else -> c.skipValue()
                }
            }
            if (transaction && version) status else null
        } catch (_: IllegalArgumentException) { null }
    }

    private class Cursor(val bytes: ByteArray, var pos: Int, val end: Int) {
        fun hasMore() = pos < end
        fun octet(): Int { require(pos < end) { "truncated" }; return bytes[pos++].toInt() and 0xFF }
        fun peek(): Int { require(pos < end) { "truncated" }; return bytes[pos].toInt() and 0xFF }
        fun take(n: Int): ByteArray {
            require(n in 0..(end - pos)) { "truncated" }
            return bytes.copyOfRange(pos, pos + n).also { pos += n }
        }
        fun uintvar(): Int {
            var value = 0L
            repeat(5) {
                val o = octet()
                value = (value shl 7) or (o and 0x7F).toLong()
                require(value <= Int.MAX_VALUE) { "too large" }
                if (o and 0x80 == 0) return value.toInt()
            }
            throw IllegalArgumentException("uintvar too long")
        }
        fun valueLength(): Int {
            val o = octet()
            return when {
                o <= 30 -> o
                o == 31 -> uintvar()
                else -> throw IllegalArgumentException("not a value length")
            }
        }
        fun text(): String {
            if (peek() == 0x7F || peek() == 0x22) pos++
            val start = pos
            while (true) { if (octet() == 0) break }
            return String(bytes, start, pos - start - 1, Charsets.UTF_8)
        }
        fun encodedString(): String {
            if (peek() > 31) return text()
            val inner = sub(valueLength())
            inner.skipInteger()
            return inner.text()
        }
        fun skipInteger() { val o = octet(); if (o < 0x80) take(o) }
        fun skipValue() {
            val o = peek()
            when {
                o <= 31 -> { val n = valueLength(); take(n) }
                o < 0x80 -> text()
                else -> pos++
            }
        }
        fun sub(n: Int): Cursor {
            require(n in 0..(end - pos)) { "truncated" }
            return Cursor(bytes, pos, pos + n).also { pos += n }
        }
        fun media(): String {
            val o = peek()
            return if (o >= 0x80) { pos++; WELL_KNOWN_TYPES[o and 0x7F] ?: "#${o and 0x7F}" } else text()
        }
        /** The media type and the name parameter. */
        fun contentType(): Pair<String, String?> {
            if (peek() > 31) return media() to null
            val inner = sub(valueLength())
            val media = inner.media()
            var name: String? = null
            while (inner.hasMore()) {
                val token = inner.peek()
                if (token >= 0x80) {
                    inner.pos++
                    if (token and 0x7F == P_NAME) name = inner.text() else inner.skipValue()
                } else {
                    inner.text()
                    inner.skipValue()
                }
            }
            return media to name
        }
        fun multipart(): List<Part> {
            val count = uintvar()
            require(count in 0..64) { "too many parts" }
            return List(count) {
                val headersLength = uintvar()
                val dataLength = uintvar()
                val headers = sub(headersLength)
                val (type, name) = headers.contentType()
                var location: String? = null
                var id: String? = null
                while (headers.hasMore()) {
                    val field = headers.octet()
                    if (field >= 0x80) {
                        when (field and 0x7F) {
                            P_CONTENT_ID -> id = headers.text()
                            P_CONTENT_LOCATION -> location = headers.text()
                            else -> headers.skipValue()
                        }
                    } else {
                        headers.pos--
                        headers.text()
                        headers.text()
                    }
                }
                Part(type, name, location, id, take(dataLength))
            }
        }
    }
}
