// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.*
import org.plainphone.policy.*

class PreviewPinAccessTest {
    @get:Rule val tmp = TemporaryFolder()
    private var sequence = 0
    private val clock = RunningClock { ClockReading(RunningTime("epoch", 0), BootTime("boot", 0)) }
    private fun pin() = CharArray(6) { '1' }
    private fun vault(store: JointAuthorityStore): PinVault = PinVault(store.credentials, KeyDerivation { chars, salt, _ ->
            MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
        }, SecretEntropy { n -> ByteArray(n) { (++sequence).toByte() } }, clock)
    private fun host(dir: File): PreviewPinAccess {
        val store = JointAuthorityStore(dir)
        return PreviewPinAccess(store, vault(store))
    }

    /** Slot 7a cannot use the preview entry for supervised permission edits without a new route. */
    @Test fun validSupervisedCredentialCannotEnterPreviewOrUseItsFreshRemovalRoute() {
        val store = JointAuthorityStore(tmp.newFolder())
        val vault = vault(store)
        val person = TrustedPersonId("trusted-person")
        val credential = vault.prepareCredential(pin()).credential
        assertTrue(store.initialize(CredentialState(Mode.TRUSTED, mapOf(person to credential)),
            WaitingState(storageGeneration = "supervised-test"), ElevationRecord()))
        val action = PrivilegedAction("test:supervised", "app")
        val authorized = vault.authenticate(person, pin(), action)
        assertTrue("The credential is valid; this is an entry-route limitation", authorized is PinResult.Authorized)
        assertTrue(vault.consumeFor((authorized as PinResult.Authorized).authority, action, person))

        val preview = PreviewPinAccess(store, vault)
        assertEquals(PinEntry.UNAVAILABLE, preview.entry())
        val menuPin = pin()
        assertEquals(PinAccessResult.Unavailable, preview.authenticate(menuPin))
        assertTrue(menuPin.all { it == '\u0000' })
        val removalPin = pin()
        assertEquals(PinAccessResult.Unavailable, preview.authenticateRemoval("camera", removalPin))
        assertTrue(removalPin.all { it == '\u0000' })
        assertEquals(Mode.TRUSTED, store.credentials.read()!!.mode)
        assertEquals(setOf(person), store.credentials.read()!!.people.keys)
    }

    @Test fun enrollWrongRightAndNewProcessKeepsPinButNotAccess() {
        val dir = tmp.newFolder()
        val host = host(dir)
        assertEquals(PinEntry.SETUP, host.entry())
        assertTrue(host.enroll(pin()) is PinEnrollmentResult.Created)
        assertEquals(PinEntry.ENTER, host.entry())
        val wrong = CharArray(6) { '2' }
        assertTrue(host.authenticate(wrong) is PinAccessResult.Wrong)
        assertTrue(wrong.all { it == '\u0000' })
        assertEquals(PinAccessResult.Open, host.authenticate(pin()))
        val reopened = host(dir)
        assertEquals(PinEntry.ENTER, reopened.entry())
        assertTrue(reopened.authenticate(CharArray(6) { '2' }) is PinAccessResult.Wrong)
        assertEquals(PinAccessResult.Open, reopened.authenticate(pin()))
    }

    @Test fun menuPinDoesNotReplaceFreshRemovalPinAndAllInputsAreCleared() {
        val host = host(tmp.newFolder())
        host.enroll(pin())
        assertEquals(PinAccessResult.Open, host.authenticate(pin()))
        val wrong = CharArray(6) { '2' }
        assertTrue(host.authenticateRemoval("calculator", wrong) is PinAccessResult.Wrong)
        assertTrue(wrong.all { it == '\u0000' })
        val correct = pin()
        assertEquals(PinAccessResult.Open, host.authenticateRemoval("calculator", correct))
        assertTrue(correct.all { it == '\u0000' })
        assertTrue(host.authenticateRemoval("camera", CharArray(6) { '2' }) is PinAccessResult.Wrong)
    }

    @Test fun menuAndRemovalCannotAuthorizeAdditionAndPinIsCleared() {
        val host = host(tmp.newFolder()); host.enroll(pin())
        assertEquals(PinAccessResult.Open, host.authenticate(pin()))
        assertEquals(PinAccessResult.Open, host.authenticateRemoval("calculator", pin()))
        val wrong = CharArray(6) { '2' }
        assertTrue(host.authenticateAddition("calculator", wrong) is PinAccessResult.Wrong)
        assertTrue(wrong.all { it == '\u0000' })
        assertEquals(PinAccessResult.Open, host.authenticateAddition("calculator", pin()))
        assertTrue(host.authenticateAddition("camera", CharArray(6) { '2' }) is PinAccessResult.Wrong)
    }

    @Test fun pinOnlyEnrollmentHasNoWaitOrLostRecordOrInventedProfile() {
        val dir = tmp.newFolder()
        assertTrue(host(dir).enroll(pin()) is PinEnrollmentResult.Created)
        val store = JointAuthorityStore(dir)
        assertNull(store.credentials.read()!!.recoveryProfile)
        assertNotNull(store.waitingStore.read())
        assertNull(store.waitingStore.read()!!.operation)
        assertNull(store.reconciliation.lostRecord())
    }

    @Test fun lockoutIsDurableAndRightPinCannotBypassItsDelay() {
        val dir = tmp.newFolder()
        val host = host(dir)
        host.enroll(pin())
        repeat(5) { assertTrue(host.authenticate(CharArray(6) { '2' }) is PinAccessResult.Wrong) }
        assertEquals(PinAccessResult.Delayed(30_000), host(dir).authenticate(pin()))
    }

    @Test fun damagedRecordNeverOffersSetupAndAuthenticationFailsClosed() {
        val dir = tmp.newFolder()
        File(dir, "authority-a.rec").writeText("damaged")
        val host = host(dir)
        assertEquals(PinEntry.UNAVAILABLE, host.entry())
        assertEquals(PinAccessResult.Unavailable, host.authenticate(pin()))
        assertEquals(PinEnrollmentResult.UNAVAILABLE, host.enroll(pin()))
    }
}
