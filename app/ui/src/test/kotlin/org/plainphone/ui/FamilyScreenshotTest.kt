// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.*
import org.junit.Assert.*
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
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    @Test fun messagesAction() {
        var checked = false
        shot {
            val view = LocalView.current as ViewRootForTest
            Box(Modifier.onGloballyPositioned {
                val nodes = view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                fun text(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
                val picture = nodes.single { it.config.getOrNull(SemanticsProperties.ContentDescription) == listOf("Picture of $name") }
                val title = nodes.single { text(it) == name }
                val back = nodes.single { text(it).startsWith("‹") }
                val record = nodes.single { text(it) == "Record" && it.config.contains(SemanticsActions.OnClick) }
                assertTrue("Picture and name must precede Back", picture.positionInRoot.y < title.positionInRoot.y && title.positionInRoot.y < back.positionInRoot.y)
                val messages = nodes.single { text(it) == "Messages with $name" && it.config.contains(SemanticsActions.OnClick) }
                assertEquals(listOf("Messages with $name, opens Messages"), messages.config[SemanticsProperties.ContentDescription])
                assertTrue(title.positionInRoot.y < messages.positionInRoot.y)
                assertTrue(messages.positionInRoot.y < back.positionInRoot.y)
                assertTrue(back.positionInRoot.y < record.positionInRoot.y)
                val scroll = nodes.single { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
                val range = scroll.config[SemanticsProperties.VerticalScrollAxisRange]
                assertTrue("Messages fits in scroll viewport", messages.size.height <= scroll.boundsInRoot.height)
                assertTrue("Messages can scroll above Record", messages.positionInRoot.y + messages.size.height - range.maxValue() <= scroll.boundsInRoot.bottom + 1f)
                assertTrue(scroll.boundsInRoot.bottom <= record.boundsInRoot.top)
                assertTrue(record.boundsInRoot.height >= record.size.height - 1f)
                checked = true
            }) { VoicePracticeScreen(VoiceUiState.Person(VoicePersonUi("sam", name, pictureInInk = true), openMessages = true), {}) }
        }
        assertTrue(checked)
    }
    @Test fun familyMenu() = family(FamilyStage.List)
    @Test fun nameEntry() = family(FamilyStage.Name)
    @Test fun numberEntry() = family(FamilyStage.Number)
    @Test fun picture() = family(FamilyStage.Picture)
    @Test fun confirm() = family(FamilyStage.Confirm)
    @Test fun remove() = family(FamilyStage.Remove)
    @Test fun editConfirm() = family(FamilyStage.Confirm, editing = true)
    @Test fun microphoneFailure() = shot { FamilyScreen(FamilyUiState(
        AdminContext("Alex", "Sam"), failed = true, micFailed = true, canContinue = true), {}) }
    private fun family(stage: FamilyStage, editing: Boolean = false) = shot { FamilyScreen(FamilyUiState(
        context = AdminContext("Alex", "Sam"), stage = stage, enabled = true,
        people = listOf(FamilyPersonRow("sam", name)), name = name, spokenName = name,
        number = "555-0100", canContinue = true, editing = editing), {}) }
    private fun shot(content: @androidx.compose.runtime.Composable () -> Unit) = paparazzi.snapshot {
        PlainphoneTheme { HomeTestFrame(content) }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "scale={0},name={1}") fun cases() =
            listOf(1f, 1.3f, 1.5f, 1.8f, 2f).flatMap { scale -> listOf("Sam", "Sam Jo Alex Sam Jo Alex").map { arrayOf<Any>(scale, it) } }
    }
}
