/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import androidx.compose.runtime.Composable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.user.*
import org.plainphone.ui.theme.PlainphoneTheme

/** Synthetic display data. All screens reserve the same system bars as the home goldens. */
@RunWith(Parameterized::class)
class UserScreensScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 360, screenHeight = 640,
            xdpi = 160, ydpi = 160, density = Density.MEDIUM, fontScale = scale, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0,
    )
    @Test fun moreOnePerson() = tallShot { MoreScreen(MoreUiState(MoreMode.Trusted(listOf("Sam"), "Sam")), {}) }
    @Test fun moreTwoPeople() = tallShot { MoreScreen(MoreUiState(MoreMode.Trusted(listOf("Sam", "Jo"), "Sam")), {}) }
    @Test fun moreSelfManaged() = shot { MoreScreen(MoreUiState(MoreMode.SelfManaged(10)), {}) }
    @Test fun moreOneMinute() = shot { MoreScreen(MoreUiState(MoreMode.SelfManaged(1)), {}) }
    @Test fun requestEmpty() = shot { AskForAppScreen(AskForAppUiState("Sam"), {}) }
    @Test fun requestReady() = shot { AskForAppScreen(AskForAppUiState("Sam", "The bus times app", canSend = true), {}) }
    @Test fun requestCounter() = tallShot { AskForAppScreen(AskForAppUiState("Sam", "The bus times app", charactersRemaining = 1), {}) }
    @Test fun requestHistory() = tallShot { AskForAppScreen(AskForAppUiState("Sam", requests = listOf(
        AppRequestRow("one", "The bus times app", "Thu", AppRequestStatus.Delivered("Sam")),
        AppRequestRow("two", "A recipe app", "2 Oct", AppRequestStatus.Added))), {}) }
    @Test fun requestFailureRow() = tallShot { AskForAppScreen(AskForAppUiState("Sam", requests = listOf(
        AppRequestRow("one", "The bus times app", "Thu", AppRequestStatus.Failed))), {}) }
    @Test fun sentWithDeliveryHistory() = tallShot { AppRequestResultScreen(AppRequestResultUiState("Sam", AppRequestResult.Sent,
        requests = listOf(AppRequestRow("one", "The bus times app", "Thu", AppRequestStatus.Delivered("Sam")))), {}) }
    @Test fun requestSent() = shot { AppRequestResultScreen(AppRequestResultUiState("Sam", AppRequestResult.Sent), {}) }
    @Test fun requestFailed() = shot { AppRequestResultScreen(AppRequestResultUiState("Sam", AppRequestResult.Failed), {}) }
    @Test fun requestLimit() = shot { AppRequestResultScreen(AppRequestResultUiState("Sam", AppRequestResult.DailyLimit), {}) }
    @Test fun blockedLink() = shot { BlockedLinkScreen(BlockedLinkUiState.Blocked("example.invalid", shareWith = "Sam"), {}) }
    @Test fun unusualHost() = shot { BlockedLinkScreen(BlockedLinkUiState.Blocked("xn--e1afmkfd.invalid", unusualLetters = true, shareWith = "Sam"), {}) }
    @Test fun unusualHostTwoPeople() = shot { BlockedLinkScreen(BlockedLinkUiState.Blocked("xn--e1afmkfd.invalid", unusualLetters = true, shareWith = "Sam and Jo"), {}) }
    @Test fun confirmSending() = shot { BlockedLinkScreen(BlockedLinkUiState.Confirm("Sam", "Own Phone: Alex would like to look at this link with you: example.invalid/bus", sending = true), {}) }
    @Test fun longHost() = shot { BlockedLinkScreen(BlockedLinkUiState.Blocked("a".repeat(63) + ".invalid"), {}) }
    @Test fun overlongLink() = shot { BlockedLinkScreen(BlockedLinkUiState.TooLong, {}) }
    @Test fun confirmLinkTwoPeople() = shot { BlockedLinkScreen(BlockedLinkUiState.Confirm("Sam and Jo", "Own Phone: Alex would like to look at this link with you: example.invalid/bus"), {}) }
    @Test fun confirmLink() = shot { BlockedLinkScreen(BlockedLinkUiState.Confirm("Sam", "Own Phone: Alex would like to look at this link with you: example.invalid/bus"), {}) }
    @Test fun linkSent() = shot { BlockedLinkScreen(BlockedLinkUiState.Sent("Sam"), {}) }
    @Test fun linkFailed() = shot { BlockedLinkScreen(BlockedLinkUiState.Failed, {}) }
    @Test fun wifiSignIn() = shot { WifiMessageScreen(WifiMessageUiState(WifiProblem.SignInRequired), {}) }
    @Test fun wifiNoInternet() = shot { WifiMessageScreen(WifiMessageUiState(WifiProblem.NoInternet), {}) }
    private fun tallShot(content: @Composable () -> Unit) {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 360, screenHeight = 1400,
            xdpi = 160, ydpi = 160, density = Density.MEDIUM, fontScale = scale, locale = "en"))
        shot(content)
    }
    private fun shot(content: @Composable () -> Unit) = paparazzi.snapshot {
        PlainphoneTheme { HomeTestFrame(content) }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(arrayOf(1f), arrayOf(2f))
    }
}
