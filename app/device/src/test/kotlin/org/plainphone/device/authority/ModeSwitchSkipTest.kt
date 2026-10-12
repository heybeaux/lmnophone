// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.Mode
import org.plainphone.device.authority.ModeSwitchRig.Companion.ALEX
import org.plainphone.device.authority.ModeSwitchRig.Companion.BATTERY
import org.plainphone.device.authority.ModeSwitchRig.Companion.JO
import org.plainphone.device.authority.ModeSwitchRig.Companion.SAM
import org.plainphone.policy.CredentialState
import org.plainphone.policy.PeopleChangeOutcome
import org.plainphone.policy.WaitingEvent
import org.plainphone.policy.WaitingOutcome
import org.plainphone.policy.WaitingPhase
import org.plainphone.policy.WaitingSkipResult

/**
 * PLA-97 slice 5: only supervised-to-self-managed has the trusted recovery skip, on the file-backed
 * joint record. A current trusted person's recovery code rotates only their own PIN and code and
 * moves the exact operation to COMMITTING in one write; the switch then completes with the incoming
 * self-managed credential. The reverse wait cannot be skipped. JVM file-system tests.
 */
class ModeSwitchSkipTest {
    @get:Rule val tmp = TemporaryFolder()
    private val skipPin get() = CharArray(6) { '7' }

    private fun bytes(c: CredentialState): ByteArray = checkNotNull(AuthorityCodec.encodeCredentials(c))
    /** One holder's complete credential (verifiers, counters, generation, contact) as stored. */
    private fun holder(c: CredentialState, person: org.plainphone.core.TrustedPersonId): ByteArray =
        bytes(CredentialState(c.mode, mapOf(person to c.people.getValue(person))))
    private fun files(rig: ModeSwitchRig) = rig.dir.listFiles()!!.filter { it.isFile && it.name.endsWith(".rec") }
        .associate { it.name to it.readBytes() }
    private fun assertUntouched(what: String, before: Map<String, ByteArray>, rig: ModeSwitchRig) {
        val after = files(rig)
        assertEquals(what, before.keys, after.keys)
        before.forEach { (name, b) -> assertArrayEquals("$what $name", b, after.getValue(name)) }
    }

