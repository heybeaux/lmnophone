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
import org.plainphone.ui.home.*
import org.plainphone.ui.trusted.*
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.user.*

@RunWith(Parameterized::class)
class FamilyScreenshotTest(private val scale: Float, private val name: String) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"), showSystemUi = false)
    @Test fun home() = shot { HomeScreen(HomeUiState("10:42", "Tuesday 13 October", "Tue 13 Oct", showFamily = true), {}) }
    @Test fun people() = shot { VoicePracticeScreen(VoiceUiState.ChoosePerson(listOf(VoicePersonUi("sam", name, pictureInInk = true)), fromHome = true), {}) }
    @Test fun messagesAction() = shot { VoicePracticeScreen(VoiceUiState.Person(VoicePersonUi("sam", name, pictureInInk = true), openMessages = true), {}) }
    @Test fun familyMenu() = family(FamilyStage.List)
    @Test fun nameEntry() = family(FamilyStage.Name)
    @Test fun numberEntry() = family(FamilyStage.Number)
    @Test fun picture() = family(FamilyStage.Picture)
    @Test fun confirm() = family(FamilyStage.Confirm)
    @Test fun remove() = family(FamilyStage.Remove)
    private fun family(stage: FamilyStage) = shot { FamilyScreen(FamilyUiState(
        context = AdminContext("Alex", "Sam"), stage = stage, enabled = true,
        people = listOf(FamilyPersonRow("sam", name)), name = name, spokenName = name,
        number = "555-0100", canContinue = true), {}) }
    private fun shot(content: @androidx.compose.runtime.Composable () -> Unit) = paparazzi.snapshot {
        PlainphoneTheme { HomeTestFrame(content) }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "scale={0},name={1}") fun cases() =
            listOf(1f, 1.3f, 1.5f, 1.8f, 2f).flatMap { scale -> listOf("Sam", "Sam Jo Alex Sam Jo Alex").map { arrayOf<Any>(scale, it) } }
    }
}
