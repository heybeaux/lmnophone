// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice.send

import org.plainphone.device.voice.VoiceFormat

/** The carrier's MMS settings (SmsManager.getCarrierConfigValues: `enabledMMS`, `maxMessageSize`). */
data class CarrierMms(val enabled: Boolean, val maxMessageBytes: Int)

/** How long a recording may be so the whole message still fits the carrier's largest MMS (PLA-499 scope 3). */
object VoiceSendLimits {
    const val MAX_RECORD_MS = 120_000L
    /** Used when the carrier settings can't be read: AOSP MmsConfig's default `maxMessageSize` (300 KiB). */
    const val DEFAULT_MAX_MESSAGE_BYTES = 307_200
    /** MP4 container bytes that don't grow with length (ftyp, moov headers). */
    const val CONTAINER_FIXED_BYTES = 3_072
    /** m-send-req headers and part headers. Measured about 135 bytes in the PLA-458 spike. */
    const val PDU_OVERHEAD_BYTES = 2_048
    /** Only this share of the carrier limit is planned for, so an encoder running a little fast still fits. */
    const val USABLE_PERCENT = 90

    fun maxMessageBytes(carrier: CarrierMms?): Int = carrier?.maxMessageBytes ?: DEFAULT_MAX_MESSAGE_BYTES

    /** 0 when no audio fits at all; never more than [MAX_RECORD_MS]. MMS being off doesn't change the size. */
    fun maxRecordMs(carrier: CarrierMms?, format: VoiceFormat): Long {
        val max = maxMessageBytes(carrier).toLong()
        if (max <= 0) return 0L
        val audioBytes = max * USABLE_PERCENT / 100 - CONTAINER_FIXED_BYTES - PDU_OVERHEAD_BYTES
        if (audioBytes <= 0) return 0L
        return (audioBytes * 1000 / format.bytesPerSecond).coerceIn(0L, MAX_RECORD_MS)
    }
}
