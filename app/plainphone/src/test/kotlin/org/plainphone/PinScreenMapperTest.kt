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
    @Test fun productionMenuExposesGetNewApp() {
        assertEquals(PinMenuTarget.Play, pinMenuTarget(AdminDestination.AddApp))
        assertTrue(pinMenuUiState(AdminContext("Alex")).items.any { it.destination == AdminDestination.AddApp })
    }
    @Test fun productionPlayRowHasLocalizedDetailFromTheCurrentWait() {
        val detail = "Opens the Play Store, after a 15-minute wait"
        val menu = pinMenuUiState(AdminContext("Alex"), playDetail = detail)
        assertEquals(detail, menu.items.single { it.destination == AdminDestination.AddApp }.detail)
        assertEquals(detail, pinMenuUiState(AdminContext("Alex"), null, null, copy,
            playDetail = detail).items.single { it.destination == AdminDestination.AddApp }.detail)
    }
    private val copy = NeedsYouCopy({ "Waiting" }, "Attention", { n, _ -> "$n" })
    private fun menus() = listOf(pinMenuUiState(AdminContext("Alex")), pinMenuUiState(AdminContext("Alex"), 3),
        pinMenuUiState(AdminContext(""), null, null, copy),
        pinMenuUiState(AdminContext("Alex"), null, org.plainphone.ui.home.HomeNotice.NeedsAttention, copy,
            org.plainphone.policy.RequestView(true, "Jo", listOf(org.plainphone.policy.RequestDisplay("a", "Maps", 1, org.plainphone.policy.RequestStatus.Sending)))))
    @Test fun everyShownMenuRowHasAWorkingTarget() {
        for (menu in menus()) {
            assertTrue(menu.items.isNotEmpty())
            for (item in menu.items) assertNotNull(item.destination.name, pinMenuTarget(item.destination))
        }
    }
    @Test fun rowsWithoutAScreenInThisBuildAreNotShown() {
        val unfinished = listOf(AdminDestination.Updates, AdminDestination.Settings,
            AdminDestination.Limits, AdminDestination.People, AdminDestination.History, AdminDestination.Waits,
            AdminDestination.UpdatePlainphone, AdminDestination.SwitchMode)
        for (destination in unfinished) assertNull(destination.name, pinMenuTarget(destination))
        for (menu in menus()) assertTrue(menu.items.none { it.destination in unfinished })
    }
    @Test fun workingRowsKeepTheirOrder() {
        assertEquals(listOf(AdminDestination.VoiceMessages, AdminDestination.AddApp, AdminDestination.HiddenApp, AdminDestination.Apps,
            AdminDestination.Requests, AdminDestination.RequestRecipient, AdminDestination.ChangePin,
            AdminDestination.NewRecoveryCode, AdminDestination.TurnOff),
            pinMenuUiState(AdminContext("Alex")).items.map { it.destination })
        assertEquals(PinMenuTarget.AddHiddenApp, pinMenuTarget(AdminDestination.HiddenApp))
        assertEquals(PinMenuTarget.Apps, pinMenuTarget(AdminDestination.Apps))
    }
    @Test fun activityHandlesEveryTargetAndNeverOpensTheNotInBuildPageFromTheMenu() {
        val activity = java.io.File("src/main/kotlin/org/plainphone/PinAccessActivity.kt").readText()
        val handler = activity.substringAfter("is AdminMenuEvent.Open -> when (pinMenuTarget(it.destination)) {")
            .substringBefore("} }")
        for (target in PinMenuTarget.entries) assertTrue(target.name, handler.contains("PinMenuTarget.${target.name} ->"))
        assertTrue(handler.contains("PinMenuTarget.AddHiddenApp -> openApps(hidden = true)"))
        assertTrue(handler.contains("PinMenuTarget.Apps -> openApps()"))
        assertFalse(handler.contains("unsupported"))
        assertFalse(handler.contains("else ->"))
        assertFalse(activity.contains("flow.unsupported()"))
        assertTrue(activity.contains("if (hidden) appsFlow?.openHidden() else appsFlow?.open()"))
        assertTrue(activity.contains("if (appsFlow?.leavesOnBack() != false)"))
    }
    @Test fun aboutLimitationNamesNoScreenThatNoLongerAppears() {
        val resources = java.io.File("../ui/src/main/res/values/production_strings.xml").readText()
        val limitation = resources.substringAfter("<string name=\"about_limitations\">").substringBefore("</string>")
        assertFalse(limitation.contains("Not in this test build yet"))
        assertFalse(limitation.contains("“") || limitation.contains("”"))
        assertTrue(limitation.contains("PIN settings"))
    }
}
