// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.BuildInventory
import org.plainphone.core.Mode
import org.plainphone.core.NeverHideList
import kotlin.test.Test
import kotlin.test.assertEquals

class PolicyEngineTest {
    @Test
    fun aPolicyEngineIsAPureFunctionOfItsInput() {
        val empty = PolicyPlan(emptySet(), emptyList(), emptySet(), emptySet(), emptyList())
        val engine = PolicyEngine { empty }
        val input = PolicyInput(Mode.TRUSTED, BuildInventory("m", "f", NeverHideList(emptySet()), emptySet()), emptySet(), emptySet())
        assertEquals(empty, engine.decide(input))
    }
}
