// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice.send

/**
 * What LMNOPhone can truthfully say about one send. There is no "delivered" or "heard": MMS gives this app no
 * such answer, and the person's phone is never asked.
 */
sealed interface SendStatus {
    /** Handed to the phone's MMS service; no answer yet. */
    data object Sending : SendStatus
    /** The carrier's MMS server accepted the message (RESULT_OK and an m-send-conf with status Ok). */
    data object Sent : SendStatus
    data class NotSent(val problem: SendProblem) : SendStatus
}

enum class SendProblem {
    NoSignal, MmsOff, TooBig, NoPermission, OtherError,
    /** No answer came back (the app was closed mid-send, or Android never answered). It may or may not have gone. */
    NotConfirmed,
}

/** Maps the MMS sentIntent result (SmsManager.MMS_ERROR_*, API 36) and its EXTRA_MMS_DATA to a [SendStatus]. */
object MmsSendResult {
    /** android.app.Activity.RESULT_OK. */
    const val RESULT_OK = -1
    /** SmsManager.EXTRA_MMS_DATA: the carrier's m-send-conf bytes. */
    const val EXTRA_MMS_DATA = "android.telephony.extra.MMS_DATA"
    const val RESPONSE_STATUS_OK = 0x80

    private const val MMS_ERROR_UNABLE_CONNECT_MMS = 3
    private const val MMS_ERROR_NO_DATA_NETWORK = 8
    private const val MMS_ERROR_DATA_DISABLED = 11
    private const val MMS_ERROR_MMS_DISABLED_BY_CARRIER = 12

    fun map(code: Int, data: ByteArray?): SendStatus = when (code) {
        RESULT_OK -> if (MmsPduReader.sendConfStatus(data) == RESPONSE_STATUS_OK) SendStatus.Sent
            else SendStatus.NotSent(SendProblem.OtherError)
        MMS_ERROR_UNABLE_CONNECT_MMS, MMS_ERROR_NO_DATA_NETWORK, MMS_ERROR_DATA_DISABLED ->
            SendStatus.NotSent(SendProblem.NoSignal)
        MMS_ERROR_MMS_DISABLED_BY_CARRIER -> SendStatus.NotSent(SendProblem.MmsOff)
        else -> SendStatus.NotSent(SendProblem.OtherError)
    }
}
