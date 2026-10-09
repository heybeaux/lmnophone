// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class UpdateCommitPolicyTest {
    private val person = TrustedPersonId("self")
    private val self = ApprovalBinding(PackageName("test.plainphone"), setOf(CertDigest("ab".repeat(32))))
    private val staged = StagedUpdateBinding("cd".repeat(32), 2)
    private val facts = StagedApkFacts(self, staged, signatureValid = true)
    private val now = ClockReading(RunningTime("epoch", 600_000), BootTime("boot", 600_000))
    private val credential = PersonCredential(SecretVerifier(ByteArray(16), ByteArray(32), 600_000),
        SecretVerifier(ByteArray(16), ByteArray(32), 600_000))
    private val credentials = CredentialState(Mode.SELF_MANAGED, mapOf(person to credential))
    private val state = ElevationState(ElevationLedger(listOf(ElevationDebit("grant", now.proven))),
        grant = ElevationGrant("grant", ElevationTarget.Update(staged), now.boot, BootTime("boot", 1_200_000), false))
    private val record = ElevationRecord(state = state, person = ElevationPersonBinding(person, 0, 0))
    private fun check(facts: StagedApkFacts? = this.facts, credentials: CredentialState? = this.credentials,
        reading: ClockReading? = now, record: ElevationRecord = this.record, installedVersion: Long = 1,
        id: String = "grant") = UpdateCommitPolicy.propose(record, id, credentials, self, installedVersion, facts, reading)

    @Test fun `exact freshly verified bytes package signer and version propose one use without mutating storage`() {
        val result = check()
        assertEquals(ElevationOutcome.CONSUMED, result.outcome); assertNull(result.state.grant)
        assertSame(state.ledger, result.state.ledger); assertSame(state, record.state)
        val consumed = ElevationRecord(1, result.state)
        assertEquals(ElevationOutcome.REFUSED, check(record = consumed).outcome)
    }
    @Test fun `changed staged bytes version package signer and invalid signature refuse`() {
        val wrong = listOf(
            facts.copy(binding = staged.copy(sha256 = "ef".repeat(32))),
            facts.copy(binding = staged.copy(versionCode = 3)),
            facts.copy(identity = ApprovalBinding(PackageName("test.other"), self.signingCerts)),
            facts.copy(identity = ApprovalBinding(self.packageName, setOf(CertDigest("ef".repeat(32))))),
            facts.copy(signatureValid = false),
        )
        for (changed in wrong) assertEquals(ElevationOutcome.REFUSED, check(facts = changed).outcome)
        assertEquals(ElevationOutcome.REFUSED, check(facts = null).outcome)
        assertEquals(ElevationOutcome.REFUSED, check(installedVersion = 2).outcome)
        assertEquals(ElevationOutcome.REFUSED, check(installedVersion = 3).outcome)
    }
    @Test fun `wrong grant kind and stale identifier refuse even with correct staged bytes`() {
        assertEquals(ElevationOutcome.REFUSED, check(id = "old").outcome)
        for (target in listOf(ElevationTarget.Play, ElevationTarget.Settings, ElevationTarget.Approve(self))) {
            val other = ElevationRecord(state = ElevationState(state.ledger,
                grant = ElevationGrant("grant", target, now.boot, BootTime("boot", 1_200_000), false)), person = record.person)
            assertEquals(ElevationOutcome.REFUSED, check(record = other).outcome)
        }
    }
    @Test fun `final commit checks strict deadline boot and elapsed rollback after staging delay`() {
        assertEquals(ElevationOutcome.CONSUMED, check(reading = now.copy(boot = BootTime("boot", 1_199_999))).outcome)
        for (time in listOf(BootTime("boot", 1_200_000), BootTime("reboot", 1), BootTime("boot", 599_999))) {
            assertEquals(ElevationOutcome.LAPSED, check(reading = now.copy(boot = time)).outcome)
        }
        assertEquals(ElevationOutcome.NOT_READY, check(reading = null).outcome)
    }
    @Test fun `credential mode generation structure and missing state refuse`() {
        for (changed in listOf(null, CredentialState(Mode.TRUSTED, credentials.people),
            CredentialState(Mode.SELF_MANAGED, mapOf(person to credential.copy(generation = 1))),
            CredentialState(Mode.SELF_MANAGED, credentials.people, structureGeneration = 1))) {
            assertEquals(ElevationOutcome.REFUSED, check(credentials = changed).outcome)
        }
    }
    @Test fun `epoch repair keeps fixed deadline and used debit while restart revokes proposal`() {
        val result = check(reading = now.copy(proven = RunningTime("repaired", 0)))
        assertEquals(ElevationOutcome.CONSUMED, result.outcome); assertSame(state.ledger, result.state.ledger)
        assertEquals(ElevationOutcome.REFUSED, check(record = ElevationRecord(state = ElevationMachine.onRestart(state))).outcome)
    }
}
