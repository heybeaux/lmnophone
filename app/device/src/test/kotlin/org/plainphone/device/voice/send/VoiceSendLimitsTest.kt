// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice.send

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.device.voice.VoiceFormat

/** PLA-499 scope 3: the recording stops where the carrier's largest message would be full. */
class VoiceSendLimitsTest {
    private val aac = VoiceFormat.AacM4a

    @Test fun aLargeCarrierLimitKeepsTheTwoMinuteCap() {
        assertEquals(120_000L, VoiceSendLimits.maxRecordMs(CarrierMms(enabled = true, maxMessageBytes = 1_048_576), aac))
    }

    @Test fun aSmallerLimitShortensTheRecordingAtTheRealBitRate() {
        // 300 KiB: 90 % of it, less the PDU and container budget, at 3352 bytes a second.
        assertEquals((307_200L * 90 / 100 - 5_120) * 1000 / 3_352,
            VoiceSendLimits.maxRecordMs(CarrierMms(true, 307_200), aac))
        assertEquals((102_400L * 90 / 100 - 5_120) * 1000 / 3_352,
            VoiceSendLimits.maxRecordMs(CarrierMms(true, 102_400), aac))
    }

    @Test fun theBudgetUsesTheRecordersOwnSettings() {
        assertEquals(44_100, aac.sampleRate)
        assertEquals(24_000, aac.bitRate)
        assertEquals(3_352, aac.bytesPerSecond)
        assertTrue(VoiceFormat.AmrNb.bytesPerSecond >= 1_600)
    }

    @Test fun aLimitTooSmallForAnyAudioGivesZero() {
        assertEquals(0L, VoiceSendLimits.maxRecordMs(CarrierMms(true, 4_096), aac))
        assertEquals(0L, VoiceSendLimits.maxRecordMs(CarrierMms(true, 0), aac))
        assertEquals(0L, VoiceSendLimits.maxRecordMs(CarrierMms(true, -1), aac))
    }

    @Test fun mmsOffStillUsesTheSizeAndUnknownSettingsUseTheAndroidDefault() {
        assertEquals(VoiceSendLimits.maxRecordMs(CarrierMms(true, 307_200), aac),
            VoiceSendLimits.maxRecordMs(CarrierMms(false, 307_200), aac))
        assertEquals(VoiceSendLimits.maxRecordMs(CarrierMms(true, VoiceSendLimits.DEFAULT_MAX_MESSAGE_BYTES), aac),
            VoiceSendLimits.maxRecordMs(null, aac))
    }

    @Test fun aRecordingAtTheCapFitsTheCarrierLimit() {
        for (max in listOf(51_200, 102_400, 307_200, 614_400)) {
            val ms = VoiceSendLimits.maxRecordMs(CarrierMms(true, max), aac)
            val worst = ms * aac.bytesPerSecond / 1000 + VoiceSendLimits.CONTAINER_FIXED_BYTES + VoiceSendLimits.PDU_OVERHEAD_BYTES
            assertTrue("$max: $worst", worst <= max)
        }
    }
}
