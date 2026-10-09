/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.trusted.*

class RecoveryCodeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun recoveryLockoutRoutesReflectTheSuppliedPinAndEntryRoute() {
        val state = mutableStateOf(PinUiState(AdminContext("Pat", "Sam\u202E"), PinStage.RecoveryLocked,
            wait = "1 minute", helper = "Jo\u2066"))
        val events = mutableListOf<PinEvent>()
        compose.setContent { PlainphoneTheme { Box(Modifier.size(360.dp, 560.dp)) { PinScreen(state.value, events::add) } } }
        compose.onNodeWithText("Too many recovery code tries").assertExists()
        compose.onNodeWithTag("recovery-input").assertDoesNotExist()
        compose.onNodeWithText("Your PIN still works, if you remember it.").assertExists()
        compose.onNodeWithText("Enter PIN instead").performClick()
        assertEquals(listOf(PinEvent.EnterPin), events)
        compose.runOnIdle { state.value = state.value.copy(recoveryLockout = RecoveryLockoutKind.PinWaiting, pinWait = "2 minutes") }
        compose.onNodeWithText("You can try your PIN again in 2 minutes.").assertExists()
        compose.onNodeWithText("Enter PIN instead").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(recoveryLockout = RecoveryLockoutKind.Skip, otherName = "Alex\u202E") }
        compose.onNodeWithText("Too many tries for Sam's recovery code").assertExists()
        compose.onNodeWithText("Nothing was skipped. The change is still waiting.").assertExists()
        compose.onNodeWithText("Alex's PIN still works.").assertExists()
        compose.onNodeWithText("Choose someone else").assertIsDisplayed().performClick()
        assertEquals(PinEvent.ChooseSomeoneElse, events.last())
        compose.onNodeWithTag("recovery-input").assertDoesNotExist()
    }
    @Test fun wrongCodeAndSelfManagedPermissionCopyAreVerbatim() {
        val page = mutableStateOf(false)
        compose.setContent { PlainphoneTheme { Box(Modifier.size(360.dp, 560.dp)) {
            if (!page.value) PinScreen(PinUiState(AdminContext("Pat"), PinStage.Recovery, wrongRecoveryCode = true), {})
            else PermissionsScreen(PermissionsUiState(AdminContext("Pat"), "Bus times", emptyList(), waitMinutes = 20), {})
        } } }
        compose.onNodeWithText("That code isn't right. Check it against what you wrote down, and try again.").assertExists()
        compose.runOnIdle { page.value = true }
        compose.onNodeWithText("Choose what Bus times can use.", substring = true).assertExists()
        compose.onNodeWithText("That takes a 20-minute wait and one opening.", substring = true).assertExists()
        compose.onNodeWithText("can't change them alone", substring = true).assertDoesNotExist()
    }
}
