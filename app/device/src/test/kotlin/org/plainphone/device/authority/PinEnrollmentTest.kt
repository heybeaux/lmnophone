// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.*
import org.plainphone.policy.*

class PinEnrollmentTest {
    @get:Rule val tmp = TemporaryFolder()
    private val person = TrustedPersonId("self")
    private val clock = RunningClock { ClockReading(RunningTime("epoch", 0), BootTime("boot", 0)) }
    private var sequence = 0
    private fun vault(store: JointAuthorityStore) = PinVault(store.credentials,
        KeyDerivation { chars, salt, _ -> MessageDigest.getInstance("SHA-256")
            .digest(chars.concatToString().toByteArray() + salt) },
        SecretEntropy { n -> ByteArray(n) { (++sequence).toByte() } }, clock)
    private fun pin() = CharArray(6) { '1' }

    @Test fun realEnrollmentPersistsAndFreshPinAuthorityIsSingleUse() {
        val dir = tmp.newFolder()
        val store = JointAuthorityStore(dir)
        val secret = pin()
        assertTrue(PinEnrollment(store, vault(store)).create(person, secret) is PinEnrollmentResult.Created)
        assertTrue(secret.all { it == '\u0000' })
        val reopened = JointAuthorityStore(dir)
        assertEquals(Mode.SELF_MANAGED, reopened.credentials.read()!!.mode)
        assertNull("PIN setup must not invent reset-protection accounts", reopened.credentials.read()!!.recoveryProfile)
        val auth = vault(reopened)
        val action = PrivilegedAction("test", "one")
        val grant = (auth.authenticate(person, pin(), action) as PinResult.Authorized).authority
        assertTrue(auth.consumeFor(grant, action, person))
        assertFalse(auth.consumeFor(grant, action, person))
    }

    @Test fun existingEnrollmentCannotBeReplacedByOpeningSetupAgain() {
        val store = JointAuthorityStore(tmp.newFolder())
        val enrollment = PinEnrollment(store, vault(store))
        assertTrue(enrollment.create(person, pin()) is PinEnrollmentResult.Created)
        val before = store.credentials.read()!!.people.getValue(person).pin.hash
        assertEquals(PinEnrollmentResult.UNAVAILABLE, enrollment.create(person, CharArray(6) { '2' }))
        assertArrayEquals(before, store.credentials.read()!!.people.getValue(person).pin.hash)
    }

    @Test fun damagedRecordIsNotAnInvitationToEnroll() {
        val dir = tmp.newFolder()
        File(dir, "authority-a.rec").writeText("damaged")
        val store = JointAuthorityStore(dir)
        assertEquals(PinEnrollmentResult.UNAVAILABLE, PinEnrollment(store, vault(store)).create(person, pin()))
        assertEquals("damaged", File(dir, "authority-a.rec").readText())
    }

    @Test fun invalidPinDoesNotCreateAuthority() {
        for (value in listOf("12345", "12345x")) {
            val store = JointAuthorityStore(tmp.newFolder())
            val secret = value.toCharArray()
            assertEquals(PinEnrollmentResult.INVALID_PIN, PinEnrollment(store, vault(store)).create(person, secret))
            assertFalse(store.recordPresent())
            assertTrue(secret.all { it == '\u0000' })
        }
    }

    @Test fun failedDurabilityNeverReportsCreated() {
        val store = JointAuthorityStore(tmp.newFolder()).withDirectorySync { throw IOException("unavailable") }
        assertEquals(PinEnrollmentResult.UNAVAILABLE, PinEnrollment(store, vault(store)).create(person, pin()))
    }

    @Test fun pinOnlyEnrollmentCannotPretendToProvideFrictionSettings() {
        val store = JointAuthorityStore(tmp.newFolder())
        val auth = vault(store)
        assertTrue(PinEnrollment(store, auth).create(person, pin()) is PinEnrollmentResult.Created)
        val waits = WaitingChangeController(store.waitingStore, store.credentials::read, clock,
            auth, store.identities, store.cancelIntents)
        assertNull(waits.prepareFriction(person, FrictionSettings(1_200_000, 2)))
        assertNull(store.waitingStore.read()!!.operation)
    }
}
