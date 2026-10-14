// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.requests

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.policy.*

class RequestFileStoreTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun removalErasesTextFromFileAndPreservesQuotaAfterReopen() {
        val file = File(folder.root, "requests")
        val store = RequestFileStore(file)
        val port = object : RequestSmsPort {
            override fun problem() = RequestStatus.NoSignal
            override fun parts(text: String) = listOf(text)
            override fun send(number: String, id: String, parts: List<String>) = error("No sending")
        }
        val engine = AppRequests(store, port, "test", { 0L }, { 10L })
        engine.setRecipient(RequestRecipient("Jo", "555-0100"))
        repeat(5) { engine.submit("r$it", "Private request text $it") }
        assertTrue(engine.remove("r0"))
        assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("Private request text 0"))
        assertFalse(File(folder.root, "requests.tmp").exists())
        val restarted = AppRequests(RequestFileStore(file), port, "next", { 0L }, { 10L })
        assertEquals(4, restarted.view().requests.size)
        assertEquals(RequestStatus.DailyLimit, restarted.submit("six", "Maps app").status)
        assertEquals(RequestRecipient("Jo", "555-0100"), restarted.recipient())
        assertFalse(restarted.result("r0", 0, RequestPart.Accepted))
    }
    @Test fun versionOneStoreUpgradesWithoutLosingData() {
        val file = File(folder.root, "requests")
        java.io.DataOutputStream(file.outputStream()).use {
            it.writeInt(1); it.writeBoolean(false); it.writeInt(1)
            it.writeUTF("old"); it.writeUTF("Maps app"); it.writeLong(10)
            it.writeLong(0); it.writeUTF("old"); it.writeInt(1); it.writeInt(-1)
        }
        val store = RequestFileStore(file)
        val old = store.read()
        store.write(old)
        assertEquals(old, RequestFileStore(file).read())
        assertEquals("Maps app", old.requests.single().text)
    }
    @Test fun reopenedStoreKeepsRecipientTextDayAndPartResults() {
        val f = File(folder.root, "requests")
        val book = RequestBook(RequestRecipient("Sam", "555-0100"), listOf(RequestEntry("a", "Bus\napp", 10, 200, "old", listOf(RequestPart.Accepted, null))))
        RequestFileStore(f).write(book)
        assertEquals(book, RequestFileStore(f).read())
        assertFalse(File(folder.root, "requests.tmp").exists())
    }
    @Test fun oversizedEncodedWritePreservesTheExistingBookAndRemovesTemp() {
        val f = File(folder.root, "requests")
        val store = RequestFileStore(f)
        val previous = RequestBook(RequestRecipient("Sam", "555-0100"), listOf(
            RequestEntry("old", "Maps", 10, 200, "old", listOf(RequestPart.Accepted))))
        store.write(previous)
        val bytes = f.readBytes()
        // Valid 280-character requests exceed the byte ceiling when encoded as modified UTF-8.
        val oversized = previous.copy(requests = List(10_000) { i ->
            RequestEntry("r$i", "漢".repeat(280), 10, 200, "test", listOf(RequestPart.Accepted))
        })
        assertThrows(Exception::class.java) { store.write(oversized) }
        assertArrayEquals(bytes, f.readBytes())
        assertEquals(previous, RequestFileStore(f).read())
        assertFalse(File(folder.root, "requests.tmp").exists())
    }
    @Test fun absentIsEmptyButDamagedIsAnError() {
        val f = File(folder.root, "requests")
        assertEquals(RequestBook(), RequestFileStore(f).read())
        f.writeText("broken")
        assertThrows(Exception::class.java) { RequestFileStore(f).read() }
    }
}
