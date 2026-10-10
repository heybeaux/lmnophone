// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.core.PackageName
import org.plainphone.policy.AppApprovalOutcome

/**
 * PLA-229 finding 2. Before any unhide, the window must resolve the candidate and every package left in
 * the applied lock-task list to a known UID. A UID that reads as absent (null) or throws cannot prove the
 * candidate is excluded, so the window refuses, for an approval and for an edit. It never removes the
 * allowed sibling or the floor to make the candidate fit.
 */
class UnknownUidExclusionTest {
    private val h = ApprovalHarness()
    private val port = h.port

    @After fun stop() = h.close()

    private enum class Fault { ABSENT, THROWS }

    private fun fault(name: PackageName, fault: Fault) {
        when (fault) {
            Fault.ABSENT -> port.uidAbsent += name
            Fault.THROWS -> port.uidUnreadable += name
        }
    }

    private fun unhides() = port.ops.count { it == "unhide ${h.target.value}" }

    private fun assertRefusedBeforeUnhide(label: String, run: () -> AppApprovalOutcome) {
        val before = unhides()
        assertEquals(label, AppApprovalOutcome.NOT_READY, run())
        assertEquals("$label: never unhidden: ${port.ops}", before, unhides())
        assertTrue("$label: hidden", port.hidden(h.target.value))
        assertFalse("$label: out of lock task", h.target in port.lockTask)
        assertTrue("$label: the floor keeps its access", port.lockTask.containsAll(setOf(h.plainphone, h.messages, h.dialer)))
    }

    private fun approval(name: PackageName, fault: Fault) {
        fault(name, fault)
        assertRefusedBeforeUnhide("approval, ${name.value} $fault") { h.approve() }
        assertFalse("never listed on Home", h.home.lists(h.target))
    }

    private fun edit(name: PackageName, fault: Fault) {
        assertEquals(AppApprovalOutcome.APPROVED, h.approve())
        fault(name, fault)
        assertRefusedBeforeUnhide("edit, ${name.value} $fault") { h.edit() }
    }

    @Test fun anApprovalRefusesAListedPackageWithAnAbsentUid() = approval(h.dialer, Fault.ABSENT)
    @Test fun anApprovalRefusesAListedPackageWithAThrowingUid() = approval(h.dialer, Fault.THROWS)
    @Test fun anApprovalRefusesACandidateWithAnAbsentUid() = approval(h.target, Fault.ABSENT)
    @Test fun anApprovalRefusesACandidateWithAThrowingUid() = approval(h.target, Fault.THROWS)

    @Test fun anEditRefusesAListedPackageWithAnAbsentUid() = edit(h.dialer, Fault.ABSENT)
    @Test fun anEditRefusesAListedPackageWithAThrowingUid() = edit(h.dialer, Fault.THROWS)
    @Test fun anEditRefusesACandidateWithAnAbsentUid() = edit(h.target, Fault.ABSENT)
    @Test fun anEditRefusesACandidateWithAThrowingUid() = edit(h.target, Fault.THROWS)

    @Test fun anAllowedSiblingWithAnAbsentUidKeepsItsAccessAndBlocksTheWindow() {
        assertEquals(AppApprovalOutcome.APPROVED, h.approve(h.sibling))
        fault(h.sibling, Fault.ABSENT)
        assertRefusedBeforeUnhide("approval beside an absent sibling") { h.approve() }
        assertTrue("the sibling keeps its access", h.visibleAndPermitted(h.sibling))
        assertTrue("the sibling stays on Home", h.home.lists(h.sibling))
    }

    @Test fun theWindowOpensAgainOnceEveryUidReads() {
        fault(h.dialer, Fault.ABSENT)
        h.dispatcher.run {
            assertFalse(h.effects.openPermissionWindow(h.identity))
            assertTrue(port.hidden(h.target.value))
            assertTrue(h.effects.closePermissionWindow(h.identity))
            port.uidAbsent.clear()
            assertTrue("the gate is not sticky", h.effects.openPermissionWindow(h.identity))
            assertFalse(port.hidden(h.target.value))
            assertTrue(h.effects.closePermissionWindow(h.identity))
        }
    }
}
