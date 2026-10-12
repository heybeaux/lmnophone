// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.update

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.device.SerialDispatcher
import org.plainphone.ui.home.HomeNotice
import org.w3c.dom.Element

class PendingSystemUpdateTest {
    @get:Rule val folder = TemporaryFolder()
    private val file get() = File(folder.root, "system-update")

    private fun store(build: String = "build-a") = PendingSystemUpdate(file, build)

    @Test fun pendingCallbackStoresTheReceivedTime() {
        assertTrue(store().onPending(1_760_000_000_000L))
        assertTrue(store().pending())
        assertEquals(1_760_000_000_000L, store().receivedTime())
    }

    @Test fun nothingRecordedIsNotPending() {
        assertFalse(store().pending())
        assertNull(store().receivedTime())
    }

    /** Android passes -1 when no update is pending any more. */
    @Test fun minusOneClearsTheRecord() {
        store().onPending(1_760_000_000_000L)
        assertFalse(store().onPending(-1L))
        assertFalse(store().pending())
        assertFalse(file.exists())
    }

    /** Once the phone runs a different build the update has landed; the warning is no longer "before". */
    @Test fun aDifferentBuildClearsTheRecord() {
        store("build-a").onPending(1_760_000_000_000L)
        assertFalse(store("build-b").pending())
        assertFalse(file.exists())
    }

    @Test fun aDamagedRecordIsNotPendingAndIsRemoved() {
        file.writeText("garbage")
        assertFalse(store().pending())
        assertFalse(file.exists())
    }

    /**
     * PLA-450 R1: a failed record write (here the temp file cannot be opened; a full disk also throws
     * IOException) is contained: not pending, no partial record, and nothing escapes to the caller.
     */
    @Test fun aFailedWriteIsContainedAndNotPending() {
        File(folder.root, "system-update.tmp").mkdir()
        assertFalse(store().onPending(42L))
        assertFalse(store().pending())
        assertFalse(file.exists())
    }

    /**
     * PLA-450 R1: on the shared policy dispatcher a failed write must not reach the uncaught-exception
     * handler (Android's kills the app), and later dispatcher work still runs.
     */
    @Test fun aFailedWriteLeavesTheDispatcherRunning() {
        File(folder.root, "system-update.tmp").mkdir()
        val uncaught = mutableListOf<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> synchronized(uncaught) { uncaught += e } }
        try {
            SerialDispatcher("pending-test").use { dispatcher ->
                var pending: Boolean? = null
                dispatcher.post { pending = store().onPending(42L) }
                assertTrue(dispatcher.run { true })
                assertEquals(false, pending)
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
        assertEquals(emptyList<Throwable>(), uncaught)
    }

    /** The record holds the time and an opaque build key only: no fingerprint text. */
    @Test fun recordHoldsOnlyTimeAndBuildKey() {
        store("k1").onPending(42L)
        assertEquals(listOf("v1", "42", "k1"), file.readLines())
    }

    @Test fun buildKeyIsAHashNotTheFingerprint() {
        val fingerprint = "brand/product/device:15/AP3A/123:user/release-keys"
        val key = buildKey(fingerprint)
        assertFalse(key.contains("brand") || key.contains("release"))
        assertEquals(key, buildKey(fingerprint))
        assertFalse(key == buildKey("$fingerprint-2"))
    }

    @Test fun homeShowsTheUpdateLineOnlyWithNoOtherLine() {
        assertEquals(HomeNotice.SoftwareUpdateWaiting, withUpdateLine(null, updatePending = true))
        assertNull(withUpdateLine(null, updatePending = false))
        assertEquals(HomeNotice.NeedsAttention, withUpdateLine(HomeNotice.NeedsAttention, updatePending = true))
    }

    /** The phone's own line from docs/ux/flows-limits-and-off.md L2d. */
    @Test fun lineTextIsTheDesignCopy() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("../ui/src/main/res/values/strings.xml"))
        val strings = document.getElementsByTagName("string")
        val text = (0 until strings.length).map { strings.item(it) as Element }
            .single { it.getAttribute("name") == "notice_system_update" }.textContent
        assertEquals("Phone software update waiting – the LMNOPhone team will review it", text)
    }
}
