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

    private fun boot(
        decide: (Boolean) -> PolicyPlan,
        floor: () -> FloorResult? = { FloorResult(setOf(dialer), setOf(dialer), true) },
        floorComplete: (() -> Boolean)? = null,
    ): BootReapply {
        val journal = FileJournalStore(tmp.newFolder(), null, "model", "build")
        assertTrue(journal.compareAndSet(SessionJournal.Damaged, SessionJournal.Empty))
        return if (floorComplete == null) {
            BootReapply(journal, port, emptySet(), decide, RestrictionFilter.TEST_BUILD, floor = floor, home = home)
        } else {
            BootReapply(journal, port, emptySet(), decide, RestrictionFilter.TEST_BUILD, floor = floor, home = home,
                floorComplete = floorComplete)
        }
    }

    @Test fun keepIsThePlansLockTaskListFloorAndHomeWhenFeaturesDoNotReadBack() {
        port.featuresStick = false
        val plan = PolicyEngine.decide(input)
        val outcome = boot({ plan }).run(false)
        assertFalse(outcome.ready)
        assertEquals(plan.lockTaskPackages + plan.neverHide + plan.emergencyUi + home, outcome.keep)
        assertTrue(dialer in outcome.keep)
        assertFalse(chrome in outcome.keep)
    }

    @Test fun keepFallsBackToTheOsFloorAndHomeWithoutAPlan() {
        port.featuresStick = false
        val outcome = boot({ throw IllegalStateException("no facts") }).run(false)
        assertEquals(setOf(dialer, home), outcome.keep)
    }

    // PLA-399 finding 1: containment may use the keep set only when the floor behind it was read completely.
    @Test fun keepIsUnknownByDefault() {
        port.featuresStick = false
        assertFalse(boot({ PolicyEngine.decide(input) }).run(false).keepComplete)
    }

    @Test fun keepIsCompleteWhenThePlansFloorWasReadCompletely() {
        port.featuresStick = false
        assertTrue(boot({ PolicyEngine.decide(input) }, floorComplete = { true }).run(false).keepComplete)
    }

    @Test fun keepIsUnknownWhenThePlansFloorHadAnUnreadableRole() {
        port.featuresStick = false
        assertFalse(boot({ PolicyEngine.decide(input) }, floorComplete = { false }).run(false).keepComplete)
    }

    @Test fun keepIsUnknownWhenNeitherThePlanNorTheOsFloorCouldBeRead() {
        port.featuresStick = false
        val outcome = boot({ throw IllegalStateException("no facts") }, floor = { throw IllegalStateException("no floor") },
            floorComplete = { true }).run(false)
        assertEquals(setOf(home), outcome.keep)
        assertFalse(outcome.keepComplete)
    }

    @Test fun keepIsCompleteFromACompletelyReadOsFloorWithoutAPlan() {
        port.featuresStick = false
        val outcome = boot({ throw IllegalStateException("no facts") }, floorComplete = { true }).run(false)
        assertTrue(outcome.keepComplete)
    }

    @Test fun aThrowingCompletenessReadIsUnknown() {
        port.featuresStick = false
        assertFalse(boot({ PolicyEngine.decide(input) }, floorComplete = { throw IllegalStateException() }).run(false).keepComplete)
    }
}
