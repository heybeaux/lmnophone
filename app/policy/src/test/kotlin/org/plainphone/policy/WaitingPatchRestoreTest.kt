// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class WaitingPatchRestoreTest {
    private val a = TrustedPersonId("first")
    private val b = TrustedPersonId("second")
    private val next = TrustedPersonId("incoming")
    private val verifier = SecretVerifier(ByteArray(16), ByteArray(32), 600_000)
    private val credential = PersonCredential(verifier, verifier)
    private val checks = EnrollmentConfirmations(true, true, true, true, true, true, true, true, true)
    private val selfChecks = SelfManagedEnrollmentConfirmations(true, true, true, true, true, true)
    private val contact = PersonContact("synthetic-name", "synthetic-number")
    private fun person(witness: EnrollmentWitness = EnrollmentWitness.Helper) = assertNotNull(
        IncomingPersonEnrollment.confirm(next, contact, "synthetic-account", PreparedCredential(credential, "synthetic-code"), checks, witness))
    private fun self(witness: EnrollmentWitness = EnrollmentWitness.Helper) = assertNotNull(
        IncomingSelfManagedEnrollment.confirm(next, "synthetic-account", PreparedCredential(credential, "synthetic-code"), selfChecks, witness))
    private fun state(mode: Mode = Mode.TRUSTED, two: Boolean = false): CredentialState {
        val people = if (two) mapOf(a to credential.copy(generation = 3), b to credential.copy(generation = 7))
            else mapOf(a to credential.copy(generation = 3))
        return CredentialState(mode, people, 10, 4, recoveryProfile = RecoveryProfile(
            people.keys.associateWith { "synthetic-${it.value}" }, "synthetic-backup"))
    }
    private fun decode(p: IncomingPersonEnrollment) = assertNotNull(IncomingPersonEnrollment.restore(
        p.person, p.credential, p.resetProtectionUserId, p.confirmations, p.witness))
    private fun decode(p: IncomingSelfManagedEnrollment) = assertNotNull(IncomingSelfManagedEnrollment.restore(
        p.person, p.credential, p.resetProtectionUserId, p.confirmations, p.witness))
    private fun decode(p: TrustedPersonPatch) = assertNotNull(TrustedPersonPatch.restore(
        p.kind, p.starter, p.structureGeneration, p.dependencyGenerations.toMap(), p.removedSlots.toSet(), p.incoming?.let { decode(it) }))
    private fun decode(p: ModeSwitchPatch) = assertNotNull(ModeSwitchPatch.restore(
        p.starter, p.sourceMode, p.structureGeneration, p.dependencyGenerations.toMap(),
        p.incomingSelfManaged?.let { decode(it) }, p.incomingTrustedPerson?.let { decode(it) }))

    @Test fun `enrollment decoders retain stored verifiers contact confirmations and witness`() {
        val p = person(EnrollmentWitness.CurrentPerson(a)); val d = decode(p)
        assertEquals(p.person, d.person); assertSame(p.credential, d.credential)
        assertSame(contact, d.credential.contact); assertEquals(p.resetProtectionUserId, d.resetProtectionUserId)
        assertEquals(p.confirmations, d.confirmations); assertEquals(p.witness, d.witness)
        val s = self(EnrollmentWitness.CurrentPerson(a)); val restored = decode(s)
        assertEquals(s.person, restored.person); assertSame(s.credential, restored.credential)
        assertNull(restored.credential.contact); assertEquals(s.resetProtectionUserId, restored.resetProtectionUserId)
        assertEquals(s.confirmations, restored.confirmations); assertEquals(s.witness, restored.witness)
    }

    @Test fun `enrollment decoders reject incomplete confirmations malformed accounts and used credentials`() {
        val p = person(); val s = self()
        val incomplete = listOf(checks.copy(inPerson = false), checks.copy(nameAndNumberEntered = false),
            checks.copy(ownPinSet = false), checks.copy(recoveryCodeReceived = false), checks.copy(resetProtectionIdFromOwnDevice = false),
            checks.copy(testTextReceived = false), checks.copy(offDeviceSignInChecked = false),
            checks.copy(independentSecondFactorChecked = false), checks.copy(accountAtLeast72HoursOld = false))
        incomplete.forEach { assertNull(IncomingPersonEnrollment.restore(next, p.credential, p.resetProtectionUserId, it, p.witness)) }
        val incompleteSelf = listOf(selfChecks.copy(ownPinSet = false), selfChecks.copy(recoveryCodeReceived = false),
            selfChecks.copy(resetProtectionIdFromOwnDevice = false), selfChecks.copy(offDeviceSignInChecked = false),
            selfChecks.copy(independentSecondFactorChecked = false), selfChecks.copy(accountAtLeast72HoursOld = false))
        incompleteSelf.forEach { assertNull(IncomingSelfManagedEnrollment.restore(next, s.credential, s.resetProtectionUserId, it, s.witness)) }
        for (account in listOf("", " ", "synthetic account", "synthetic\naccount")) {
            assertNull(IncomingPersonEnrollment.restore(next, p.credential, account, checks, p.witness))
            assertNull(IncomingSelfManagedEnrollment.restore(next, s.credential, account, selfChecks, s.witness))
        }
        for (bad in listOf(credential.copy(generation = 1), credential.copy(pinCounter = FailureCounter(1)),
            credential.copy(recoveryCounter = FailureCounter(1)),
            credential.copy(pinCounter = FailureCounter(checkpoint = RunningTime("epoch", 0))))) {
            assertNull(IncomingPersonEnrollment.restore(next, bad.copy(contact = contact), p.resetProtectionUserId, checks, p.witness))
            assertNull(IncomingSelfManagedEnrollment.restore(next, bad, s.resetProtectionUserId, selfChecks, s.witness))
        }
        assertNull(IncomingPersonEnrollment.restore(next, credential, p.resetProtectionUserId, checks, p.witness))
        assertNull(IncomingSelfManagedEnrollment.restore(next, credential.copy(contact = contact), s.resetProtectionUserId, selfChecks, s.witness))
    }

    @Test fun `all people patches round trip original dependencies including unrelated handover witness`() {
        val one = state(); val two = state(two = true)
        val patches = listOf(TrustedPersonPatch.add(one, a, person())!!,
            TrustedPersonPatch.handOver(two, a, person(EnrollmentWitness.CurrentPerson(b)))!!,
            TrustedPersonPatch.remove(two, a, b)!!, TrustedPersonPatch.remove(two, a, a)!!)
        for (p in patches) {
            val d = decode(p)
            assertEquals(p.kind, d.kind); assertEquals(p.starter, d.starter)
            assertEquals(p.structureGeneration, d.structureGeneration)
            assertEquals(p.dependencyGenerations, d.dependencyGenerations)
            assertEquals(p.removedSlots, d.removedSlots); assertEquals(p.changedSlots, d.changedSlots)
            assertEquals(p.incoming?.witness, d.incoming?.witness)
            assertTrue(d.dependenciesMatch(if (p.kind == WaitingChangeKind.ADD_PERSON) one else two))
            val newer = two.replacing(a, credential.copy(generation = 4))
            assertFalse(d.dependenciesMatch(newer)) // Decoder must not rebase stored generations.
        }
    }

    @Test fun `people decoder rejects impossible kind slot generation and dependency combinations`() {
        fun restore(kind: WaitingChangeKind = WaitingChangeKind.ADD_PERSON, structure: Long = 4,
            dependencies: Map<TrustedPersonId, Long> = mapOf(a to 3L), removed: Set<TrustedPersonId> = emptySet(),
            incoming: IncomingPersonEnrollment? = person()) = TrustedPersonPatch.restore(kind, a, structure, dependencies, removed, incoming)
        assertNull(restore(structure = -1)); assertNull(restore(dependencies = mapOf(a to -1L)))
        assertNull(restore(dependencies = emptyMap())); assertNull(restore(dependencies = mapOf(b to 3L)))
        assertNull(restore(dependencies = mapOf(a to 3L, b to 7L)))
        assertNull(restore(incoming = null)); assertNull(restore(removed = setOf(a)))
        assertNull(restore(incoming = person(EnrollmentWitness.CurrentPerson(b))))
        assertNull(restore(WaitingChangeKind.HAND_OVER, removed = setOf(b)))
        assertNull(restore(WaitingChangeKind.HAND_OVER, removed = setOf(a, b)))
        assertNull(restore(WaitingChangeKind.HAND_OVER, removed = setOf(a), incoming = null))
        assertNull(restore(WaitingChangeKind.HAND_OVER, removed = setOf(a), incoming = person(EnrollmentWitness.CurrentPerson(next))))
        assertNull(restore(WaitingChangeKind.REMOVE_PERSON, removed = setOf(a)))
        assertNull(restore(WaitingChangeKind.REMOVE_PERSON, incoming = null))
        assertNull(restore(WaitingChangeKind.REMOVE_PERSON, removed = setOf(b), incoming = null))
        for (kind in WaitingChangeKind.entries.filter { it !in setOf(WaitingChangeKind.ADD_PERSON, WaitingChangeKind.HAND_OVER, WaitingChangeKind.REMOVE_PERSON) })
            assertNull(restore(kind))
        val existing = IncomingPersonEnrollment.restore(a, person().credential, "synthetic-account", checks, EnrollmentWitness.Helper)!!
        assertNull(restore(incoming = existing))
    }

    @Test fun `mode patches round trip both directions and original outgoing generations`() {
        for (current in listOf(state(), state(two = true), state(Mode.SELF_MANAGED))) {
            val p = if (current.mode == Mode.TRUSTED) ModeSwitchPatch.toSelfManaged(current, a, self(EnrollmentWitness.CurrentPerson(a)))!!
                else ModeSwitchPatch.toSupervised(current, a, person())!!
            val d = decode(p)
            assertEquals(p.sourceMode, d.sourceMode); assertEquals(p.targetMode, d.targetMode)
            assertEquals(p.starter, d.starter); assertEquals(p.structureGeneration, d.structureGeneration)
            assertEquals(p.dependencyGenerations, d.dependencyGenerations); assertEquals(p.removedSlots, d.removedSlots)
            assertEquals(p.changedSlots, d.changedSlots); assertEquals(p.incomingPerson, d.incomingPerson)
            assertTrue(d.dependenciesMatch(current))
            assertFalse(d.dependenciesMatch(current.replacing(a, credential.copy(generation = 4))))
        }
    }

    @Test fun `mode decoder rejects mismatched enrollment mode witness and outgoing slots`() {
        fun restore(mode: Mode = Mode.TRUSTED, structure: Long = 4,
            dependencies: Map<TrustedPersonId, Long> = mapOf(a to 3L),
            user: IncomingSelfManagedEnrollment? = self(), person: IncomingPersonEnrollment? = null) =
            ModeSwitchPatch.restore(a, mode, structure, dependencies, user, person)
        assertNull(restore(structure = -1)); assertNull(restore(dependencies = mapOf(a to -1L)))
        assertNull(restore(dependencies = emptyMap())); assertNull(restore(dependencies = mapOf(b to 3L)))
        assertNull(restore(dependencies = mapOf(a to 3L, b to 7L, next to 0L)))
        assertNull(restore(user = null)); assertNull(restore(person = person()))
        assertNull(restore(user = null, person = person())); assertNull(restore(mode = Mode.SELF_MANAGED))
        assertNull(restore(mode = Mode.SELF_MANAGED, dependencies = mapOf(a to 3L, b to 7L), user = null, person = person()))
        assertNull(restore(user = self(EnrollmentWitness.CurrentPerson(b))))
        val existing = IncomingSelfManagedEnrollment.restore(a, credential, "synthetic-account", selfChecks, EnrollmentWitness.Helper)!!
        assertNull(restore(user = existing))
    }

    @Test fun `restored patch collections snapshot inputs and expose immutable views`() {
        val dependencies = mutableMapOf(a to 3L); val removed = mutableSetOf(a)
        val people = TrustedPersonPatch.restore(WaitingChangeKind.HAND_OVER, a, 4, dependencies, removed, person())!!
        val mode = ModeSwitchPatch.restore(a, Mode.TRUSTED, 4, dependencies, self(), null)!!
        dependencies.clear(); removed.clear()
        assertEquals(mapOf(a to 3L), people.dependencyGenerations); assertEquals(setOf(a), people.removedSlots)
        assertEquals(mapOf(a to 3L), mode.dependencyGenerations); assertEquals(setOf(a), mode.removedSlots)
        assertFailsWith<UnsupportedOperationException> { (people.dependencyGenerations as MutableMap).clear() }
        assertFailsWith<UnsupportedOperationException> { (people.removedSlots as MutableSet).clear() }
        assertFailsWith<UnsupportedOperationException> { (mode.dependencyGenerations as MutableMap).clear() }
        assertFailsWith<UnsupportedOperationException> { (mode.changedSlots as MutableSet).clear() }
    }
}
