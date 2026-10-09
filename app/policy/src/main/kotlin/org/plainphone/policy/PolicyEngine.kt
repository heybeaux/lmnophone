// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

/** Pure decision. Refuses any plan that touches the never-hide list. */
fun interface PolicyEngine {
    fun decide(input: PolicyInput): PolicyPlan
}
