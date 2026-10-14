// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice.replies

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.device.voice.FamilyList
import org.plainphone.device.voice.FamilyPerson
import org.plainphone.device.voice.send.VoiceNumber

class ReplySelectorTest {
    private val sam = FamilyPerson("sam", "Sam", "Sam", "555-0100")
    private val jo = FamilyPerson("jo", "Jo", "Jo", "(604) 555-0111")
    private val family = FamilyList(enabled = true, people = listOf(sam, jo))
    private fun msg(id: Long, from: String?, vararg parts: Pair<Long, String?>, dateMs: Long = id * 1_000) =
        MmsInboxMessage(id, dateMs, from, parts.map { MmsPart(it.first, it.second) })
    /** Stand-in for PhoneNumberUtils.formatNumberToE164 with a North American SIM: 10 digits gain +1. */
    private val nanp: (String) -> String? = { raw ->
        VoiceNumber.normalize(raw)?.let { n -> when { n.startsWith("+") -> n; n.length == 10 -> "+1$n"
            n.length == 11 && n.startsWith("1") -> "+$n"; else -> null } }
    }
    private fun select(vararg m: MmsInboxMessage, list: FamilyList = family, played: (String) -> Set<String> = { emptySet() },
        e164: (String) -> String? = nanp) = ReplySelector.select(m.toList(), list, played, e164)

    @Test fun onlyFamilySendersAppearAndStrangersNever() {
        val out = select(msg(1, "5550100", 10L to "audio/mp4"), msg(2, "5550199", 11L to "audio/mp4"))
        assertEquals(setOf("sam"), out.keys)
        assertEquals(10L, out.getValue("sam").single().partId)
    }

    @Test fun numberFormatsMatchTheSameWayAsSend() {
        for (from in listOf("555-0100", "555 0100", "5550100", "555.0100", "(555)0100")) {
            assertEquals(from, setOf("sam"), select(msg(1, from, 10L to "audio/amr")).keys)
        }
        for (from in listOf("6045550111", "+16045550111", "604-555-0111", "+1 (604) 555-0111", "+16045550111/TYPE=PLMN")) {
            assertEquals(from, setOf("jo"), select(msg(1, from, 10L to "audio/amr")).keys)
        }
    }

    @Test fun strangerSharingDigitsWithFamilyNeverMatches() {
        for (from in listOf("+17785550111", "+16045550112", "5550111", "+15550100", "+445550100",
                "+9916045550111", "+446045550111", "insert-address-token", "sam@example.com", "", null)) {
            assertTrue("$from", select(msg(1, from, 10L to "audio/mp4")).isEmpty())
        }
    }

    @Test fun nationalAndInternationalFormsMatchOnlyThroughThePhonesOwnCountryRules() {
        val uk = FamilyPerson("uk", "Jo", "Jo", "07700 900123")
        val local = FamilyList(true, listOf(uk))
        val gb: (String) -> String? = { raw -> VoiceNumber.normalize(raw)?.let { n ->
            when { n.startsWith("+") -> n; n.startsWith("0") -> "+44" + n.drop(1); else -> null } } }
        assertEquals(setOf("uk"), select(msg(1, "+447700900123", 10L to "audio/mp4"), list = local, e164 = gb).keys)
        assertEquals(setOf("uk"), select(msg(1, "07700900123", 10L to "audio/mp4"), list = local, e164 = gb).keys)
        assertTrue(select(msg(1, "+447700900124", 10L to "audio/mp4"), list = local, e164 = gb).isEmpty())
        assertTrue(select(msg(1, "+17700900123", 10L to "audio/mp4"), list = local, e164 = gb).isEmpty())
        // Without the phone's country rules only the exact same digits match.
        assertTrue(select(msg(1, "+447700900123", 10L to "audio/mp4"), list = local, e164 = { null }).isEmpty())
        assertEquals(setOf("uk"), select(msg(1, "07700-900123", 10L to "audio/mp4"), list = local, e164 = { null }).keys)
    }

    @Test fun aFailingCountryLookupFallsBackToExactDigits() {
        val out = select(msg(1, "5550100", 10L to "audio/mp4"), msg(2, "+16045550111", 20L to "audio/mp4"),
            e164 = { throw IllegalStateException("no SIM") })
        assertEquals(setOf("sam"), out.keys)
    }

