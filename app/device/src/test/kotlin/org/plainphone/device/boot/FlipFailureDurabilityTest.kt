// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.boot

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.PackageName
import org.plainphone.device.FakeDevicePolicyPort
import org.plainphone.device.hiddenApp
import org.plainphone.device.storage.FileFlipFailureStore
import org.plainphone.device.storage.FileJournalStore
import org.plainphone.device.visible
import org.plainphone.policy.FloorIdentity
import org.plainphone.policy.FloorRole
import org.plainphone.policy.InstalledPackage
import org.plainphone.policy.PolicyEngine
import org.plainphone.policy.PolicyInput
import org.plainphone.policy.SessionJournal

/**
 * Flip-failure notice storage (PLA-264 findings 2 and 3). A save that is not acknowledged is retried on
 * every later pass until one is; a record that cannot be read keeps attention. Storage trouble never
 * undoes an emergency unhide.
 */
class FlipFailureDurabilityTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dialer = PackageName("com.android.dialer")
    private val home = PackageName("org.plainphone")
    private val port = FakeDevicePolicyPort(mapOf(dialer.value to hiddenApp(), home.value to visible()), flushComponent = null)
    private val journalDir by lazy { tmp.newFolder("journal") }

    private fun input(locked: Boolean) = PolicyInput(
        model = "model", fingerprint = "build", plainphone = home, messages = PackageName("com.example.messages"),
        installed = listOf(InstalledPackage(dialer, true, 1), InstalledPackage(home, false, 1)),
        identities = listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, FloorRole.SYSTEM_DIALER.source)),
        lockedBoot = locked,
    )

    /** A fresh process over the same journal directory. */
    private fun process(store: FlipFailureStore): BootReapply {
        val journal = FileJournalStore(journalDir, null, "model", "build")
        if (journal.read() == SessionJournal.Damaged) assertTrue(journal.compareAndSet(SessionJournal.Damaged, SessionJournal.Empty))
        return BootReapply(journal, port, emptySet(), { PolicyEngine.decide(input(it)) }, RestrictionFilter.TEST_BUILD, flipFailures = store)
    }

    /** Saves fail (false or a throw) while [failing] is true; reads return the last acknowledged save. */
    private class FlakyStore(var failing: Boolean, private val throws: Boolean = false) : FlipFailureStore {
        var saved = FlipFailures.NONE
        var saves = 0
        override fun read() = saved
        override fun save(state: FlipFailures): Boolean {
            saves++
            if (failing) if (throws) throw IOException("disk") else return false
            saved = state
            return true
        }
    }

    @Test fun aFalseSaveIsRetriedOnEveryPassUntilAcknowledged() {
        val store = FlakyStore(failing = true)
        val boot = process(store)
        assertEquals(setOf(dialer), boot.run(locked = true).apply!!.flipFailures)
        boot.run(locked = false)
        assertEquals(2, store.saves)
        store.failing = false
        boot.run(locked = false)
        assertEquals(FlipFailures(setOf(dialer)), store.saved)
        boot.run(locked = false)
        assertEquals("no more saves once acknowledged", 3, store.saves)
        assertEquals(setOf(dialer), process(store).run(locked = false).apply!!.flipFailures)
    }

    @Test fun aThrownSaveIsRetriedAndARestartAfterTheRetryReadsIt() {
        val store = FlakyStore(failing = true, throws = true)
        val boot = process(store)
        val outcome = boot.run(locked = true)
        assertEquals("the emergency unhide stays", false, port.state[dialer]!!.hidden)
        assertTrue(outcome.needsAttention)
        store.failing = false
        boot.run(locked = false)
        val restarted = process(store).run(locked = false)
        assertEquals(setOf(dialer), restarted.apply!!.flipFailures)
        assertTrue(restarted.needsAttention)
    }

    @Test fun aFailedAcknowledgementIsRetriedAndARestartBeforeTheRetryStillShowsTheFailure() {
        val store = FlakyStore(failing = false)
        val boot = process(store)
        boot.run(locked = true)
        store.failing = true
        boot.acknowledgeFlipFailures()
        assertEquals("not acknowledged: a new process still sees the failure",
            setOf(dialer), process(store).run(locked = false).apply!!.flipFailures)
        port.flushComponent = false
        store.failing = false
        boot.run(locked = false)
        assertEquals(FlipFailures.NONE, store.saved)
        val after = process(store).run(locked = false)
        assertTrue(after.apply!!.flipFailures.isEmpty())
        assertFalse(after.flipRecordUnknown)
    }

    @Test fun anUnreadableStoreKeepsAttentionAndStillUnhidesTheFloor() {
        val store = object : FlipFailureStore {
            override fun read(): FlipFailures = throw IOException("unreadable")
            override fun save(state: FlipFailures): Boolean = throw IOException("unwritable")
        }
        port.flushComponent = false
        val outcome = process(store).run(locked = false)
        assertEquals(false, port.state[dialer]!!.hidden)
        assertTrue(outcome.flipRecordUnknown)
        assertTrue(outcome.needsAttention)
    }

    @Test fun anUnknownRecordOutlivesALaterSuccessfulFlipUntilAcknowledged() {
        val dir = tmp.newFolder("flips")
        val store = FileFlipFailureStore(dir)
        store.save(FlipFailures(setOf(dialer)))
        File(dir, "flip-failures.rec").writeText("corrupt")
        port.flushComponent = false
        val boot = process(FileFlipFailureStore(dir))
        val outcome = boot.run(locked = false)
        assertTrue(outcome.apply!!.flipFailures.isEmpty())
        assertTrue(outcome.flipRecordUnknown)
        assertTrue(outcome.needsAttention)
        assertTrue("a restart still sees it", process(FileFlipFailureStore(dir)).run(locked = false).flipRecordUnknown)

        boot.acknowledgeFlipFailures()
        assertEquals(FlipFailures.NONE, FileFlipFailureStore(dir).read())
        assertFalse(process(FileFlipFailureStore(dir)).run(locked = false).flipRecordUnknown)
    }
}
