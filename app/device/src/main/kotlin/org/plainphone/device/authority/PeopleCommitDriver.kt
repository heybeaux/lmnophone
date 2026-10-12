// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import org.plainphone.core.RunningClock
import org.plainphone.policy.PeopleChangeController
import org.plainphone.policy.PeopleChangeResult
import org.plainphone.policy.PeopleResetProtectionEffects
import org.plainphone.policy.PersonAuthorityConsumer
import org.plainphone.policy.WaitingPhase

/**
 * The directed callback for people changes (policy README, "People forward commit and fresh-PIN
 * rollback"), on the one authority [store]. Call it only from the common serialized dispatcher.
 *
 * [drive] runs one `PeopleChangeController.tick` for a people change that is COMMITTING or ROLLING_BACK,
 * in the direction the record holds now. The request ID and direction come from the same fresh read,
 * and the controller re-checks both, so a stale forward step can never resume a rollback. A failure
 * keeps the record where it is (old holders while committing) and the next pass retries.
 *
 * Pending waits belong to [WaitingCheckpoints]; turn-off and mode switches are not driven here.
 */
class PeopleCommitDriver(
    private val store: JointAuthorityStore,
    effects: PeopleResetProtectionEffects,
    clock: RunningClock,
    authority: PersonAuthorityConsumer,
) {
    private val controller = PeopleChangeController(
        store.waitingStore, store.credentials, store.peopleCommitter, effects, clock, authority,
    )

    /** One step of a committing or rolling-back people change; null when there is none. */
    fun drive(): PeopleChangeResult? {
        val op = store.waitingStore.read()?.operation ?: return null
        if (op.peoplePatch == null) return null
        if (op.phase != WaitingPhase.COMMITTING && op.phase != WaitingPhase.ROLLING_BACK) return null
        return controller.tick(op.identity.requestId, op.phase)
    }
}
