// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.*
import org.plainphone.policy.*
import java.security.MessageDigest

class PreviewRecoveryTest {
    @get:Rule val tmp = TemporaryFolder()
    private val self = TrustedPersonId("self")
    private var sequence = 0
    private var now = 0L
    private val clock = RunningClock { ClockReading(RunningTime("epoch", now), BootTime("boot", now)) }
    private fun pin() = CharArray(6) { '1' }
    private fun vault(store: JointAuthorityStore) = PinVault(store.credentials, KeyDerivation { chars, salt, _ ->
        MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
    }, SecretEntropy { n -> ByteArray(n) { (++sequence).toByte() } }, clock)
    @Test fun enrollmentCodeCanBeTakenOnceAndIsNeverInDiagnostics() {
        val store = JointAuthorityStore(tmp.newFolder()); val vault = vault(store)
        val created = PinEnrollment(store, vault).create(self, pin()) as PinEnrollmentResult.Created
        val code = created.code.take()!!
        assertEquals(32, code.size)
        assertFalse(created.toString().contains(code.concatToString()))
        assertNull(created.code.take())
        assertTrue(vault.recover(self, code, CharArray(6) { '2' }) is RecoveryResult.Replaced)
        assertEquals(PinEnrollmentResult.UNAVAILABLE, PinEnrollment(store, vault).create(self, pin()))
    }
    @Test fun rotationRequiresFreshPinAndRecoveryWorksDuringPinLockout() {
        val store = JointAuthorityStore(tmp.newFolder()); val vault = vault(store)
        PinEnrollment(store, vault).create(self, pin())
        val host = PreviewRecovery(store, vault)
        assertTrue(host.rotate(CharArray(6) { '9' }) is RecoveryAccessResult.WrongPin)
        val rotated = host.rotate(pin()) as RecoveryAccessResult.Issued
        val code = rotated.code.take()!!
        repeat(5) { PreviewPinAccess(store, vault).authenticate(CharArray(6) { '9' }) }
        assertTrue(PreviewPinAccess(store, vault).authenticate(pin()) is PinAccessResult.Delayed)
        val wrong = host.recover("bad".toCharArray(), CharArray(6) { '2' })
        assertTrue(wrong is RecoveryAccessResult.WrongCode)
        assertEquals(5, store.credentials.read()!!.people.getValue(self).pinCounter.failures)
        val newPin = CharArray(6) { '2' }
        val result = host.recover(code, newPin)
        assertTrue(result is RecoveryAccessResult.Issued)
        assertTrue(code.all { it == '\u0000' } && newPin.all { it == '\u0000' })
        assertEquals(PinAccessResult.Open, PreviewPinAccess(store, vault).authenticate(CharArray(6) { '2' }))
    }
    @Test fun interruptedRotationFiresEachHookAndLeavesExactlyOldOrNewVerifier() {
        for (point in WritePoint.entries) {
            var armed = false; var fired = false
            val dir = tmp.newFolder()
            val store = JointAuthorityStore(dir) { at, _ -> if (armed && at == point) { fired = true; throw SimulatedCrash() } }
            val vault = vault(store)
            PinEnrollment(store, vault).create(self, pin())
            val action = PrivilegedAction("preview:new-recovery-code", "one")
            val grant = (vault.authenticate(self, pin(), action) as PinResult.Authorized).authority
            val before = store.credentials.read()!!.people.getValue(self)
            // Predict only the deterministic test entropy; real codes use SecureRandom.
            val nextCode = ByteArray(16) { (sequence + it + 1).toByte() }.joinToString("") { "%02x".format(it.toInt() and 255) }
            armed = true
            try { vault.replaceRecoveryCode(self, grant, action) } catch (_: SimulatedCrash) { }
            armed = false
            assertTrue("hook $point fired", fired)
            val reopened = JointAuthorityStore(dir)
            val after = reopened.credentials.read()!!.people.getValue(self)
            assertArrayEquals(before.pin.hash, after.pin.hash)
            assertEquals(before.pinCounter, after.pinCounter)
            assertEquals(before.generation, after.generation)
            val old = before.recovery.hash.contentEquals(after.recovery.hash)
            val result = vault(reopened).recover(self, nextCode.toCharArray(), pin())
            assertEquals("$point old-or-new", !old, result is RecoveryResult.Replaced)
        }
    }
}
