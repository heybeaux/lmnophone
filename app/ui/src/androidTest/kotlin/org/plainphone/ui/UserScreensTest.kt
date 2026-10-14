/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.user.*
import org.plainphone.ui.home.*

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
        compose.onNodeWithText("Sam's phone got it").performScrollTo().assertIsDisplayed()
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
        compose.onNodeWithText("Sam's phone got it").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("OK").assertIsDisplayed()
    }
    @Test fun unusualWarningIsFullyVisibleWithoutScrolling() {
        data class Case(val scale: Float, val recipient: String, val host: String)
        val cases = listOf(1.3f, 1.5f, 1.8f, 2f).flatMap { scale ->
            listOf("Sam", "Sam and Jo").flatMap { recipient ->
                listOf("xn--e1afmkfd.invalid", "a".repeat(63) + ".invalid",
                    "xn--bcherbcherbcherbcherbcherbcherbcherbcher-kfefffffff.invalid").map { host ->
                    Case(scale, recipient, host)
                }
            }
        }
        val current = mutableStateOf(cases.first())
        compose.setContent {
            val case = current.value
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, case.scale)) {
                PlainphoneTheme {
                    Box(Modifier.size(360.dp, 560.dp)) {
                        // Each case starts with fresh scroll state, exactly as on initial entry.
                        key(case) { BlockedLinkScreen(BlockedLinkUiState.Blocked(case.host, true, case.recipient), {}) }
                    }
                }
            }
        }
        cases.forEach { case ->
            compose.runOnIdle { current.value = case }
            val warning = compose.onNodeWithText("This address uses unusual letters. It may not be what it looks like.")
            warning.assertIsDisplayed()
            val node = warning.fetchSemanticsNode()
            org.junit.Assert.assertTrue("Entire warning must be visible: $case",
                node.boundsInRoot.height >= node.layoutInfo.coordinates.size.height - 1)
            // A visible label is restored at every scale, even where it starts below the fold.
            compose.onNodeWithText("It goes to:").assertExists()
        }
    }
    @Test fun hostStripsBidiOverridesBeforeDisplay() {
        show { BlockedLinkScreen(BlockedLinkUiState.Blocked("safe\u202E.invalid"), {}) }
        compose.onNodeWithText("safe.invalid").assertIsDisplayed()
        compose.onNodeWithText("safe\u202E.invalid").assertDoesNotExist()
    }
    @Test fun previewStripsBidiControlsFromHostAndPath() {
        show { BlockedLinkScreen(BlockedLinkUiState.Confirm("Sam and Jo",
            "LMNOPhone: Alex would like to look at this link with you: safe\u202E.invalid/\u2066bus"), {}) }
        compose.onNodeWithText("Look at this link with Sam and Jo?").assertIsDisplayed()
        compose.onNodeWithText("LMNOPhone: Alex would like to look at this link with you: safe.invalid/bus")
            .assertExists()
    }
    @Test fun confirmStripsBidiControlsFromRecipient() {
        show { BlockedLinkScreen(BlockedLinkUiState.Confirm("Sam\u202E and Jo", "example.invalid"), {}) }
        compose.onNodeWithText("Look at this link with Sam and Jo?").assertIsDisplayed()
        compose.onNodeWithText("Sam and Jo will get this text:").assertIsDisplayed()
    }
    @Test fun moreStripsBidiControlsFromNamesAndRequestRecipient() {
        show { MoreScreen(MoreUiState(MoreMode.Trusted(listOf("Sam\u202E and Jo"), "Sam\u202E and Jo")), {}) }
        compose.onNodeWithText("Sam and Jo adds apps with you").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("For Sam and Jo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Where Sam and Jo adds apps, with a PIN").assertIsDisplayed()
    }
    @Test fun requestAndDeliveryStripBidiControlsFromNames() {
        val result = mutableStateOf(false)
        show {
            if (!result.value) AskForAppScreen(AskForAppUiState("Sam\u202E"), {})
            else AppRequestResultScreen(AppRequestResultUiState("Sam\u202E", AppRequestResult.Sent,
                listOf(AppRequestRow("one", "Bus times", "Thu", AppRequestStatus.Delivered("Sam\u202E")))), {})
        }
        compose.onNodeWithText("Send to Sam").assertIsDisplayed()
        compose.onNodeWithText("Sam adds apps to this phone", substring = true).assertIsDisplayed()
        compose.runOnIdle { result.value = true }
        compose.onNodeWithText("Sent to Sam").assertIsDisplayed()
        compose.onNodeWithText("Next time you're together, Sam can add it", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Sam's phone got it").performScrollTo().assertIsDisplayed()
    }
    @Test fun blockedAndSentStripBidiControlsFromNames() {
        val state = mutableStateOf<BlockedLinkUiState>(BlockedLinkUiState.Blocked("example.invalid", shareWith = "Sam\u202E and Jo"))
        show { BlockedLinkScreen(state.value, {}) }
        compose.onNodeWithText("Look at it with Sam and Jo").assertIsDisplayed()
        compose.runOnIdle { state.value = BlockedLinkUiState.Sent("Sam\u202E and Jo") }
        compose.onNodeWithText("Sent to Sam and Jo.").assertIsDisplayed()
    }
    @Test fun monthlyCheckStripsBidiControlsFromAllNames() {
        show { HomeScreen(HomeUiState("10:42", "Thu 8 Oct", "Thu 8 Oct",
            notice = HomeNotice.MonthlyCheck(listOf("Sam\u202E", "Jo\u2066"))), {}) }
        compose.onNodeWithText("Ask Sam or Jo to do the monthly phone check").assertIsDisplayed()
    }
    @Test fun confirmActionsAreDisabledWhileHostSends() {
        val events = mutableListOf<BlockedLinkEvent>()
        val state = mutableStateOf(BlockedLinkUiState.Confirm("Sam", "example.invalid/bus"))
        show { BlockedLinkScreen(state.value) {
            events.add(it)
            if (it == BlockedLinkEvent.Send) state.value = state.value.copy(sending = true)
        } }
        compose.onNodeWithText("Don't send").assertIsEnabled().performClick()
        compose.onNodeWithText("Send").performClick()
        compose.onNodeWithText("Sending…").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithText("Don't send").assertIsNotEnabled().performTouchInput { click() }
        assertEquals(listOf(BlockedLinkEvent.DontSend, BlockedLinkEvent.Send), events)
    }
    @Test fun resultRetryIsDisabledWhileHostSendsAndReenabledAfterFailure() {
        val events = mutableListOf<AppRequestResultEvent>()
        val state = mutableStateOf(AppRequestResultUiState("Sam", AppRequestResult.Failed))
        show { AppRequestResultScreen(state.value) {
            events.add(it)
            if (it == AppRequestResultEvent.Retry) state.value = state.value.copy(sending = true)
        } }
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Sending…").assertIsNotEnabled().performTouchInput { click() }
        assertEquals(listOf(AppRequestResultEvent.Retry), events)
        compose.runOnIdle { state.value = state.value.copy(sending = false) }
        compose.onNodeWithText("Try again").assertIsEnabled().performClick()
        compose.onNodeWithText("OK, leave it saved").performClick()
        assertEquals(listOf(AppRequestResultEvent.Retry, AppRequestResultEvent.Retry, AppRequestResultEvent.Ok), events)
    }
    @Test fun sendingRequestShowsProgressEvenIfHostEligibilityIsStillTrue() {
        val events = mutableListOf<AskForAppEvent>()
        show { AskForAppScreen(AskForAppUiState("Sam", "Bus times", canSend = true, sending = true), events::add) }
        compose.onNodeWithText("Sending…").assertIsDisplayed().assertIsNotEnabled().performTouchInput { click() }
        assertEquals(emptyList<AskForAppEvent>(), events)
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
        compose.onNodeWithText("Home").performClick()
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
        compose.onNodeWithText("More").performClick()
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
