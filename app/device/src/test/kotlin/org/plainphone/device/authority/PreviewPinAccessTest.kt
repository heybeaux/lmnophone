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
    private fun host(dir: File): PreviewPinAccess {
        val store = JointAuthorityStore(dir)
        val vault = PinVault(store.credentials, KeyDerivation { chars, salt, _ ->
            MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
        }, SecretEntropy { n -> ByteArray(n) { (++sequence).toByte() } }, clock)
        return PreviewPinAccess(store, vault)
    }

    @Test fun enrollWrongRightAndNewProcessKeepsPinButNotAccess() {
        val dir = tmp.newFolder()
        val host = host(dir)
        assertEquals(PinEntry.SETUP, host.entry())
        assertEquals(PinEnrollmentResult.CREATED, host.enroll(pin()))
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

    @Test fun pinOnlyEnrollmentHasNoWaitOrLostRecordOrInventedProfile() {
        val dir = tmp.newFolder()
        assertEquals(PinEnrollmentResult.CREATED, host(dir).enroll(pin()))
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
