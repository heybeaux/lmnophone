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
import androidx.compose.ui.semantics.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.user.*

@RunWith(Parameterized::class)
class RingtoneScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0)
    private val choices = listOf(RingtoneOption("a", "Alpha"), RingtoneOption("b", "Beta"), RingtoneOption("c", "Calm"))
    @Test fun selected() = shot(RingtoneUiState(choices, "b", canSave = true))
    @Test fun unlisted() = shot(RingtoneUiState(choices, currentUnlisted = true, canSave = true))
    @Test fun empty() = shot(RingtoneUiState(currentUnlisted = true, canSave = true))
    @Test fun noGrant() = shot(RingtoneUiState(choices, "b", failed = true))
    @Test fun longTitles() = shot(RingtoneUiState(listOf(
        RingtoneOption("a", "A very long built-in ringtone title that wraps onto several lines"),
        RingtoneOption("b", "Calm")), "a", canSave = true))
    @Test fun soundScrolledTitle() = soundScrolled("Calm")
    @Test fun soundScrolledLongTitle() = soundScrolled("A very long built-in ringtone title that wraps onto several lines")
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun soundScrolled(title: String) {
        var scrolled = false
        val view = androidx.compose.ui.platform.ComposeView(paparazzi.context).apply {
            setContent {
                PlainphoneTheme {
                    val root = LocalView.current as ViewRootForTest
                    Box(Modifier.onGloballyPositioned {
                        if (!scrolled) {
                            val node = root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                                .single { it.config.contains(SemanticsActions.ScrollBy) }
                            scrolled = true
                            kotlinx.coroutines.runBlocking(object : androidx.compose.runtime.MonotonicFrameClock {
                                private var frame = 0L
                                override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                                    frame += 16_000_000L
                                    return onFrame(frame)
                                }
                            }) {
                                node.config[SemanticsActions.ScrollByOffset].invoke(androidx.compose.ui.geometry.Offset(0f, 10000f))
                            }
                            val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
                            assertTrue("Sound must actually scroll", range.value() > 0f)
                        }
                    }) {
                        HomeTestFrame { SoundScreen(SoundUiState(VolumeUiState(4, 1, 7),
                            VolumeUiState(7, 0, 15), ringtoneTitle = title), {}) }
                    }
                }
            }
        }
        paparazzi.snapshot(view, offsetMillis = 1_000)
        assertTrue(scrolled)
    }
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun shot(state: RingtoneUiState) = paparazzi.snapshot {
        PlainphoneTheme {
            val view = LocalView.current as ViewRootForTest
            Box(Modifier.onGloballyPositioned {
                val nodes = view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                fun text(node: androidx.compose.ui.semantics.SemanticsNode) =
                    node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text }
                val texts = nodes.flatMap(::text)
                assertTrue("Ringtone" in texts); assertTrue("‹ Sound" in texts)
                assertEquals(state.currentUnlisted, "Your current ringtone stays until you choose another." in texts)
                assertEquals(state.failed, "Ringtone couldn't be changed." in texts)
                val done = nodes.single { "Done" in text(it) }
                assertEquals(!state.canSave, done.config.contains(SemanticsProperties.Disabled))
                for (choice in state.choices) {
                    val node = nodes.single { choice.title in text(it) }
                    assertEquals(choice.id == state.selectedId, node.config[SemanticsProperties.Selected])
                    assertFalse(node.config.contains(SemanticsProperties.Disabled))
                    assertTrue(node.config.contains(SemanticsActions.OnClick))
                }
            }) { HomeTestFrame { RingtoneScreen(state, {}) } }
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(1f, 1.3f, 1.5f, 1.8f, 2f).map { arrayOf(it) }
    }
}
