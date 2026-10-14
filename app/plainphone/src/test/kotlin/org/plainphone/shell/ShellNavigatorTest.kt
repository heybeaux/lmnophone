// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.plainphone.ui.home.HomeEvent
import org.plainphone.ui.user.AppRequestResult
import org.plainphone.ui.user.AppRequestResultEvent
import org.plainphone.ui.user.AskForAppEvent
import org.plainphone.ui.user.BlockedLinkEvent
import org.plainphone.ui.user.BlockedLinkUiState
import org.plainphone.ui.user.MoreEvent
import org.plainphone.ui.user.WifiMessageEvent
import org.plainphone.ui.user.WifiProblem

class ShellNavigatorTest {
    private val phone = PreviewPhone(PreviewPhone.empty())
    private val nav = ShellNavigator(phone, phone, "LMNOPhone")

    private fun at(vararg routes: ShellRoute) = ShellState(listOf(ShellRoute.Home, *routes))

    @Test fun startsAtHome() {
        assertEquals(ShellRoute.Home, ShellState().current)
    }

    @Test fun homeRoutesMoreInsideAndPhoneMessagesAppsOutside() {
        assertEquals(at(ShellRoute.More), nav.onHome(ShellState(), HomeEvent.OpenMore).state)
        assertEquals(ShellEffect.OpenDialer, nav.onHome(ShellState(), HomeEvent.OpenPhone).effect)
        assertEquals(ShellEffect.OpenMessages, nav.onHome(ShellState(), HomeEvent.OpenMessages).effect)
        assertEquals(ShellEffect.OpenApp("com.example.clock"),
            nav.onHome(ShellState(), HomeEvent.OpenApp("com.example.clock")).effect)
    }

    @Test fun unwiredDestinationsStayPut() {
        // Remaining unhosted destinations stay put outside the test-build shell.
        assertEquals(Step(ShellState()), nav.onHome(ShellState(), HomeEvent.OpenNotice))
        for (e in listOf(MoreEvent.AddApp,
            MoreEvent.OpenSelfManagedSettings)) {
            assertEquals(Step(at(ShellRoute.More)), nav.onMore(at(ShellRoute.More), e))
        }
    }

    @Test fun trustedPersonOpensThePrivatePinHost() {
        assertEquals(Step(at(ShellRoute.More), ShellEffect.OpenTrustedPerson),
            nav.onMore(at(ShellRoute.More), MoreEvent.OpenTrustedPerson))
    }

    @Test fun moreBackEmergencyAndAsk() {
        assertEquals(ShellState(), nav.onMore(at(ShellRoute.More), MoreEvent.Back).state)
        val emergency = nav.onMore(at(ShellRoute.More), MoreEvent.OpenEmergencyDialer)
        assertEquals(ShellEffect.OpenEmergencyDialer, emergency.effect)
        assertEquals(at(ShellRoute.More), emergency.state)
        assertEquals(at(ShellRoute.More, ShellRoute.AskForApp),
            nav.onMore(at(ShellRoute.More), MoreEvent.AskForApp).state)
    }

    @Test fun backPopsAndFinishesAtHome() {
        assertEquals(Step(at(ShellRoute.More)), nav.back(at(ShellRoute.More, ShellRoute.AskForApp)))
        assertEquals(Step(ShellState(), ShellEffect.Finish), nav.back(ShellState()))
    }

    @Test fun draftAcceptsUpTo280Characters() {
        val ask = at(ShellRoute.More, ShellRoute.AskForApp)
        val full = nav.onAsk(ask, AskForAppEvent.MessageChanged("a".repeat(280))).state
        assertEquals(280, full.draft.length)
        assertEquals(full, nav.onAsk(full, AskForAppEvent.MessageChanged("a".repeat(281))).state)
    }

    @Test fun sendWithoutALetterDoesNothing() {
        val ask = at(ShellRoute.More, ShellRoute.AskForApp).copy(draft = "123 !")
        assertEquals(Step(ask), nav.onAsk(ask, AskForAppEvent.Send))
        assertEquals(0, phone.snapshot().requests.size)
    }

    @Test fun unconfiguredRequestCannotSaveOrClaimSent() {
        val ask = at(ShellRoute.More, ShellRoute.AskForApp).copy(draft = "The bus times app")
        repeat(6) { assertEquals(ask, nav.onAsk(ask, AskForAppEvent.Send).state) }
        assertEquals(0, phone.requestsOn(phone.snapshot().now.toLocalDate()))
    }

    @Test fun blockedLinkNeverClaimsASendWithoutTransport() {
        val start = at(ShellRoute.BlockedLink(BlockedLinkUiState.Blocked("example.invalid", shareWith = null), "example.invalid/bus"))
        assertEquals(ShellRoute.NotInBuild, nav.onLink(start, BlockedLinkEvent.Send).state.current)
        assertEquals(ShellState(), nav.onLink(start, BlockedLinkEvent.GoBack).state)
    }

    @Test fun wifiMessagesCloseWithEitherAction() {
        val wifi = at(ShellRoute.Wifi(WifiProblem.SignInRequired))
        assertEquals(ShellState(), nav.onWifi(wifi, WifiMessageEvent.Ok).state)
        assertEquals(ShellState(), nav.onWifi(wifi, WifiMessageEvent.ForgetNetwork).state)
    }

    @Test fun previewEntryRoutes() {
        assertEquals(ShellState(), ShellState.forPreviewEntry(null))
        assertEquals(ShellState(), ShellState.forPreviewEntry("nonsense"))
        assertEquals(at(ShellRoute.Wifi(WifiProblem.SignInRequired)), ShellState.forPreviewEntry("wifi_sign_in"))
        assertEquals(at(ShellRoute.Wifi(WifiProblem.NoInternet)), ShellState.forPreviewEntry("wifi_no_internet"))
        assertEquals(at(ShellRoute.More), ShellState.forPreviewEntry("more"))
        val link = ShellState.forPreviewEntry("blocked_link").current as ShellRoute.BlockedLink
        assertEquals(BlockedLinkUiState.Blocked("example.invalid", shareWith = null), link.stage)
        assertNull(Step(ShellState()).effect)
    }
}
