// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.user.*
import org.plainphone.ui.user.AboutUiState

@RunWith(Parameterized::class)
class AboutScreenScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 360, screenHeight = 640,
            xdpi = 160, ydpi = 160, density = Density.MEDIUM, fontScale = scale, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0,
    )

    private val latest = ReleaseNoteUi("49", listOf("About shows the installed version and release history.",
        "App requests explain when sending is not set up."))
    private val older = listOf(ReleaseNoteUi("48", listOf("Recover your PIN with a recovery code.")),
        ReleaseNoteUi("47", listOf("Send voice messages to people in Family.")))
    private val state = AboutUiState("0.1.0-preview-49", 49, latest, emptyList(), older)
    @Test fun latestOnly() = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame { AboutScreen(state, {}) } } }
    @Test fun skippedBuilds() = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        AboutScreen(state.copy(sincePrevious = listOf(latest) + older), {})
    } } }
    @Test fun olderVersions() = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        AboutScreen(state.copy(showOlder = true), {})
    } } }
    @Test fun emptyFamilyPractice() = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        VoicePracticeScreen(VoiceUiState.ChoosePerson(emptyList()), {})
    } } }
    @Test fun requestNotSetUp() = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        AskForAppScreen(AskForAppUiState("", isSetUp = false), {})
    } } }
    @Test fun neutralMore() = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        MoreScreen(MoreUiState(MoreMode.SelfManaged(10), pinHolderOnly = true), {})
    } } }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(arrayOf(1f), arrayOf(1.3f), arrayOf(1.5f), arrayOf(1.8f), arrayOf(2f))
    }
}
