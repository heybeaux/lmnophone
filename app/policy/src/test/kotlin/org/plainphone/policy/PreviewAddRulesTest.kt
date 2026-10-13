// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.junit.Assert.*
import org.junit.Test

class PreviewAddRulesTest {
    private val good = PreviewAddFacts(installed = true, launchable = true, hidden = true,
        readable = true, browserAllowed = true)
    @Test fun onlyKnownHiddenLaunchableAppsOutsideStarterAreEligible() {
        assertTrue(PreviewAppRules.canAdd("calculator", emptySet(), good))
        assertFalse(PreviewAppRules.canAdd("calculator", setOf("calculator"), good))
        assertFalse(PreviewAppRules.canAdd("calculator", emptySet(), null))
        listOf(good.copy(installed = false), good.copy(launchable = false), good.copy(hidden = false),
            good.copy(readable = false), good.copy(browserAllowed = false), good.copy(protected = true),
            good.copy(installer = true), good.copy(store = true), good.copy(home = true), good.copy(self = true)
        ).forEach { assertFalse(it.toString(), PreviewAppRules.canAdd("calculator", emptySet(), it)) }
    }
}
