// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.plainphone.ui.user.BlockedLinkEvent
import org.plainphone.ui.user.BlockedLinkUiState
import org.plainphone.ui.user.MoreEvent
import org.plainphone.ui.user.WifiMessageEvent
import org.plainphone.ui.user.WifiProblem

/** preview-2 (PLA-144): nothing unwired may look as if it worked. */
class TestBuildNavigatorTest {
    private val phone = PreviewPhone(PreviewPhone.sample())
    private val nav = ShellNavigator(phone, phone, testBuild = true)

    private fun at(vararg routes: ShellRoute) = ShellState(listOf(ShellRoute.Home, *routes))

    @Test fun everyUnwiredMoreRowSaysNotInThisTestBuild() {
        for (e in listOf(MoreEvent.AskForApp, MoreEvent.AddApp,
            MoreEvent.OpenTrustedPerson, MoreEvent.OpenSelfManagedSettings, MoreEvent.OpenAbout)) {
            val step = nav.onMore(at(ShellRoute.More), e)
            assertEquals(e.toString(), at(ShellRoute.More, ShellRoute.NotInBuild), step.state)
            assertNull(step.effect)
        }
    }

    @Test fun phoneSettingsOpensWithoutPinAndBackReturnsToMore() {
        val step = nav.onMore(at(ShellRoute.More), MoreEvent.OpenPhoneSettings)
        assertEquals(at(ShellRoute.More, ShellRoute.PhoneSettings), step.state)
        assertNull(step.effect)
        assertEquals(at(ShellRoute.More), nav.back(step.state).state)
    }

    @Test fun emergencyStillOpensTheDialerWithTheNumberTypedIn() {
        assertEquals(ShellEffect.OpenEmergencyDialer, nav.onMore(at(ShellRoute.More), MoreEvent.OpenEmergencyDialer).effect)
    }

    @Test fun notInBuildGoesBackWhereItCameFrom() {
        assertEquals(at(ShellRoute.More), nav.back(at(ShellRoute.More, ShellRoute.NotInBuild)).state)
    }

    @Test fun sharingABlockedLinkSaysNotInThisTestBuildInsteadOfSent() {
        val link = ShellRoute.BlockedLink(BlockedLinkUiState.Confirm("Sam", "text"), "example.invalid/bus")
        assertEquals(at(link, ShellRoute.NotInBuild), nav.onLink(at(link), BlockedLinkEvent.Send).state)
    }

    @Test fun forgetThisNetworkSaysNotInThisTestBuildFromBothWifiEntries() {
        for (entry in listOf("wifi_sign_in", "wifi_no_internet")) {
            val state = ShellState.forPreviewEntry(entry)
            val step = nav.onWifi(state, WifiMessageEvent.ForgetNetwork)
            assertEquals(entry, ShellRoute.NotInBuild, step.state.current)
            assertNull(step.effect)
        }
    }

    @Test fun okOnAWifiMessageStillJustCloses() {
        val wifi = at(ShellRoute.Wifi(WifiProblem.NoInternet))
        assertEquals(ShellState(), nav.onWifi(wifi, WifiMessageEvent.Ok).state)
    }
}
