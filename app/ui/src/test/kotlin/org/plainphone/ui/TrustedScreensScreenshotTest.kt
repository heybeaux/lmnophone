/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.trusted.*

/** Every fixture is rendered at the minimum viewport, including reserved system bars. */
@RunWith(Parameterized::class)
class TrustedScreensScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0)
    @Test fun screens() {
        trustedFixtures().forEach { fixture ->
            paparazzi.snapshot(name = fixture.name) { PlainphoneTheme { HomeTestFrame { fixture.content() } } }
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}")
        fun scales() = listOf(arrayOf(1f), arrayOf(2f))
    }
}

@RunWith(Parameterized::class)
class TrustedNamesScreenshotTest(private val scale: Float, private val two: Boolean, private val long: Boolean) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 360, screenHeight = 640, xdpi = 160, ydpi = 160,
        density = Density.MEDIUM, fontScale = scale, locale = "en"),
        showSystemUi = false, maxPercentDifference = 0.0)
    @Test fun names() {
        trustedFixtures(two, long).filter { it.hasName }.forEach { fixture ->
            paparazzi.snapshot(name = fixture.name) { PlainphoneTheme { HomeTestFrame { fixture.content() } } }
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "scale={0},two={1},long={2}")
        fun cases() = listOf(1.3f, 1.5f, 1.8f, 2f).flatMap { s ->
            listOf(false, true).flatMap { two -> listOf(false, true).map { long -> arrayOf<Any>(s, two, long) } }
        }
    }
}
