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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.Hyphens
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
    /** V2: the picture carries "hear the name"; the small link is hidden from TalkBack so it isn't offered twice. */
    @Test fun person() = shot(VoiceUiState.Person(person)) { texts, nodes ->
        wholeName(nodes); bigButton(nodes, "Record"); pictureSaysName(nodes)
        assertFalse("Hear the name" in texts)
    }
    @Test fun personNothingRecorded() = shot(VoiceUiState.Person(person, VoiceUiNotice.NothingRecorded)) { texts, nodes ->
        assertTrue("That was too short. Try again." in texts); bigButton(nodes, "Record")
    }
    /** Nothing is spoken while recording, so the picture doesn't offer the name there. */
    @Test fun recording() = shot(VoiceUiState.Recording(person)) { texts, nodes ->
        assertTrue("Recording – practice" in texts); bigButton(nodes, "Stop"); wholeName(nodes)
        assertFalse(nodes.any { "Record" in label(it) && it.clickable() })
        assertFalse(nodes.any { "Picture of" in label(it) && it.clickable() })
    }
    /** Listen, Record again and Delete are all whole on screen without scrolling (PLA-473 item 1). */
    @Test fun review() = shot(VoiceUiState.Review(person, playing = false)) { _, nodes ->
        bigButton(nodes, "Listen"); reviewActions(nodes); wholeName(nodes); pictureSaysName(nodes)
    }
    @Test fun reviewPlaying() = shot(VoiceUiState.Review(person, playing = true)) { _, nodes ->
        bigButton(nodes, "Stop listening"); reviewActions(nodes)
    }
    @Test fun reviewMaxLength() = shot(VoiceUiState.Review(person, notice = VoiceUiNotice.MaxLength)) { texts, nodes ->
        assertTrue("That's as long as a message can be." in texts); bigButton(nodes, "Listen"); reviewActions(nodes)
    }
    @Test fun reviewInterrupted() = shot(VoiceUiState.Review(person, notice = VoiceUiNotice.Interrupted)) { texts, nodes ->
        assertTrue("Recording stopped." in texts); bigButton(nodes, "Listen"); reviewActions(nodes)
    }
    @Test fun reviewCouldNotPlay() = shot(VoiceUiState.Review(person, notice = VoiceUiNotice.CouldNotPlay)) { texts, nodes ->
        assertTrue("This message could not play." in texts); bigButton(nodes, "Listen"); reviewActions(nodes)
    }
    @Test fun personMicBusy() = shot(VoiceUiState.Person(person, VoiceUiNotice.Busy)) { texts, nodes ->
        assertTrue("The microphone is busy. Try again in a moment." in texts); bigButton(nodes, "Record")
    }
    /** V9: an unsent message from earlier names its person and offers Listen and Delete, never Send. */
    @Test fun reviewUnsent() = shot(VoiceUiState.Review(person, notice = VoiceUiNotice.Unsent)) { texts, nodes ->
        assertTrue("Your message to $name isn't sent yet" in texts); bigButton(nodes, "Listen"); reviewActions(nodes)
    }
    @Test fun micPermission() = shot(VoiceUiState.MicOff(person, VoiceUiMicProblem.NotAllowed)) { texts, nodes ->
        assertTrue("Voice messages aren't set up. Ask for help. Calls and texts still work." in texts)
        bigButton(nodes, "Try again"); pictureSaysName(nodes)
    }
    /** The silenced-microphone screen: no recording is offered as a message (PLA-473). */
    @Test fun micPrivacy() = shot(VoiceUiState.MicOff(person, VoiceUiMicProblem.TurnedOff)) { texts, nodes ->
        assertTrue("The microphone is turned off. Ask for help to turn it on. Calls and texts still work." in texts)
        bigButton(nodes, "Try again")
        assertFalse(nodes.any { (label(it).startsWith("Listen") || "Delete" in label(it)) && it.clickable() })
    }
    @Test fun micError() = shot(VoiceUiState.MicOff(person, VoiceUiMicProblem.DidNotWork)) { texts, nodes ->
        assertTrue("The microphone didn't work. Try again." in texts); bigButton(nodes, "Try again")
    }

    /** The picture's tap says the name (V2 picture tap) and is labelled for TalkBack. */
    @Test fun pictureTapHearsName() {
        val events = mutableListOf<VoiceUiEvent>()
        shot(VoiceUiState.Review(person), { events += it }) { _, nodes ->
            val picture = nodes.single { label(it) == "Picture of $name" }
            val click = picture.config[SemanticsActions.OnClick]
            assertEquals("hear the name", click.label)
            events.clear(); click.action!!.invoke()
            assertEquals(listOf<VoiceUiEvent>(VoiceUiEvent.HearName), events.toList())
        }
    }

    private fun label(n: SemanticsNode) = (n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")
    private fun SemanticsNode.clickable() = config.contains(SemanticsActions.OnClick)

    private fun pictureSaysName(nodes: List<SemanticsNode>) {
        val picture = nodes.single { label(it) == "Picture of $name" }
        assertEquals("hear the name", picture.config[SemanticsActions.OnClick].label)
    }

    /** The name is never cut or shrunk; a word too long for a line breaks with a hyphen (PLA-473 item 2). */
    private fun wholeName(nodes: List<SemanticsNode>) {
        val node = nodes.single { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == name } }
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        val layout = layouts.single()
        assertEquals(name, layout.layoutInput.text.text)
        assertFalse("$name overflows", layout.hasVisualOverflow)
        for (line in 0 until layout.lineCount) assertFalse("$name cut", layout.isLineEllipsized(line))
        assertEquals(Hyphens.Auto, layout.layoutInput.style.hyphens)
        assertTrue("$name: ${layout.lineCount} lines", layout.lineCount <= 3)
    }

    /** Record again and Delete: at least 72 dp tall (UX "Sizes"), whole and inside the visible frame. */
    private fun reviewActions(nodes: List<SemanticsNode>) {
        for (word in listOf("Record again", "Delete")) {
            val node = nodes.single { it.clickable() && label(it).startsWith(word) }
            assertTrue("$word ${node.size}", node.size.height >= 72)
            wholeOnScreen(node, word)
        }
    }

    private fun wholeOnScreen(node: SemanticsNode, word: String) {
        // boundsInRoot is clipped by the scrolling parent, so a part-hidden button is smaller than its size.
        val b = node.boundsInRoot
        assertTrue("$word visible: $b of ${node.size}", b.height >= node.size.height - 0.5f &&
            b.width >= node.size.width - 0.5f && b.top >= 0f && b.bottom <= 640f - FRAME_BOTTOM)
    }

    /**
     * Record, stop and play targets are at least 96 dp (density 1: px = dp), name their action and are
     * fully on screen without scrolling (PLA-465 finding 3): not clipped, and inside the frame's visible
     * area, which ends [FRAME_BOTTOM] above the 640 dp screen. Node size alone passes when scrolled away.
     */
    private fun bigButton(nodes: List<SemanticsNode>, word: String) {
        val node = nodes.single { it.clickable() && label(it).startsWith(word) }
        assertTrue("$word ${node.size}", node.size.width >= 96 && node.size.height >= 96)
        wholeOnScreen(node, word)
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun shot(state: VoiceUiState, onEvent: (VoiceUiEvent) -> Unit = {},
        check: (List<String>, List<SemanticsNode>) -> Unit) {
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
                    assertTrue(nodes.any { label(it) in listOf("Back", "More") && it.clickable() })
                    check(texts, nodes)
                    checked = true
                }) { HomeTestFrame { VoicePracticeScreen(state, onEvent) } }
            }
        }
        assertTrue(checked)
    }

    companion object {
        /** HomeTestFrame's bottom padding (the navigation bar stand-in). */
        private const val FRAME_BOTTOM = 48f
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0},name={1}")
        fun matrix() = listOf(1f, 1.3f, 1.5f, 1.8f, 2f).flatMap { s ->
            // Short; 20 characters; one 33-character word (PLA-473).
            listOf("Sam", "Maximilian Rodriguez", "Wolfeschlegelsteinhausenbergerdor").map { arrayOf<Any>(s, it) }
        }
    }
}
