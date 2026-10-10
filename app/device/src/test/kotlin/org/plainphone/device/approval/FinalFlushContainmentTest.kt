// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.device.hiddenApp
import org.plainphone.policy.AppApprovalOutcome

/**
 * PLA-218 finding 2, through the real controller and adapter. When the final committed apply fails
 * (here: the mitigation-B flip does not read back), the controller reports NOT_READY. The discretionary
 * candidate must then not be both visible and lock-task permitted; floor repairs stay as they are.
 * A later resume with working flips completes the approval.
 */
class FinalFlushContainmentTest {
    private val h = ApprovalHarness()
    private val port = h.port

    @After fun stop() = h.close()

    private fun failFirstApply(extra: () -> Unit = {}) {
        var armed = true
        h.beforeApply = { if (armed) { armed = false; extra(); port.flipsIgnored = 2 } }
    }

    private fun assertContained(when_: String) {
        assertFalse("$when_: ${h.state()}", h.visibleAndPermitted())
        assertTrue("$when_: hidden ${h.state()}", port.hidden(h.target.value))
        assertFalse("$when_: out of lock task ${h.state()}", h.target in port.lockTask)
        assertFalse("$when_: not on Home", h.home.lists(h.target))
    }

    private fun assertResumes(expected: AppApprovalOutcome) {
        h.beforeApply = {}
        assertEquals(expected, h.resume())
        assertTrue(h.visibleAndPermitted() && h.home.lists(h.target))
    }

    @Test fun aCandidateFlipFailureLeavesTheCandidateContained() {
        failFirstApply()
        assertEquals(AppApprovalOutcome.NOT_READY, h.approve())
        assertEquals(listOf(false), h.applies)
        assertContained("approval")
        assertTrue("floor untouched", port.lockTask.containsAll(setOf(h.plainphone, h.messages, h.dialer)))
        assertResumes(AppApprovalOutcome.APPROVED)
    }

    @Test fun aCandidateFlipFailureAfterAnEditLeavesTheCandidateContained() {
        assertEquals(AppApprovalOutcome.APPROVED, h.approve())
        failFirstApply()
        assertEquals(AppApprovalOutcome.NOT_READY, h.edit())
        assertContained("edit")
        assertResumes(AppApprovalOutcome.PERMISSIONS_CHANGED)
    }

    @Test fun aFloorFlipFailureKeepsTheFloorRepairAndContainsTheCandidate() {
        failFirstApply {
            // The emergency dialer was hidden behind Plainphone's back; the floor stage unhides it first.
            port.state[h.dialer] = hiddenApp()
        }
        assertEquals(AppApprovalOutcome.NOT_READY, h.approve())
        assertEquals(listOf(false), h.applies)
        assertFalse("the floor repair stays: dialer visible", port.hidden(h.dialer.value))
        assertTrue("the floor repair stays: dialer in lock task", h.dialer in port.lockTask)
        assertContained("floor flip failure")
        assertResumes(AppApprovalOutcome.APPROVED)
    }
}
