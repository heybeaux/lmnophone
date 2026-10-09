// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class PinVaultTest {
    private val first = TrustedPersonId("first")
    private val second = TrustedPersonId("second")
    // Synthetic fixtures only. No real credentials or account identities.
    private val pinA get() = CharArray(6) { '1' }
    private val pinB get() = CharArray(6) { '2' }
    private val wrong get() = CharArray(6) { '9' }
    private val action = PrivilegedAction("approve", "test.app:synthetic-signer")
    private var time = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
    private var clockWorks = true
    private val clock = RunningClock { if (clockWorks) time else null }
    private val kdf = FakeDerivation()
    private val store = MemoryCredentials()
    private val vault = PinVault(store, kdf, FakeEntropy(), clock)
    private fun advance(ms: Long) { time = ClockReading(time.proven.copy(totalMillis = time.proven.totalMillis + ms), time.boot.copy(elapsedMillis = time.boot.elapsedMillis + ms)) }
    private fun enroll() {
        store.state = CredentialState(Mode.TRUSTED, mapOf(first to vault.prepareCredential(pinA).credential, second to vault.prepareCredential(pinB).credential))
        kdf.checkedSalts.clear()
    }

    @Test fun `person-bound authority cannot misattribute the starter dependency`() {
        enroll()
        val grant = assertIs<PinResult.Authorized>(vault.authenticate(first, pinA, action)).authority
        assertFalse(vault.consumeFor(grant, action, second))
        assertTrue(vault.consumeFor(grant, action, first))
        assertFalse(vault.consumeFor(grant, action, first))
    }

    @Test fun `PIN requires at least six ASCII digits and strong derivation inputs`() {
        listOf(charArrayOf(), CharArray(5) { '1' }, "abcdef".toCharArray(), "１２３４５６".toCharArray()).forEach {
            assertFailsWith<IllegalArgumentException> { vault.prepareCredential(it) }
        }
        val prepared = vault.prepareCredential(pinA)
        assertTrue(kdf.iterations.all { it >= 600_000 })
        assertTrue(kdf.sizes.all { it == 16 })
        assertTrue(prepared.recoveryCode.length >= 32)
        assertFalse(prepared.credential.toString().contains(String(pinA)))
        assertFalse(prepared.credential.toString().contains(prepared.recoveryCode))
    }

    @Test fun `selected person alone is checked and charged`() {
        enroll()
        val before = store.state!!.people.getValue(first)
        assertIs<PinResult.Wrong>(vault.authenticate(second, pinA, action))
        assertSame(before, store.state!!.people.getValue(first))
        assertEquals(1, store.state!!.people.getValue(second).pinCounter.failures)
        assertEquals(listOf(store.state!!.people.getValue(second).pin.salt.toList()), kdf.checkedSalts)
    }

    @Test fun `equal PINs work independently without comparing verifiers`() {
        store.state = CredentialState(Mode.TRUSTED, mapOf(first to vault.prepareCredential(pinA).credential, second to vault.prepareCredential(pinA).credential))
        kdf.checkedSalts.clear()
        assertIs<PinResult.Authorized>(vault.authenticate(first, pinA, action))
        assertEquals(1, kdf.checkedSalts.size)
        assertIs<PinResult.Authorized>(vault.authenticate(second, pinA, action))
        assertEquals(2, kdf.checkedSalts.size)
    }

    @Test fun `one or two trusted holders and exactly one self managed holder`() {
        val p = vault.prepareCredential(pinA).credential
        assertFailsWith<IllegalArgumentException> { CredentialState(Mode.TRUSTED, emptyMap()) }
        assertFailsWith<IllegalArgumentException> { CredentialState(Mode.TRUSTED, mapOf(first to p, second to p, TrustedPersonId("third") to p)) }
        assertFailsWith<IllegalArgumentException> { CredentialState(Mode.SELF_MANAGED, mapOf(first to p, second to p)) }
    }

    @Test fun `exact schedule including cap and tenth failure notice`() {
        enroll()
        for (failure in 1..15) {
            val result = assertIs<PinResult.Wrong>(vault.authenticate(first, wrong, action))
            val expected = when (failure) { in 1..4 -> 0L; 5 -> 30_000L; 6 -> 60_000L; 7 -> 120_000L; 8 -> 240_000L; 9 -> 480_000L; 10 -> 960_000L; 11 -> 1_920_000L; else -> 3_600_000L }
            assertEquals(expected, result.remainingMillis, "failure $failure")
            assertEquals(failure == 10, result.notifyLockout)
            advance(expected)
        }
    }

    @Test fun `entries during delay are not checked counted or extended`() {
        enroll(); repeat(5) { vault.authenticate(first, wrong, action) }
        val checks = kdf.checkedSalts.size
        advance(10_000)
        assertEquals(20_000L, assertIs<PinResult.Delayed>(vault.authenticate(first, pinA, action)).remainingMillis)
        assertEquals(checks, kdf.checkedSalts.size)
        assertEquals(5, store.state!!.people.getValue(first).pinCounter.failures)
        advance(20_000)
        assertIs<PinResult.Authorized>(vault.authenticate(first, pinA, action))
        assertEquals(0, store.state!!.people.getValue(first).pinCounter.failures)
    }

    @Test fun `reboot resumes remaining sixth failure delay with no wall clock input`() {
        enroll(); repeat(5) { vault.authenticate(first, wrong, action) }; advance(30_000)
        vault.authenticate(first, wrong, action); advance(20_000)
        assertEquals(40_000L, assertIs<PinResult.Delayed>(vault.authenticate(first, wrong, action)).remainingMillis)
        time = time.copy(boot = BootTime("next-boot", 0))
        val restarted = PinVault(store, kdf, FakeEntropy(), clock)
        assertEquals(40_000L, assertIs<PinResult.Delayed>(restarted.authenticate(first, wrong, action)).remainingMillis)
        advance(40_000)
        assertIs<PinResult.Authorized>(restarted.authenticate(first, pinA, action))
    }

    @Test fun `PIN lockout never blocks another person or recovery`() {
        val a = vault.prepareCredential(pinA)
        store.state = CredentialState(Mode.TRUSTED, mapOf(first to a.credential, second to vault.prepareCredential(pinB).credential))
        repeat(5) { vault.authenticate(first, wrong, action) }
        assertIs<PinResult.Authorized>(vault.authenticate(second, pinB, action))
        val recovered = assertIs<RecoveryResult.Replaced>(vault.recover(first, a.recoveryCode.toCharArray(), pinB))
        assertNotEquals(a.recoveryCode, recovered.recoveryCode)
        assertEquals(1, store.state!!.people.getValue(first).generation)
        assertIs<PinResult.Authorized>(vault.authenticate(first, pinB, action))
    }

    @Test fun `wrong recovery uses its own schedule and never changes another holder`() {
        enroll(); val other = store.state!!.people.getValue(second)
        repeat(5) { vault.recover(first, wrong, pinB) }
        assertEquals(5, store.state!!.people.getValue(first).recoveryCounter.failures)
        assertEquals(0, store.state!!.people.getValue(first).pinCounter.failures)
        assertSame(other, store.state!!.people.getValue(second))
        assertIs<PinResult.Authorized>(vault.authenticate(first, pinA, action))
        assertIs<RecoveryResult.Delayed>(vault.recover(first, wrong, pinB))
    }

    @Test fun `failed recovery write preserves old credentials together`() {
        val a = vault.prepareCredential(pinA)
        store.state = CredentialState(Mode.TRUSTED, mapOf(first to a.credential))
        store.fail = true
        assertIs<RecoveryResult.Unavailable>(vault.recover(first, a.recoveryCode.toCharArray(), pinB))
        assertSame(a.credential, store.state!!.people.getValue(first))
        store.fail = false
        assertIs<PinResult.Authorized>(vault.authenticate(first, pinA, action))
        assertIs<RecoveryResult.Replaced>(vault.recover(first, a.recoveryCode.toCharArray(), pinB))
        assertIs<RecoveryResult.Wrong>(vault.recover(first, a.recoveryCode.toCharArray(), pinA))
    }

    @Test fun `clock or persistence failure grants no authority`() {
        enroll(); clockWorks = false
        assertIs<PinResult.Unavailable>(vault.authenticate(first, pinA, action))
        assertTrue(kdf.checkedSalts.isEmpty())
        clockWorks = true; store.fail = true
        assertIs<PinResult.Unavailable>(vault.authenticate(first, pinA, action))
    }

    @Test fun `authority is single use action bound and lost on restart or credential replacement`() {
        enroll()
        val auth = assertIs<PinResult.Authorized>(vault.authenticate(first, pinA, action)).authority
        assertFalse(vault.consume(auth, PrivilegedAction("turn-off", "phone")))
        assertTrue(vault.consume(auth, action))
        assertFalse(vault.consume(auth, action))
        val another = assertIs<PinResult.Authorized>(vault.authenticate(first, pinA, action)).authority
        assertFalse(PinVault(store, kdf, FakeEntropy(), clock).consume(another, action))
        val original = store.state!!.people.getValue(first)
        store.state = CredentialState(Mode.TRUSTED, store.state!!.people + (first to original.copy(generation = original.generation + 1)))
        assertFalse(vault.consume(another, action))
    }

    @Test fun `credential salt and hash cannot be mutated through caller arrays`() {
        val salt = ByteArray(16) { 1 }; val hash = ByteArray(32) { 2 }
        val verifier = SecretVerifier(salt, hash, 600_000)
        salt.fill(9); hash.fill(9)
        assertEquals(1.toByte(), verifier.salt[0]); assertEquals(2.toByte(), verifier.hash[0])
        verifier.salt.fill(8); verifier.hash.fill(8)
        assertEquals(1.toByte(), verifier.salt[0]); assertEquals(2.toByte(), verifier.hash[0])
    }

    @Test fun `tenth failure notice survives a restart and later successful entry`() {
        enroll()
        repeat(10) {
            val result = assertIs<PinResult.Wrong>(vault.authenticate(first, wrong, action))
            advance(result.remainingMillis)
        }
        assertEquals(1, store.state!!.pendingNotices.size)
        val notice = store.state!!.pendingNotices.single()
        assertEquals(first, notice.person)
        assertEquals(CredentialChannel.PIN, notice.channel)
        assertIs<PinResult.Authorized>(PinVault(store, kdf, FakeEntropy(), clock).authenticate(first, pinA, action))
        assertEquals(setOf(notice), store.state!!.pendingNotices)
    }

    @Test fun `authority cannot survive reboot`() {
        enroll()
        val auth = assertIs<PinResult.Authorized>(vault.authenticate(first, pinA, action)).authority
        time = time.copy(boot = BootTime("next", 0))
        assertFalse(vault.consume(auth, action))
    }

    @Test fun `fresh authority expires at two minutes of boot time`() {
        enroll()
        var auth = assertIs<PinResult.Authorized>(vault.authenticate(first, pinA, action)).authority
        advance(119_999)
        assertTrue(vault.consume(auth, action))
        auth = assertIs<PinResult.Authorized>(vault.authenticate(first, pinA, action)).authority
        advance(120_000)
        assertFalse(vault.consume(auth, action))
        assertFalse(vault.consume(auth, action))
    }

    @Test fun `authority lifetime ignores epoch repairs and rejects backwards boot time`() {
        enroll(); advance(500_000)
        var auth = assertIs<PinResult.Authorized>(vault.authenticate(first, pinA, action)).authority
        time = time.copy(proven = RunningTime("repaired", 0))
        advance(120_000)
        assertFalse(vault.consume(auth, action))
        auth = assertIs<PinResult.Authorized>(vault.authenticate(first, pinA, action)).authority
        time = time.copy(boot = time.boot.copy(elapsedMillis = time.boot.elapsedMillis - 1))
        assertFalse(vault.consume(auth, action))
    }

    @Test fun `recovery accepts uppercase and groups of four with spaces or hyphens`() {
        for (separator in listOf("", " ", "-", " - ")) {
            val prepared = vault.prepareCredential(pinA)
            store.state = CredentialState(Mode.TRUSTED, mapOf(first to prepared.credential))
            val written = prepared.recoveryCode.uppercase().chunked(4).joinToString(separator).toCharArray()
            assertIs<RecoveryResult.Replaced>(vault.recover(first, written, pinB))
            assertIs<PinResult.Authorized>(vault.authenticate(first, pinB, action))
        }
    }

    @Test fun `recovery rejects other punctuation without silently dropping it`() {
        val prepared = vault.prepareCredential(pinA)
        store.state = CredentialState(Mode.TRUSTED, mapOf(first to prepared.credential))
        assertIs<RecoveryResult.Wrong>(vault.recover(first, (prepared.recoveryCode + "!").toCharArray(), pinB))
        assertEquals(1, store.state!!.people.getValue(first).recoveryCounter.failures)
        assertEquals(0, store.state!!.people.getValue(first).pinCounter.failures)
    }

    private class FakeDerivation : KeyDerivation {
        val checkedSalts = mutableListOf<List<Byte>>()
        val iterations = mutableListOf<Int>()
        val sizes = mutableListOf<Int>()
        override fun derive(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray {
            checkedSalts += salt.toList(); this.iterations += iterations; sizes += salt.size
            return java.security.MessageDigest.getInstance("SHA-256").digest(secret.concatToString().toByteArray() + salt)
        }
    }
    private class FakeEntropy : SecretEntropy {
        var next = 0
        override fun bytes(count: Int): ByteArray = ByteArray(count) { (++next).toByte() }
    }
    private class MemoryCredentials : CredentialStore {
        var state: CredentialState? = null
        var fail = false
        override fun read(): CredentialState? = state
        override fun compareAndSet(expected: CredentialState, replacement: CredentialState): Boolean {
            if (fail || state !== expected) return false
            state = replacement; return true
        }
    }
}
