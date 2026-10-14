// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice.replies

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.device.voice.FamilyList
import org.plainphone.device.voice.FamilyPerson

class VoiceReplyBookTest {
    @get:Rule val temp = TemporaryFolder()
    private val sam = FamilyPerson("sam", "Sam", "Sam", "555-0100")
    private var family = FamilyList(true, listOf(sam))
    private var inbox = listOf(
        MmsInboxMessage(1, 1_000, "5550100", listOf(MmsPart(10, "audio/mp4"))),
        MmsInboxMessage(2, 2_000, "5550199", listOf(MmsPart(20, "audio/mp4"))),
    )
    private val store by lazy { ReplyPlayedStore(File(temp.root, ReplyPlayedStore.DIR)) }
    private fun book() = VoiceReplyBook({ inbox }, { family }, store)

    @Test fun listsUnplayedForTheCurrentFamilyOnly() {
        assertEquals(setOf("sam"), book().unplayed().keys)
    }

    @Test fun playedClipIsRememberedAndNoLongerNew() {
        val clip = book().unplayed().getValue("sam").single()
        book().played("sam", clip)
        assertTrue(book().unplayed().isEmpty())
        assertEquals(setOf(clip.key), store.played("sam"))
    }

    @Test fun neverRecordsPlayedForSomeoneRemovedOrWhenOff() {
        val clip = book().unplayed().getValue("sam").single()
        family = family.copy(enabled = false)
        assertTrue(book().unplayed().isEmpty())
        assertFalse(book().canPlay("sam"))
        book().played("sam", clip)
        family = FamilyList(true, emptyList())
        book().played("sam", clip)
        assertFalse(book().canPlay("sam"))
        assertTrue(store.played("sam").isEmpty())
    }

    @Test fun aClipIsRecordedOnlyForItsOwnPerson() {
        val jo = FamilyPerson("jo", "Jo", "Jo", "555-0111")
        family = FamilyList(true, listOf(sam, jo))
        val clip = book().unplayed().getValue("sam").single()
        book().played("jo", clip)
        assertTrue(store.played("jo").isEmpty())
    }

    @Test fun unreadableInboxOrFamilyListShowsNothingAndNeverThrows() {
        assertTrue(VoiceReplyBook({ throw SecurityException("no READ_SMS") }, { family }, store).unplayed().isEmpty())
        assertTrue(VoiceReplyBook({ inbox }, { throw java.io.IOException("bad") }, store).unplayed().isEmpty())
        assertFalse(VoiceReplyBook({ inbox }, { throw java.io.IOException("bad") }, store).canPlay("sam"))
    }

    @Test fun anUndownloadedPartIsNeitherOfferedNorAuthorized() {
        val clip = book().unplayed().getValue("sam").single()
        val unreadable = VoiceReplyBook({ inbox }, { family }, store, readable = { false })
        assertTrue(unreadable.unplayed().isEmpty())
        assertFalse(unreadable.canPlay("sam", clip))
    }

    @Test fun aNumberChangeOrDeletedPartInvalidatesTheCachedClip() {
        val book = book()
        val clip = book.unplayed().getValue("sam").single()
        family = family.copy(people = listOf(FamilyPerson("sam", "Sam", "Sam", "555-0111")))
        assertFalse(book.canPlay("sam", clip))
        book.played("sam", clip)
        assertTrue(store.played("sam").isEmpty())
        family = family.copy(people = listOf(sam))
        assertTrue(book.canPlay("sam", clip))
        inbox = emptyList()
        assertFalse(book.canPlay("sam", clip))
    }

    @Test fun aHeardClipMayBeReplayedWithoutBeingOfferedAsNew() {
        val book = book()
        val clip = book.unplayed().getValue("sam").single()
        book.played("sam", clip)
        assertTrue(book.unplayed().isEmpty())
        assertTrue(book.canPlay("sam", clip))
    }
}
