// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.plainphone.core.Mode
import org.plainphone.core.RunningTime
import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.AppliedPeopleChange
import org.plainphone.policy.CredentialChannel
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationDebit
import org.plainphone.policy.ElevationLedger
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.ElevationState
import org.plainphone.policy.FailureCounter
import org.plainphone.policy.FrictionSettings
import org.plainphone.policy.FrictionSettingsPatch
import org.plainphone.policy.LockoutNotice
import org.plainphone.policy.PersonContact
import org.plainphone.policy.PersonCredential
import org.plainphone.policy.RecoveryProfile
import org.plainphone.policy.RecoveryReceipt
import org.plainphone.policy.SecretVerifier
import org.plainphone.policy.WaitingEvent
import org.plainphone.policy.WaitingIdentity
import org.plainphone.policy.WaitingOperation
import org.plainphone.policy.WaitingPhase
import org.plainphone.policy.WaitingState
import org.plainphone.policy.WaitingText

class AuthorityCodecTest {
    private val self = TrustedPersonId("self")
    private val other = TrustedPersonId("other")
    private fun verifier(seed: Int) = SecretVerifier(ByteArray(16) { (seed + it).toByte() }, ByteArray(32) { (seed * 3 + it).toByte() }, 600_000)
    private val base = FrictionSettings(1_200_000, 1)

    private fun credentials(contact: PersonContact? = PersonContact("synthetic-self", "synthetic-contact")) = CredentialState(
        Mode.SELF_MANAGED,
        mapOf(self to PersonCredential(verifier(1), verifier(2), FailureCounter(5, 30_000, RunningTime("epoch", 9)),
            FailureCounter(1), 4, contact)),
        revision = 7, structureGeneration = 3,
        pendingNotices = setOf(LockoutNotice(self, CredentialChannel.PIN, 6)),
        recoveryProfile = RecoveryProfile(mapOf(self to "synthetic-account"), "synthetic-backup", base),
        appliedPeopleChange = AppliedPeopleChange("wait-1", "live", 2))

    private fun op(phase: WaitingPhase = WaitingPhase.PENDING): WaitingOperation {
        val patch = FrictionSettingsPatch.restore(self, 4, 3, base, FrictionSettings(600_000, 3))!!
        return WaitingOperation(WaitingIdentity("wait-2", "synthetic-cancel-2"), self, 4, 3, Mode.SELF_MANAGED,
            RunningTime("epoch", 11), phase, mapOf(self to 4L), recipientContacts = mapOf(self to null), frictionPatch = patch)
    }

    @Test fun credentialsRoundTripEveryField() {
        for (contact in listOf(PersonContact("synthetic-self", "synthetic-contact"), null)) {
            val original = credentials(contact)
            val bytes = AuthorityCodec.encodeCredentials(original)!!
            val decoded = checkNotNull(AuthorityCodec.decodeCredentials(bytes))
            assertArrayEquals(bytes, AuthorityCodec.encodeCredentials(decoded)!!)
            assertEquals(original.revision, decoded.revision)
            assertEquals(original.pendingNotices, decoded.pendingNotices)
            assertEquals(original.appliedPeopleChange, decoded.appliedPeopleChange)
            assertEquals(base, decoded.recoveryProfile!!.friction)
            assertEquals("synthetic-backup", decoded.recoveryProfile!!.backupUserId)
            val person = decoded.people.getValue(self)
            assertArrayEquals(original.people.getValue(self).pin.hash, person.pin.hash)
            assertEquals(original.people.getValue(self).pinCounter, person.pinCounter)
            assertEquals(contact?.number, person.contact?.number)
        }
    }

    @Test fun waitingRoundTripKeepsOriginalFrictionPatchFieldsAndFixedRecipients() {
        val pending = op()
        val text = WaitingText("6:wait-2:1", WaitingEvent.STARTED, pending, mapOf(self to 4L, other to 0L),
            mapOf(self to PersonContact("synthetic-self", "synthetic-contact"), other to null))
        val state = WaitingState(1, pending, listOf(text), "live",
            listOf(RecoveryReceipt("loss-1", Mode.SELF_MANAGED, mapOf(self to 4L), base)))
        val bytes = checkNotNull(AuthorityCodec.encodeWaiting(state))
        val decoded = checkNotNull(AuthorityCodec.decodeWaiting(bytes))
        assertArrayEquals(bytes, AuthorityCodec.encodeWaiting(decoded))
        val patch = decoded.operation!!.frictionPatch!!
        assertEquals(base, patch.source)
        assertEquals(FrictionSettings(600_000, 3), patch.target)
        assertEquals(3L, patch.structureGeneration)
        assertEquals(mapOf(self to 4L, other to 0L), decoded.texts.single().recipientGenerations)
        assertNull(decoded.texts.single().recipientContacts.getValue(other))
        assertEquals("loss-1", decoded.recoveryReceipts.single().key)
        assertEquals("live", decoded.storageGeneration)
    }

    @Test fun vacantWaitingAndIdleElevationRoundTrip() {
        val waiting = WaitingState(storageGeneration = "live")
        assertEquals("live", AuthorityCodec.decodeWaiting(AuthorityCodec.encodeWaiting(waiting)!!)!!.storageGeneration)
        val elevation = ElevationRecord(5, ElevationState(ElevationLedger(listOf(
            ElevationDebit("turn-1", RunningTime("old-epoch", 3)), ElevationDebit("turn-2", RunningTime("epoch", 40))))))
        val bytes = AuthorityCodec.encodeElevation(elevation)!!
        val decoded = AuthorityCodec.decodeElevation(bytes)!!
        assertEquals(5L, decoded.revision)
        assertEquals(elevation.state.ledger.debits, decoded.state.ledger.debits)
    }

    @Test fun malformedBytesDecodeAsNullNeverAsEmptyState() {
        val bytes = AuthorityCodec.encodeCredentials(credentials())!!
        for (length in 0 until bytes.size) assertNull(AuthorityCodec.decodeCredentials(bytes.copyOf(length)))
        assertNull(AuthorityCodec.decodeCredentials(bytes + 0))
        val elevation = AuthorityCodec.encodeElevation(ElevationRecord())!!
        for (length in 0 until elevation.size) assertNull(AuthorityCodec.decodeElevation(elevation.copyOf(length)))
    }
}
