// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import org.plainphone.policy.ModeSwitchEffects
import org.plainphone.policy.PeopleResetProtectionEffects

/**
 * The Android [ModeSwitchEffects] (PLA-97). The policy calls them in order: [revokeTemporaryAuthority],
 * then [readBackProtectedState], then the reset-protection steps; the swap is written only after all
 * of them succeed. Every call is synchronous and idempotent, and any exception is a failed step.
 *
 * - [revokeTemporaryAuthority] runs [revoke], the common startup revocation: it closes every session
 *   relaxation and re-applies the protected plan. The policy has already cleared the elevation record
 *   durably and kept the ledger; this never changes credentials, the waiting record or the ledger.
 * - [readBackProtectedState] runs [readBack] as a [JointAuthorityStore.androidEffect], so it can never
 *   interleave with a store write: it reads the current protected plan back and writes nothing.
 * - The reset-protection steps are [reset], the same effect as lost-record reconciliation and people
 *   changes, so test builds withhold the platform write there (PLA-92 wiring 6).
 */
class DeviceModeSwitchEffects(
    private val revoke: () -> Boolean,
    private val readBack: () -> Boolean,
    private val reset: PeopleResetProtectionEffects,
    private val store: JointAuthorityStore,
) : ModeSwitchEffects, PeopleResetProtectionEffects by reset {
    override fun revokeTemporaryAuthority(): Boolean = attempt { revoke() }
    override fun readBackProtectedState(): Boolean = attempt { store.androidEffect { readBack() } }

    private inline fun attempt(call: () -> Boolean): Boolean = try { call() } catch (_: Exception) { false }
}
