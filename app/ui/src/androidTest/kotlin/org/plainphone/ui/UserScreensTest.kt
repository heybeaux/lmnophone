/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.user.*

@OptIn(ExperimentalTestApi::class)
class UserScreensTest {
    @get:Rule val compose = createComposeRule()
    private fun show(content: @Composable () -> Unit) = compose.setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
            PlainphoneTheme { Box(Modifier.size(360.dp, 560.dp)) { content() } }
        }
    }
    @Test fun moreRoutesAndNameJoins() {
        val events = mutableListOf<MoreEvent>()
        show { MoreScreen(MoreUiState(MoreMode.Trusted(listOf("Sam", "Jo"), "Sam")), events::add) }
        compose.onNodeWithText("Emergency 9‑1‑1").assertIsDisplayed().performClick()
        compose.onNodeWithText("For Sam and Jo").performScrollTo().performClick()
        compose.onNodeWithText("Where Sam or Jo add apps, with a PIN").assertIsDisplayed()
        compose.onNodeWithText("About this phone").performScrollTo().performClick()
        assertEquals(listOf(MoreEvent.OpenEmergencyDialer, MoreEvent.OpenTrustedPerson, MoreEvent.OpenAbout), events)
    }
    @Test fun askUsesHostValidationAndEmitsTextOnly() {
        val events = mutableListOf<AskForAppEvent>()
        val state = mutableStateOf(AskForAppUiState("Sam"))
        show { AskForAppScreen(state.value, events::add) }
        compose.onNodeWithText("Send to Sam").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("request-message").performScrollTo().performTextInput("Bus times")
        assertEquals(listOf(AskForAppEvent.MessageChanged("Bus times")), events)
        compose.runOnIdle { state.value = state.value.copy(message = "Bus times", canSend = true) }
        compose.onNodeWithText("Send to Sam").assertIsDisplayed().performClick()
        assertEquals(AskForAppEvent.Send, events.last())
    }
    @Test fun historyStatusAndRetryRemainReachable() {
        val events = mutableListOf<AskForAppEvent>()
        show { AskForAppScreen(AskForAppUiState("Sam", requests = listOf(
            AppRequestRow("delivered", "Bus times", "Thu", AppRequestStatus.Delivered("Sam")),
            AppRequestRow("retry", "Recipe app", "Wed", AppRequestStatus.Failed))), events::add) }
        compose.onNodeWithText("Sam's phone got it ✓").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Didn't send – Try again").performScrollTo().performClick()
        compose.onNodeWithText("Send to Sam").assertIsDisplayed()
        assertEquals(listOf(AskForAppEvent.Retry("retry")), events)
    }
    @Test fun blockedLinkHasOnlyIsolatedActionsAndFullHost() {
        val events = mutableListOf<BlockedLinkEvent>()
        val host = "a".repeat(63) + ".invalid"
        show { BlockedLinkScreen(BlockedLinkUiState.Blocked(host, true, "Sam"), events::add) }
        compose.onNodeWithText(host).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("This address uses unusual letters. It may not be what it looks like.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("More").assertDoesNotExist()
        compose.onNodeWithText("Look at it with Sam").assertIsDisplayed().performClick()
        compose.onNodeWithText("Go back").assertIsDisplayed().performClick()
        assertEquals(listOf(BlockedLinkEvent.ReviewSharing, BlockedLinkEvent.GoBack), events)
    }
    @Test fun overlongLinkOffersNoSend() {
        val events = mutableListOf<BlockedLinkEvent>()
        show { BlockedLinkScreen(BlockedLinkUiState.TooLong, events::add) }
        compose.onNodeWithText("This link is too long to show.").assertIsDisplayed()
        compose.onNodeWithText("Send").assertDoesNotExist()
        compose.onNodeWithText("Look at it", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Go back").performClick()
        assertEquals(listOf(BlockedLinkEvent.GoBack), events)
    }
    @Test fun wifiBothActionsStayVisibleAt200Percent() {
        val events = mutableListOf<WifiMessageEvent>()
        show { WifiMessageScreen(WifiMessageUiState(WifiProblem.SignInRequired), events::add) }
        compose.onNodeWithText("Calls and texts still work").assertIsDisplayed()
        compose.onNodeWithText("Forget this network").assertIsDisplayed().performClick()
        compose.onNodeWithText("OK").assertIsDisplayed().performClick()
        assertEquals(listOf(WifiMessageEvent.ForgetNetwork, WifiMessageEvent.Ok), events)
    }
}
