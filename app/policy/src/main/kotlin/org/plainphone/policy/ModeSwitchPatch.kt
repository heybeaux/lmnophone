// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import java.util.Collections
import org.plainphone.core.*

/** Local enrollment attestations, not verification of an account or what a real wipe accepts. */
data class SelfManagedEnrollmentConfirmations(
    val ownPinSet: Boolean,
    val recoveryCodeReceived: Boolean,
    val resetProtectionIdFromOwnDevice: Boolean,
    val offDeviceSignInChecked: Boolean,
    val independentSecondFactorChecked: Boolean,
    val accountAtLeast72HoursOld: Boolean,
) {
    internal val complete: Boolean get() = ownPinSet && recoveryCodeReceived && resetProtectionIdFromOwnDevice &&
        offDeviceSignInChecked && independentSecondFactorChecked && accountAtLeast72HoursOld
}

/** Pending user credentials have no authority. Self-managed enrollment does not require an SMS contact. */
class IncomingSelfManagedEnrollment private constructor(
    val person: TrustedPersonId,
    val credential: PersonCredential,
    val resetProtectionUserId: String,
    val confirmations: SelfManagedEnrollmentConfirmations,
    val witness: EnrollmentWitness,
) {
    override fun toString() = "IncomingSelfManagedEnrollment(redacted)"

    companion object {
        fun confirm(person: TrustedPersonId, resetProtectionUserId: String, prepared: PreparedCredential,
            confirmations: SelfManagedEnrollmentConfirmations, witness: EnrollmentWitness): IncomingSelfManagedEnrollment? {
            if (!confirmations.complete || resetProtectionUserId.isBlank() || resetProtectionUserId.any(Char::isWhitespace)) return null
            val credential = prepared.credential
            if (credential.generation != 0L || credential.pinCounter != FailureCounter() ||
                credential.recoveryCounter != FailureCounter()) return null
            return IncomingSelfManagedEnrollment(person, credential.copy(contact = null), resetProtectionUserId, confirmations, witness)
        }
    }
}

/**
 * Immutable switch inputs, never authority to apply. Every outgoing holder is removed, so every
 * outgoing generation is a dependency. Incoming IDs must be new and never reused on this phone.
 * Exactly one enrollment variant is retained, including its account-check audit attestations.
 */
class ModeSwitchPatch private constructor(
    val starter: TrustedPersonId,
    val sourceMode: Mode,
    val structureGeneration: Long,
    dependencyGenerations: Map<TrustedPersonId, Long>,
    val incomingSelfManaged: IncomingSelfManagedEnrollment?,
    val incomingTrustedPerson: IncomingPersonEnrollment?,
) {
    val targetMode: Mode = if (sourceMode == Mode.TRUSTED) Mode.SELF_MANAGED else Mode.TRUSTED
    val dependencyGenerations: Map<TrustedPersonId, Long> = Collections.unmodifiableMap(LinkedHashMap(dependencyGenerations))
    val removedSlots: Set<TrustedPersonId> = immutableSet(dependencyGenerations.keys)
    val incomingPerson: TrustedPersonId = incomingSelfManaged?.person ?: incomingTrustedPerson!!.person
    val changedSlots: Set<TrustedPersonId> = immutableSet(removedSlots + incomingPerson)
    private val incomingCredential: PersonCredential = incomingSelfManaged?.credential ?: incomingTrustedPerson!!.credential
    private val incomingUserId: String = incomingSelfManaged?.resetProtectionUserId ?: incomingTrustedPerson!!.resetProtectionUserId

    fun dependenciesMatch(current: CredentialState): Boolean = structureMatches(current) &&
        dependencyGenerations.all { (person, generation) -> current.people[person]?.generation == generation }

    internal fun structureMatches(current: CredentialState): Boolean = current.mode == sourceMode &&
        current.structureGeneration == structureGeneration && current.recoveryProfile != null &&
        current.people.keys == removedSlots && incomingPerson !in current.people

    /**
     * Candidate calculation only. Future executor must persist COMMITTING, revoke old authority,
     * set/broadcast/read back the target reset-protection list, then atomically swap mode/credentials
     * with an applied marker. Preserve the separate elevation ledger across that transaction.
     * Post-commit PIN changes do not stop the switch; replay after structure change is refused.
     */
    internal fun replacementForCommit(current: CredentialState): CredentialState? {
        if (!structureMatches(current)) return null
        val profile = current.recoveryProfile!!
        val revision = try { Math.addExact(current.revision, 1) } catch (_: ArithmeticException) { return null }
        val structure = try { Math.addExact(current.structureGeneration, 1) } catch (_: ArithmeticException) { return null }
        return CredentialState(targetMode, mapOf(incomingPerson to incomingCredential), revision, structure, current.pendingNotices,
            RecoveryProfile(mapOf(incomingPerson to incomingUserId), profile.backupUserId, profile.friction))
    }

    override fun toString() = "ModeSwitchPatch($sourceMode, $targetMode, redacted)"

    companion object {
        fun toSelfManaged(current: CredentialState, starter: TrustedPersonId, incoming: IncomingSelfManagedEnrollment): ModeSwitchPatch? {
            if (current.mode != Mode.TRUSTED || !eligible(current, starter, incoming.person, incoming.witness)) return null
            return ModeSwitchPatch(starter, current.mode, current.structureGeneration,
                current.people.mapValues { it.value.generation }, incoming, null)
        }

        fun toSupervised(current: CredentialState, starter: TrustedPersonId, incoming: IncomingPersonEnrollment): ModeSwitchPatch? {
            if (current.mode != Mode.SELF_MANAGED || !eligible(current, starter, incoming.person, incoming.witness)) return null
            return ModeSwitchPatch(starter, current.mode, current.structureGeneration,
                current.people.mapValues { it.value.generation }, null, incoming)
        }

        private fun eligible(current: CredentialState, starter: TrustedPersonId, incoming: TrustedPersonId,
            witness: EnrollmentWitness): Boolean = current.recoveryProfile != null && starter in current.people &&
            incoming !in current.people && (witness !is EnrollmentWitness.CurrentPerson || witness.person in current.people)
    }
}
