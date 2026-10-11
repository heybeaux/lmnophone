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

/** Independent PLA-322 failure injection; production sources unchanged. */
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
    private fun unknownSurvivesRestart(throwOnRead: Boolean) {
        var unreadable = true
        var saves = 0
        var persisted = FlipFailures.NONE
        val store = object : FlipFailureStore {
            override fun read(): FlipFailures {
                if (unreadable) {
                    if (throwOnRead) throw IOException("transient read failure")
                    return FlipFailures.UNKNOWN
                }
                return persisted
            }
            override fun save(state: FlipFailures): Boolean {
                saves++
                return false
            }
        }
        val first = boot(store)
        assertTrue(first.run(false).flipRecordUnknown)
        unreadable = false
        // Kill the process before any successful persistence retry.
        val restarted = boot(store).run(false)
        println("throwOnRead=$throwOnRead saves=$saves unknownAfterRestart=${restarted.flipRecordUnknown} attentionAfterRestart=${restarted.needsAttention} setupMayFinish=${restarted.setupMayFinish}")
        assertTrue("observed unknown must survive restart until acknowledgement", restarted.flipRecordUnknown)
    }
    @Test fun transientThrownReadMustPersistUnknown() = unknownSurvivesRestart(true)
    @Test fun transientUnknownReadMustPersistUnknown() = unknownSurvivesRestart(false)
}
