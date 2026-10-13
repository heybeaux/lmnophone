// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.junit.Assert.*
import org.junit.Test

class PreviewAppRulesTest {
    private val floor = setOf("phone", "sms", "home", "keyboard", "settings", "sos")
    @Test fun removalPreservesEveryFloorEntry() {
        val starter = floor + "calculator"
        floor.forEach { assertNull(PreviewAppRules.remove(starter, it, floor)) }
        assertEquals(floor, PreviewAppRules.remove(starter, "calculator", floor))
    }
    @Test fun moveCannotMoveOrCrossFixedRows() {
        val order = listOf("camera", "calculator", "sms", "phone")
        assertEquals(listOf("calculator", "camera", "sms", "phone"), PreviewAppRules.move(order, "calculator", -1, floor))
        assertNull(PreviewAppRules.move(order, "sms", -1, floor))
        assertNull(PreviewAppRules.move(order, "calculator", 1, floor))
        assertNull(PreviewAppRules.move(order, "camera", -1, floor))
    }
}
