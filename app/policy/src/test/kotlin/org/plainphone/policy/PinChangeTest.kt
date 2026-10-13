// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

/** PLA-494: Change PIN. The old PIN is checked by [PinVault.authenticate]; the change spends that grant. */
class PinChangeTest {
    private val self = TrustedPersonId("self")
    private val other = TrustedPersonId("other")
    // Synthetic fixtures only. No real credentials.
    private val oldPin get() = CharArray(6) { '1' }
    private val newPin get() = CharArray(7) { '2' }
    private val wrong get() = CharArray(6) { '9' }
    private val change = PrivilegedAction("preview:change-pin", "flow-1")
    private var time = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
    private val clock = RunningClock { time }
    private val kdf = FakeDerivation()
    private val store = MemoryCredentials()
    private val vault = PinVault(store, kdf, FakeEntropy(), clock)
    private lateinit var recoveryCode: String
    private fun advance(ms: Long) { time = ClockReading(time.proven.copy(totalMillis = time.proven.totalMillis + ms), time.boot.copy(elapsedMillis = time.boot.elapsedMillis + ms)) }
    private fun enroll(people: Set<TrustedPersonId> = setOf(self), mode: Mode = Mode.SELF_MANAGED) {
        val prepared = people.associateWith { vault.prepareCredential(oldPin) }
        recoveryCode = prepared.getValue(self).recoveryCode
        store.state = CredentialState(mode, prepared.mapValues { it.value.credential.copy(contact = null) })
    }
    private fun grant(action: PrivilegedAction = change) =
        assertIs<PinResult.Authorized>(vault.authenticate(self, oldPin, action)).authority

    @Test fun `right old PIN changes it, the new PIN opens and the old one is refused`() {
        enroll()
        val before = store.state!!.people.getValue(self)
        assertEquals(PinChangeResult.Changed, vault.changePin(self, grant(), change, newPin))
        val after = store.state!!.people.getValue(self)
        assertEquals(before.generation + 1, after.generation)
        assertSame(before.recovery, after.recovery)
        assertEquals(before.contact, after.contact)
        assertEquals(0, after.pinCounter.failures)
        assertIs<PinResult.Wrong>(vault.authenticate(self, oldPin, change))
        assertIs<PinResult.Authorized>(vault.authenticate(self, newPin, change))
    }

    @Test fun `the recovery code still resets after a change`() {
        enroll()
        assertEquals(PinChangeResult.Changed, vault.changePin(self, grant(), change, newPin))
        assertIs<RecoveryResult.Replaced>(vault.recover(self, recoveryCode.toCharArray(), oldPin))
        assertIs<PinResult.Authorized>(vault.authenticate(self, oldPin, change))
    }

    @Test fun `a wrong old PIN is a normal failed try and a locked-out person cannot change`() {
        enroll()
        repeat(5) { assertIs<PinResult.Wrong>(vault.authenticate(self, wrong, change)) }
        assertEquals(5, store.state!!.people.getValue(self).pinCounter.failures)
        assertIs<PinResult.Delayed>(vault.authenticate(self, oldPin, change))
        val generation = store.state!!.people.getValue(self).generation
        // No grant exists, so nothing can be changed while locked out.
        assertEquals(PinChangeResult.Unavailable, vault.changePin(self, FreshAuthority(), change, newPin))
        assertEquals(generation, store.state!!.people.getValue(self).generation)
    }

    @Test fun `the grant must be for changing the PIN, this person, fresh and unspent`() {
        enroll(setOf(self, other), Mode.TRUSTED)
        val wrongAction = grant(PrivilegedAction("preview:controls", "x"))
        assertEquals(PinChangeResult.Unavailable, vault.changePin(self, wrongAction, change, newPin))
        val forSelf = grant()
        assertEquals(PinChangeResult.Unavailable, vault.changePin(other, forSelf, change, newPin))
        val stale = grant(); advance(120_000)
        assertEquals(PinChangeResult.Unavailable, vault.changePin(self, stale, change, newPin))
        val once = grant()
        assertEquals(PinChangeResult.Changed, vault.changePin(self, once, change, newPin))
        assertEquals(PinChangeResult.Unavailable, vault.changePin(self, once, change, oldPin))
        assertIs<PinResult.Authorized>(vault.authenticate(self, newPin, change))
    }

    @Test fun `no grant from before the change authorizes anything after it`() {
        enroll()
        val g = grant()
        assertEquals(PinChangeResult.Changed, vault.changePin(self, g, change, newPin))
        assertFalse(vault.consumeFor(g, change, self))
        assertFalse(vault.consume(g, change))
    }

    @Test fun `new PIN must follow enrollment rules and nothing is written otherwise`() {
        enroll()
        listOf(CharArray(5) { '1' }, "abcdef".toCharArray(), charArrayOf()).forEach { bad ->
            val g = grant()
            val before = store.state
            assertEquals(PinChangeResult.Unavailable, vault.changePin(self, g, change, bad))
            assertSame(before, store.state)
        }
    }

    @Test fun `a false write reports failure and the old PIN still works`() {
        enroll()
        val g = grant()
        store.fail = true
        assertEquals(PinChangeResult.Unavailable, vault.changePin(self, g, change, newPin))
        store.fail = false
        assertIs<PinResult.Authorized>(vault.authenticate(self, oldPin, change))
    }

    @Test fun `a false write that landed is never reported as success from a read-back`() {
        enroll()
        val g = grant()
        store.landButReportFalse = true
        assertEquals(PinChangeResult.Unavailable, vault.changePin(self, g, change, newPin))
    }

    @Test fun `a write that throws reports nothing and the old PIN still works`() {
        enroll()
        val g = grant()
        store.throws = true
        assertFailsWith<IllegalStateException> { vault.changePin(self, g, change, newPin) }
        store.throws = false
        assertIs<PinResult.Authorized>(vault.authenticate(self, oldPin, change))
    }

    @Test fun `success needs the stored verifier to read back as the new PIN`() {
        enroll()
        val g = grant()
        store.readBackStale = true
        assertEquals(PinChangeResult.Unavailable, vault.changePin(self, g, change, newPin))
    }

    @Test fun `the new PIN array is zeroed`() {
        enroll()
        val pin = newPin
        vault.changePin(self, grant(), change, pin)
        assertTrue(pin.all { it == '\u0000' })
    }

    private class FakeDerivation : KeyDerivation {
        override fun derive(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray =
            (String(secret) + "|" + salt.joinToString(",")).toByteArray()
    }
    private class FakeEntropy : SecretEntropy {
        private var next = 0
        override fun bytes(count: Int): ByteArray = ByteArray(count) { (++next).toByte() }
    }
    private class MemoryCredentials : CredentialStore {
        var state: CredentialState? = null
        var fail = false
        var throws = false
        var landButReportFalse = false
        var readBackStale = false
        private var staleCopy: CredentialState? = null
        override fun read(): CredentialState? = if (readBackStale && staleCopy != null) staleCopy else state
        override fun compareAndSet(expected: CredentialState, replacement: CredentialState): Boolean {
            if (throws) throw IllegalStateException("disk")
            if (fail || state !== expected) return false
            if (readBackStale) staleCopy = expected
            state = replacement
            return !landButReportFalse
        }
    }
}
