// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.junit.Assert.*
import org.junit.Test

class HandoverCheckTest {
    @Test fun everyCombinationCountsFailuresAndUnknownsAndNeverMarksReady() {
        repeat(243) { combination ->
            var rest = combination
            val facts = HandoverItem.entries.associateWith { (rest % 3).also { rest /= 3 } }
            val report = HandoverCheck.read { when (facts.getValue(it)) { 0 -> true; 1 -> false; else -> null } }
            assertEquals(facts.values.count { it != 0 }, report.needsHelper)
            assertFalse(report.ready)
            assertEquals(HandoverItem.entries, report.items.keys.toList())
            for ((item, fact) in facts) assertEquals(when (fact) {
                0 -> HandoverState.OK; 1 -> HandoverState.NEEDS_HELPER; else -> HandoverState.COULD_NOT_CHECK
            }, report.items[item])
        }
    }
    @Test fun throwingReaderDoesNotPreventTheOtherChecks() {
        val report = HandoverCheck.read { if (it == HandoverItem.EMERGENCY) error("unavailable") else true }
        assertEquals(HandoverState.COULD_NOT_CHECK, report.items[HandoverItem.EMERGENCY])
        assertEquals(1, report.needsHelper)
        assertEquals(4, report.items.values.count { it == HandoverState.OK })
    }
}
