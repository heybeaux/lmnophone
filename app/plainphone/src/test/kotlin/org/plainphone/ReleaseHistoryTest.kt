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
    @Test fun productionAboutUsesInstalledMetadataAndDoesNotInventHistory() {
        val latest = releaseAbout("installed-name", ObservedVersions(49,null), true)
        assertEquals("installed-name", latest.versionName)
        assertEquals(49L, latest.versionCode)
        assertTrue(latest.sincePrevious.isEmpty())
        assertTrue(latest.isDeviceSecure)
        assertEquals(listOf("48", "47"), releaseAbout("actual", ObservedVersions(49,46)).sincePrevious.map { it.version })
        assertEquals("49", releaseAbout("actual", ObservedVersions(49,46)).latest!!.version)
        assertTrue(releaseAbout("actual", ObservedVersions(49,48)).sincePrevious.isEmpty())
        assertTrue(releaseAbout("actual", ObservedVersions(48,49)).sincePrevious.isEmpty())
        assertNull(releaseAbout("unknown", ObservedVersions(100,null)).latest)
        assertEquals("48", latest.older.first().version)
    }
    @Test fun everyBuildNeedsItsOwnReleaseNote() {
        val code = Regex("versionCode = (\\d+)").find(File("build.gradle.kts").readText())!!.groupValues[1].toLong()
        assertNotNull("Missing release notes for versionCode $code", ReleaseNotes.current(code))
    }
}
