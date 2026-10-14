// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice.replies

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReplyPlayedStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val dir get() = File(temp.root, ReplyPlayedStore.DIR)
    private fun store() = ReplyPlayedStore(dir)
    private val k1 = ReplyKey.of(1, 1_000)
    private val k2 = ReplyKey.of(2, 2_000)

    @Test fun nothingPlayedAtFirst() { assertTrue(store().played("sam").isEmpty()) }

    @Test fun playedSurvivesANewInstanceAndIsPerPerson() {
        store().markPlayed("sam", k1)
        store().markPlayed("sam", k2)
        store().markPlayed("sam", k1)
        assertEquals(setOf(k1, k2), store().played("sam"))
        assertTrue(store().played("jo").isEmpty())
        assertFalse(File(dir, "sam.played.tmp").exists())
    }

    @Test fun storedFileHoldsOnlyOpaqueKeys() {
        store().markPlayed("sam", k1)
        val text = File(dir, "sam.played").readText()
        assertEquals(k1, text.trim())
    }

    @Test fun keepsOnlyTheMostRecentKeys() {
        val keys = (1L..(ReplyPlayedStore.MAX_KEYS + 10L)).map { ReplyKey.of(it, it) }
        keys.forEach { store().markPlayed("sam", it) }
        val played = store().played("sam")
        assertEquals(ReplyPlayedStore.MAX_KEYS, played.size)
        assertTrue(played.contains(keys.last()))
        assertFalse(played.contains(keys.first()))
    }

    @Test fun eraseRemovesFileAndLeftoverTempAndIsSafeToRepeat() {
        store().markPlayed("sam", k1)
        store().markPlayed("jo", k2)
        File(dir, "sam.played.tmp").writeText(k2)
        store().erase("sam")
        store().erase("sam")
        assertTrue(store().played("sam").isEmpty())
        assertFalse(File(dir, "sam.played").exists())
        assertFalse(File(dir, "sam.played.tmp").exists())
        assertEquals(setOf(k2), store().played("jo"))
    }

    @Test fun eraseThatCannotDeleteThrows() {
        File(dir, "sam.played").apply { mkdirs(); File(this, "child").writeText("x") }
        assertThrows(IOException::class.java) { store().erase("sam") }
    }

    @Test fun corruptOrForeignContentReadsAsNothingPlayed() {
        dir.mkdirs()
        File(dir, "sam.played").writeText("5550100\nnot-a-key\n")
        assertTrue(store().played("sam").isEmpty())
    }

    @Test fun rejectsPathsAndBadKeys() {
        assertThrows(IllegalArgumentException::class.java) { store().markPlayed("../sam", k1) }
        assertThrows(IllegalArgumentException::class.java) { store().markPlayed("sam", "5550100") }
        assertTrue(store().played("../sam").isEmpty())
    }
}
