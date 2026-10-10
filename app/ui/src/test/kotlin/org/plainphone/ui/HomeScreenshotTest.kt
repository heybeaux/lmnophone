/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import java.time.LocalDate
import org.plainphone.ui.home.HomeNoticeDay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.home.HomeApp
import org.plainphone.ui.home.HomeNotice
import org.plainphone.ui.home.HomeScreen
import org.plainphone.ui.home.HomeUiState
import org.plainphone.ui.theme.PlainphoneTheme

/** Synthetic data only. 360 × 640 includes simulated 32/48 dp system bars. */
@RunWith(Parameterized::class)
class HomeScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 360, screenHeight = 640,
            xdpi = 160, ydpi = 160, density = Density.MEDIUM,
            fontScale = scale, locale = "en",
        ),
        showSystemUi = false,
        maxPercentDifference = 0.0,
    )

    @Test fun zeroApps() = snapshot(null, emptyList())
    @Test fun oneApp() = snapshot(null, listOf(HomeApp("camera", "Camera")))

    @Test fun firstLook() = snapshot(null)
    @Test fun overdueMonthlyCheck() = snapshot(HomeNotice.MonthlyCheck(listOf("Sam", "Jo")))
    @Test fun attention() = snapshot(HomeNotice.NeedsAttention)
    @Test fun pendingTurnOff() = snapshot(HomeNotice.TurnOffWaiting("21:15", HomeNoticeDay(LocalDate.of(2026, 10, 9), 1)))
    @Test fun longestWaitingNotice() = snapshot(HomeNotice.ModeChangeWaiting)
    @Test fun selfManagedReady() = snapshot(HomeNotice.ReadyToStart)

    @Test fun cancelling() = snapshot(HomeNotice.Cancelling)
    @Test fun eightApps() = snapshot(HomeNotice.ModeChangeWaiting,
        (1..8).map { HomeApp("app-$it", "App $it") })
    @Test fun duplicateIds() = snapshot(HomeNotice.ModeChangeWaiting,
        listOf(HomeApp("same", "Camera"), HomeApp("same", "Work camera")))
    @Test fun oneMinute() = snapshot(HomeNotice.OpeningUp(1))
    @Test fun twoMinutes() = snapshot(HomeNotice.OpeningUp(2))
    @Test fun oneTrustedPerson() = snapshot(HomeNotice.MonthlyCheck(listOf("Sam")))
    @Test fun fourAppsWithMonthlyNotice() = snapshot(HomeNotice.MonthlyCheck(listOf("Sam")),
        listOf(HomeApp("music", "Spotify"), HomeApp("chat", "Slack"),
            HomeApp("camera", "Camera"), HomeApp("maps", "Maps")))
    @Test fun noTrustedPeople() = snapshot(HomeNotice.MonthlyCheck(emptyList()))
    @Test fun sameDay() = snapshot(HomeNotice.TurnOffWaiting("21:15",
        HomeNoticeDay(LocalDate.of(2026, 10, 8), 0)))
    @Test fun weekday() = snapshot(HomeNotice.TurnOffWaiting("21:15",
        HomeNoticeDay(LocalDate.of(2026, 10, 10), 2)))
    @Test fun laterDate() = snapshot(HomeNotice.TurnOffWaiting("21:15",
        HomeNoticeDay(LocalDate.of(2026, 10, 15), 7)))

    private fun snapshot(notice: HomeNotice?, apps: List<HomeApp> = listOf(
        HomeApp("music", "Spotify"), HomeApp("chat", "Slack"), HomeApp("camera", "Camera"))) {
        paparazzi.snapshot {
            PlainphoneTheme {
                HomeTestFrame {
                    HomeScreen(
                        HomeUiState("10:42", "Thursday 8 October", "Thu 8 Oct",
                            apps, notice),
                        onEvent = {},
                    )
                }
            }
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(1.0f, 1.3f, 1.5f, 1.8f, 2.0f).map { arrayOf(it) }
    }
}
