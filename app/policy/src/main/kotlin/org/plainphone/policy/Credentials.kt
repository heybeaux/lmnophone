// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import java.util.Collections
import org.plainphone.core.*

/** Lane D implements PBKDF2-HMAC-SHA256. The fake used in tests is not a production implementation. */
fun interface KeyDerivation {
    fun derive(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray
}

/** Cryptographically secure random bytes, supplied by lane D. */
fun interface SecretEntropy { fun bytes(count: Int): ByteArray }

/** Copies both ways, with no secret bytes in generated diagnostics. */
class SecretVerifier(salt: ByteArray, hash: ByteArray, val iterations: Int) {
    private val saltBytes = salt.copyOf()
    private val hashBytes = hash.copyOf()
    val salt: ByteArray get() = saltBytes.copyOf()
    val hash: ByteArray get() = hashBytes.copyOf()
    init { require(salt.size == 16 && hash.size >= 32 && iterations >= 600_000) }
    override fun toString(): String = "SecretVerifier(redacted)"
}

data class FailureCounter(val failures: Int = 0, val remainingMillis: Long = 0, val checkpoint: RunningTime? = null) {
    init { require(failures >= 0 && remainingMillis in 0..3_600_000) }
    fun at(now: RunningTime): FailureCounter = copy(
        remainingMillis = (remainingMillis - (checkpoint?.let { now.since(it) } ?: 0)).coerceAtLeast(0), checkpoint = now,
    )
    fun failed(now: RunningTime): FailureCounter {
        val count = if (failures == Int.MAX_VALUE) failures else failures + 1
        val delay = when {
            count < 5 -> 0L
            count >= 12 -> 3_600_000L
            else -> 30_000L * (1L shl (count - 5))
        }
        return FailureCounter(count, delay, now)
    }
}

data class PersonCredential(
    val pin: SecretVerifier,
    val recovery: SecretVerifier,
    val pinCounter: FailureCounter = FailureCounter(),
    val recoveryCounter: FailureCounter = FailureCounter(),
    val generation: Long = 0,
    /** Absent only for older records; pending enrollment never populates an active slot. */
    val contact: PersonContact? = null,
) {
    init { require(generation >= 0) }
}

enum class CredentialChannel { PIN, RECOVERY }
data class LockoutNotice(val person: TrustedPersonId, val channel: CredentialChannel, val revision: Long)

class CredentialState(val mode: Mode, people: Map<TrustedPersonId, PersonCredential>, val revision: Long = 0, val structureGeneration: Long = 0, pendingNotices: Set<LockoutNotice> = emptySet(), val recoveryProfile: RecoveryProfile? = null,
    val appliedPeopleChange: AppliedPeopleChange? = null) {
    val pendingNotices: Set<LockoutNotice> = immutableSet(pendingNotices)
    val people: Map<TrustedPersonId, PersonCredential> = Collections.unmodifiableMap(people.toMap())
    init {
        require(people.size in 1..2 && (mode == Mode.TRUSTED || people.size == 1))
        require(revision >= 0 && structureGeneration >= 0)
        require(recoveryProfile == null || recoveryProfile.holderUserIds.keys == people.keys)
        require(appliedPeopleChange == null || appliedPeopleChange.structureGeneration <= structureGeneration)
    }
    fun replacing(person: TrustedPersonId, credential: PersonCredential, notice: CredentialChannel? = null): CredentialState {
        require(person in people)
        val nextRevision = Math.addExact(revision, 1)
        val notices = pendingNotices + if (notice == null) emptySet() else setOf(LockoutNotice(person, notice, nextRevision))
        return CredentialState(mode, people + (person to credential), nextRevision, structureGeneration, notices, recoveryProfile, appliedPeopleChange)
    }
}

/** Atomic device-protected credential record. All writers use this same serialization boundary. */
interface CredentialStore {
    /** Null means missing/invalid. The vault must not guess or enroll over it. */
    fun read(): CredentialState?
    /**
     * Compare the durable revision and replace the entire record atomically.
     *
     * The result contract for every Boolean store write in this module (PLA-160): true means
     * acknowledged, so the replacement reads back. False means not acknowledged: storage may hold the
     * expected record or the replacement, for example when the store's directory became unreadable
     * after the commit point. A caller must read again before acting. It never treats false as
     * "unchanged" or as a definite refusal, never retries with the same expected value, takes no
     * permissive action, and reports not ready (fail closed).
     */
    fun compareAndSet(expected: CredentialState, replacement: CredentialState): Boolean
}

/** A newly generated recovery code is returned to the setup/recovery screen once; do not log it. */
class PreparedCredential(val credential: PersonCredential, val recoveryCode: String) {
    override fun toString(): String = "PreparedCredential(redacted)"
}

data class PrivilegedAction(val kind: String, val binding: String) {
    init { require(kind.isNotBlank() && binding.isNotBlank()) }
}
/** Opaque process-local capability. Only its issuing vault can consume it. Never serialize it. */
class FreshAuthority internal constructor()

sealed interface PinResult {
    data class Authorized(val authority: FreshAuthority) : PinResult
    data class Wrong(val remainingMillis: Long, val notifyLockout: Boolean) : PinResult
    data class Delayed(val remainingMillis: Long) : PinResult
    data object Unavailable : PinResult
}
/** Change PIN (PLA-494). Unavailable covers every refusal: nothing was changed or success could not be read back. */
enum class PinChangeResult { Changed, Unavailable }
sealed interface RecoveryResult {
    class Replaced(val recoveryCode: String) : RecoveryResult { override fun toString(): String = "Replaced(redacted)" }
    data class Wrong(val remainingMillis: Long, val notifyLockout: Boolean) : RecoveryResult
    data class Delayed(val remainingMillis: Long) : RecoveryResult
    data object Unavailable : RecoveryResult
}
