// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.BootTime
import org.plainphone.core.ClockReading
import org.plainphone.core.Mode
import org.plainphone.core.RunningClock
import org.plainphone.core.RunningTime
import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.CredentialState
import org.plainphone.policy.EnrollmentConfirmations
import org.plainphone.policy.EnrollmentWitness
import org.plainphone.policy.FrictionSettings
import org.plainphone.policy.IncomingPersonEnrollment
import org.plainphone.policy.IncomingSelfManagedEnrollment
import org.plainphone.policy.KeyDerivation
import org.plainphone.policy.PersonContact
import org.plainphone.policy.PinResult
import org.plainphone.policy.PinVault
import org.plainphone.policy.RecoveryProfile
import org.plainphone.policy.SecretEntropy
import org.plainphone.policy.SelfManagedEnrollmentConfirmations
import org.plainphone.policy.WaitingChangeController
import org.plainphone.policy.WaitingChangeKind
import org.plainphone.policy.WaitingOutcome
import org.plainphone.policy.WaitingPhase
import org.plainphone.policy.WaitingState

/**
 * PLA-97 slice 2: the joint record stores a mode-switch wait exactly, in both directions, and reads
 * it back after a restart with the original immutable patch (never re-derived from credentials).
 * Records without a mode patch keep their schema 6 bytes. JVM file-system tests only.
 */
class ModeSwitchRecordTest {
    @get:Rule val tmp = TemporaryFolder()
    private val anna = TrustedPersonId("anna")
    private val ben = TrustedPersonId("ben")
    private val sam = TrustedPersonId("sam")
    private val friction = FrictionSettings(1_200_000, 2)
    private val pin = CharArray(6) { '1' }
    private var sequence = 0
    private val clock = RunningClock { ClockReading(RunningTime("epoch", 0), BootTime("boot", 0)) }

    private inner class Rig(mode: Mode, val dir: File = tmp.newFolder()) {
        val store = JointAuthorityStore(dir)
        val vault = PinVault(store.credentials, KeyDerivation { chars, salt, _ ->
            MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
        }, SecretEntropy { n -> ByteArray(n) { (++sequence).toByte() } }, clock)
        val waiting = WaitingChangeController(store.waitingStore, { store.credentials.read() }, clock, vault,
            store.identities, store.cancelIntents)

        init {
            val holders = (if (mode == Mode.TRUSTED) listOf(anna, ben) else listOf(sam)).associateWith {
                vault.prepareCredential(pin).credential.let { c ->
                    if (mode == Mode.TRUSTED) c.copy(contact = PersonContact("synthetic-${it.value}", "synthetic-number-${it.value}")) else c
                }
            }
            val credentials = CredentialState(mode, holders,
                recoveryProfile = RecoveryProfile(holders.keys.associateWith { "account-${it.value}" }, "account-backup", friction))
            assertEquals(ProvisionOutcome.CREATED, InitialProvisioning(store).provision(credentials, "live"))
        }

        fun start(prepared: org.plainphone.policy.WaitingStart?, starter: TrustedPersonId): String {
            val r = checkNotNull(prepared)
            val fresh = (vault.authenticate(starter, pin, r.action) as PinResult.Authorized).authority
            assertEquals(WaitingOutcome.PENDING, waiting.startModeSwitch(r, fresh, true))
            return checkNotNull(JointAuthorityStore(dir).waitingStore.read()?.operation).identity.requestId
        }
    }

    private fun selfManagedIncoming(rig: Rig) = checkNotNull(IncomingSelfManagedEnrollment.confirm(sam, "account-sam",
        rig.vault.prepareCredential(CharArray(6) { '5' }), SelfManagedEnrollmentConfirmations(true, true, true, true, true, true),
        EnrollmentWitness.CurrentPerson(anna)))

