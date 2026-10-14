// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.ui.trusted.*

class PinScreenMapperTest {
    private fun map(page: PinPageState) = pinUiState(page, AdminContext("Alex"), "abcdef", "", "synthetic", "30 seconds", "1 minute", "Turn LMNOPhone off")
    @Test fun productionMapsRecoveryEntryWrongCodeWaitsAndOneTimeDisplay() {
        val wrong = map(PinPageState(PinPage.RECOVERY, wrong = true))
        assertEquals(PinStage.Recovery, wrong.stage)
        assertTrue(wrong.wrongRecoveryCode); assertFalse(wrong.wrongPin); assertTrue(wrong.canContinue)
        assertEquals("", wrong.recoveryCode); assertNotNull(wrong.pendingChange)
        val locked = map(PinPageState(PinPage.RECOVERY_LOCKED, pinWaitMillis = 60_000))
        assertEquals(PinStage.RecoveryLocked, locked.stage)
        assertEquals(RecoveryLockoutKind.PinWaiting, locked.recoveryLockout)
        assertEquals("1 minute", locked.pinWait); assertEquals("30 seconds", locked.wait)
        assertEquals(RecoveryLockoutKind.PinAvailable, map(PinPageState(PinPage.RECOVERY_LOCKED)).recoveryLockout)
        assertEquals(PinStage.NewPin, map(PinPageState(PinPage.RECOVERY_NEW)).stage)
        assertEquals(PinStage.Enter, map(PinPageState(PinPage.RECOVERY_PIN)).stage)
        val issued = map(PinPageState(PinPage.RECOVERY_ISSUED))
        assertEquals(PinStage.RecoveryIssued, issued.stage); assertEquals("synthetic", issued.recoveryCode)
        assertFalse(issued.toString().contains("synthetic"))
    }
    @Test fun productionMenuRequestDetailHasPositiveCountOnly() {
        for (count in listOf(null, 0, 1, 12)) {
            val row = pinMenuUiState(AdminContext("Alex"), count).items.single { it.destination == AdminDestination.Requests }
            assertEquals(if (count != null && count > 0) count.toString() else "", row.detail)
        }
    }
    @Test fun productionMenuExposesNewCodeRow() {
        assertEquals(1, pinMenuUiState(AdminContext("Alex")).items.count { it.destination == AdminDestination.NewRecoveryCode })
    }
}
