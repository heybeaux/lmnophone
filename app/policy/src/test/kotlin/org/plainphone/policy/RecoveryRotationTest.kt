// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class RecoveryRotationTest {
    private val person = TrustedPersonId("self")
    private var sequence = 0
    private var now = 0L
    private var state: CredentialState? = null
    private var fail = false
    private var falseAfterWrite = false
    private var staleReadback = false
    private var stale: CredentialState? = null
    private val store = object : CredentialStore {
        override fun read() = stale ?: state
        override fun compareAndSet(expected: CredentialState, replacement: CredentialState): Boolean {
            if (fail || state !== expected) return false
            if (staleReadback) stale = expected
            state = replacement; return !falseAfterWrite
        }
    }
    private val vault = PinVault(store, KeyDerivation { chars, salt, _ ->
        java.security.MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
    }, SecretEntropy { n -> ByteArray(n) { (++sequence).toByte() } },
        RunningClock { ClockReading(RunningTime("epoch", now), BootTime("boot", now)) })
    private val action = PrivilegedAction("preview:new-recovery-code", "one")
    private fun pin() = CharArray(6) { '1' }
    private fun enroll(): String {
        val prepared = vault.prepareCredential(pin())
        state = CredentialState(Mode.SELF_MANAGED, mapOf(person to prepared.credential))
        return prepared.recoveryCode
    }
    private fun grant() = assertIs<PinResult.Authorized>(vault.authenticate(person, pin(), action)).authority
    @Test fun `rotation resets only recovery and old code fails while new works`() {
        val old = enroll()
        vault.recover(person, "wrong".toCharArray(), pin())
        val grant = grant()
        vault.authenticate(person, CharArray(6) { '9' }, action)
        val before = state!!.people.getValue(person)
        val result = assertIs<RecoveryResult.Replaced>(vault.replaceRecoveryCode(person, grant, action))
        val after = state!!.people.getValue(person)
        assertSame(before.pin, after.pin)
        assertEquals(before.pinCounter, after.pinCounter)
        assertEquals(before.generation, after.generation)
        assertEquals(0, after.recoveryCounter.failures)
        assertIs<RecoveryResult.Wrong>(vault.recover(person, old.toCharArray(), pin()))
        assertIs<RecoveryResult.Replaced>(vault.recover(person, result.recoveryCode.uppercase().toCharArray(), pin()))
        assertIs<PinResult.Authorized>(vault.authenticate(person, pin(), action))
    }
    @Test fun `rotation needs fresh person and action bound single use authority`() {
        enroll()
        assertEquals(RecoveryResult.Unavailable, vault.replaceRecoveryCode(person, FreshAuthority(), action))
        val g = grant()
        assertEquals(RecoveryResult.Unavailable, vault.replaceRecoveryCode(TrustedPersonId("other"), g, action))
        assertEquals(RecoveryResult.Unavailable, vault.replaceRecoveryCode(person, g, PrivilegedAction("other", "one")))
        now = 120_000
        assertEquals(RecoveryResult.Unavailable, vault.replaceRecoveryCode(person, g, action))
        val fresh = grant()
        assertIs<RecoveryResult.Replaced>(vault.replaceRecoveryCode(person, fresh, action))
        assertEquals(RecoveryResult.Unavailable, vault.replaceRecoveryCode(person, fresh, action))
    }
    @Test fun `failed rotation does not claim success or invalidate old code`() {
        val old = enroll(); val g = grant(); fail = true
        assertEquals(RecoveryResult.Unavailable, vault.replaceRecoveryCode(person, g, action))
        fail = false
        assertIs<RecoveryResult.Replaced>(vault.recover(person, old.toCharArray(), pin()))
    }
    @Test fun `unacknowledged landing and stale readback never report a code`() {
        enroll(); val first = grant(); falseAfterWrite = true
        assertEquals(RecoveryResult.Unavailable, vault.replaceRecoveryCode(person, first, action))
        falseAfterWrite = false
        val second = grant(); staleReadback = true
        assertEquals(RecoveryResult.Unavailable, vault.replaceRecoveryCode(person, second, action))
    }
    @Test fun `recovery is independent of PIN wait with identical schedule`() {
        val code = enroll()
        repeat(5) { vault.authenticate(person, CharArray(6) { '9' }, action) }
        val pinCounter = state!!.people.getValue(person).pinCounter
        repeat(5) { vault.recover(person, "wrong".toCharArray(), pin()) }
        assertEquals(pinCounter, state!!.people.getValue(person).pinCounter)
        assertEquals(30_000L, assertIs<RecoveryResult.Delayed>(vault.recover(person, code.toCharArray(), pin())).remainingMillis)
        for (delay in listOf(60_000L,120_000L,240_000L,480_000L,960_000L,1_920_000L,3_600_000L,3_600_000L)) {
            now += state!!.people.getValue(person).recoveryCounter.remainingMillis
            assertEquals(delay, assertIs<RecoveryResult.Wrong>(vault.recover(person, "wrong".toCharArray(), pin())).remainingMillis)
        }
        assertEquals(pinCounter, state!!.people.getValue(person).pinCounter)
    }
}
