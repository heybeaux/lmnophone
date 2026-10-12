// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.boot

import org.plainphone.policy.PolicyPlan
import org.plainphone.policy.Restriction

/**
 * Restrictions a build leaves out of every plan it applies, both the write and the readback.
 * `PolicyEngine.decide` asks for every [Restriction]; a test build must not apply
 * DISALLOW_DEBUGGING_FEATURES (it ends adb at once) or DISALLOW_FACTORY_RESET (TEAM-POLICY: not with
 * debugging until the removal path is proven).
 *
 * [forSdk] also leaves out restrictions the live build does not have. device-lockdown applies
 * DISALLOW_CONFIG_DEFAULT_APPS only "where the API level supports it"; an older build silently
 * ignores the unknown key, so it would never read back.
 */
class RestrictionFilter(val withheld: Set<Restriction>) {
    fun apply(plan: PolicyPlan): PolicyPlan {
        if (withheld.isEmpty()) return plan
        return plan.copy(
            restrictions = plan.restrictions - withheld,
            expected = plan.expected.copy(restrictions = plan.expected.restrictions?.minus(withheld)),
        )
    }

    /**
     * True when this build withholds DISALLOW_FACTORY_RESET: it then withholds the reset-protection
     * write as well (Lead decision on PLA-92), so a recovery-mode erase never meets a reset-protection
     * policy on a test phone. See `resetProtectionFor`.
     */
    val withholdsResetProtection: Boolean get() = Restriction.DISALLOW_FACTORY_RESET in withheld

    /** This filter plus every restriction that API level [sdk] does not have. */
    fun forSdk(sdk: Int) = RestrictionFilter(withheld + unsupportedOn(sdk))

    companion object {
        /** Restrictions API level [sdk] does not have. */
        fun unsupportedOn(sdk: Int): Set<Restriction> = Restriction.entries.filterTo(mutableSetOf()) { sdk < sinceSdk(it) }

        /** The API level each key appeared in (SDK api-versions.xml). Anything at or below minSdk 30 is always there. */
        private fun sinceSdk(r: Restriction): Int = when (r) {
            Restriction.DISALLOW_CONFIG_DEFAULT_APPS -> 34
            Restriction.DISALLOW_SAFE_BOOT, Restriction.DISALLOW_FACTORY_RESET, Restriction.DISALLOW_DEBUGGING_FEATURES,
            Restriction.DISALLOW_ADD_USER, Restriction.DISALLOW_USER_SWITCH, Restriction.DISALLOW_ADD_MANAGED_PROFILE,
            Restriction.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY, Restriction.DISALLOW_USB_FILE_TRANSFER,
            Restriction.DISALLOW_CONFIG_VPN, Restriction.DISALLOW_CONFIG_PRIVATE_DNS, Restriction.DISALLOW_NETWORK_RESET,
            Restriction.DISALLOW_CONFIG_DATE_TIME, Restriction.DISALLOW_SYSTEM_ERROR_DIALOGS,
            Restriction.DISALLOW_CREATE_WINDOWS, Restriction.DISALLOW_MODIFY_ACCOUNTS,
            Restriction.DISALLOW_CONFIG_CELL_BROADCASTS -> 30
        }

        val NONE = RestrictionFilter(emptySet())
        val TEST_BUILD = RestrictionFilter(setOf(Restriction.DISALLOW_DEBUGGING_FEATURES, Restriction.DISALLOW_FACTORY_RESET))
    }
}