    private fun supervisedIncoming(rig: Rig) = checkNotNull(IncomingPersonEnrollment.confirm(anna,
        PersonContact("synthetic-anna", "synthetic-number-anna"), "account-anna", rig.vault.prepareCredential(CharArray(6) { '5' }),
        EnrollmentConfirmations(true, true, true, true, true, true, true, true, true), EnrollmentWitness.Helper))

    @Test fun aSupervisedToSelfManagedWaitIsStoredExactlyAndSurvivesARestart() {
        val rig = Rig(Mode.TRUSTED)
        val incoming = selfManagedIncoming(rig)
        val id = rig.start(rig.waiting.prepareSelfManaged(anna, incoming), anna)
        val op = checkNotNull(JointAuthorityStore(rig.dir).waitingStore.read()?.operation)
        assertEquals(id, op.identity.requestId)
        assertEquals(WaitingChangeKind.SWITCH_MODE, op.kind)
        assertEquals(WaitingPhase.PENDING, op.phase)
        val patch = checkNotNull(op.modePatch)
        assertEquals(Mode.TRUSTED, patch.sourceMode); assertEquals(Mode.SELF_MANAGED, patch.targetMode)
        assertEquals(anna, patch.starter); assertEquals(setOf(anna, ben), patch.removedSlots)
        val stored = checkNotNull(patch.incomingSelfManaged)
        assertNull(patch.incomingTrustedPerson)
        assertEquals(sam, stored.person); assertEquals("account-sam", stored.resetProtectionUserId)
        assertEquals(incoming.confirmations, stored.confirmations); assertEquals(incoming.witness, stored.witness)
        assertArrayEquals(incoming.credential.pin.hash, stored.credential.pin.hash)
        assertArrayEquals(incoming.credential.recovery.hash, stored.credential.recovery.hash)
        // The operation re-encodes to the same bytes: nothing was re-derived from current credentials.
        assertArrayEquals(AuthorityCodec.encodeOperation(op), AuthorityCodec.encodeOperation(
            checkNotNull(JointAuthorityStore(rig.dir).waitingStore.read()?.operation)))
    }

    @Test fun aSelfManagedToSupervisedWaitIsStoredExactly() {
        val rig = Rig(Mode.SELF_MANAGED)
        val incoming = supervisedIncoming(rig)
        rig.start(rig.waiting.prepareSupervised(sam, incoming), sam)
        val patch = checkNotNull(JointAuthorityStore(rig.dir).waitingStore.read()?.operation?.modePatch)
        assertEquals(Mode.SELF_MANAGED, patch.sourceMode); assertEquals(Mode.TRUSTED, patch.targetMode)
        assertEquals(setOf(sam), patch.removedSlots)
        val stored = checkNotNull(patch.incomingTrustedPerson)
        assertNull(patch.incomingSelfManaged)
        assertEquals(anna, stored.person); assertEquals("account-anna", stored.resetProtectionUserId)
        assertEquals(incoming.confirmations, stored.confirmations)
        assertEquals(incoming.credential.contact?.name, stored.credential.contact?.name)
        assertEquals(incoming.credential.contact?.number, stored.credential.contact?.number)
    }

    @Test fun aWaitWithoutAModePatchKeepsItsBytes() {
        val rig = Rig(Mode.TRUSTED)
        val r = checkNotNull(rig.waiting.prepareTurnOff(anna))
        val fresh = (rig.vault.authenticate(anna, pin, r.action) as PinResult.Authorized).authority
        assertEquals(WaitingOutcome.PENDING, rig.waiting.startTurnOff(r, fresh, true, true))
        val op = checkNotNull(JointAuthorityStore(rig.dir).waitingStore.read()?.operation)
        val bytes = checkNotNull(AuthorityCodec.encodeOperation(op))
        // The trailing patch tag stays the schema 6 boolean: 0 with no people patch.
        assertEquals(0.toByte(), bytes.last())
        assertNotNull(AuthorityCodec.decodeWaiting(checkNotNull(AuthorityCodec.encodeWaiting(WaitingState(1, op, storageGeneration = "live")))))
        assertTrue(op.modePatch == null)
    }
}
