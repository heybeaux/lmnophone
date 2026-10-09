// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class TrustedPersonPatchTest {
    private val first = TrustedPersonId("first-slot")
    private val second = TrustedPersonId("second-slot")
    private val incoming = TrustedPersonId("incoming-slot")
    private val friction = FrictionSettings(600_000, 3)
    private val pin get() = CharArray(6) { '1' }
    private var credentials: CredentialState? = null
    private var sequence = 0
    private val vault = PinVault(object : CredentialStore {
        override fun read() = credentials
        override fun compareAndSet(expected: CredentialState, replacement: CredentialState): Boolean {
            if (credentials !== expected) return false
            credentials = replacement
            return true
        }
    }, KeyDerivation { chars, salt, _ ->
        java.security.MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
    }, SecretEntropy { count -> ByteArray(count) { (++sequence).toByte() } },
        RunningClock { ClockReading(RunningTime("epoch", 0), BootTime("boot", 0)) })
    private val confirmations = EnrollmentConfirmations(
        inPerson = true, nameAndNumberEntered = true, ownPinSet = true, recoveryCodeReceived = true,
        resetProtectionIdFromOwnDevice = true, testTextReceived = true, offDeviceSignInChecked = true,
        independentSecondFactorChecked = true, accountAtLeast72HoursOld = true,
    )
    private fun state(two: Boolean = true, mode: Mode = Mode.TRUSTED): CredentialState {
        val people = linkedMapOf(first to vault.prepareCredential(pin).credential.copy(generation = 3,
            contact = PersonContact("synthetic-first-name", "synthetic-first-number")))
        if (two) people[second] = vault.prepareCredential(pin).credential.copy(generation = 7,
            contact = PersonContact("synthetic-second-name", "synthetic-second-number"))
        return CredentialState(mode, people, revision = 10, structureGeneration = 4,
            pendingNotices = setOf(LockoutNotice(first, CredentialChannel.PIN, 8)),
            recoveryProfile = RecoveryProfile(people.keys.associateWith { "synthetic-${it.value}-account" },
                "synthetic-backup-account", friction)).also { credentials = it }
    }
    private fun enroll(person: TrustedPersonId = incoming, checks: EnrollmentConfirmations = confirmations,
        witness: EnrollmentWitness = EnrollmentWitness.Helper, prepared: PreparedCredential = vault.prepareCredential(pin)) =
        IncomingPersonEnrollment.confirm(person, PersonContact("synthetic-incoming-name", "synthetic-incoming-number"),
            "synthetic-incoming-account", prepared, checks, witness)
    private fun replaceGeneration(current: CredentialState, person: TrustedPersonId): CredentialState =
        current.replacing(person, current.people.getValue(person).copy(generation = current.people.getValue(person).generation + 1))

    @Test fun `each enrollment confirmation is mandatory before producing a patch input`() {
        val missing = listOf(
            confirmations.copy(inPerson = false), confirmations.copy(nameAndNumberEntered = false),
            confirmations.copy(ownPinSet = false), confirmations.copy(recoveryCodeReceived = false),
            confirmations.copy(resetProtectionIdFromOwnDevice = false), confirmations.copy(testTextReceived = false),
            confirmations.copy(offDeviceSignInChecked = false), confirmations.copy(independentSecondFactorChecked = false),
            confirmations.copy(accountAtLeast72HoursOld = false),
        )
        missing.forEach { assertNull(enroll(checks = it)) }
        assertNotNull(enroll())
    }

    @Test fun `enrollment retains all attestations contact and account without recovery plaintext`() {
        val prepared = vault.prepareCredential(pin)
        val enrolled = enroll(prepared = prepared)!!
        assertSame(confirmations, enrolled.confirmations)
        assertEquals(EnrollmentWitness.Helper, enrolled.witness)
        assertEquals("synthetic-incoming-name", enrolled.credential.contact!!.name)
        assertEquals("synthetic-incoming-number", enrolled.credential.contact!!.number)
        assertEquals("synthetic-incoming-account", enrolled.resetProtectionUserId)
        assertSame(prepared.credential.pin, enrolled.credential.pin)
        assertSame(prepared.credential.recovery, enrolled.credential.recovery)
        val diagnostics = listOf(enrolled.toString(), enrolled.credential.toString(), enrolled.credential.contact.toString())
        diagnostics.forEach { text ->
            listOf("synthetic-incoming-name", "synthetic-incoming-number", "synthetic-incoming-account", prepared.recoveryCode)
                .forEach { assertFalse(text.contains(it)) }
        }
    }

    @Test fun `blank contact and malformed account are refused`() {
        assertFailsWith<IllegalArgumentException> { PersonContact(" ", "synthetic-number") }
        assertFailsWith<IllegalArgumentException> { PersonContact("synthetic-name", " ") }
        for (account in listOf("", " ", "synthetic account")) {
            assertNull(IncomingPersonEnrollment.confirm(incoming, PersonContact("synthetic-name", "synthetic-number"),
                account, vault.prepareCredential(pin), confirmations, EnrollmentWitness.Helper))
        }
    }

    @Test fun `enrollment accepts only a fresh credential with unused independent counters`() {
        val prepared = vault.prepareCredential(pin)
        for (credential in listOf(prepared.credential.copy(generation = 1),
            prepared.credential.copy(pinCounter = FailureCounter(failures = 1)),
            prepared.credential.copy(recoveryCounter = FailureCounter(failures = 1)))) {
            assertNull(enroll(prepared = PreparedCredential(credential, prepared.recoveryCode)))
        }
    }

    @Test fun `pending credentials and code grant nothing even with same PIN as active holder`() {
        val current = state(false)
        val prepared = vault.prepareCredential(pin)
        assertNotNull(TrustedPersonPatch.add(current, first, enroll(prepared = prepared)!!))
        val action = PrivilegedAction("test", "pending-is-not-authority")
        assertIs<PinResult.Unavailable>(vault.authenticate(incoming, pin, action))
        assertIs<RecoveryResult.Unavailable>(vault.recover(incoming, prepared.recoveryCode.toCharArray(), pin))
        assertSame(current, credentials)
        assertIs<PinResult.Authorized>(vault.authenticate(first, pin, action))
    }

    @Test fun `add captures only starter generation and immutable new slot`() {
        val current = state(false)
        val enrolled = enroll()!!
        val patch = TrustedPersonPatch.add(current, first, enrolled)!!
        assertEquals(WaitingChangeKind.ADD_PERSON, patch.kind)
        assertEquals(first, patch.starter)
        assertEquals(4L, patch.structureGeneration)
        assertEquals(mapOf(first to 3L), patch.dependencyGenerations)
        assertEquals(setOf(incoming), patch.changedSlots)
        assertTrue(patch.removedSlots.isEmpty())
        assertSame(enrolled, patch.incoming)
        assertTrue(patch.dependenciesMatch(current))
        assertSame(current, credentials)
    }

    @Test fun `add refuses a third person a current identity or an unknown starter`() {
        assertNull(TrustedPersonPatch.add(state(), first, enroll()!!))
        val current = state(false)
        assertNull(TrustedPersonPatch.add(current, first, enroll(first)!!))
        assertNull(TrustedPersonPatch.add(current, second, enroll()!!))
    }

    @Test fun `handover binds outgoing person as starter and never the other holder`() {
        val patch = TrustedPersonPatch.handOver(state(), first, enroll()!!)!!
        assertEquals(WaitingChangeKind.HAND_OVER, patch.kind)
        assertEquals(first, patch.starter)
        assertEquals(mapOf(first to 3L), patch.dependencyGenerations)
        assertEquals(setOf(first), patch.removedSlots)
        assertEquals(setOf(first, incoming), patch.changedSlots)
    }

    @Test fun `handover rejects an unknown outgoing person and any existing incoming identity`() {
        val current = state()
        assertNull(TrustedPersonPatch.handOver(current, incoming, enroll()!!))
        assertNull(TrustedPersonPatch.handOver(current, first, enroll(first)!!))
        assertNull(TrustedPersonPatch.handOver(current, first, enroll(second)!!))
    }

    @Test fun `removal depends on both starter and removed person`() {
        val current = state()
        val patch = TrustedPersonPatch.remove(current, first, second)!!
        assertEquals(WaitingChangeKind.REMOVE_PERSON, patch.kind)
        assertEquals(mapOf(first to 3L, second to 7L), patch.dependencyGenerations)
        assertEquals(setOf(second), patch.changedSlots)
        assertEquals(setOf(second), patch.removedSlots)
        assertNull(patch.incoming)
        assertFalse(patch.dependenciesMatch(replaceGeneration(current, first)))
        assertFalse(patch.dependenciesMatch(replaceGeneration(current, second)))
    }

    @Test fun `removing self keeps the other person and depends on just the departing slot`() {
        val current = state()
        val patch = TrustedPersonPatch.remove(current, first, first)!!
        assertEquals(mapOf(first to 3L), patch.dependencyGenerations)
        assertTrue(patch.dependenciesMatch(replaceGeneration(current, second)))
        assertEquals(setOf(second), patch.replacementForCommit(current)!!.people.keys)
    }

    @Test fun `last person removal and nonexistent starter or target are refused`() {
        assertNull(TrustedPersonPatch.remove(state(false), first, first))
        val current = state()
        assertNull(TrustedPersonPatch.remove(current, incoming, first))
        assertNull(TrustedPersonPatch.remove(current, first, incoming))
    }

    @Test fun `all people changes reject self managed mode and absent recovery profile`() {
        for (current in listOf(state(false, Mode.SELF_MANAGED), CredentialState(Mode.TRUSTED, state().people))) {
            assertNull(TrustedPersonPatch.add(current, first, enroll()!!))
            assertNull(TrustedPersonPatch.handOver(current, first, enroll()!!))
            assertNull(TrustedPersonPatch.remove(current, first, second))
        }
    }

    @Test fun `account checks may be recorded by helper or a current trusted person only`() {
        val current = state(false)
        assertNotNull(TrustedPersonPatch.add(current, first, enroll(witness = EnrollmentWitness.CurrentPerson(first))!!))
        assertNotNull(TrustedPersonPatch.add(current, first, enroll(witness = EnrollmentWitness.Helper)!!))
        assertNull(TrustedPersonPatch.add(current, first, enroll(witness = EnrollmentWitness.CurrentPerson(incoming))!!))
    }

    @Test fun `dependency changes reject pending patch but unrelated credentials and counters do not`() {
        val current = state()
        val patch = TrustedPersonPatch.handOver(current, first, enroll()!!)!!
        assertFalse(patch.dependenciesMatch(replaceGeneration(current, first)))
        assertTrue(patch.dependenciesMatch(replaceGeneration(current, second)))
        val counterOnly = current.replacing(first, current.people.getValue(first).copy(pinCounter = FailureCounter(5, 30_000)))
        assertTrue(patch.dependenciesMatch(counterOnly))
        val changedStructure = CredentialState(current.mode, current.people, 11, 5, recoveryProfile = current.recoveryProfile)
        assertFalse(patch.dependenciesMatch(changedStructure))
        assertFalse(patch.dependenciesMatch(state(false, Mode.SELF_MANAGED)))
    }

    @Test fun `handover patch preserves unrelated latest credentials counters contact notices backup and friction`() {
        val current = state()
        val patch = TrustedPersonPatch.handOver(current, first, enroll()!!)!!
        val newerCredential = current.people.getValue(second).copy(generation = 8,
            pinCounter = FailureCounter(6, 60_000), recoveryCounter = FailureCounter(5, 30_000))
        val latest = current.replacing(second, newerCredential, CredentialChannel.RECOVERY)
        val result = patch.replacementForCommit(latest)!!
        assertEquals(setOf(second, incoming), result.people.keys)
        assertSame(newerCredential, result.people.getValue(second))
        assertEquals(latest.pendingNotices, result.pendingNotices)
        assertEquals(latest.revision + 1, result.revision)
        assertEquals(latest.structureGeneration + 1, result.structureGeneration)
        assertEquals(latest.mode, result.mode)
        assertSame(friction, result.recoveryProfile!!.friction)
        assertEquals("synthetic-backup-account", result.recoveryProfile!!.backupUserId)
        assertEquals("synthetic-second-slot-account", result.recoveryProfile!!.holderUserIds[second])
        assertEquals("synthetic-incoming-account", result.recoveryProfile!!.holderUserIds[incoming])
        assertSame(patch.incoming!!.credential, result.people[incoming])
        assertSame(current, credentials) // Calculation is not a store write or authority to commit.
    }

    @Test fun `future reset protection includes surviving incoming and backup accounts`() {
        val current = state()
        val patch = TrustedPersonPatch.handOver(current, first, enroll()!!)!!
        val future = patch.replacementForCommit(current)!!.recoveryProfile!!.resetProtectionUserIds
        assertEquals(setOf("synthetic-second-slot-account", "synthetic-incoming-account", "synthetic-backup-account"), future)
        assertEquals(setOf(first, second), current.people.keys)
    }

    @Test fun `add preserves existing holder and removal preserves remaining holder`() {
        val current = state(false)
        val added = TrustedPersonPatch.add(current, first, enroll()!!)!!.replacementForCommit(current)!!
        assertSame(current.people[first], added.people[first])
        assertEquals(setOf(first, incoming), added.people.keys)
        val removed = TrustedPersonPatch.remove(added, first, incoming)!!.replacementForCommit(added)!!
        assertSame(added.people[first], removed.people[first])
        assertEquals(setOf(first), removed.people.keys)
        assertEquals(setOf(first), removed.recoveryProfile!!.holderUserIds.keys)
        assertEquals(setOf("synthetic-first-slot-account", "synthetic-backup-account"), removed.recoveryProfile!!.resetProtectionUserIds)
    }

    @Test fun `credential replacement after commit decision does not block patch`() {
        val current = state()
        val patch = TrustedPersonPatch.handOver(current, first, enroll()!!)!!
        val changed = replaceGeneration(current, first)
        assertFalse(patch.dependenciesMatch(changed))
        assertNotNull(patch.replacementForCommit(changed))
    }

    @Test fun `replay after applied patch cannot restore incoming setup PIN or raise structure twice`() {
        val current = state()
        val patch = TrustedPersonPatch.handOver(current, first, enroll()!!)!!
        val applied = patch.replacementForCommit(current)!!
        val recovered = replaceGeneration(applied, incoming)
        assertNull(patch.replacementForCommit(recovered))
        assertEquals(5L, recovered.structureGeneration)
        assertEquals(1L, recovered.people.getValue(incoming).generation)
    }

    @Test fun `commit calculation rejects unexpected mode structure missing profile and overflow`() {
        val current = state()
        val patch = TrustedPersonPatch.handOver(current, first, enroll()!!)!!
        assertNull(patch.replacementForCommit(state(false, Mode.SELF_MANAGED)))
        assertNull(patch.replacementForCommit(CredentialState(current.mode, current.people, 11, 5, recoveryProfile = current.recoveryProfile)))
        assertNull(patch.replacementForCommit(CredentialState(current.mode, current.people, 11, 4)))
        assertNull(patch.replacementForCommit(CredentialState(current.mode, current.people, Long.MAX_VALUE, 4, recoveryProfile = current.recoveryProfile)))
        val maxStructure = CredentialState(current.mode, current.people, 11, Long.MAX_VALUE, recoveryProfile = current.recoveryProfile)
        assertNull(TrustedPersonPatch.handOver(maxStructure, first, enroll()!!)!!.replacementForCommit(maxStructure))
    }

    @Test fun `dependency and changed slot views cannot be mutated`() {
        val patch = TrustedPersonPatch.handOver(state(), first, enroll()!!)!!
        assertFailsWith<UnsupportedOperationException> { (patch.dependencyGenerations as MutableMap).clear() }
        assertFailsWith<UnsupportedOperationException> { (patch.changedSlots as MutableSet).clear() }
        assertFailsWith<UnsupportedOperationException> { (patch.removedSlots as MutableSet).clear() }
        assertEquals(mapOf(first to 3L), patch.dependencyGenerations)
    }

    @Test fun `credential recovery retains holder contact`() {
        val prepared = vault.prepareCredential(pin)
        val contact = PersonContact("synthetic-name", "synthetic-number")
        credentials = CredentialState(Mode.TRUSTED, mapOf(first to prepared.credential.copy(contact = contact)))
        assertIs<RecoveryResult.Replaced>(vault.recover(first, prepared.recoveryCode.toCharArray(), pin))
        assertSame(contact, credentials!!.people.getValue(first).contact)
    }
}
