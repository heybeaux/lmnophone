// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.boot

import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.PackageName
import org.plainphone.device.*
import org.plainphone.device.storage.FileJournalStore
import org.plainphone.policy.*

/**
 * Started as the Verifier's independent PLA-322 failure injection (finding 4). Per the Lead's decision on
 * PLA-324 the two cases now pin the stated limit instead of an unconditional promise.
 */
class Pla322ReviewTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dialer = PackageName("com.android.dialer")
    private val home = PackageName("org.plainphone")
    private val port = FakeDevicePolicyPort(mapOf(dialer.value to visible(), home.value to visible()))
    private fun boot(store: FlipFailureStore): BootReapply {
        val journal = FileJournalStore(tmp.newFolder(), null, "model", "build")
        assertTrue(journal.compareAndSet(SessionJournal.Damaged, SessionJournal.Empty))
        val input = PolicyInput(model = "model", fingerprint = "build", plainphone = home,
            messages = PackageName("com.example.messages"),
            installed = listOf(InstalledPackage(dialer, true, 1), InstalledPackage(home, false, 1)),
            identities = listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, FloorRole.SYSTEM_DIALER.source)))
        return BootReapply(journal, port, emptySet(),
            { PolicyEngine.decide(input.copy(lockedBoot = it)).copy(needsAttention = false) },
            RestrictionFilter.TEST_BUILD, flipFailures = store)
    }
    /** A store whose reads fail while [unreadable] and whose saves succeed only when [saveWorks] says so. */
    private class FlakyStore(var unreadable: Boolean, val throwOnRead: Boolean, val saveWorks: (Int) -> Boolean) : FlipFailureStore {
        var saves = 0
        var persisted = FlipFailures.NONE
        override fun read(): FlipFailures {
            if (unreadable) {
                if (throwOnRead) throw IOException("transient read failure")
                return FlipFailures.UNKNOWN
            }
            return persisted
        }
        override fun save(state: FlipFailures): Boolean {
            saves++
            if (!saveWorks(saves)) return false
            persisted = state
            return true
        }
    }

    /**
     * The attention record's stated limit (copy-test spec, "The attention record"; PLA-322 finding 4; Lead
     * decision on PLA-324): durable once a save succeeds. Until then the running process shows attention
     * and retries the save on every pass. If every save fails and the process dies before one succeeds,
     * the record is lost, because no durable channel was available to remember it. This pins that limit;
     * it is not a promise that only acknowledgement clears unknown.
     */
    private fun knownLimitEverySaveFailsThenProcessDies(throwOnRead: Boolean) {
        val store = FlakyStore(unreadable = true, throwOnRead = throwOnRead) { false }
        val first = boot(store)
        val pass1 = first.run(false)
        assertTrue("the running process shows unknown", pass1.flipRecordUnknown)
        assertTrue("and attention", pass1.needsAttention)
        assertTrue("emergency repair and the pass do not wait for the record", pass1.ready)
        val pass2 = first.run(false)
        assertTrue(pass2.flipRecordUnknown && pass2.needsAttention)
        assertEquals("the failed save is retried on the next pass", 2, store.saves)
        store.unreadable = false
        // The process dies before any save succeeds: the restart reads the old empty record.
        val restarted = boot(store).run(false)
        assertFalse("known limit: lost when every save failed before the process died", restarted.flipRecordUnknown)
    }
    @Test fun knownLimitThrownReadLostWhenEverySaveFailsBeforeDeath() = knownLimitEverySaveFailsThenProcessDies(true)
    @Test fun knownLimitUnknownReadLostWhenEverySaveFailsBeforeDeath() = knownLimitEverySaveFailsThenProcessDies(false)

    /** Durable once a save succeeds: the first save fails, a later pass saves, and a restart keeps unknown. */
    private fun aLaterSuccessfulSaveSurvivesRestart(throwOnRead: Boolean) {
        val store = FlakyStore(unreadable = true, throwOnRead = throwOnRead) { it > 1 }
        val first = boot(store)
        assertTrue(first.run(false).flipRecordUnknown)
        assertTrue(first.run(false).flipRecordUnknown)
        assertEquals(2, store.saves)
        store.unreadable = false
        val restarted = boot(store).run(false)
        assertTrue("saved unknown survives restart", restarted.flipRecordUnknown)
        assertTrue(restarted.needsAttention)
    }
    @Test fun thrownReadSavedOnSecondPassSurvivesRestart() = aLaterSuccessfulSaveSurvivesRestart(true)
    @Test fun unknownReadSavedOnSecondPassSurvivesRestart() = aLaterSuccessfulSaveSurvivesRestart(false)
}