    @Test fun onlyAudioPartsCountAndTextOrPictureOnlyRepliesShowNothing() {
        val out = select(
            msg(1, "5550100", 10L to "text/plain"),
            msg(2, "5550100", 20L to "image/jpeg", 21L to "application/smil"),
            msg(3, "5550100", 30L to "text/plain", 31L to "audio/amr"),
            msg(4, "5550100", 40L to "AUDIO/MP4"),
            msg(5, "5550100", 50L to null),
            msg(6, "5550100"),
        )
        assertEquals(listOf(31L, 40L), out.getValue("sam").map { it.partId })
        assertEquals(listOf("audio/amr", "audio/mp4"), out.getValue("sam").map { it.contentType })
    }

    @Test fun aMessageWithSeveralAudioPartsIsOneMessageUsingItsFirstPart() {
        assertEquals(listOf(11L), select(msg(1, "5550100", 12L to "audio/amr", 11L to "audio/mp4")).getValue("sam").map { it.partId })
    }

    @Test fun severalUnplayedPlayOldestFirstFromTheLatestFiveOnly() {
        val messages = (1L..7L).map { msg(it, "5550100", it * 10 to "audio/mp4", dateMs = it * 1_000) }.shuffled()
        val out = ReplySelector.select(messages, family, { emptySet() }, nanp)
        assertEquals(listOf(30L, 40L, 50L, 60L, 70L), out.getValue("sam").map { it.partId })
    }

    @Test fun playedMessagesStillCountTowardTheLatestFive() {
        val messages = (1L..7L).map { msg(it, "5550100", it * 10 to "audio/mp4") }
        val playedKeys = messages.filter { it.id >= 5 }.map { ReplyKey.of(it.id, it.dateMs) }.toSet()
        val out = ReplySelector.select(messages, family, { if (it == "sam") playedKeys else emptySet() }, nanp)
        assertEquals(listOf(30L, 40L), out.getValue("sam").map { it.partId })
        val all = messages.map { ReplyKey.of(it.id, it.dateMs) }.toSet()
        assertTrue(ReplySelector.select(messages, family, { all }, nanp).isEmpty())
    }

    @Test fun peopleKeepFamilyOrderAndEachGetsOwnClips() {
        val out = select(msg(1, "6045550111", 10L to "audio/mp4"), msg(2, "5550100", 20L to "audio/mp4"))
        assertEquals(listOf("sam", "jo"), out.keys.toList())
        assertEquals("jo", out.getValue("jo").single().personId)
    }

    @Test fun voiceMessagesOffOrPersonRemovedShowsNothing() {
        val m = msg(1, "5550100", 10L to "audio/mp4")
        assertTrue(select(m, list = family.copy(enabled = false)).isEmpty())
        assertTrue(select(m, list = family.copy(people = listOf(jo))).isEmpty())
    }

    @Test fun keysAreOpaqueStableAndNeverHoldTheNumberOrIds() {
        val key = ReplyKey.of(1234, 1_700_000_000_000)
        assertEquals(key, ReplyKey.of(1234, 1_700_000_000_000))
        assertNotEquals(key, ReplyKey.of(1234, 1_700_000_001_000))
        assertNotEquals(key, ReplyKey.of(1235, 1_700_000_000_000))
        assertTrue(key.matches(Regex("[0-9a-f]{32}")))
        assertFalse(key.contains("1234"))
        val clip = select(msg(1, "5550100", 10L to "audio/mp4")).getValue("sam").single()
        assertEquals(ReplyKey.of(1, 1_000), clip.key)
    }

    @Test fun noNumbersNamesOrIdsInToString() {
        val m = msg(77, "5550100", 10L to "audio/mp4")
        val clip = select(m).getValue("sam").single()
        for (text in listOf(m.toString(), clip.toString(), select(m).toString())) {
            assertFalse(text, text.contains("555"))
            assertFalse(text, text.contains("sam", ignoreCase = true))
            assertFalse(text, text.contains("77"))
        }
    }
}
