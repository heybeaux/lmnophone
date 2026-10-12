// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.Mode
import org.plainphone.device.authority.ModeSwitchRig.Companion.ALEX
import org.plainphone.device.authority.ModeSwitchRig.Companion.SAM
import org.plainphone.policy.ElevationOutcome
import org.plainphone.policy.ElevationTarget
import org.plainphone.policy.PeopleChangeOutcome
import org.plainphone.policy.WaitingEvent
import org.plainphone.policy.WaitingPhase

/**
 * PLA-97 slice 3: faults at every mode-switch boundary over the file-backed joint record. An effect
 * failure or a crash at any write point leaves the old or the new credentials, never a mix; a retry
 * from a new process finishes exactly once; and the durable fence on new elevation authority holds
 * until the terminal write. JVM file-system tests: no Android durability or real wipe accounts.
 */
class ModeSwitchFaultTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun rig(mode: Mode) = ModeSwitchRig(mode, tmp.newFolder())

    /** Old self-managed holder sam, or the switched supervised holder alex; nothing in between. */
    private fun assertOldOrNew(rig: ModeSwitchRig, what: String) {
        val c = rig.credentials()
        assertTrue("$what: ${c.mode} ${c.people.keys}",
            (c.mode == Mode.SELF_MANAGED && c.people.keys == setOf(SAM) && c.structureGeneration == 0L && c.appliedPeopleChange == null) ||
                (c.mode == Mode.TRUSTED && c.people.keys == setOf(ALEX) && c.structureGeneration == 1L && c.appliedPeopleChange != null))
    }

    /** Drives the switch to COMPLETED from a new process and checks it applied once. */
    private fun finish(rig: ModeSwitchRig, id: String, what: String) {
        rig.wire()
        val first = rig.people.tick(id, WaitingPhase.COMMITTING).outcome
        if (first == PeopleChangeOutcome.APPLIED) assertEquals(what, PeopleChangeOutcome.COMPLETED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        else assertEquals(what, PeopleChangeOutcome.COMPLETED, first)
        val c = rig.credentials()
        assertEquals(what, Mode.TRUSTED, c.mode); assertEquals(what, setOf(ALEX), c.people.keys); assertEquals(what, 1L, c.structureGeneration)
        assertEquals(what, WaitingPhase.COMPLETED, rig.journal().operation?.phase)
        assertEquals(what, 1, rig.journal().texts.count { it.event == WaitingEvent.COMPLETED })
    }

    @Test fun anEffectFailureAtEveryStepChangesNoCredentialsAndARetryFinishes() {
        for (step in ModeSwitchFakeEffects.STEPS) {
            val rig = rig(Mode.SELF_MANAGED)
            val id = rig.toSupervised(); rig.mature(id)
            rig.effects.failAt = step
            assertEquals(step, PeopleChangeOutcome.NEEDS_ATTENTION, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
            assertOldOrNew(rig, step)
            assertEquals(step, Mode.SELF_MANAGED, rig.credentials().mode)
            assertEquals(step, WaitingPhase.COMMITTING, rig.journal().operation?.phase)
            rig.effects.failAt = null
            finish(rig, id, step)
        }
    }

    @Test fun aCrashAtEveryEffectStepResumesForwardFromANewProcess() {
        for (step in ModeSwitchFakeEffects.STEPS) {
            val rig = rig(Mode.SELF_MANAGED)
            val id = rig.toSupervised(); rig.mature(id)
            rig.effects.onCall = { if (it == step) throw SimulatedCrash() }
            assertTrue(step, runCatching { rig.people.tick(id, WaitingPhase.COMMITTING) }.isFailure)
            rig.effects.onCall = null
            assertEquals(step, Mode.SELF_MANAGED, rig.credentials().mode)
            finish(rig, id, step)
        }
    }

    @Test fun aCrashAtEveryWritePointOfTheSwapLeavesOldOrNewAndFinishesOnce() {
        for (point in WritePoint.entries) {
            val rig = rig(Mode.SELF_MANAGED)
            val id = rig.toSupervised(); rig.mature(id)
            assertTrue("$point fired", rig.crashAt(point) { rig.people.tick(id, WaitingPhase.COMMITTING) }.isFailure)
            rig.wire()
            assertOldOrNew(rig, "$point")
            // Before or after a landed swap, a fresh-PIN stop is offered only before it.
            val landed = rig.credentials().appliedPeopleChange != null
            assertEquals("$point", landed, rig.people.prepareStop(id, SAM) == null)
            finish(rig, id, "$point")
        }
    }

    @Test fun aCrashAtEveryWritePointOfTheCompletionFinishesOnce() {
        for (point in WritePoint.entries) {
            val rig = rig(Mode.SELF_MANAGED)
            val id = rig.toSupervised(); rig.mature(id)
            assertEquals("$point", PeopleChangeOutcome.APPLIED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
            assertTrue("$point fired", rig.crashAt(point) { rig.people.tick(id, WaitingPhase.COMMITTING) }.isFailure)
            val phase = rig.journal().operation?.phase
            assertTrue("$point: $phase", phase == WaitingPhase.COMMITTING || phase == WaitingPhase.COMPLETED)
            if (phase == WaitingPhase.COMMITTING) finish(rig, id, "$point") else {
                rig.wire()
                assertEquals("$point", PeopleChangeOutcome.IGNORED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
                assertEquals("$point", 1, rig.journal().texts.count { it.event == WaitingEvent.COMPLETED })
            }
        }
    }

    @Test fun anUnacknowledgedSwapNeedsAttentionAndCompletesWithoutReapplying() {
        val rig = rig(Mode.SELF_MANAGED)
        val id = rig.toSupervised(); rig.mature(id)
        val result = SearchDenial(WritePoint.AFTER_COPY_B).run(rig.dir, { rig.faults = it }) { rig.people.tick(id, WaitingPhase.COMMITTING) }
        assertEquals(PeopleChangeOutcome.NEEDS_ATTENTION, result.outcome)
        assertEquals(Mode.TRUSTED, rig.credentials().mode) // the swap landed although it was not acknowledged
        assertNull(rig.people.prepareStop(id, SAM))
        finish(rig, id, "unacknowledged")
    }

    @Test fun aCrashAtEveryWritePointOfTheStopLeavesCommittingOrRollingBack() {
        for (point in WritePoint.entries) {
            val rig = rig(Mode.SELF_MANAGED)
            val id = rig.toSupervised(); rig.mature(id)
            val stop = checkNotNull(rig.people.prepareStop(id, SAM))
            val grant = rig.auth(SAM, stop.action)
            assertTrue("$point fired", rig.crashAt(point) { rig.people.stop(stop, grant) }.isFailure)
            rig.wire()
            val phase = rig.journal().operation?.phase
            assertTrue("$point: $phase", phase == WaitingPhase.COMMITTING || phase == WaitingPhase.ROLLING_BACK)
            if (phase == WaitingPhase.ROLLING_BACK) {
                // A landed rollback never resumes forward.
                assertEquals("$point", PeopleChangeOutcome.IGNORED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
                assertEquals("$point", PeopleChangeOutcome.CANCELLED, rig.people.tick(id, WaitingPhase.ROLLING_BACK).outcome)
                assertEquals("$point", Mode.SELF_MANAGED, rig.credentials().mode)
            } else finish(rig, id, "$point")
        }
    }

    @Test fun theDurableOperationFencesNewElevationUntilTheTerminalWriteEvenAfterARestart() {
        val rig = rig(Mode.SELF_MANAGED)
        val id = rig.toSupervised()
        // Pending: normal self-managed rules apply.
        rig.wire()
        val pending = rig.elevations(); assertEquals(ElevationOutcome.IDLE, pending.onStart())
        assertNotNull(pending.prepare(SAM, ElevationTarget.Settings))
        rig.mature(id)
        // COMMITTING, before any executor pass in this process: fenced, though startup cleanup succeeds.
        rig.wire()
        val committing = rig.elevations(); assertEquals(ElevationOutcome.IDLE, committing.onStart())
        assertNull(committing.prepare(SAM, ElevationTarget.Settings))
        val stop = checkNotNull(rig.people.prepareStop(id, SAM))
        assertEquals(PeopleChangeOutcome.ROLLING_BACK, rig.people.stop(stop, rig.auth(SAM, stop.action)).outcome)
        rig.wire()
        val rolling = rig.elevations(); assertEquals(ElevationOutcome.IDLE, rolling.onStart())
        assertNull(rolling.prepare(SAM, ElevationTarget.Settings))
        // A failed rollback effect keeps the fence; the terminal write releases it.
        rig.effects.failAt = "set"
        assertEquals(PeopleChangeOutcome.NEEDS_ATTENTION, rig.people.tick(id, WaitingPhase.ROLLING_BACK).outcome)
        assertNull(rolling.prepare(SAM, ElevationTarget.Settings))
        rig.effects.failAt = null
        assertEquals(PeopleChangeOutcome.CANCELLED, rig.people.tick(id, WaitingPhase.ROLLING_BACK).outcome)
        rig.wire()
        val after = rig.elevations(); assertEquals(ElevationOutcome.IDLE, after.onStart())
        assertNotNull(after.prepare(SAM, ElevationTarget.Settings))
    }
}
