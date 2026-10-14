// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.plainphone.release.*

class ReleaseHistoryTest {
    private class Store(var value: ObservedVersions? = null) : VersionStore {
        var writes = 0
        var fail = false
        override fun read() = value
        override fun commit(value: ObservedVersions): Boolean {
            writes++
            if (fail) return false
            this.value = value
            return true
        }
    }
    @Test fun firstObservationDoesNotInventPreviousInstall() {
        val store = Store()
        assertNull(VersionTracker(store).observe(49).previous)
        assertEquals(ObservedVersions(49, null), store.value)
        assertNull(VersionTracker(store).observe(49).previous)
        assertEquals(1, store.writes)
    }
    @Test fun upgradeAndRestartKeepPreviousUntilNextUpgrade() {
        val store = Store(ObservedVersions(48, 47))
        assertEquals(ObservedVersions(49, 48), VersionTracker(store).observe(49))
        assertEquals(ObservedVersions(49, 48), VersionTracker(store).observe(49))
        assertEquals(1, store.writes)
    }
    @Test fun skippedVersionsNewestFirstAndDowngradeOrUnknownLatestOnly() {
        assertEquals(listOf(49L,48L,47L), ReleaseNotes.since(49,46).map { it.lastCode })
        assertTrue(ReleaseNotes.since(49,null).isEmpty())
        assertTrue(ReleaseNotes.since(48,49).isEmpty())
        assertTrue(ReleaseNotes.since(49,1).isEmpty())
        val store = Store(ObservedVersions(49,48))
        assertEquals(ObservedVersions(48,49), VersionTracker(store).observe(48))
    }
    @Test fun failedWriteMakesNoHistoricalClaimAndCanRetry() {
        val store = Store(ObservedVersions(48,null)).also { it.fail = true }
        assertNull(VersionTracker(store).observe(49).previous)
        assertEquals(48L, store.value!!.current)
        store.fail = false
        assertEquals(48L, VersionTracker(store).observe(49).previous)
    }
    @Test fun everyBuildNeedsItsOwnReleaseNote() {
        val code = Regex("versionCode = (\\d+)").find(File("build.gradle.kts").readText())!!.groupValues[1].toLong()
        assertNotNull("Missing release notes for versionCode $code", ReleaseNotes.current(code))
    }
}
