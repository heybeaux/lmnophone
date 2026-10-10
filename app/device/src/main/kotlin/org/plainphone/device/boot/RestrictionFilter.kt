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
 */
class RestrictionFilter(val withheld: Set<Restriction>) {
    fun apply(plan: PolicyPlan): PolicyPlan {
        if (withheld.isEmpty()) return plan
        return plan.copy(
            restrictions = plan.restrictions - withheld,
            expected = plan.expected.copy(restrictions = plan.expected.restrictions?.minus(withheld)),
        )
    }

    companion object {
        val NONE = RestrictionFilter(emptySet())
        val TEST_BUILD = RestrictionFilter(setOf(Restriction.DISALLOW_DEBUGGING_FEATURES, Restriction.DISALLOW_FACTORY_RESET))
    }
}
