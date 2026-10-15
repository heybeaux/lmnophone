// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.plainphone.ui.home.HomeApp
import org.plainphone.ui.home.HomeNotice
import org.plainphone.ui.home.HomeScreen
import org.plainphone.ui.home.HomeUiState
import org.plainphone.ui.theme.PlainphoneTheme

/** Exercise the production screen after changes, including activity saved-state restoration. */
@OptIn(ExperimentalTestApi::class)
class HomeAnchorTest {
    @get:Rule val compose = createComposeRule()
    private val four = listOf("Calendar", "Music", "Maps", "Camera").map { HomeApp(it, it) }
    private val state = mutableStateOf(HomeUiState("10:42", "Thursday 8 October", "Thu 8 Oct", four))
    // Not Compose state: the new scale takes effect only in the restored composition.
    private var scale = 1f

    @Composable private fun Home() {
        DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale)) {
            PlainphoneTheme {
                Box(Modifier.size(360.dp, 560.dp)) { HomeScreen(state.value, {}) }
            }
        }
    }

    private fun assertAnchored(label: String) {
        compose.onNodeWithText(label).assertIsDisplayed()
        val list = compose.onNodeWithTag("home-apps").getUnclippedBoundsInRoot()
        val row = compose.onNodeWithText(label).getUnclippedBoundsInRoot()
        // Unclipped row bounds prevent a partially hidden last row from passing.
        assertEquals("$label must end at the list bottom", list.bottom.value, row.bottom.value, 1f)
    }

    @Test fun noticeAppearingAtLargeTextKeepsLastAppAtBottom() {
        scale = 2f
        compose.setContent { Home() }
        assertAnchored("Camera")
        compose.runOnIdle { state.value = state.value.copy(notice = HomeNotice.NeedsAttention) }
        assertAnchored("Camera")
    }

    @Test fun addedAppsKeepNewLastAppAtBottom() {
        compose.setContent { Home() }
        assertAnchored("Camera")
        compose.runOnIdle {
            state.value = state.value.copy(apps = four + (1..10).map { HomeApp("extra-$it", "Extra $it") })
        }
        assertAnchored("Extra 10")
    }

    @Test fun restoringAtLargerTextKeepsLastAppAtBottom() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Home() }
        assertAnchored("Camera")
        // Save the fitted 1.0 list (index 0), then restore with 2.0 text, as after recreation.
        compose.runOnIdle { scale = 2f }
        restoration.emulateSavedInstanceStateRestore()
        assertAnchored("Camera")
    }

    @Test fun changesDoNotPullSomeoneAwayFromEarlierApps() {
        scale = 2f
        compose.setContent { Home() }
        compose.onNodeWithTag("home-apps").performScrollToNode(hasText("Calendar"))
        compose.onNodeWithText("Calendar").assertIsDisplayed()
        compose.runOnIdle {
            state.value = state.value.copy(notice = HomeNotice.NeedsAttention,
                apps = four + HomeApp("extra", "Extra"))
        }
        compose.onNodeWithText("Calendar").assertIsDisplayed()
    }

    @Test fun restoringPreservesAnIntentionalScrollToEarlierApps() {
        scale = 2f
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Home() }
        compose.onNodeWithTag("home-apps").performScrollToNode(hasText("Calendar"))
        val before = compose.onNodeWithText("Calendar").getUnclippedBoundsInRoot()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Calendar").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithText("Calendar").getUnclippedBoundsInRoot())
    }
}
