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

    /**
     * Reads fail (a throw, or [FlipFailures.UNKNOWN]) while [unreadable]; saves fail while [failing].
     * Reads return the last acknowledged save once readable (PLA-307 finding 3).
     */
    private class TransientStore(var unreadable: Boolean, private val throwOnRead: Boolean, var failing: Boolean = false) : FlipFailureStore {
        var saved = FlipFailures.NONE
        var saves = 0
        override fun read(): FlipFailures {
            if (unreadable) if (throwOnRead) throw IOException("transient") else return FlipFailures.UNKNOWN
            return saved
        }
        override fun save(state: FlipFailures): Boolean {
            saves++
            if (failing) return false
            saved = state
            return true
        }
    }

    @Test fun aTransientReadThrowIsSavedAsUnknownAndARestartStillSeesIt() = transientUnknownIsDurable(throwOnRead = true)

    @Test fun aTransientUnknownReadIsSavedAsUnknownAndARestartStillSeesIt() = transientUnknownIsDurable(throwOnRead = false)

    private fun transientUnknownIsDurable(throwOnRead: Boolean) {
        val store = TransientStore(unreadable = true, throwOnRead = throwOnRead)
        port.flushComponent = false
        val boot = process(store)
        assertTrue(boot.run(locked = false).flipRecordUnknown)
        assertEquals("a newly found unknown state is saved at once", FlipFailures.UNKNOWN, store.saved)
        store.unreadable = false
        val restarted = process(store).run(locked = false)
        assertTrue(restarted.flipRecordUnknown)
        assertTrue(restarted.needsAttention)
    }

    @Test fun aFailedUnknownSaveIsRetriedUntilAcknowledgedAndARestartBeforeThatStillShowsAttention() {
        val store = TransientStore(unreadable = true, throwOnRead = true, failing = true)
        port.flushComponent = false
        val boot = process(store)
        val first = boot.run(locked = false)
        assertTrue(first.flipRecordUnknown)
        assertEquals("the emergency unhide stays", false, port.state[dialer]!!.hidden)
        boot.run(locked = false)
        assertEquals("retried on the next pass", 2, store.saves)
        assertTrue("still unreadable: a restart still sees unknown", process(TransientStore(true, true)).run(locked = false).flipRecordUnknown)
        store.unreadable = false
        store.failing = false
        boot.run(locked = false)
        assertEquals(FlipFailures.UNKNOWN, store.saved)
        boot.run(locked = false)
        assertEquals("no more saves once acknowledged", 3, store.saves)
        assertTrue(process(store).run(locked = false).flipRecordUnknown)

        boot.acknowledgeFlipFailures()
        assertEquals(FlipFailures.NONE, store.saved)
        assertFalse("only an acknowledgement clears it", process(store).run(locked = false).flipRecordUnknown)
    }
}
