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
        compose.onNodeWithContentDescription("Emergency 9-1-1", substring = true).assertIsDisplayed().performClick()
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
        compose.onNodeWithTag("request-message").performScrollTo().performClick().performTextInput("Bus times")
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
    @Test fun sentResultIncludesThePromisedHistory() {
        show { AppRequestResultScreen(AppRequestResultUiState("Sam", AppRequestResult.Sent,
            requests = listOf(AppRequestRow("one", "Bus times", "Thu", AppRequestStatus.Delivered("Sam")))), {}) }
        compose.onNodeWithText("Sam's phone got it ✓").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("OK").assertIsDisplayed()
    }
    @Test fun unusualWarningIsFullyVisibleWithoutScrolling() {
        show { BlockedLinkScreen(BlockedLinkUiState.Blocked("xn--e1afmkfd.invalid", true, "Sam"), {}) }
        val warning = compose.onNodeWithText("This address uses unusual letters. It may not be what it looks like.")
        warning.assertIsDisplayed()
        val full = warning.fetchSemanticsNode().layoutInfo.coordinates.size.height
        val visible = warning.fetchSemanticsNode().boundsInRoot.height
        org.junit.Assert.assertTrue("Entire warning must be visible", visible >= full - 1)
    }
    @Test fun hostStripsBidiOverridesBeforeDisplay() {
        show { BlockedLinkScreen(BlockedLinkUiState.Blocked("safe\u202E.invalid"), {}) }
        compose.onNodeWithText("safe.invalid").assertIsDisplayed()
        compose.onNodeWithText("safe\u202E.invalid").assertDoesNotExist()
    }
    @Test fun previewStripsBidiControlsFromHostAndPath() {
        show { BlockedLinkScreen(BlockedLinkUiState.Confirm("Sam and Jo",
            "Plainphone: Margaret would like to look at this link with you: safe\u202E.invalid/\u2066bus"), {}) }
        compose.onNodeWithText("Look at this link with Sam and Jo?").assertIsDisplayed()
        compose.onNodeWithText("Plainphone: Margaret would like to look at this link with you: safe.invalid/bus")
            .assertExists()
    }
    @Test fun confirmSendIsDisabledWhileHostSendsAndCanBeCancelled() {
        val events = mutableListOf<BlockedLinkEvent>()
        val state = mutableStateOf(BlockedLinkUiState.Confirm("Sam", "example.invalid/bus"))
        show { BlockedLinkScreen(state.value) {
            events.add(it)
            if (it == BlockedLinkEvent.Send) state.value = state.value.copy(sending = true)
        } }
        compose.onNodeWithText("Send").performClick()
        compose.onNodeWithText("Send").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithText("Don't send").performClick()
        assertEquals(listOf(BlockedLinkEvent.Send, BlockedLinkEvent.DontSend), events)
    }
    @Test fun resultRetryIsDisabledWhileHostSendsAndReenabledAfterFailure() {
        val events = mutableListOf<AppRequestResultEvent>()
        val state = mutableStateOf(AppRequestResultUiState("Sam", AppRequestResult.Failed))
        show { AppRequestResultScreen(state.value) {
            events.add(it)
            if (it == AppRequestResultEvent.Retry) state.value = state.value.copy(sending = true)
        } }
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Try again").assertIsNotEnabled().performTouchInput { click() }
        assertEquals(listOf(AppRequestResultEvent.Retry), events)
        compose.runOnIdle { state.value = state.value.copy(sending = false) }
        compose.onNodeWithText("Try again").assertIsEnabled().performClick()
        compose.onNodeWithText("OK, leave it saved").performClick()
        assertEquals(listOf(AppRequestResultEvent.Retry, AppRequestResultEvent.Retry, AppRequestResultEvent.Ok), events)
    }
    @Test fun retryHistoryRowStopsOfferingRetryWhileSending() {
        val events = mutableListOf<AppRequestResultEvent>()
        val row = AppRequestRow("one", "Bus times", "Thu", AppRequestStatus.Failed)
        val state = mutableStateOf(AppRequestResultUiState("Sam", AppRequestResult.Sent, listOf(row)))
        show { AppRequestResultScreen(state.value) {
            events.add(it)
            if (it is AppRequestResultEvent.RetryRequest) state.value = state.value.copy(
                requests = listOf(row.copy(status = AppRequestStatus.Sending)))
        } }
        compose.onNodeWithText("Didn't send – Try again").performScrollTo().performClick()
        compose.onNodeWithText("Didn't send – Try again").assertDoesNotExist()
        compose.onNodeWithText("Sending…").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        assertEquals(listOf(AppRequestResultEvent.RetryRequest("one"), AppRequestResultEvent.Ok), events)
    }
    @Test fun linkSentAndFailedOfferOk() {
        val events = mutableListOf<BlockedLinkEvent>()
        val state = mutableStateOf<BlockedLinkUiState>(BlockedLinkUiState.Sent("Sam"))
        show { BlockedLinkScreen(state.value, events::add) }
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { state.value = BlockedLinkUiState.Failed }
        compose.onNodeWithText("OK").performClick()
        assertEquals(listOf(BlockedLinkEvent.Ok, BlockedLinkEvent.Ok), events)
    }
    @Test fun selfManagedMoreAndBackEvents() {
        val events = mutableListOf<MoreEvent>()
        show { MoreScreen(MoreUiState(MoreMode.SelfManaged(10)), events::add) }
        compose.onNodeWithText("‹ Home").performClick()
        compose.onNodeWithText("Phone settings").performScrollTo().performClick()
        compose.onNodeWithText("Add an app").performScrollTo().performClick()
        compose.onNodeWithText("Your settings").performScrollTo().performClick()
        assertEquals(listOf(MoreEvent.Back, MoreEvent.OpenPhoneSettings, MoreEvent.AddApp, MoreEvent.OpenSelfManagedSettings), events)
    }
    @Test fun emergencySpeechUsesPlainHyphens() {
        show { MoreScreen(MoreUiState(MoreMode.SelfManaged(10)), {}) }
        compose.onNodeWithContentDescription("Emergency 9-1-1", substring = true).assertIsDisplayed()
    }
    @Test fun requestBackEvent() {
        val events = mutableListOf<AskForAppEvent>()
        show { AskForAppScreen(AskForAppUiState("Sam"), events::add) }
        compose.onNodeWithText("‹ More").performClick()
        assertEquals(listOf(AskForAppEvent.Back), events)
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
