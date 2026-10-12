// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.core.CertDigest
import org.plainphone.core.PackageName
import org.plainphone.device.ReadbackPolicyApplier
import org.plainphone.policy.AppApprovalOutcome
import org.plainphone.policy.AppApprovalPhase
import org.plainphone.policy.BatteryState
import org.plainphone.policy.PolicyEngine

/**
 * [ApprovalRecovery], the app's wiring of the approval controller (PLA-281). Every policy pass
 * (`PolicyRuntime.run`: boot, user unlock, Home start, package events, the alarm, after a trusted-person
 * action) first gives the controller one retry ([ApprovalRecovery.beforePass]), then decides with
 * [ApprovalRecovery.forPass] and hands back only through [ApprovalRecovery.handback]. [pass] drives that
 * sequence with the real controller, the real [DeviceApprovalEffects] and the boot re-apply's applier.
 *
 * Setup: [sibling] is an already-approved active app. A failed activation of [target] contains both.
 */
class ApprovalRecoveryTest {
    private val h = ApprovalHarness()
    private val target = h.target
    private val sibling = h.sibling
    private val recovery = ApprovalRecovery(h.dispatcher, h.store, h.controller)
    private var failApply = false

    init {
        h.beforeApply = { if (failApply) error("apply failed") }
        assertEquals(AppApprovalOutcome.APPROVED, h.approve(sibling))
        assertTrue(h.visibleAndPermitted(sibling))
    }

    @After fun stop() = h.close()

    /** One ordinary pass after its controller retry; true when it may hand the phone back. */
    private fun pass(locked: Boolean = false): Boolean {
        recovery.beforePass(locked)
        return h.dispatcher.run {
            val used = recovery.forPass()
            val plan = PolicyEngine.decide(h.input().copy(approvalState = used, lockedBoot = locked))
            ReadbackPolicyApplier(h.port).apply(plan).failedStage == null && recovery.handback(used)
        }
    }

    private fun assertContained(name: PackageName) {
        assertTrue("hidden: ${h.state(name)}", h.port.hidden(name.value))
        assertFalse("out of lock task: ${h.state(name)}", name in h.port.lockTask)
    }

    /** Approves [target] with its final activation failing: COMMITTED stays pending, both apps contained. */
    private fun failedActivation() {
        failApply = true
        assertEquals(AppApprovalOutcome.NOT_READY, h.approve(target))
        failApply = false
        assertEquals(AppApprovalPhase.COMMITTED, h.approvals.pending?.phase)
        assertContained(target)
        assertContained(sibling)
    }

    @Test fun containedAppsComeBackOnceARetrySucceeds() {
        failedActivation()
        assertTrue(pass())
        assertNull("the operation finished", h.approvals.pending)
        assertTrue(h.state(target), h.visibleAndPermitted(target))
        assertTrue("the contained sibling is back: ${h.state(sibling)}", h.visibleAndPermitted(sibling))
    }

    @Test fun anOrdinaryPassNeverBringsBackWhatAFailedActivationContains() {
        failedActivation()
        failApply = true // the retry fails again; the pass after it still decides
        pass()
        assertNull("no approvals while an operation is pending", recovery.forPass())
        assertNotNull(h.approvals.apps[sibling])
        assertContained(target)
        assertContained(sibling)
    }

    @Test fun startupPassRunsStartupRecoveryOnTheProcessFirstPass() {
        failedActivation()
        // A new process: the first pass of a fresh wiring runs onStart, which resumes the COMMITTED op.
        val restarted = ApprovalRecovery(h.dispatcher, h.store, h.controller)
        assertEquals(AppApprovalOutcome.APPROVED, restarted.beforePass(locked = false))
        assertTrue(h.visibleAndPermitted(target))
        assertTrue(h.visibleAndPermitted(sibling))
    }

    @Test fun lockedPassNeverRunsTheController() {
        failedActivation()
        val before = h.approvals
        assertNull(recovery.beforePass(locked = true))
        assertTrue("the locked pass changed nothing", before === h.approvals)
        assertContained(sibling)
    }

