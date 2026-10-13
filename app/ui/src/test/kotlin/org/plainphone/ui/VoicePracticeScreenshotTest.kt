/* Copyright 2026 LMNOPhone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.getAllSemanticsNodes
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.user.*

/** Font scale × name length matrix. Synthetic people only. */
@RunWith(Parameterized::class)
class VoicePracticeScreenshotTest(private val scale: Float, private val name: String) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0)

    private val person = VoicePersonUi("p", name)

    @Test fun choosePerson() = shot(VoiceUiState.ChoosePerson(listOf(person, VoicePersonUi("jo", "Jo"),
        VoicePersonUi("alex", "Alex")))) { texts, nodes ->
        assertTrue("Who do you want to talk to?" in texts)
        for (n in listOf(name, "Jo", "Alex")) assertTrue(n, nodes.any { n in label(it) && it.clickable() })
    }
    @Test fun person() = shot(VoiceUiState.Person(person)) { texts, nodes ->
        assertTrue(name in texts); bigButton(nodes, "Record"); assertTrue("Hear name" in texts)
    }
    @Test fun personNothingRecorded() = shot(VoiceUiState.Person(person, VoiceUiNotice.NothingRecorded)) { texts, nodes ->
        assertTrue("That was too short." in texts); bigButton(nodes, "Record")
    }
    @Test fun recording() = shot(VoiceUiState.Recording(person)) { texts, nodes ->
        assertTrue("Recording – practice" in texts); bigButton(nodes, "Stop")
        assertFalse(nodes.any { "Record" in label(it) && it.clickable() })
    }
    @Test fun review() = shot(VoiceUiState.Review(person, playing = false)) { _, nodes ->
        bigButton(nodes, "Listen")
        for (action in listOf("Record again", "Delete")) assertTrue(action, nodes.any { action in label(it) && it.clickable() })
    }
    @Test fun reviewPlaying() = shot(VoiceUiState.Review(person, playing = true)) { _, nodes -> bigButton(nodes, "Stop listening") }
    @Test fun reviewMaxLength() = shot(VoiceUiState.Review(person, notice = VoiceUiNotice.MaxLength)) { texts, _ ->
        assertTrue("That's as long as a message can be." in texts)
    }
    @Test fun reviewInterrupted() = shot(VoiceUiState.Review(person, notice = VoiceUiNotice.Interrupted)) { texts, _ ->
        assertTrue("Recording stopped." in texts)
    }
    @Test fun personMicBusy() = shot(VoiceUiState.Person(person, VoiceUiNotice.Busy)) { texts, nodes ->
        assertTrue("The microphone is busy. Try again in a moment." in texts); bigButton(nodes, "Record")
    }
    /** V9: an unsent message from earlier names its person and offers Listen and Delete, never Send. */
    @Test fun reviewUnsent() = shot(VoiceUiState.Review(person, notice = VoiceUiNotice.Unsent)) { texts, nodes ->
        assertTrue("Your message to $name isn't sent yet" in texts); bigButton(nodes, "Listen")
        assertTrue(nodes.any { "Delete" in label(it) && it.clickable() })
    }
    @Test fun micPermission() = shot(VoiceUiState.MicOff(person, VoiceUiMicProblem.NotAllowed)) { texts, nodes ->
        assertTrue("The microphone is not allowed." in texts); bigButton(nodes, "Try again")
    }
    @Test fun micPrivacy() = shot(VoiceUiState.MicOff(person, VoiceUiMicProblem.TurnedOff)) { texts, _ ->
        assertTrue("The microphone is turned off." in texts)
    }
    @Test fun micError() = shot(VoiceUiState.MicOff(person, VoiceUiMicProblem.DidNotWork)) { texts, _ ->
        assertTrue("The microphone did not work." in texts)
    }

    private fun label(n: SemanticsNode) = (n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")
    private fun SemanticsNode.clickable() = config.contains(SemanticsActions.OnClick)

    /** Record, stop and play targets are at least 96 dp (density 1: px = dp) and name their action. */
    private fun bigButton(nodes: List<SemanticsNode>, word: String) {
        val node = nodes.single { it.clickable() && label(it).startsWith(word) }
        assertTrue("$word ${node.size}", node.size.width >= 96 && node.size.height >= 96)
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun shot(state: VoiceUiState, check: (List<String>, List<SemanticsNode>) -> Unit) {
        var checked = false
        paparazzi.snapshot {
            PlainphoneTheme {
                val view = LocalView.current as ViewRootForTest
                Box(Modifier.onGloballyPositioned {
                    val nodes = view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                    val texts = nodes.flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
                    // Every screen says it is a practice; nothing offers to send.
                    assertTrue("Practice – not sent" in texts)
                    assertFalse(nodes.any { label(it).contains("Send", ignoreCase = true) })
                    assertFalse(texts.any { '!' in it })
                    assertTrue(nodes.any { label(it).startsWith("‹ ") && it.clickable() })
                    check(texts, nodes)
                    checked = true
                }) { HomeTestFrame { VoicePracticeScreen(state, {}) } }
            }
        }
        assertTrue(checked)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0},name={1}")
        fun matrix() = listOf(1f, 1.3f, 1.5f, 1.8f, 2f).flatMap { s ->
            listOf("Sam", "Alexandria Montgomery-Fitzwilliam").map { arrayOf<Any>(s, it) }
        }
    }
}
