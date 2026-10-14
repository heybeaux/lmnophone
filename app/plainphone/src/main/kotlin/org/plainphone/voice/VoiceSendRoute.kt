// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.voice

import org.plainphone.device.voice.VoiceNumbers
import org.plainphone.device.voice.VoicePerson
import org.plainphone.device.voice.VoiceRecipientSource
import org.plainphone.device.voice.VoiceSendPort
import org.plainphone.device.voice.send.SendView

/**
 * Which sender and which numbers the voice screen uses (PLA-513). The host shows the family list or the practice,
 * and [family] says which one it is now; each has its own sender ([org.plainphone.device.voice.send.VoiceSendChannel]).
 *
 * - Family: [familySender] is present only where the build turns family sending on ([VoiceSendBuild.familySend];
 *   release never). The number is looked up by id in [recipients] at every check and every send, so a removed
 *   person or a turned-off list has none.
 * - Practice: no recipients are configured; only the saved Family route can send.
 *
 * A side without a sender has no numbers, so nobody there gets Send and nothing is handed over.
 */
internal class VoiceSendRoute(
    private val family: () -> Boolean,
    private val familySender: VoiceSendPort?,
    private val recipients: VoiceRecipientSource,
    private val practiceSender: VoiceSendPort?,
    private val practiceNumbers: VoiceNumbers?,
) {
    private fun current(): VoiceSendPort? = if (family()) familySender else practiceSender

    /** Null when neither side can send: the controller then ignores Send and Try again. */
    val port: VoiceSendPort? = if (familySender == null && practiceSender == null) null else object : VoiceSendPort {
        override fun send(person: VoicePerson, number: String): SendView =
            current()?.send(person, number) ?: throw IllegalStateException("sending is off here")
        override fun view(personId: String): SendView? = current()?.view(personId)
        override fun seen(personId: String) { current()?.seen(personId) }
    }

    val numbers = VoiceNumbers { id ->
        if (family()) familySender?.let { recipients.recipient(id)?.number }
        else practiceSender?.let { practiceNumbers?.number(id) }
    }
}
