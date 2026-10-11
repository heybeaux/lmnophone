// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.policy.AppApprovalOutcome

/**
 * PLA-218 finding 1. Android lets every package that shares a UID with an allowed lock-task package
 * start lock task too (`DevicePolicyManager.setLockTaskPackages`). So taking the candidate's name out of
 * the list is not enough: the window must refuse, before any unhide, while a package left in the
 * applied list shares the candidate's UID or a UID cannot be read. It never removes that sibling or the
 * floor to make room.
 */
class SharedUidExclusionTest {
    private val h = ApprovalHarness()
    private val port = h.port

    @After fun stop() = h.close()

    private fun share() { port.uids[h.sibling] = checkNotNull(port.uid(h.target)) }
    private fun unhides(name: String) = port.ops.count { it == "unhide $name" }

    @Test fun anApprovalIsRefusedBeforeTheUnhideWhileASharedUidSiblingIsAllowed() {
        share()
        assertEquals(AppApprovalOutcome.APPROVED, h.approve(h.sibling))
        assertTrue(h.visibleAndPermitted(h.sibling))
        val unhidesBefore = unhides("test.target")
        assertEquals(AppApprovalOutcome.NOT_READY, h.approve(h.target))
        assertEquals("never unhidden: ${port.ops}", unhidesBefore, unhides("test.target"))
        assertTrue(port.hidden(h.target.value))
        assertTrue("the sibling keeps its access", h.visibleAndPermitted(h.sibling))
        assertTrue("the floor keeps its access", port.lockTask.containsAll(setOf(h.plainphone, h.messages, h.dialer)))
    }

    @Test fun anEditIsRefusedBeforeTheUnhideWhileASharedUidSiblingIsAllowed() {
        // Both approved while their UIDs were distinct; the shared UID stands in for a sibling admitted
        // before this gate existed. The edit hides the target first, then must not reopen it.
        assertEquals(AppApprovalOutcome.APPROVED, h.approve(h.target))
        assertEquals(AppApprovalOutcome.APPROVED, h.approve(h.sibling))
        share()
        val unhidesBefore = unhides("test.target")
        assertEquals(AppApprovalOutcome.NOT_READY, h.edit(h.target))
        assertEquals("never unhidden again: ${port.ops}", unhidesBefore, unhides("test.target"))
        assertTrue(port.hidden(h.target.value))
        assertFalse(h.target in port.lockTask)
        assertTrue("the sibling keeps its access", h.visibleAndPermitted(h.sibling))
    }

    @Test fun anUnreadableUidIsRefusedBeforeTheUnhide() {
        for (unreadable in listOf(h.target, h.dialer)) {
            port.uidUnreadable.clear(); port.uidUnreadable += unreadable
            val unhidesBefore = unhides("test.target")
            assertFalse("unreadable ${unreadable.value}", h.dispatcher.run { h.effects.openPermissionWindow(h.identity) })
            assertEquals(unhidesBefore, unhides("test.target"))
            assertTrue(port.hidden(h.target.value))
            assertTrue(h.dispatcher.run { h.effects.closePermissionWindow(h.identity) })
        }
    }

    @Test fun theControlWithADifferentUidStillOpensAndApproves() {
        assertEquals(AppApprovalOutcome.APPROVED, h.approve(h.sibling))
        assertEquals(AppApprovalOutcome.APPROVED, h.approve(h.target))
        assertTrue(h.visibleAndPermitted(h.target) && h.visibleAndPermitted(h.sibling))
    }
}
