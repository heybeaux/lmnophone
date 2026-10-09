// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import java.util.Collections
import org.plainphone.core.*

/** Sensitive fixed delivery data, persisted with its holder; never interpolate into diagnostics. */
class PersonContact(val name: String, val number: String) {
    init { require(name.isNotBlank() && number.isNotBlank()) }
    override fun toString() = "PersonContact(redacted)"
}

/**
 * Facts recorded by the local enrollment flow, not evidence obtained from an outside intent.
 * These attestations do not verify a Google account, SMS delivery or which account a wipe asks for.
 */
data class EnrollmentConfirmations(
    val inPerson: Boolean,
    val nameAndNumberEntered: Boolean,
    val ownPinSet: Boolean,
    val recoveryCodeReceived: Boolean,
    val resetProtectionIdFromOwnDevice: Boolean,
    val testTextReceived: Boolean,
    val offDeviceSignInChecked: Boolean,
    val independentSecondFactorChecked: Boolean,
    val accountAtLeast72HoursOld: Boolean,
) {
    internal val complete: Boolean get() = inPerson && nameAndNumberEntered && ownPinSet &&
        recoveryCodeReceived && resetProtectionIdFromOwnDevice && testTextReceived &&
        offDeviceSignInChecked && independentSecondFactorChecked && accountAtLeast72HoursOld
}

/** Who recorded the three account checks. This is an audit fact, never an authority capability. */
sealed interface EnrollmentWitness {
    data object Helper : EnrollmentWitness
    data class CurrentPerson(val person: TrustedPersonId) : EnrollmentWitness
}

/**
 * Immutable pending inputs. Only verifiers survive preparation, never the plaintext recovery code.
 * The adapter reserves a new, never-reused opaque person ID and collects confirmations in person.
 * Construction does not write the credential store or grant PIN/recovery/action authority.
 */
class IncomingPersonEnrollment private constructor(
    val person: TrustedPersonId,
    val credential: PersonCredential,
    val resetProtectionUserId: String,
    val confirmations: EnrollmentConfirmations,
    val witness: EnrollmentWitness,
) {
    override fun toString() = "IncomingPersonEnrollment(redacted)"

    companion object {
        fun confirm(person: TrustedPersonId, contact: PersonContact, resetProtectionUserId: String,
            prepared: PreparedCredential, confirmations: EnrollmentConfirmations,
            witness: EnrollmentWitness): IncomingPersonEnrollment? {
            if (!confirmations.complete || resetProtectionUserId.isBlank() || resetProtectionUserId.any(Char::isWhitespace)) return null
            val credential = prepared.credential
            if (credential.generation != 0L || credential.pinCounter != FailureCounter() ||
                credential.recoveryCounter != FailureCounter()) return null
            return IncomingPersonEnrollment(person, credential.copy(contact = contact), resetProtectionUserId, confirmations, witness)
        }
    }
}

/**
 * A patch names only the slots it adds or removes. It is immutable data, not permission to apply it.
 * The waiting controller must bind fresh authority to starter, persist the 24-hour wait and recheck
 * dependencies before committing. In a handover starter is necessarily the outgoing person.
 */
class TrustedPersonPatch private constructor(
    val kind: WaitingChangeKind,
    val starter: TrustedPersonId,
    val structureGeneration: Long,
    dependencyGenerations: Map<TrustedPersonId, Long>,
    removedSlots: Set<TrustedPersonId>,
    val incoming: IncomingPersonEnrollment?,
) {
    val dependencyGenerations: Map<TrustedPersonId, Long> = Collections.unmodifiableMap(LinkedHashMap(dependencyGenerations))
    val removedSlots: Set<TrustedPersonId> = immutableSet(removedSlots)
    val changedSlots: Set<TrustedPersonId> = immutableSet(removedSlots + listOfNotNull(incoming?.person))

    /** Pending-only guard. Counter/revision changes alone never invalidate a dependency. */
    fun dependenciesMatch(current: CredentialState): Boolean = structureMatches(current) &&
        dependencyGenerations.all { (person, generation) -> current.people[person]?.generation == generation }

    /**
     * Pure candidate calculation for the future committing controller, not a store write.
     * Caller must first persist COMMITTING and set/read back this candidate's reset-protection list.
     * Apply via CAS against this exact current state, under the shared serialized dispatcher.
     * Generations are deliberately not rechecked after commit; preserve all latest unaffected slots.
     * A structure mismatch (including replay after application) yields null, never a second patch.
     */
    internal fun replacementForCommit(current: CredentialState): CredentialState? {
        if (!structureMatches(current)) return null
        val profile = current.recoveryProfile ?: return null
        val people = current.people.toMutableMap()
        val accounts = profile.holderUserIds.toMutableMap()
        removedSlots.forEach { people.remove(it); accounts.remove(it) }
        incoming?.let { people[it.person] = it.credential; accounts[it.person] = it.resetProtectionUserId }
        if (people.size !in 1..2) return null
        val revision = try { Math.addExact(current.revision, 1) } catch (_: ArithmeticException) { return null }
        val structure = try { Math.addExact(current.structureGeneration, 1) } catch (_: ArithmeticException) { return null }
        return CredentialState(current.mode, people, revision, structure, current.pendingNotices,
            RecoveryProfile(accounts, profile.backupUserId, profile.friction))
    }

    private fun structureMatches(current: CredentialState): Boolean = current.mode == Mode.TRUSTED &&
        current.structureGeneration == structureGeneration && current.recoveryProfile != null &&
        current.people.keys.containsAll(dependencyGenerations.keys) &&
        (incoming == null || incoming.person !in current.people)

    override fun toString() = "TrustedPersonPatch($kind, redacted)"

    companion object {
        fun add(current: CredentialState, starter: TrustedPersonId, incoming: IncomingPersonEnrollment): TrustedPersonPatch? {
            if (current.people.size != 1) return null
            return prepare(current, WaitingChangeKind.ADD_PERSON, starter, emptySet(), incoming)
        }

        fun handOver(current: CredentialState, outgoing: TrustedPersonId, incoming: IncomingPersonEnrollment): TrustedPersonPatch? =
            prepare(current, WaitingChangeKind.HAND_OVER, outgoing, setOf(outgoing), incoming)

        fun remove(current: CredentialState, starter: TrustedPersonId, removed: TrustedPersonId): TrustedPersonPatch? {
            if (current.people.size != 2) return null
            return prepare(current, WaitingChangeKind.REMOVE_PERSON, starter, setOf(removed), null)
        }

        private fun prepare(current: CredentialState, kind: WaitingChangeKind, starter: TrustedPersonId,
            removed: Set<TrustedPersonId>, incoming: IncomingPersonEnrollment?): TrustedPersonPatch? {
            if (current.mode != Mode.TRUSTED || current.recoveryProfile == null || starter !in current.people ||
                !current.people.keys.containsAll(removed) || incoming?.person in current.people) return null
            val witness = incoming?.witness
            if (witness is EnrollmentWitness.CurrentPerson && witness.person !in current.people) return null
            val dependencies = (removed + starter).associateWith { current.people.getValue(it).generation }
            return TrustedPersonPatch(kind, starter, current.structureGeneration, dependencies, removed, incoming)
        }
    }
}
