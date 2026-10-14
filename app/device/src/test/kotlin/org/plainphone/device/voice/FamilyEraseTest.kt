// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.device.voice.replies.ReplyKey
import org.plainphone.device.voice.replies.ReplyPlayedStore
import org.plainphone.device.voice.send.SendRecord
import org.plainphone.device.voice.send.SendStatus
import org.plainphone.device.voice.send.VoiceSendChannel
import org.plainphone.device.voice.send.VoiceSendStore

/** PLA-527: removing a person or turning voice messages off leaves nothing of theirs (build-47 observation, PLA-513). */
class FamilyEraseTest {
    @get:Rule val temp = TemporaryFolder()
    private val root get() = temp.root
    private val sendDir get() = File(root, VoiceSendChannel.Family.recordDir)
    private val playedDir get() = File(root, ReplyPlayedStore.DIR)
    private fun store() = FamilyStore(File(root, "voice-family"), FamilyErase(root)::erase)
    private val record = SendRecord("a1", SendStatus.Sent, "p1", 1L, "none", 2, true)

    private fun seed(id: String) {
        VoiceFormat.entries.forEach { format ->
            val drafts = VoiceDraftStore(File(root, VoiceSendChannel.Family.draftDir), format) { 0L }
            drafts.file(id).apply { parentFile.mkdirs(); writeBytes(format.header) }
            drafts.takeFile(id).writeBytes(format.header)
        }
        VoiceSendStore(sendDir).write(id, record)
        File(sendDir, "$id.send.tmp").writeText("status=sending\n")
        ReplyPlayedStore(playedDir).markPlayed(id, ReplyKey.of(1, 1_000))
    }

    private fun leftovers(id: String) = root.walkTopDown().filter { it.isFile && it.name.startsWith("$id.") }.map { it.relativeTo(root).path }.toList()

    @Test fun removingAPersonErasesDraftsSendRecordAndPlayedState() {
        store().save(FamilyPerson("sam", "Sam", "Sam", "555-0100"))
        store().save(FamilyPerson("jo", "Jo", "Jo", "555-0111"))
        seed("sam"); seed("jo")
        store().remove("sam")
        assertEquals(emptyList<String>(), leftovers("sam"))
        assertNull(VoiceSendStore(sendDir).read("sam"))
        assertTrue(ReplyPlayedStore(playedDir).played("sam").isEmpty())
        assertEquals(record, VoiceSendStore(sendDir).read("jo"))
        assertEquals(1, ReplyPlayedStore(playedDir).played("jo").size)
    }

    @Test fun turningOffErasesEveryonesRecordsAndKeepsThePeople() {
        store().save(FamilyPerson("sam", "Sam", "Sam", "555-0100"))
        store().save(FamilyPerson("jo", "Jo", "Jo", "555-0111"))
        store().setEnabled(true)
        seed("sam"); seed("jo")
        store().setEnabled(false)
        assertEquals(emptyList<String>(), leftovers("sam") + leftovers("jo"))
        assertEquals(2, store().read().people.size)
    }

    @Test fun changingTheNumberErasesTheOldRecordsButARenameDoesNot() {
        store().save(FamilyPerson("sam", "Sam", "Sam", "555-0100"))
        seed("sam")
        store().save(FamilyPerson("sam", "Samuel", "Sam", "555-0100"))
        assertEquals(record, VoiceSendStore(sendDir).read("sam"))
        store().save(FamilyPerson("sam", "Samuel", "Sam", "555-0101"))
        assertEquals(emptyList<String>(), leftovers("sam"))
    }

    @Test fun aSendRecordThatCannotBeErasedAbortsTheRemoval() {
        store().save(FamilyPerson("sam", "Sam", "Sam", "555-0100"))
        File(sendDir, "sam.send").apply { mkdirs(); File(this, "child").writeText("x") }
        assertThrows(IOException::class.java) { store().remove("sam") }
        assertEquals(1, store().read().people.size)
    }

    @Test fun playedStateThatCannotBeErasedAbortsTurningOff() {
        store().save(FamilyPerson("sam", "Sam", "Sam", "555-0100"))
        store().setEnabled(true)
        File(playedDir, "sam.played").apply { mkdirs(); File(this, "child").writeText("x") }
        assertThrows(IOException::class.java) { store().setEnabled(false) }
        assertTrue(store().read().enabled)
    }

    @Test fun erasingSomeoneWithNothingStoredIsQuietAndRepeatable() {
        FamilyErase(root).erase("sam")
        FamilyErase(root).erase("sam")
        assertFalse(sendDir.exists() && sendDir.listFiles()!!.isNotEmpty())
    }

    @Test fun neverErasesThePracticeChannel() {
        val practice = File(root, VoiceSendChannel.Practice.recordDir)
        VoiceSendStore(practice).write("sam", record)
        FamilyErase(root).erase("sam")
        assertEquals(record, VoiceSendStore(practice).read("sam"))
    }
}
