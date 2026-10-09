/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.plainphone.ui.home.HomeApp
import org.plainphone.ui.home.HomeEvent
import org.plainphone.ui.home.HomeNotice
import org.plainphone.ui.home.HomeScreen
import org.plainphone.ui.home.HomeUiState
import org.plainphone.ui.theme.PlainphoneTheme

/** Forced font scale makes the regression independent of emulator settings. */
@OptIn(ExperimentalTestApi::class)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pinnedActionsStayVisibleAndEmitOnlyTheirEvents() {
        val events = mutableListOf<HomeEvent>()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                PlainphoneTheme {
                    // Content remaining on 360 × 640 after 32/48 dp system bars.
                    Box(Modifier.size(360.dp, 560.dp)) {
                        HomeScreen(HomeUiState("10:42", "Thursday 8 October", "Thu 8 Oct",
                            (1..20).map { HomeApp("app-$it", "App $it") },
                            HomeNotice.ModeChangeWaiting), events::add)
                    }
                }
            }
        }
        // The last supplied app must be present immediately, above the pinned block.
        compose.onNodeWithText("App 20").assertIsDisplayed()
        compose.onNodeWithText("Emergency", substring = true).assertDoesNotExist()
        compose.onNodeWithText("9-1-1", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Messages").assertIsDisplayed().performClick()
        compose.onNodeWithText("Phone").assertIsDisplayed().performClick()
        compose.onNodeWithText("More").assertIsDisplayed().performClick()
        compose.onNodeWithTag("home-apps").performScrollToNode(hasText("App 1"))
        compose.onNodeWithText("App 1").assertIsDisplayed().performClick()
        compose.onNodeWithText("Phone").assertIsDisplayed()
        compose.onNodeWithTag("home-notice")
            .assertTextContains("A change to how this phone is set up is waiting. Tap to cancel.")
            .assertIsDisplayed().performClick()
        assertEquals(listOf(HomeEvent.OpenMessages, HomeEvent.OpenPhone, HomeEvent.OpenMore,
            HomeEvent.OpenApp("app-1"), HomeEvent.OpenNotice), events)
    }
    @Test fun cancellingWithDuplicateIdsRemainsUsable() {
        val events = mutableListOf<HomeEvent>()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                PlainphoneTheme {
                    Box(Modifier.size(360.dp, 560.dp)) {
                        HomeScreen(HomeUiState("10:42", "Thursday 8 October", "Thu 8 Oct",
                            listOf(HomeApp("same", "Camera"), HomeApp("same", "Work camera")),
                            HomeNotice.Cancelling), events::add)
                    }
                }
            }
        }
        compose.onNodeWithText("Work camera").assertIsDisplayed().performClick()
        compose.onNodeWithText("Cancelling…").assertIsDisplayed().performClick()
        assertEquals(listOf(HomeEvent.OpenApp("same"), HomeEvent.OpenNotice), events)
    }

    @Test fun belowMinimumViewportStillReachesPinnedActionsByScrolling() {
        val events = mutableListOf<HomeEvent>()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                PlainphoneTheme {
                    Box(Modifier.size(360.dp, 320.dp)) {
                        HomeScreen(HomeUiState("10:42", "Thursday 8 October", "Thu 8 Oct",
                            (1..8).map { HomeApp("app-$it", "App $it") },
                            HomeNotice.ModeChangeWaiting), events::add)
                    }
                }
            }
        }
        compose.onNodeWithTag("home-whole-page").assertExists()
        compose.onNodeWithText("Phone").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("More").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf(HomeEvent.OpenPhone, HomeEvent.OpenMore), events)
    }
}
