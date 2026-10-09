// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.plainphone.policy.PolicyPlan

/** Applies a [PolicyPlan] through DevicePolicyManager in its apply order, reading back each step. */
fun interface PolicyApplier {
    fun apply(plan: PolicyPlan): Boolean
}
