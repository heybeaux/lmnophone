// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VoiceDraftStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private var now = 10 * VoiceDraftStore.RETENTION_MS
    private fun store() = VoiceDraftStore(tmp.newFolder("voice")) { now }
    private fun VoiceDraftStore.write(id: String, bytes: ByteArray = VoiceFormat.AmrNb.header + ByteArray(64)) {
        takeFile(id).writeBytes(bytes); assertNotNull(keepTake(id))
    }

    @Test fun keepReplacesTheOneDraftAtomically() {
        val s = store(); s.write("sam"); s.write("sam", VoiceFormat.AmrNb.header + ByteArray(96))
        assertEquals(listOf("sam.amr"), s.dir.list()!!.toList())
        assertEquals(6 + 96L, s.file("sam").length())
        assertEquals(VoiceDraft("sam", now), s.draft("sam"))
    }

    @Test fun takeWithoutAValidHeaderIsRefused() {
        val s = store()
        s.takeFile("sam").writeBytes(ByteArray(64))
        assertNull(s.keepTake("sam")); assertFalse(s.file("sam").exists()); assertFalse(s.takeFile("sam").exists())
        s.takeFile("sam").writeBytes(VoiceFormat.AmrNb.header)
        assertNull("header alone is nothing recorded", s.keepTake("sam"))
    }

    @Test fun draftsArePrunedAfterSevenDaysAndLeftoverTakesOnOpen() {
        val s = store(); s.write("sam"); s.write("jo")
        s.file("jo").setLastModified(now - VoiceDraftStore.RETENTION_MS - 1)
        s.takeFile("alex").writeBytes(ByteArray(10))
        s.prune()
        assertNotNull(s.draft("sam")); assertNull(s.draft("jo")); assertFalse(s.takeFile("alex").exists())
        assertEquals(7 * 24 * 60 * 60 * 1000L, VoiceDraftStore.RETENTION_MS)
    }

    @Test fun deleteRemovesDraftAndTake() {
        val s = store(); s.write("sam"); s.takeFile("sam").writeBytes(ByteArray(3))
        s.delete("sam")
        assertTrue(s.dir.list()!!.isEmpty()); assertNull(s.draft("sam"))
    }

    @Test fun onlyFixturePersonIdsBecomeFileNames() {
        val s = store()
        for (bad in listOf("../x", "a/b", "", "Sam", "x.amr")) {
            assertThrows(IllegalArgumentException::class.java) { s.file(bad) }
        }
    }

    @Test fun amrHeaderIsTheSingleChannelNarrowbandMagic() {
        assertArrayEquals("#!AMR\n".toByteArray(Charsets.US_ASCII), VoiceFormat.AmrNb.header)
        assertEquals("amr", VoiceFormat.AmrNb.extension)
    }
}
