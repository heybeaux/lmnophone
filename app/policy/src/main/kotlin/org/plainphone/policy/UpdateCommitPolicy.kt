// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

/**
 * Adapter observations of the exact immutable bytes bound to the installer session, obtained again
 * at its final commit boundary. The adapter verifies the APK signature and hashes the actual bytes.
 * These are facts, not an authorization capability; never accept them from UI/intent parameters.
 */
data class StagedApkFacts(val identity: ApprovalBinding, val binding: StagedUpdateBinding, val signatureValid: Boolean)

/**
 * Pure final-boundary proposal for the updater owner. Does NOT establish process ownership, consume
 * durably or call an installer. Even CONSUMED means only "proposed consumed state". A runtime adapter
 * must additionally prove this controller owns the live PIN-bound grant, compare durable revisions,
 * and join use-up to the actual installer commit without an intervening retry/yield. An intent record
 * alone is insufficient. ElevationController deliberately refuses Update until that integration lands.
 */
object UpdateCommitPolicy {
    fun propose(record: ElevationRecord, ownedGrantId: String, credentials: CredentialState?,
        pinnedSelf: ApprovalBinding, installedVersionCode: Long, staged: StagedApkFacts?,
        now: org.plainphone.core.ClockReading?): ElevationTransition {
        val grant = record.state.grant
        if (grant?.target !is ElevationTarget.Update || grant.id != ownedGrantId ||
            record.person?.matches(credentials) != true || record.journal != SessionJournal.Empty ||
            grant.packageAccessOpen || staged == null || !staged.signatureValid ||
            staged.identity != pinnedSelf || installedVersionCode < 0 ||
            staged.binding.versionCode <= installedVersionCode) return ElevationTransition(record.state, ElevationOutcome.REFUSED)
        return ElevationMachine.consume(record.state, ownedGrantId, ElevationAction.Update(staged.binding), now)
    }
}
