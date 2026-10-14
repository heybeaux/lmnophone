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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
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
    @Test fun latestOnly() = aboutShot(state)
    @Test fun skippedBuilds() = aboutShot(state.copy(sincePrevious = older))
    @Test fun olderVersions() = aboutShot(state.copy(showOlder = true))

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun aboutShot(value: AboutUiState) {
        var checked = false
        paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
            val view = LocalView.current as ViewRootForTest
            Box(Modifier.onGloballyPositioned {
                val nodes = view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                fun text(n: androidx.compose.ui.semantics.SemanticsNode) =
                    n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
                val texts = nodes.map(::text)
                assertTrue("LMNOPhone" in texts)
                assertEquals(0, texts.count { it == "Version 49" })
                assertEquals(if (value.showOlder) 0 else 1, texts.count { it == latest.changes.first() })
                assertTrue("Version 0.1.0-preview-49 (49)" in texts)
                assertEquals(!value.showOlder && value.sincePrevious.isNotEmpty(), "New since your last version" in texts)
                assertTrue("Tap Phone, type 9-1-1, and press call." in texts)
                assertTrue("Or tap More, then Emergency 9-1-1." in texts)
                assertEquals(value.isDeviceSecure, "From the lock screen, tap Emergency call." in texts)
                assertTrue(texts.none { Regex("\\b(Sam|Jo|Alex)\\b").containsMatchIn(it) })
                val ok = nodes.single { text(it) == "OK" && it.config.contains(SemanticsActions.OnClick) }
                val scroll = nodes.single { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
                val range = scroll.config[SemanticsProperties.VerticalScrollAxisRange]
                val emergency = nodes.single { text(it) == "Or tap More, then Emergency 9-1-1." }
                assertTrue("Safety help must be reachable by scrolling", emergency.positionInRoot.y + emergency.size.height - range.maxValue() <= scroll.boundsInRoot.bottom + 1f)
                assertTrue("Scroll content must not cover OK", scroll.boundsInRoot.bottom <= ok.boundsInRoot.top)
                assertTrue("OK must fit", ok.boundsInRoot.height >= ok.size.height - 1f)
                if (value.showOlder || value.sincePrevious.isNotEmpty()) {
                    assertTrue("Version 48" in texts); assertTrue("Version 47" in texts)
                }
                checked = true
            }) { AboutScreen(value, {}) }
        } } }
        assertTrue(checked)
    }
    @Test fun emptyFamilyPractice() = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        VoicePracticeScreen(VoiceUiState.ChoosePerson(emptyList()), {})
    } } }
    @Test fun requestNotSetUp() = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        assertEquals("Not set up on this phone yet. Nothing has been sent. The person who set up this phone can turn it on.",
            androidx.compose.ui.res.stringResource(R.string.ask_missing_recipient))
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
