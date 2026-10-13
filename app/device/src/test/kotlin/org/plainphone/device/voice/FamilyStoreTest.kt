// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FamilyStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val deleted = mutableListOf<String>()
    private fun store() = FamilyStore(File(temp.root, "family"), { deleted += it })
    private fun person(id: String = "sam", number: String = "555-0100") = FamilyPerson(id, "Sam", "Sam", number)
    @Test fun startsEmptyAndOff() { assertEquals(FamilyList(), store().read()) }
    @Test fun savesImmediatelyAndSurvivesNewInstance() {
        store().save(person()); store().setEnabled(true)
        val list = store().read()
        assertTrue(list.enabled)
        assertEquals("555-0100", list.people.single().number)
        assertEquals(FamilyPicture.Letter, list.people.single().picture)
    }
    @Test fun sixPeopleMaximumButEditingAtLimitWorks() {
        repeat(6) { store().save(person("p$it", "555-010$it")) }
        assertThrows(IllegalArgumentException::class.java) { store().save(person("p6")) }
        store().save(FamilyPerson("p0", "Jo", "Jo", "555-0100"))
        assertEquals(6, store().read().people.size)
        assertEquals("Jo", store().read().people.first().name)
    }
    @Test fun removalErasesDraftBeforePublishingList() {
        store().save(person())
        FamilyStore(File(temp.root, "family"), { id ->
            assertEquals("sam", store().read().people.single().id)
            deleted += id
        }).remove("sam")
        assertEquals(listOf("sam"), deleted)
        assertTrue(store().read().people.isEmpty())
    }
    @Test fun failedErasureDoesNotClaimRemoval() {
        store().save(person())
        val broken = FamilyStore(File(temp.root, "family"), { throw IOException("erase failed") })
        assertThrows(IOException::class.java) { broken.remove("sam") }
        assertEquals(1, store().read().people.size)
    }
    @Test fun changingNumberErasesOldRecipientsDraftButNameChangeDoesNot() {
        store().save(person()); store().save(FamilyPerson("sam", "Sam", "Sam", "555-0100"))
        assertTrue(deleted.isEmpty())
        store().save(person(number = "555-0101"))
        assertEquals(listOf("sam"), deleted)
    }
    @Test fun turningOffErasesAllDraftsAndKeepsPeople() {
        store().save(person()); store().save(person("jo", "555-0101")); store().setEnabled(true)
        store().setEnabled(false)
        assertEquals(listOf("sam", "jo"), deleted)
        assertFalse(store().read().enabled)
        assertEquals(2, store().read().people.size)
    }
    @Test fun rejectsIntentSyntaxControlsAndPathTraversalWithoutEchoingData() {
        for (number in listOf("555-0100?body=hello", "555-0100;555-0101", "911", "", "555\n0100")) {
            assertThrows(IllegalArgumentException::class.java) { store().save(person(number = number)) }
        }
        assertThrows(IllegalArgumentException::class.java) { store().save(person("../sam")) }
        assertThrows(IllegalArgumentException::class.java) { store().save(FamilyPerson("sam", "Sam\u202e", "Sam", "555-0100")) }
        assertFalse(person().toString().contains("555"))
    }
    @Test fun corruptFileFailsClosedWithoutReplacingIt() {
        val file = File(temp.root, "family").apply { writeText("broken") }
        assertThrows(IOException::class.java) { store().read() }
        assertEquals("broken", file.readText())
    }
    @Test fun recipientLookupUsesOnlyEnabledLiveList() {
        store().save(person())
        assertNull(store().recipient("sam"))
        store().setEnabled(true)
        assertEquals("555-0100", store().recipient("sam")?.number)
        store().remove("sam")
        assertNull(store().recipient("sam"))
    }
}
