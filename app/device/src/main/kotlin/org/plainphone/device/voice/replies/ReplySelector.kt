// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice.replies

import org.plainphone.device.voice.FamilyList
import org.plainphone.device.voice.send.VoiceNumber

/**
 * Which inbox voice replies a person on the family list can hear (UX V1, V12, V12a). Pure, so every rule is
 * tested on the host:
 * - only while voice messages are on, and only from someone on the current list. The sender matches when
 *   both numbers reduce to the same digits ([VoiceNumber.normalize], the rule Send uses), or when the phone's
 *   own country rules ([e164]) turn both into the same international number. A shared ending never matches;
 * - only audio parts (`audio/...`, any codec). A message without one shows nothing here; with several, the
 *   lowest readable part id (the first) is the message;
 * - per person, the latest [LATEST] messages, played or not; the unplayed ones of those, oldest first.
 */
object ReplySelector {
    const val LATEST = 5
    private const val ADDRESS_TYPE = "/TYPE="

    fun select(messages: List<MmsInboxMessage>, family: FamilyList, played: (String) -> Set<String>,
        e164: (String) -> String?, readable: (Long) -> Boolean = { true }): ReplyClips {
        if (!family.enabled || family.people.isEmpty()) return ReplyClips(emptyMap())
        val international = { raw: String -> try { e164(raw) } catch (_: Exception) { null } }
        val people = family.people.mapNotNull { person -> address(person.number, international)?.let { person to it } }
        val byPerson = LinkedHashMap<String, MutableList<ReplyClip>>()
        for (message in messages) {
            val sender = address(message.from?.substringBefore(ADDRESS_TYPE), international) ?: continue
            val person = people.firstOrNull { it.second.matches(sender) }?.first ?: continue
            val part = message.parts.filter { it.contentType?.trim()?.lowercase()?.startsWith("audio/") == true }
                .sortedBy { it.id }.firstOrNull { try { readable(it.id) } catch (_: Exception) { false } } ?: continue
            byPerson.getOrPut(person.id) { mutableListOf() } += ReplyClip(ReplyKey.of(message.id, message.dateMs),
                person.id, message.dateMs, part.id, part.contentType!!.trim().lowercase())
        }
        val out = LinkedHashMap<String, List<ReplyClip>>()
        for (person in family.people) {
            val clips = byPerson[person.id] ?: continue
            val heard = try { played(person.id) } catch (_: Exception) { emptySet() }
            val unplayed = clips.sortedWith(compareByDescending<ReplyClip> { it.dateMs }.thenByDescending { it.partId })
                .distinctBy { it.key }.take(LATEST).filterNot { it.key in heard }.reversed()
            if (unplayed.isNotEmpty()) out[person.id] = unplayed
        }
        return ReplyClips(out)
    }

    /** Digits as Send reduces them, and the international form by the phone's own country rules (null: none). */
    private class Address(val digits: String, val international: String?) {
        fun matches(other: Address) = digits == other.digits || (international != null && international == other.international)
    }

    private fun address(raw: String?, e164: (String) -> String?): Address? {
        val digits = raw?.trim()?.let(VoiceNumber::normalize) ?: return null
        return Address(digits, e164(digits))
    }
}
