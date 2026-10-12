// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import org.plainphone.core.RunningClock
import org.plainphone.device.boot.BootOutcome
import org.plainphone.policy.ModeSwitchEffects
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
 * Only after startup (PLA-390 R1): [driveAfter] takes the common startup's [BootOutcome] and runs the step
 * only when it is ready, which means its session revocation succeeded. A failed or missing startup
 * runs nothing; its own emergency repair still ran, and the next pass retries.
 *
 * Mode switches (PLA-97) are driven only when [effects] is a [org.plainphone.policy.ModeSwitchEffects]
 * such as [DeviceModeSwitchEffects]: the controller then commits through [JointAuthorityStore.modeCommitter],
 * which passes people changes to the same people committer. With plain reset-protection effects a mode
 * switch is left alone. Nothing here starts or skips a switch.
 *
 * Pending waits belong to [WaitingCheckpoints]; turn-off is not driven here.
 */
class PeopleCommitDriver(
    private val store: JointAuthorityStore,
    effects: PeopleResetProtectionEffects,
    clock: RunningClock,
    authority: PersonAuthorityConsumer,
) {
    private val modes = effects is ModeSwitchEffects
    private val controller = PeopleChangeController(
        store.waitingStore, store.credentials, if (modes) store.modeCommitter else store.peopleCommitter, effects, clock, authority,
    )

    /** One step after a ready [startup]; null when the startup was not ready or there is no such change. */
    fun driveAfter(startup: BootOutcome?): PeopleChangeResult? = if (startup?.ready == true) drive() else null

    /** One step of a committing or rolling-back people change or mode switch; null when there is none. Callers go through [driveAfter]. */
    internal fun drive(): PeopleChangeResult? {
        val op = store.waitingStore.read()?.operation ?: return null
        if (op.peoplePatch == null && (op.modePatch == null || !modes)) return null
        if (op.phase != WaitingPhase.COMMITTING && op.phase != WaitingPhase.ROLLING_BACK) return null
        return controller.tick(op.identity.requestId, op.phase)
    }
}
