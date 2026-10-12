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
import org.plainphone.ui.user.AboutScreen
import org.plainphone.ui.user.AboutUiState

@RunWith(Parameterized::class)
class AboutScreenScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 360, screenHeight = 640,
            xdpi = 160, ydpi = 160, density = Density.MEDIUM, fontScale = scale, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0,
    )

    @Test fun oneTrustedPerson() = shot(listOf("Sam"))
    @Test fun twoTrustedPeople() = shot(listOf("Sam", "Jo"))
    @Test fun longTrustedNames() = shot(listOf("Sam ".repeat(8).trim(), "Jo ".repeat(8).trim()))

    @Test fun onePersonWithoutSecureLock() = shot(listOf("Sam"), false)
    @Test fun twoPeopleWithoutSecureLock() = shot(listOf("Sam", "Jo"), false)
    @Test fun longNamesWithoutSecureLock() = shot(listOf("Sam ".repeat(8).trim(), "Jo ".repeat(8).trim()), false)

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun shot(people: List<String>, secure: Boolean = true) = paparazzi.snapshot {
        PlainphoneTheme {
            val view = LocalView.current as ViewRootForTest
            Box(Modifier.onGloballyPositioned {
                val texts = view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false)
                    .flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } }.map { it.text }
                val everyone = people.joinToString(" and ")
                val choices = if (people.size == 1)
                    "$everyone helps choose which apps are on it, together with you, with the phone in $everyone's hands. \"For $everyone\" needs $everyone's PIN so that nobody else can change your phone."
                else "$everyone help choose which apps are on it, together with you, with the phone in their hands. \"For $everyone\" needs a PIN from one of them so that nobody else can change your phone."
                assertTrue("Exact choices copy: $texts", choices in texts)
                val pinUsed = if (people.size == 1) "$everyone's PIN is used" else "one of their PINs is used"
                val pinWrong = if (people.size == 1) "$everyone's PIN is typed" else "one of their PINs is typed"
                val knows = if (people.size == 1) "$everyone knows" else "they know"
                assertTrue(texts.any { it == "LMNOPhone sends $everyone a text only when you choose to ask for an app or share a link, when the monthly check is due, when LMNOPhone needs a check or a phone update is coming, when $pinUsed to change something on this phone, when a big change (like turning LMNOPhone off) starts, is cancelled or finishes, or if $pinWrong wrongly many times (so $knows if someone is guessing it)." })
                assertEquals(secure, "From the lock screen, tap Emergency call." in texts)
                assertTrue("Tap Phone, type 9-1-1, and press call." in texts)
                assertTrue("Or tap More, then Emergency 9-1-1." in texts)
            }) {
            HomeTestFrame { AboutScreen(AboutUiState("0.1.0-preview-18", people, "Sam", "LMNOPhone"), {}) }
            }
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(arrayOf(1f), arrayOf(1.3f), arrayOf(1.5f), arrayOf(1.8f), arrayOf(2f))
    }
}
