// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.boot

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
import org.plainphone.core.PackageName

/**
 * PLA-399 findings 2 and 3: an entry leaves the record only on positive evidence that our suspension
 * is not in place, and only a save acknowledged in this process lets a pending entry be suspended.
 * The first two tests are the Verifier's reproductions.
 */
class FallbackBoundaryTest {
    private val home = PackageName("org.plainphone")
    private val app = PackageName("org.example.app")

    private class Port(val names: Set<PackageName>) : SuspensionPort {
        val suspended = mutableSetOf<PackageName>()
        var unreadAfterApply = false
        var unread = false
        var applyThrows = false
        var refuse = emptySet<PackageName>()
        override fun installed() = names
        override fun suspended(name: PackageName): Boolean? {
            if (unread) throw IOException("readback unavailable")
            return if (name in names) name in suspended else null
        }
        override fun setSuspended(names: Set<PackageName>, suspended: Boolean): Set<PackageName> {
            if (suspended && applyThrows) {
                this.suspended.addAll(names) // Applied, but the call still failed.
                throw IllegalStateException("binder")
            }
            if (suspended) this.suspended.addAll(names - refuse) else this.suspended.removeAll(names)
            if (suspended && unreadAfterApply) unread = true
            return if (suspended) names intersect refuse else emptySet()
        }
    }

    private class Record : SuspensionRecord {
        var visible = emptySet<PackageName>()
        var ack = true
        var saves = 0
        override fun read() = visible
        override fun save(names: Set<PackageName>): Boolean {
            saves++
            visible = names // Like RecordFile: the rename succeeds, the directory sync may fail.
            return ack
        }
    }

    @Test fun successfulSuspendWithUnreadableReadbackRetainsRecoveryObligation() {
        val port = Port(setOf(home, app)).apply { unreadAfterApply = true }
        val record = Record()
        val fallback = FallbackContainment(port, record, { true }, { true })
        fallback.contain(setOf(home))
        assertTrue(app in port.suspended)
        assertTrue("Successful suspend must remain recorded when readback throws", app in record.visible)
        port.unread = false
        fallback.liftIfLocked()
        assertFalse(app in port.suspended)
    }

    @Test fun visibleButUnacknowledgedSaveCannotAuthorizeNextPassSuspend() {
        val port = Port(setOf(home, app))
        val record = Record().apply { ack = false }
        val fallback = FallbackContainment(port, record, { false }, { false })
        assertFalse(fallback.contain(setOf(home)).recorded)
        assertTrue(port.suspended.isEmpty())
        val second = fallback.contain(setOf(home))
        assertFalse("No durable save succeeded, including on retry", second.recorded)
        assertTrue("No suspend until durable save succeeds", port.suspended.isEmpty())
    }

    @Test fun aSuspendCallThatThrowsKeepsItsRecoveryEntry() {
        val port = Port(setOf(home, app)).apply { applyThrows = true }
        val record = Record()
        FallbackContainment(port, record, { false }, { false }).contain(setOf(home))
        assertTrue(app in port.suspended)
        assertTrue(app in record.visible)
    }

    @Test fun anEntryAndroidRefusedAndThatReadsUnsuspendedLeavesTheRecord() {
        val port = Port(setOf(home, app)).apply { refuse = setOf(app) }
        val record = Record()
        val result = FallbackContainment(port, record, { false }, { false }).contain(setOf(home))
        assertFalse(app in record.visible)
        assertTrue(app in result.refused)
    }

    @Test fun aNewProcessSavesTheRecordAgainBeforeApplyingPendingEntries() {
        val port = Port(setOf(home, app))
        // Bytes left visible by an earlier process whose save was not acknowledged.
        val record = Record().apply { visible = setOf(app); ack = false }
        val result = FallbackContainment(port, record, { false }, { false }).contain(setOf(home))
        assertEquals(1, record.saves)
        assertFalse(result.recorded)
        assertTrue(port.suspended.isEmpty())

        record.ack = true
        val retry = FallbackContainment(port, record, { false }, { false }).contain(setOf(home))
        assertTrue(retry.recorded)
        assertEquals(setOf(app), port.suspended)
    }

    @Test fun anAcknowledgedUnchangedRecordIsNotSavedAgainInTheSameProcess() {
        val port = Port(setOf(home, app))
        val record = Record()
        val fallback = FallbackContainment(port, record, { false }, { false })
        fallback.contain(setOf(home))
        val saves = record.saves
        assertTrue(fallback.contain(setOf(home)).recorded)
        assertEquals(saves, record.saves)
    }
}
