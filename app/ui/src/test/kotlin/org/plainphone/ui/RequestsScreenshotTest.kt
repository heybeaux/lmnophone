// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.requests.*
import org.plainphone.ui.theme.PlainphoneTheme

@RunWith(Parameterized::class)
class RequestsScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 360, screenHeight = 640,
        xdpi = 160, ydpi = 160, density = Density.MEDIUM, fontScale = scale, locale = "en"), showSystemUi = false)
    private fun shot(state: RequestsUiState) = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame { RequestsScreen(state, {}) } } }
    @Test fun unavailableHistory() = shot(RequestsUiState(historyOnly = true, result = RequestUiStatus.StorageError))
    @Test fun notSetUp() = shot(RequestsUiState())
    @Test fun composeLongText() = shot(RequestsUiState(isSetUp = true, recipientName = "Sam Jo Alex Sam Jo Alex", message = "The bus times app, to check the ferry bus. ".repeat(5), canSend = true))
    @Test fun notSent() = shot(RequestsUiState(isSetUp = true, result = RequestUiStatus.NoSignal))
    @Test fun notConfirmed() = shot(RequestsUiState(isSetUp = true, result = RequestUiStatus.NotConfirmed))
    @Test fun sent() = shot(RequestsUiState(isSetUp = true, result = RequestUiStatus.Sent))
    @Test fun history() = shot(RequestsUiState(historyOnly = true, rows = listOf(
        RequestUiRow("Bus times app\nFor the ferry", "14 Oct", RequestUiStatus.NoSignal),
        RequestUiRow("Maps", "13 Oct", RequestUiStatus.Sent), RequestUiRow("Calendar", "12 Oct", RequestUiStatus.NotConfirmed))))
    @Test fun recipient() = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        RequestRecipientScreen(RequestRecipientUiState(name = "Sam Jo Alex Sam Jo Alex", number = "555-0100", editing = true, canSave = true), {})
    } } }
    @Test fun recipientDetails() = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        RequestRecipientScreen(RequestRecipientUiState(name = "Sam", number = "555-0100"), {})
    } } }
    @Test fun recipientConfirm() = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        RequestRecipientScreen(RequestRecipientUiState(name = "Sam Jo Alex Sam Jo Alex", number = "555-0100", confirming = true), {})
    } } }
    @Test fun recipientRemove() = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        RequestRecipientScreen(RequestRecipientUiState(removing = true), {})
    } } }
    @Test fun moreReady() = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        org.plainphone.ui.user.MoreScreen(org.plainphone.ui.user.MoreUiState(org.plainphone.ui.user.MoreMode.SelfManaged(10),
            pinHolderOnly = true, requestsReady = true), {})
    } } }
    companion object { @JvmStatic @Parameterized.Parameters(name = "scale={0}") fun cases() = listOf(1f, 1.5f, 2f) }
}
