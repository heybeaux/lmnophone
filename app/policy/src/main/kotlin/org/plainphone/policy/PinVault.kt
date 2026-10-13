// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import java.security.MessageDigest
import org.plainphone.core.*

/** Deterministic apart from the four injected ports; performs no Android calls or wall-clock reads. */
class PinVault(
    private val store: CredentialStore,
    private val derivation: KeyDerivation,
    private val entropy: SecretEntropy,
    private val clock: RunningClock,
) : AuthorityConsumer, PersonAuthorityConsumer {
    private data class Grant(val person: TrustedPersonId, val generation: Long, val action: PrivilegedAction, val issuedAt: BootTime)
    private val grants = mutableMapOf<FreshAuthority, Grant>()

    fun prepareCredential(pin: CharArray): PreparedCredential {
        validatePin(pin)
        val random = entropy.bytes(16).also { require(it.size == 16) }
        val code = random.joinToString("") { "%02x".format(it.toInt() and 255) }
        random.fill(0)
        return PreparedCredential(PersonCredential(hash(pin), hash(code.toCharArray())), code)
    }

    @Synchronized
    fun authenticate(person: TrustedPersonId, pin: CharArray, action: PrivilegedAction): PinResult {
        val now = clock.checkpoint() ?: return PinResult.Unavailable
        val state = store.read() ?: return PinResult.Unavailable
        val credential = state.people[person] ?: return PinResult.Unavailable
        val counter = credential.pinCounter.at(now.proven)
        if (counter.remainingMillis > 0) {
            if (!store.compareAndSet(state, state.replacing(person, credential.copy(pinCounter = counter)))) return PinResult.Unavailable
            return PinResult.Delayed(counter.remainingMillis)
        }
        val matches = verify(pin, credential.pin)
        val next = if (matches) FailureCounter(checkpoint = now.proven) else counter.failed(now.proven)
        if (!store.compareAndSet(state, state.replacing(person, credential.copy(pinCounter = next), if (!matches && next.failures == 10) CredentialChannel.PIN else null))) return PinResult.Unavailable
        if (!matches) return PinResult.Wrong(next.remainingMillis, next.failures == 10)
        val authority = FreshAuthority()
        // A fresh entry replaces any unspent authority from this credential.
        grants.entries.removeAll { it.value.person == person }
        grants[authority] = Grant(person, credential.generation, action, now.boot)
        return PinResult.Authorized(authority)
    }

    /** Must run on the same serialized action dispatcher as the following privileged write. */
    @Synchronized
    override fun consume(authority: FreshAuthority, action: PrivilegedAction): Boolean {
        val grant = grants[authority] ?: return false
        if (grant.action != action) return false
        grants.remove(authority)
        val now = clock.checkpoint() ?: return false
        val credential = store.read()?.people?.get(grant.person) ?: return false
        return now.boot.bootId == grant.issuedAt.bootId &&
            now.boot.elapsedMillis >= grant.issuedAt.elapsedMillis &&
            now.boot.elapsedMillis - grant.issuedAt.elapsedMillis < 120_000L &&
            credential.generation == grant.generation
    }

    @Synchronized
    override fun consumeFor(authority: FreshAuthority, action: PrivilegedAction, person: TrustedPersonId): Boolean {
        if (grants[authority]?.person != person) return false
        return consume(authority, action)
    }

    /**
     * Change PIN (PLA-494). [authority] must be an unspent grant for [action] from [authenticate] on the
     * old PIN, so the old PIN was checked with the normal counter and lockout. One compareAndSet writes the
     * new PIN verifier, generation + 1 and a cleared PIN counter; the recovery verifier, its counter and the
     * contact are kept. Waiting changes that depend on the old generation are cancelled by their checkpoint.
     * Success only after the stored verifier reads back as the new PIN; a false write never reads back.
     */
    @Synchronized
    fun changePin(person: TrustedPersonId, authority: FreshAuthority, action: PrivilegedAction, newPin: CharArray): PinChangeResult = try {
        if (newPin.size < 6 || newPin.any { it !in '0'..'9' } || grants[authority]?.person != person) PinChangeResult.Unavailable
        else if (!consume(authority, action)) PinChangeResult.Unavailable
        else changeAfterGrant(person, newPin)
    } finally { newPin.fill('\u0000') }

    private fun changeAfterGrant(person: TrustedPersonId, newPin: CharArray): PinChangeResult {
        val now = clock.checkpoint() ?: return PinChangeResult.Unavailable
        val state = store.read() ?: return PinChangeResult.Unavailable
        val credential = state.people[person] ?: return PinChangeResult.Unavailable
        val nextGeneration = try { Math.addExact(credential.generation, 1) } catch (_: ArithmeticException) { return PinChangeResult.Unavailable }
        if (state.revision == Long.MAX_VALUE) return PinChangeResult.Unavailable
        val pin = hash(newPin)
        val replacement = credential.copy(pin = pin, pinCounter = FailureCounter(checkpoint = now.proven), generation = nextGeneration)
        if (!store.compareAndSet(state, state.replacing(person, replacement))) return PinChangeResult.Unavailable
        grants.entries.removeAll { it.value.person == person }
        val saved = store.read()?.people?.get(person) ?: return PinChangeResult.Unavailable
        return if (saved.generation == nextGeneration && saved.pin.hash.contentEquals(pin.hash) &&
            saved.pin.salt.contentEquals(pin.salt)) PinChangeResult.Changed else PinChangeResult.Unavailable
    }

    /** Resets only the selected holder. Never grants action authority and never skips a waiting change. */
    @Synchronized
    fun recover(person: TrustedPersonId, code: CharArray, newPin: CharArray): RecoveryResult =
        recoverWithCommit(person, code, newPin) { before, replacement -> store.compareAndSet(before, replacement) }

    /** Shared verification/counters; only policy-owned workflows may replace the final atomic commit. */
    @Synchronized
    internal fun recoverWithCommit(person: TrustedPersonId, code: CharArray, newPin: CharArray,
        commit: (CredentialState, CredentialState) -> Boolean): RecoveryResult {
        validatePin(newPin)
        val now = clock.checkpoint() ?: return RecoveryResult.Unavailable
        val state = store.read() ?: return RecoveryResult.Unavailable
        val credential = state.people[person] ?: return RecoveryResult.Unavailable
        val counter = credential.recoveryCounter.at(now.proven)
        if (counter.remainingMillis > 0) {
            if (!store.compareAndSet(state, state.replacing(person, credential.copy(recoveryCounter = counter)))) return RecoveryResult.Unavailable
            return RecoveryResult.Delayed(counter.remainingMillis)
        }
        if (!verifyRecovery(code, credential.recovery)) {
            val next = counter.failed(now.proven)
            if (!store.compareAndSet(state, state.replacing(person, credential.copy(recoveryCounter = next), if (next.failures == 10) CredentialChannel.RECOVERY else null))) return RecoveryResult.Unavailable
            return RecoveryResult.Wrong(next.remainingMillis, next.failures == 10)
        }
        val nextGeneration = try { Math.addExact(credential.generation, 1) } catch (_: ArithmeticException) { return RecoveryResult.Unavailable }
        if (state.revision == Long.MAX_VALUE) return RecoveryResult.Unavailable
        val prepared = prepareCredential(newPin)
        val replacement = prepared.credential.copy(generation = nextGeneration, contact = credential.contact)
        if (!commit(state, state.replacing(person, replacement))) return RecoveryResult.Unavailable
        grants.entries.removeAll { it.value.person == person }
        return RecoveryResult.Replaced(prepared.recoveryCode)
    }

    private fun verifyRecovery(code: CharArray, verifier: SecretVerifier): Boolean {
        val normalized = code.filterNot { it == ' ' || it == '-' }.map { it.lowercaseChar() }.toCharArray()
        return try {
            normalized.size == 32 && normalized.all { it in '0'..'9' || it in 'a'..'f' } && verify(normalized, verifier)
        } finally { normalized.fill('\u0000') }
    }

    private fun validatePin(pin: CharArray) { require(pin.size >= 6 && pin.all { it in '0'..'9' }) }
    private fun hash(secret: CharArray): SecretVerifier {
        val salt = entropy.bytes(16).also { require(it.size == 16) }
        val hash = derivation.derive(secret, salt, 600_000)
        return SecretVerifier(salt, hash, 600_000).also { salt.fill(0); hash.fill(0) }
    }
    private fun verify(secret: CharArray, verifier: SecretVerifier): Boolean {
        val actual = derivation.derive(secret, verifier.salt, verifier.iterations)
        return try { MessageDigest.isEqual(actual, verifier.hash) } finally { actual.fill(0) }
    }
}