    @Test fun aTrustedPersonsCodeSkipsTheSupervisedToSelfManagedWaitAndTheSwitchCompletes() {
        val rig = ModeSwitchRig(Mode.TRUSTED, tmp.newFolder())
        val id = rig.toSelfManaged()
        val old = rig.credentials()
        val result = rig.waiting.skip(id, JO, rig.codes.getValue(JO).toCharArray(), skipPin, true, BATTERY)
        assertTrue("$result", result is WaitingSkipResult.Committing)
        val skipped = rig.credentials()
        // Only the skipper's PIN and code rotated, in the same write that moved the operation.
        assertEquals(old.revision + 1, skipped.revision)
        assertEquals(old.people.getValue(JO).generation + 1, skipped.people.getValue(JO).generation)
        assertArrayEquals(holder(old, ALEX), holder(skipped, ALEX)) // the other holder is untouched
        assertEquals(Mode.TRUSTED, skipped.mode)
        val op = checkNotNull(rig.journal().operation)
        assertEquals(WaitingPhase.COMMITTING, op.phase)
        assertEquals(JO, op.skipper); assertEquals(skipped.people.getValue(JO).generation, op.skipperGeneration)
        assertEquals(1, rig.journal().texts.count { it.event == WaitingEvent.SKIPPED })
        // No wait remains: the executor applies and completes at once, from a new process.
        rig.wire()
        assertEquals(PeopleChangeOutcome.APPLIED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        assertEquals(PeopleChangeOutcome.COMPLETED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        val after = rig.credentials()
        assertEquals(Mode.SELF_MANAGED, after.mode); assertEquals(setOf(SAM), after.people.keys)
        assertEquals(setOf("account-sam", "account-backup"), rig.effects.applied)
    }

    @Test fun theSelfManagedToSupervisedWaitCannotBeSkipped() {
        val rig = ModeSwitchRig(Mode.SELF_MANAGED, tmp.newFolder())
        val id = rig.toSupervised()
        val before = files(rig)
        val result = rig.waiting.skip(id, SAM, rig.codes.getValue(SAM).toCharArray(), skipPin, true, BATTERY)
        assertEquals(WaitingSkipResult.NotSkipped(WaitingOutcome.REFUSED), result)
        assertUntouched("reverse", before, rig)
        assertEquals(WaitingPhase.PENDING, rig.journal().operation?.phase)
    }

    @Test fun aWrongCodeOrAnUnconfirmedOrNonHolderSkipChangesNoAuthority() {
        val rig = ModeSwitchRig(Mode.TRUSTED, tmp.newFolder())
        val id = rig.toSelfManaged()
        val old = bytes(rig.credentials())
        // Not confirmed, or the incoming person: refused before any credential work.
        var before = files(rig)
        assertEquals(WaitingSkipResult.NotSkipped(WaitingOutcome.REFUSED),
            rig.waiting.skip(id, JO, rig.codes.getValue(JO).toCharArray(), skipPin, false, BATTERY))
        assertUntouched("unconfirmed", before, rig)
        assertTrue(rig.waiting.skip(id, SAM, rig.codes.getValue(JO).toCharArray(), skipPin, true, BATTERY) !is WaitingSkipResult.Committing)
        assertArrayEquals(old, bytes(rig.credentials()))
        // A wrong code counts a failure for that holder only; the wait stays pending.
        before = files(rig)
        assertTrue(rig.waiting.skip(id, JO, "000000000000".toCharArray(), skipPin, true, BATTERY) is WaitingSkipResult.Wrong)
        val wrong = rig.credentials()
        assertEquals(WaitingPhase.PENDING, rig.journal().operation?.phase)
        assertEquals(1, wrong.people.getValue(JO).recoveryCounter.failures)
        // Everything except Jo's recovery failure counter is exactly as before.
        val oldState = checkNotNull(AuthorityCodec.decodeCredentials(old))
        val jo = wrong.people.getValue(JO)
        assertArrayEquals(holder(oldState, ALEX), holder(wrong, ALEX))
        assertArrayEquals(holder(oldState, JO), holder(CredentialState(wrong.mode, mapOf(JO to jo.copy(recoveryCounter = oldState.people.getValue(JO).recoveryCounter))), JO))
        assertEquals(Mode.TRUSTED, wrong.mode); assertEquals(oldState.structureGeneration, wrong.structureGeneration)
        assertEquals(oldState.recoveryProfile?.resetProtectionUserIds, wrong.recoveryProfile?.resetProtectionUserIds)
    }

    @Test fun aCrashAtEveryWritePointOfTheSkipLeavesPendingWithOldCredentialsOrCommittingWithTheRotation() {
        for (point in WritePoint.entries) {
            val rig = ModeSwitchRig(Mode.TRUSTED, tmp.newFolder())
            val id = rig.toSelfManaged()
            val old = bytes(rig.credentials())
            rig.crashAt(point) { rig.waiting.skip(id, JO, rig.codes.getValue(JO).toCharArray(), skipPin, true, BATTERY) }
            rig.wire()
            val phase = rig.journal().operation?.phase
            val now = rig.credentials()
            when (phase) {
                WaitingPhase.PENDING -> assertArrayEquals("$point", old, bytes(now))
                WaitingPhase.COMMITTING -> {
                    val before = checkNotNull(AuthorityCodec.decodeCredentials(old))
                    assertEquals("$point", before.people.getValue(JO).generation + 1, now.people.getValue(JO).generation)
                    assertArrayEquals("$point", holder(before, ALEX), holder(now, ALEX))
                    assertEquals("$point", JO, rig.journal().operation?.skipper)
                    assertEquals("$point", now.people.getValue(JO).generation, rig.journal().operation?.skipperGeneration)
                    assertEquals("$point", PeopleChangeOutcome.APPLIED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
                }
                else -> throw AssertionError("$point: $phase")
            }
        }
    }
}
