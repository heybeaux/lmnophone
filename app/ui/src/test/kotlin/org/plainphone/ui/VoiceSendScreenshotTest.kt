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

/** PLA-499 send screens (UX V4-V9) at font scale 1.0 to 2.0, short name and one long word. Synthetic people only. */
@RunWith(Parameterized::class)
class VoiceSendScreenshotTest(private val scale: Float, private val name: String) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0)

    private val person = VoicePersonUi("p", name)

    /** V4: Listen, Record again and Delete outline, then the black Send, all whole on screen. */
    @Test fun reviewWithSend() {
        val events = mutableListOf<VoiceUiEvent>()
        shot(VoiceUiState.Review(person, canSend = true, practice = false), { events += it }) { _, nodes ->
            val send = button(nodes, "Send to $name", 96)
            for (word in listOf("Listen", "Record again", "Delete")) button(nodes, word, 72)
            val delete = nodes.single { it.clickable() && label(it).startsWith("Delete") }
            assertTrue("32 dp between Delete and Send", send.boundsInRoot.top - delete.boundsInRoot.bottom >= 31.5f)
            events.clear(); send.config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(listOf<VoiceUiEvent>(VoiceUiEvent.Send), events.toList())
        }
    }
    @Test fun reviewWithSendPlaying() = shot(VoiceUiState.Review(person, playing = true, canSend = true, practice = false)) { _, nodes ->
        button(nodes, "Stop listening", 72); button(nodes, "Send to $name", 96)
    }
    /** V9 with sending on. */
    @Test fun unsentWithSend() = shot(VoiceUiState.Review(person, notice = VoiceUiNotice.Unsent, canSend = true, practice = false)) { texts, nodes ->
        assertTrue("Your message to $name isn't sent yet" in texts); button(nodes, "Send to $name", 96)
    }
    /** V5: nothing to tap but Back. */
    @Test fun sending() = shot(VoiceUiState.Sending(person)) { texts, nodes ->
        assertTrue("Sending to $name" in texts)
        assertEquals(listOf("Back"), nodes.filter { it.clickable() && !label(it).startsWith("Picture of") }.map(::label))
    }
    @Test fun sent() {
        val events = mutableListOf<VoiceUiEvent>()
        shot(VoiceUiState.Sent(person), { events += it }) { texts, nodes ->
            assertTrue("Sent to $name" in texts)
            val done = button(nodes, "Done", 96)
            events.clear(); done.config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(listOf<VoiceUiEvent>(VoiceUiEvent.Done), events.toList())
        }
    }
    /** V7. */
    @Test fun notSent() {
        val events = mutableListOf<VoiceUiEvent>()
        shot(VoiceUiState.NotSent(person, VoiceUiSendProblem.NotSent), { events += it }) { texts, nodes ->
            assertTrue("Not sent to $name" in texts)
            assertTrue("Your message is still on this phone." in texts)
            assertFalse(texts.any { "look at the phone" in it })
            button(nodes, "Listen", 72)
            val again = button(nodes, "Try again", 96)
            events.clear(); again.config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(listOf<VoiceUiEvent>(VoiceUiEvent.TryAgain), events.toList())
        }
    }
    /** V7 again: the second sentence names the trusted person. */
    @Test fun notSentAgain() = shot(VoiceUiState.NotSent(person, VoiceUiSendProblem.NotSent, helper = "Jo")) { texts, nodes ->
        assertTrue("If this keeps happening, ask Jo to look at the phone." in texts); button(nodes, "Try again", 96)
    }
    /** V8. */
    @Test fun noSignal() = shot(VoiceUiState.NotSent(person, VoiceUiSendProblem.NoSignal)) { texts, nodes ->
        assertTrue("No phone signal" in texts)
        assertTrue("Your message is kept on this phone. Try again when there is signal." in texts)
        button(nodes, "Try again", 96)
    }
    /** No answer after a restart: never shown as sent, and never as failed. */
    @Test fun notConfirmed() = shot(VoiceUiState.NotSent(person, VoiceUiSendProblem.NotConfirmed)) { texts, nodes ->
        assertFalse(texts.any { it.startsWith("Sent") || it.startsWith("Not sent") })
        assertTrue("Not sure it was sent" in texts)
        assertTrue("Your message to ${person.name} is still on this phone. It may not have been sent. " +
            "Look in Messages with ${person.name} before you send it again." in texts)
        assertFalse(texts.any { "TODO" in it || "delivered" in it.lowercase() || "failed" in it.lowercase() })
        button(nodes, "Try again", 96)
    }
    /** V3 with sending on: the band no longer says practice. */
    @Test fun recording() = shot(VoiceUiState.Recording(person, practice = false)) { texts, nodes ->
        assertTrue("Recording" in texts); button(nodes, "Stop", 96)
    }

    private fun label(n: SemanticsNode) = (n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")
    private fun SemanticsNode.clickable() = config.contains(SemanticsActions.OnClick)

    /** At least [minDp] tall (density 1: px = dp), whole and inside the visible frame without scrolling. */
    private fun button(nodes: List<SemanticsNode>, word: String, minDp: Int): SemanticsNode {
        val node = nodes.single { it.clickable() && label(it).startsWith(word) }
        assertTrue("$word ${node.size}", node.size.height >= minDp)
        val b = node.boundsInRoot
        assertTrue("$word visible: $b of ${node.size}", b.height >= node.size.height - 0.5f &&
            b.width >= node.size.width - 0.5f && b.top >= 0f && b.bottom <= 640f - FRAME_BOTTOM)
        return node
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
                    assertFalse(state.practice)
                    assertFalse("Practice – not sent" in texts)
                    // "Sent" means only that the network accepted it (UX V6).
                    for (word in listOf("deliver", "receiv", "heard")) assertFalse(word, texts.any { word in it.lowercase() })
                    assertFalse(texts.any { '!' in it })
                    assertTrue(nodes.any { label(it) == "Back" && it.clickable() })
                    check(texts, nodes)
                    checked = true
                }) { HomeTestFrame { VoicePracticeScreen(state, onEvent) } }
            }
        }
        assertTrue(checked)
    }

    companion object {
        private const val FRAME_BOTTOM = 48f
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0},name={1}")
        fun matrix() = listOf(1f, 1.5f, 2f).flatMap { s ->
            listOf("Sam", "Wolfeschlegelsteinhausenbergerdor").map { arrayOf<Any>(s, it) }
        }
    }
}
