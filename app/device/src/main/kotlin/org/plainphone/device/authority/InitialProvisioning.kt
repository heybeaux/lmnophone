// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.util.UUID
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.SessionJournal
import org.plainphone.policy.WaitingState

/** Result of [InitialProvisioning.provision]. */
enum class ProvisionOutcome {
    /** This setup's genesis reads back: the credentials, no waiting operation and an empty debit ledger. */
    CREATED,
    /** A record or a fence already exists, readable or damaged. Nothing was written; never set up over it. */
    EXISTING,
    /** Nothing was acknowledged and nothing reads back. Run setup again later with the same credentials. */
    UNAVAILABLE,
    /** The credentials are not a first record (revision 0, no applied change, no notices). Nothing was written. */
    REFUSED,
}

/**
 * Initial setup's one write to the joint authority record (PLA-97). In both supervised and
 * self-managed modes it creates the combined elevation record with an EMPTY debit ledger, together
 * with the credentials and an empty waiting record, so a later switch or restart always finds a
 * ledger to keep. This is initial provisioning only: it never writes over an existing or damaged
 * record, so restart, migration and switching can never reset debits. A missing record in normal
 * operation keeps its conservative recovery; it is not an invitation to provision.
 *
 * Serialized with every other store write. Setup may call it again with the same credentials after
 * any outcome: a genesis that already reads back is [ProvisionOutcome.CREATED] without a write.
 */
class InitialProvisioning(private val store: JointAuthorityStore) {
    fun provision(credentials: CredentialState, storageGeneration: String = UUID.randomUUID().toString()): ProvisionOutcome {
        if (credentials.revision != 0L || credentials.appliedPeopleChange != null || credentials.pendingNotices.isNotEmpty()) {
            return ProvisionOutcome.REFUSED
        }
        val expected = AuthorityCodec.encodeCredentials(credentials) ?: return ProvisionOutcome.REFUSED
        return store.serialized(ProvisionOutcome.UNAVAILABLE) {
            when {
                isGenesis(expected) -> ProvisionOutcome.CREATED
                !store.vacant() -> ProvisionOutcome.EXISTING
                else -> {
                    // False is not acknowledged: decide only from what reads back.
                    store.initialize(credentials, WaitingState(storageGeneration = storageGeneration), ElevationRecord())
                    if (isGenesis(expected)) ProvisionOutcome.CREATED else ProvisionOutcome.UNAVAILABLE
                }
            }
        }
    }

    private fun isGenesis(credentials: ByteArray): Boolean {
        val saved = store.credentials.read()?.let(AuthorityCodec::encodeCredentials) ?: return false
        val waiting = store.waitingStore.read() ?: return false
        val elevation = store.elevationStore.read() ?: return false
        return saved.contentEquals(credentials) && waiting.revision == 0L && waiting.operation == null &&
            elevation.revision == 0L && elevation.state.ledger.debits.isEmpty() && elevation.state.countdown == null &&
            elevation.state.grant == null && elevation.person == null && elevation.journal == SessionJournal.Empty
    }
}
