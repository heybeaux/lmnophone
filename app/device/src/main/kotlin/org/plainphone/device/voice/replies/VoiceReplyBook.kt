// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice.replies

import org.plainphone.device.voice.FamilyList
import org.plainphone.device.voice.VoiceReplies

/**
 * The family's voice replies for the voice screens (PLA-527). [inbox] reads the phone's MMS inbox (read-only);
 * [family] is read again on every call, so a removed person or voice messages turned off stop showing at once.
 * Never throws: an unreadable inbox or list shows nothing new.
 */
class VoiceReplyBook(
    private val inbox: () -> List<MmsInboxMessage>,
    private val family: () -> FamilyList,
    private val store: ReplyPlayedStore,
    private val e164: (String) -> String? = { null },
    private val readable: (Long) -> Boolean = { true },
) : VoiceReplies {
    override fun unplayed(): Map<String, List<ReplyClip>> = try {
        ReplySelector.select(inbox(), family(), store::played, e164, readable)
    } catch (_: Exception) { ReplyClips(emptyMap()) }

    override fun canPlay(personId: String): Boolean = try {
        family().let { list -> list.enabled && list.people.any { it.id == personId } }
    } catch (_: Exception) { false }

    override fun canPlay(personId: String, clip: ReplyClip): Boolean = try {
        clip.personId == personId && ReplySelector.select(inbox(), family(), { emptySet() }, e164, readable)
            .get(personId).orEmpty().any { it.key == clip.key && it.partId == clip.partId }
    } catch (_: Exception) { false }

    override fun played(personId: String, clip: ReplyClip) {
        if (!canPlay(personId, clip)) return
        try { store.markPlayed(personId, clip.key) } catch (_: Exception) { }
    }
}
