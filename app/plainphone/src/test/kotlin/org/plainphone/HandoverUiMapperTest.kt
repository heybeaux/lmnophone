// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.policy.*
import org.plainphone.ui.trusted.*

class HandoverUiMapperTest {
    @Test fun productionMapperMapsEveryItemAndEveryState() {
        for (target in HandoverItem.entries) for (value in listOf(true, false, null)) {
            val report = HandoverCheck.read { if (it == target) value else true }
            val ui = handoverUiState(report)
            assertEquals(5, ui.items.size)
            assertEquals(report.needsHelper, ui.needsHelper)
            for (item in HandoverItem.entries) {
                assertEquals(item.name, ui.items[item.ordinal].kind.name)
                assertEquals(report.items.getValue(item).name, ui.items[item.ordinal].state.name)
            }
        }
    }
    @Test fun productionMenuOffersTheReadOnlyPage() {
        assertEquals(PinMenuTarget.CheckPhone, pinMenuTarget(AdminDestination.CheckPhone))
        assertEquals(1, pinMenuUiState(AdminContext("Alex")).items.count { it.destination == AdminDestination.CheckPhone })
    }
}
