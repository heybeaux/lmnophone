// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.links

import org.plainphone.core.PackageName

sealed interface LinkDecision {
    data class OpenIn(val packageName: PackageName) : LinkDecision

    data object BlockedScreen : LinkDecision

    data object NoHandler : LinkDecision
}

/** Decides where a link goes. `approved` includes the lock-task UI exceptions. */
fun interface LinkDecider {
    fun decide(intent: IntentFacts, approved: Set<PackageName>): LinkDecision
}
