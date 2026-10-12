// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.boot

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.PackageName
import org.plainphone.device.*
import org.plainphone.device.storage.FileJournalStore
import org.plainphone.policy.*

/** What fallback containment never suspends comes from the same pass's plan (PLA-344). */
class FallbackKeepTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dialer = PackageName("com.android.dialer")
    private val home = PackageName("org.plainphone")
    private val chrome = PackageName("com.android.chrome")
    private val port = FakeDevicePolicyPort(mapOf(dialer.value to visible(), home.value to visible(), chrome.value to visible()))
    private val input = PolicyInput(model = "model", fingerprint = "build", plainphone = home,
        messages = PackageName("com.example.messages"),
        installed = listOf(InstalledPackage(dialer, true, 1), InstalledPackage(home, false, 1), InstalledPackage(chrome, false, 1)),
        identities = listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, FloorRole.SYSTEM_DIALER.source)))

    private fun boot(decide: (Boolean) -> PolicyPlan): BootReapply {
        val journal = FileJournalStore(tmp.newFolder(), null, "model", "build")
        assertTrue(journal.compareAndSet(SessionJournal.Damaged, SessionJournal.Empty))
        return BootReapply(journal, port, emptySet(), decide, RestrictionFilter.TEST_BUILD,
            floor = { FloorResult(setOf(dialer), setOf(dialer), true) }, home = home)
    }

    @Test fun keepIsThePlansLockTaskListFloorAndHomeWhenFeaturesDoNotReadBack() {
        port.featuresStick = false
        val plan = PolicyEngine.decide(input)
        val outcome = boot { plan }.run(false)
        assertFalse(outcome.ready)
        assertEquals(plan.lockTaskPackages + plan.neverHide + plan.emergencyUi + home, outcome.keep)
        assertTrue(dialer in outcome.keep)
        assertFalse(chrome in outcome.keep)
    }

    @Test fun keepFallsBackToTheOsFloorAndHomeWithoutAPlan() {
        port.featuresStick = false
        val outcome = boot { throw IllegalStateException("no facts") }.run(false)
        assertEquals(setOf(dialer, home), outcome.keep)
    }
}
