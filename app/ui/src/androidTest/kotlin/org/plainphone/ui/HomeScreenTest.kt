/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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

/** Run on this lane's emulator at system font scales 1.0 and 2.0. */
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pinnedActionsStayVisibleAndEmitOnlyTheirEvents() {
        val events = mutableListOf<HomeEvent>()
        compose.setContent {
            PlainphoneTheme {
                // Content remaining on 360 × 640 after 32/48 dp system bars.
                Box(Modifier.size(360.dp, 560.dp)) {
                    HomeScreen(HomeUiState("10:42", "Thursday 8 October", "Thu 8 Oct",
                        (1..20).map { HomeApp("app-$it", "App $it") },
                        HomeNotice.ModeChangeWaiting), events::add)
                }
            }
        }
        compose.onNodeWithText("Emergency", substring = true).assertDoesNotExist()
        compose.onNodeWithText("9-1-1", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Messages").assertIsDisplayed().performClick()
        compose.onNodeWithText("Phone").assertIsDisplayed().performClick()
        compose.onNodeWithText("More").assertIsDisplayed().performClick()
        compose.onNodeWithTag("home-apps").performScrollToIndex(19)
        compose.onNodeWithText("App 20").assertIsDisplayed().performClick()
        compose.onNodeWithText("Phone").assertIsDisplayed()
        compose.onNodeWithTag("home-notice").assertIsDisplayed().performClick()
        assertEquals(listOf(HomeEvent.OpenMessages, HomeEvent.OpenPhone, HomeEvent.OpenMore,
            HomeEvent.OpenApp("app-20"), HomeEvent.OpenNotice), events)
    }
}