    @Test fun lowBatteryRetryStaysContainedAndALaterAlarmPassActivates() {
        failedActivation()
        h.battery = BatteryState(2, false)
        assertEquals(AppApprovalOutcome.LOW_BATTERY, recovery.beforePass(locked = false))
        pass()
        assertContained(target)
        assertContained(sibling)
        h.battery = BatteryState(2, true)
        assertTrue(pass())
        assertTrue(h.visibleAndPermitted(target))
        assertTrue(h.visibleAndPermitted(sibling))
    }

    @Test fun packageEventPassWithAReplacedAppKeepsItContained() {
        failedActivation()
        h.grants.signers = setOf(CertDigest("cd".repeat(32)))
        assertEquals(AppApprovalOutcome.NOT_READY, recovery.beforePass(locked = false))
        pass()
        assertEquals(AppApprovalPhase.COMMITTED, h.approvals.pending?.phase)
        assertContained(target)
        assertContained(sibling)
    }

    @Test fun pendingEditStaysContainedUntilItsRetrySucceeds() {
        assertEquals(AppApprovalOutcome.APPROVED, h.approve(target))
        h.grants.signers = null // the permission window cannot write: the edit stays EDITING
        assertEquals(AppApprovalOutcome.NOT_READY, h.edit(target))
        assertEquals(AppApprovalPhase.EDITING, h.approvals.pending?.phase)
        pass()
        assertNull(recovery.forPass())
        assertContained(target)
        assertContained(sibling)
        h.grants.signers = setOf(CertDigest("ab".repeat(32)))
        assertTrue(pass())
        assertNull(h.approvals.pending)
        assertTrue(h.visibleAndPermitted(target))
        assertTrue(h.visibleAndPermitted(sibling))
    }

    @Test fun ambiguousCompletionThatLandedIsUsedByTheNextPass() {
        // The completion write lands, but its compare-and-set returns false: the controller contains.
        h.lostAck = { it.pending == null && target in it.apps }
        assertEquals(AppApprovalOutcome.NOT_READY, h.approve(target))
        h.lostAck = { false }
        assertNull("the completion is durable", h.approvals.pending)
        assertContained(target)
        assertContained(sibling)
        // The next pass reads the durable record, not the false return.
        assertTrue(pass())
        assertTrue(h.visibleAndPermitted(target))
        assertTrue(h.visibleAndPermitted(sibling))
    }

    @Test fun ambiguousCommitThatLandedIsActivatedByTheNextRetry() {
        h.lostAck = { it.pending?.phase == AppApprovalPhase.COMMITTED }
        assertEquals(AppApprovalOutcome.NOT_READY, h.approve(target))
        h.lostAck = { false }
        assertEquals(AppApprovalPhase.COMMITTED, h.approvals.pending?.phase)
        assertNull(recovery.forPass())
        assertTrue(pass())
        assertNull(h.approvals.pending)
        assertTrue(h.visibleAndPermitted(target))
        assertTrue(h.visibleAndPermitted(sibling))
    }

    @Test fun trustedPersonOperationRunsWholeOnTheDispatcherAndTheNextPassKeepsIt() {
        val onDispatcher = recovery.operation { h.dispatcher.onDispatcher }
        assertTrue(onDispatcher)
        assertEquals(AppApprovalOutcome.APPROVED, recovery.operation { h.approve(target) })
        assertTrue(pass())
        assertTrue(h.visibleAndPermitted(target))
        assertTrue(h.visibleAndPermitted(sibling))
    }

    @Test fun anUnreadableRecordGivesNoApprovalsAndNoHandbackClaim() {
        h.unreadable = true
        assertEquals(AppApprovalOutcome.NOT_READY, recovery.beforePass(locked = false))
        assertNull(recovery.forPass())
        assertNull(recovery.pending())
        pass()
        assertContained(sibling)
        h.unreadable = false
        assertTrue(pass())
        assertTrue(h.visibleAndPermitted(sibling))
    }

    @Test fun handbackRefusesAPassWhoseApprovalsAreNoLongerCurrent() {
        val used = h.dispatcher.run { recovery.forPass() }
        assertNotNull(used)
        failedActivation()
        assertFalse(h.dispatcher.run { recovery.handback(used) })
        assertTrue("a pass that used no approvals may hand back", h.dispatcher.run { recovery.handback(null) })
        assertEquals(true, recovery.pending())
    }
}
