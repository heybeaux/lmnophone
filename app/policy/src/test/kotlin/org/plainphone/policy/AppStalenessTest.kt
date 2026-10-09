// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import java.time.Instant
import org.plainphone.core.PackageName
import kotlin.test.*

class AppStalenessTest {
    private val pkg = PackageName("com.google.android.gms")
    private val first = Instant.parse("2026-01-01T00:00:00Z")
    @Test fun `record keeps version and reported last update time`() {
        val record = AppStaleness.observe(null, pkg, 12, first, first)
        assertEquals(12L, record.versionCode)
        assertEquals(first, record.lastUpdated)
        val update = AppStaleness.observe(record, pkg, 13, first.plusSeconds(10), first.plusSeconds(20))
        assertEquals(13L, update.versionCode)
        assertEquals(first.plusSeconds(10), update.lastUpdated)
        assertEquals(first.plusSeconds(20), update.unchangedSince)
    }
    @Test fun `neutral Play services note starts exactly at thirty days and handles backwards clock`() {
        val record = AppStaleness.observe(null, pkg, 12, first, first)
        val unchanged = AppStaleness.observe(record, pkg, 12, first, first.plusSeconds(20))
        assertEquals(first, unchanged.unchangedSince)
        assertNull(AppStaleness.playServicesNote(unchanged, first.plusSeconds(30 * 86400 - 1)))
        assertEquals("Google Play services has not changed in 30 days.", AppStaleness.playServicesNote(unchanged, first.plusSeconds(30 * 86400)))
        assertNull(AppStaleness.playServicesNote(unchanged, first.minusSeconds(1)))
    }
    @Test fun `same version reinstall resets observation and other apps never get services note`() {
        val record = AppStaleness.observe(null, pkg, 12, first, first)
        val newer = first.plusSeconds(40 * 86400)
        val changed = AppStaleness.observe(record, pkg, 12, newer, newer)
        assertNull(AppStaleness.playServicesNote(changed, newer))
        assertNull(AppStaleness.playServicesNote(AppStaleness.observe(null, PackageName("test.app"), 1, first, first), newer))
    }
}
