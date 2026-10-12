// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import org.junit.Assert.assertArrayEquals
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
import org.plainphone.policy.AppliedPeopleChange
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationOutcome
import org.plainphone.policy.ElevationTarget
import org.plainphone.policy.PeopleChangeOutcome
import org.plainphone.policy.RecoveryProfile
import org.plainphone.policy.RecoveryResult
import org.plainphone.policy.WaitingEvent
import org.plainphone.policy.WaitingPhase

/**
 * PLA-97 slice 3: faults at mode-switch boundaries over the file-backed joint record, self-managed
 * to supervised (the other direction and the policy-level before/after matrix are in
 * ModeSwitchCommitTest and the policy's ModeSwitchControllerTest). The whole encoded credential
 * record (verifiers, generations, recovery profile, marker) is either exactly the old one or exactly
 * the new candidate; the elevation record keeps its bytes; a retry from a new process finishes once,
 * and a landed swap is never reapplied. Every crash proves its own hook fired. JVM file-system
 * tests: no Android durability or real wipe accounts.
 */
class ModeSwitchFaultTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun bytes(c: CredentialState): ByteArray = checkNotNull(AuthorityCodec.encodeCredentials(c))

    /** The old record, the exact new candidate and the elevation bytes, taken once the switch is COMMITTING. */
    private inner class Switch(val rig: ModeSwitchRig) {
        val id = rig.toSupervised().also { rig.mature(it) }
        val elevation = checkNotNull(AuthorityCodec.encodeElevation(rig.ledger()))
        lateinit var old: ByteArray
        lateinit var new: ByteArray
        init { capture() }

        /**
         * Takes the old record and the exact new candidate from the stored credentials now. Call again
         * after a legitimate credential write, such as the fresh-PIN authentication before a stop.
         */
        fun capture() {
            val oldState = rig.credentials()
            old = bytes(oldState)
            val journal = rig.journal()
            val incoming = checkNotNull(journal.operation?.modePatch?.incomingTrustedPerson)
            new = bytes(CredentialState(Mode.TRUSTED, mapOf(ALEX to incoming.credential), oldState.revision + 1, 1,
                oldState.pendingNotices, RecoveryProfile(mapOf(ALEX to "account-alex"), "account-backup", rig.friction),
                AppliedPeopleChange(id, journal.storageGeneration, 1)))
        }

        /** Exactly the old or the new record, with the elevation bytes untouched; true when the swap landed. */
        fun oldOrNew(what: String): Boolean {
            val now = bytes(rig.credentials())
            assertArrayEquals(what, elevation, AuthorityCodec.encodeElevation(rig.ledger()))
            if (now.contentEquals(old)) return false
            assertArrayEquals(what, new, now)
            return true
        }

        /** From a new process: a landed swap completes at once with no reset write; otherwise applies, then completes. */
        fun finish(what: String, landed: Boolean, final: ByteArray = new) {
            rig.wire()
            val calls = rig.effects.calls.size
            if (landed) {
                assertEquals(what, PeopleChangeOutcome.COMPLETED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
                val retry = rig.effects.calls.drop(calls)
                assertTrue("$what: $retry", "set" !in retry && "broadcast" !in retry)
            } else {
                assertEquals(what, PeopleChangeOutcome.APPLIED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
                assertEquals(what, PeopleChangeOutcome.COMPLETED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
                assertEquals(what, setOf("account-alex", "account-backup"), rig.effects.applied)
            }
            assertArrayEquals(what, final, bytes(rig.credentials()))
            assertArrayEquals(what, elevation, AuthorityCodec.encodeElevation(rig.ledger()))
            assertEquals(what, WaitingPhase.COMPLETED, rig.journal().operation?.phase)
            assertEquals(what, 1, rig.journal().texts.count { it.event == WaitingEvent.COMPLETED })
        }
    }

    private fun switch() = Switch(ModeSwitchRig(Mode.SELF_MANAGED, tmp.newFolder()))

    @Test fun anEffectFailureAtEveryStepLeavesTheExactOldRecordAndARetryFinishes() {
        for (step in ModeSwitchFakeEffects.STEPS) {
            val s = switch()
            s.rig.effects.failAt = step
            assertEquals(step, PeopleChangeOutcome.NEEDS_ATTENTION, s.rig.people.tick(s.id, WaitingPhase.COMMITTING).outcome)
            assertTrue(step, !s.oldOrNew(step))
            assertEquals(step, WaitingPhase.COMMITTING, s.rig.journal().operation?.phase)
            s.rig.effects.failAt = null
            s.finish(step, landed = false)
        }
    }

    @Test fun aCrashBeforeEveryEffectStepLeavesTheExactOldRecordAndResumesForward() {
        for (step in ModeSwitchFakeEffects.STEPS) {
            val s = switch()
            s.rig.crashBefore(step) { s.rig.people.tick(s.id, WaitingPhase.COMMITTING) }
            assertTrue(step, !s.oldOrNew(step))
            s.finish(step, landed = false)
        }
    }

    @Test fun aCrashAtEveryWritePointOfTheSwapLeavesExactlyOldOrNewAndFinishesOnce() {
        for (point in WritePoint.entries) {
            val s = switch()
            s.rig.crashAt(point) { s.rig.people.tick(s.id, WaitingPhase.COMMITTING) }
            s.rig.wire()
            val landed = s.oldOrNew("$point")
            // A fresh-PIN stop is offered exactly while the swap has not landed.
            assertEquals("$point", landed, s.rig.people.prepareStop(s.id, SAM) == null)
            s.finish("$point", landed)
        }
    }

    @Test fun aCrashAtEveryWritePointOfTheCompletionFinishesOnceWithoutReapplying() {
        for (point in WritePoint.entries) {
            val s = switch()
            assertEquals("$point", PeopleChangeOutcome.APPLIED, s.rig.people.tick(s.id, WaitingPhase.COMMITTING).outcome)
            s.rig.crashAt(point) { s.rig.people.tick(s.id, WaitingPhase.COMMITTING) }
            assertTrue("$point", s.oldOrNew("$point"))
            val phase = s.rig.journal().operation?.phase
            assertTrue("$point: $phase", phase == WaitingPhase.COMMITTING || phase == WaitingPhase.COMPLETED)
            if (phase == WaitingPhase.COMMITTING) s.finish("$point", landed = true) else {
                s.rig.wire()
                assertEquals("$point", PeopleChangeOutcome.IGNORED, s.rig.people.tick(s.id, WaitingPhase.COMMITTING).outcome)
                assertArrayEquals("$point", s.new, bytes(s.rig.credentials()))
                assertEquals("$point", 1, s.rig.journal().texts.count { it.event == WaitingEvent.COMPLETED })
            }
        }
    }

    @Test fun anIncomingCredentialRotatedAfterTheSwapSurvivesCompletion() {
        val s = switch()
        assertEquals(PeopleChangeOutcome.APPLIED, s.rig.people.tick(s.id, WaitingPhase.COMMITTING).outcome)
        // The new holder replaces their PIN before the completion runs (for example after a crash).
        val replaced = s.rig.vault.recover(ALEX, checkNotNull(s.rig.incomingCode).toCharArray(), CharArray(6) { '8' })
        assertTrue(replaced is RecoveryResult.Replaced)
        val rotated = s.rig.credentials()
        assertEquals(1L, rotated.people.getValue(ALEX).generation)
        s.finish("rotated", landed = true, final = bytes(rotated))
    }

    @Test fun anUnacknowledgedSwapNeedsAttentionAndCompletesWithoutReapplying() {
        val s = switch()
        val result = SearchDenial(WritePoint.AFTER_COPY_B).run(s.rig.dir, { s.rig.faults = it }) {
            s.rig.people.tick(s.id, WaitingPhase.COMMITTING)
        }
        assertEquals(PeopleChangeOutcome.NEEDS_ATTENTION, result.outcome)
        assertTrue(s.oldOrNew("unacknowledged")) // the swap landed although it was not acknowledged
        assertNull(s.rig.people.prepareStop(s.id, SAM))
        s.finish("unacknowledged", landed = true)
    }

    @Test fun aCrashAtEveryWritePointOfTheStopLeavesCommittingOrRollingBack() {
        for (point in WritePoint.entries) {
            val s = switch()
            val stop = checkNotNull(s.rig.people.prepareStop(s.id, SAM))
            val grant = s.rig.auth(SAM, stop.action)
            s.capture() // authentication itself writes the credential record
            s.rig.crashAt(point) { s.rig.people.stop(stop, grant) }
            s.rig.wire()
            assertTrue("$point", !s.oldOrNew("$point"))
            val phase = s.rig.journal().operation?.phase
            assertTrue("$point: $phase", phase == WaitingPhase.COMMITTING || phase == WaitingPhase.ROLLING_BACK)
            if (phase == WaitingPhase.ROLLING_BACK) {
                // A landed rollback never resumes forward.
                assertEquals("$point", PeopleChangeOutcome.IGNORED, s.rig.people.tick(s.id, WaitingPhase.COMMITTING).outcome)
                assertEquals("$point", PeopleChangeOutcome.CANCELLED, s.rig.people.tick(s.id, WaitingPhase.ROLLING_BACK).outcome)
                assertArrayEquals("$point", s.old, bytes(s.rig.credentials()))
            } else s.finish("$point", landed = false)
        }
    }

    @Test fun aCrashAtEveryWritePointOfTheCancelledWriteFinishesTheRollbackOnce() {
        for (point in WritePoint.entries) {
            val s = switch()
            val stop = checkNotNull(s.rig.people.prepareStop(s.id, SAM))
            assertEquals("$point", PeopleChangeOutcome.ROLLING_BACK, s.rig.people.stop(stop, s.rig.auth(SAM, stop.action)).outcome)
            s.capture() // authentication itself writes the credential record
            s.rig.crashAt(point) { s.rig.people.tick(s.id, WaitingPhase.ROLLING_BACK) }
            s.rig.wire()
            val phase = s.rig.journal().operation?.phase
            assertTrue("$point: $phase", phase == WaitingPhase.ROLLING_BACK || phase == WaitingPhase.CANCELLED)
            if (phase == WaitingPhase.ROLLING_BACK)
                assertEquals("$point", PeopleChangeOutcome.CANCELLED, s.rig.people.tick(s.id, WaitingPhase.ROLLING_BACK).outcome)
            assertEquals("$point", WaitingPhase.CANCELLED, s.rig.journal().operation?.phase)
            assertEquals("$point", 1, s.rig.journal().texts.count { it.event == WaitingEvent.CANCELLED_AFTER_ROLLBACK })
            assertArrayEquals("$point", s.old, bytes(s.rig.credentials()))
            assertArrayEquals("$point", s.elevation, AuthorityCodec.encodeElevation(s.rig.ledger()))
            assertEquals("$point", setOf("account-sam", "account-backup"), s.rig.effects.applied)
        }
    }

    @Test fun theDurableOperationFencesNewElevationUntilTheTerminalWriteEvenAfterARestart() {
        val rig = ModeSwitchRig(Mode.SELF_MANAGED, tmp.newFolder())
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
