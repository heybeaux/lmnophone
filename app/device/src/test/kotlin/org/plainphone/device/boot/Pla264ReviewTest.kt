// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.boot

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.PackageName
import org.plainphone.device.*
import org.plainphone.device.storage.FileFlipFailureStore
import org.plainphone.device.storage.FileJournalStore
import org.plainphone.policy.*

class Pla264ReviewTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dialer = PackageName("com.android.dialer")
    private val home = PackageName("org.plainphone")
    private fun input() = PolicyInput(
        model = "model", fingerprint = "build", plainphone = home, messages = PackageName("com.example.messages"),
        installed = listOf(InstalledPackage(dialer, true, 1), InstalledPackage(home, false, 1)),
        identities = listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, FloorRole.SYSTEM_DIALER.source)),
    )
    private fun boot(port: DevicePolicyPort, store: FlipFailureStore): BootReapply {
        val journal = FileJournalStore(tmp.newFolder(), null, "model", "build")
        assertTrue(journal.compareAndSet(SessionJournal.Damaged, SessionJournal.Empty))
        return BootReapply(journal, port, emptySet(), { PolicyEngine.decide(input().copy(lockedBoot = it)) },
            RestrictionFilter.TEST_BUILD, flipFailures = store)
    }
    @Test fun failedSaveMustRetryOnNextPassBeforeRestart() {
        val port = FakeDevicePolicyPort(mapOf(dialer.value to hiddenApp(), home.value to visible()), flushComponent = null)
        var saves = 0
        var persisted = FlipFailures.NONE
        val store = object : FlipFailureStore {
            override fun read() = persisted
            override fun save(state: FlipFailures): Boolean {
                saves++
                if (saves == 1) return false
                persisted = state
                return true
            }
        }
        val runtime = boot(port, store)
        assertEquals(setOf(dialer), runtime.run(true).apply!!.flipFailures)
        runtime.run(false)
        assertEquals("retry must persist the earlier failure", setOf(dialer), persisted.names)
        assertEquals(setOf(dialer), boot(port, store).run(false).apply!!.flipFailures)
    }
    @Test fun corruptNewFailureMustNotFallBackToOlderEmptyRecord() {
        val dir = tmp.newFolder()
        val store = FileFlipFailureStore(dir)
        assertTrue(store.save(FlipFailures.NONE))
        assertTrue(store.save(FlipFailures(setOf(dialer))))
        File(dir, "flip-failures.rec").writeText("corrupt")
        // PLA-269: the damaged name itself cannot be recovered (the review does not require it); the
        // record must read as unknown, which keeps attention, instead of the older empty copy.
        assertTrue("corruption must not silently clear attention", FileFlipFailureStore(dir).read().needsAttention)
    }
    @Test fun featureReadbackMustPrecedeNewHomeMembership() {
        val fake = FakeDevicePolicyPort(mapOf(dialer.value to visible(), home.value to visible()))
        fake.features = setOf(LockTaskFeature.GLOBAL_ACTIONS)
        var unsafeMembership = false
        val port = object : DevicePolicyPort by fake {
            override fun setLockTaskFeatures(features: Set<LockTaskFeature>) { /* write did not stick */ }
            override fun setLockTaskPackages(packages: Set<PackageName>) {
                if (home in packages && LockTaskFeature.KEYGUARD !in fake.features) unsafeMembership = true
                fake.setLockTaskPackages(packages)
            }
        }
        boot(port, InMemoryFlipFailureStore()).run(true)
        assertFalse("Home must not become eligible with KEYGUARD missing", unsafeMembership)
    }
}
