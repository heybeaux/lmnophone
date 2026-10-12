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
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationOutcome
import org.plainphone.policy.ElevationTarget
import org.plainphone.policy.FrictionSettings
import org.plainphone.policy.PeopleChangeOutcome
import org.plainphone.policy.PersonAuthorityConsumer
import org.plainphone.policy.PinResult
import org.plainphone.policy.PolicyPlan
import org.plainphone.policy.PrivilegedAction
import org.plainphone.policy.ReconcileOutcome
import org.plainphone.policy.ReconciliationEffects
import org.plainphone.policy.WaitingPhase
import org.plainphone.policy.WaitingReconciler

/**
 * PLA-97 slice 4: a mode switch whose waiting record is lost. Reconciliation never infers the lost
 * kind: it keeps the authoritative stored credentials exactly (the old holder before the swap, the
 * new one after it), applies that record's reset list, records one recovery receipt in that mode,
 * and never reinstalls setup or outgoing credentials. While the record is lost, new elevation stays
 * fenced. JVM file-system tests over the real joint store and reconciler; no Android effects.
 */
class ModeSwitchReconciliationTest {
    @get:Rule val tmp = TemporaryFolder()

    private class Effects : ReconciliationEffects {
        var reset: Set<String>? = null
        override fun revokeTemporaryAccess() = true
        override fun isDeviceOwner(): Boolean? = true
        override fun applyPolicy(plan: PolicyPlan) = true
        override fun applyResetProtection(userIds: Set<String>): Boolean { reset = userIds; return true }
        override fun applyFriction(settings: FrictionSettings) = true
        override fun readBackPolicy(plan: PolicyPlan) = true
        override fun readBackResetProtection(userIds: Set<String>) = reset == userIds
        override fun readBackFriction(settings: FrictionSettings) = true
    }

    private fun bytes(c: CredentialState): ByteArray = checkNotNull(AuthorityCodec.encodeCredentials(c))
    private fun lose(rig: ModeSwitchRig) = assertTrue(rig.store.replaceSectionForTest(Section.WAITING, byteArrayOf(7, 7, 7)))
    private fun reconcile(rig: ModeSwitchRig, effects: Effects) =
        WaitingReconciler(rig.store.reconciliation, { rig.store.credentials.read() }, { ModeSwitchRig.POLICY }, effects,
            PersonAuthorityConsumer { _, _, _ -> false }).reconcile()

    /** Loses the record at the given point, then checks the fence, the reconciliation and what survives. */
    private fun lostAndReconciled(rig: ModeSwitchRig, id: String, expected: ByteArray, mode: Mode, ids: Set<String>) {
        lose(rig)
        rig.wire()
        assertNull(rig.store.waitingStore.read())
        // A lost record fences new elevation even after a successful startup cleanup.
        if (mode == Mode.SELF_MANAGED) {
            val fenced = rig.elevations(); assertEquals(ElevationOutcome.IDLE, fenced.onStart())
            assertNull(fenced.prepare(SAM, ElevationTarget.Settings))
        }
        // Taken after any startup cleanup above, which legitimately rewrites the record once.
        val elevation = checkNotNull(AuthorityCodec.encodeElevation(rig.ledger()))
        // The mode executor cannot act on a record it cannot read.
        assertEquals(PeopleChangeOutcome.NEEDS_ATTENTION, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        val effects = Effects()
        assertEquals(ReconcileOutcome.RECOVERED, reconcile(rig, effects).outcome)
        assertEquals(ids, effects.reset)
        rig.wire()
        assertArrayEquals(expected, bytes(rig.credentials()))
        assertArrayEquals(elevation, AuthorityCodec.encodeElevation(rig.ledger()))
        val waiting = rig.journal()
        assertNull(waiting.operation)
        assertEquals(mode, waiting.recoveryReceipts.single().mode)
        // A stale executor callback finds nothing to finish, and nothing is reapplied.
        val calls = rig.effects.calls.toList()
        assertEquals(PeopleChangeOutcome.IGNORED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        assertEquals(PeopleChangeOutcome.IGNORED, rig.people.tick(id, WaitingPhase.ROLLING_BACK).outcome)
        assertEquals(calls, rig.effects.calls) // no effect ran (PLA-436 note)
        assertArrayEquals(expected, bytes(rig.credentials()))
        if (mode == Mode.SELF_MANAGED) {
            val released = rig.elevations(); assertEquals(ElevationOutcome.IDLE, released.onStart())
            assertNotNull(released.prepare(SAM, ElevationTarget.Settings))
        }
    }

    private fun canAuthenticate(rig: ModeSwitchRig, person: org.plainphone.core.TrustedPersonId, pin: CharArray) =
        rig.vault.authenticate(person, pin, PrivilegedAction("probe", "probe")) is PinResult.Authorized

    @Test fun aRecordLostWhilePendingKeepsTheOldHolderAndTheIncomingCredentialGrantsNothing() {
        val rig = ModeSwitchRig(Mode.SELF_MANAGED, tmp.newFolder())
        val id = rig.toSupervised()
        val old = bytes(rig.credentials())
        lostAndReconciled(rig, id, old, Mode.SELF_MANAGED, setOf("account-sam", "account-backup"))
        assertTrue(canAuthenticate(rig, SAM, rig.pin))
        assertTrue(!canAuthenticate(rig, ALEX, rig.newPin))
    }

    @Test fun aRecordLostWhileCommittingBeforeTheSwapKeepsTheOldHolder() {
        val rig = ModeSwitchRig(Mode.SELF_MANAGED, tmp.newFolder())
        val id = rig.toSupervised(); rig.mature(id)
        // The forward effects ran and set the target list, but the swap never landed.
        rig.effects.failAt = "readback"
        assertEquals(PeopleChangeOutcome.NEEDS_ATTENTION, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        rig.effects.failAt = null
        val old = bytes(rig.credentials())
        lostAndReconciled(rig, id, old, Mode.SELF_MANAGED, setOf("account-sam", "account-backup"))
        assertTrue(!canAuthenticate(rig, ALEX, rig.newPin))
    }

    @Test fun aRecordLostWhileRollingBackKeepsTheOldHolder() {
        val rig = ModeSwitchRig(Mode.SELF_MANAGED, tmp.newFolder())
        val id = rig.toSupervised(); rig.mature(id)
        val stop = checkNotNull(rig.people.prepareStop(id, SAM))
        assertEquals(PeopleChangeOutcome.ROLLING_BACK, rig.people.stop(stop, rig.auth(SAM, stop.action)).outcome)
        val old = bytes(rig.credentials())
        lostAndReconciled(rig, id, old, Mode.SELF_MANAGED, setOf("account-sam", "account-backup"))
    }

    @Test fun aRecordLostAfterTheSwapKeepsTheNewHolderAndNeverReinstallsTheOldOne() {
        val rig = ModeSwitchRig(Mode.SELF_MANAGED, tmp.newFolder())
        val id = rig.toSupervised(); rig.mature(id)
        assertEquals(PeopleChangeOutcome.APPLIED, rig.people.tick(id, WaitingPhase.COMMITTING).outcome)
        val swapped = bytes(rig.credentials())
        lostAndReconciled(rig, id, swapped, Mode.TRUSTED, setOf("account-alex", "account-backup"))
        assertTrue(canAuthenticate(rig, ALEX, rig.newPin))
        assertTrue(!canAuthenticate(rig, SAM, rig.pin))
    }
}
