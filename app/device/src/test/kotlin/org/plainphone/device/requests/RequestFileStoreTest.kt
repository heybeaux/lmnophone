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
    @Test fun reopenedStoreKeepsRecipientTextDayAndPartResults() {
        val f = File(folder.root, "requests")
        val book = RequestBook(RequestRecipient("Sam", "555-0100"), listOf(RequestEntry("a", "Bus\napp", 10, 200, "old", listOf(RequestPart.Accepted, null))))
        RequestFileStore(f).write(book)
        assertEquals(book, RequestFileStore(f).read())
        assertFalse(File(folder.root, "requests.tmp").exists())
    }
    @Test fun absentIsEmptyButDamagedIsAnError() {
        val f = File(folder.root, "requests")
        assertEquals(RequestBook(), RequestFileStore(f).read())
        f.writeText("broken")
        assertThrows(Exception::class.java) { RequestFileStore(f).read() }
    }
}
