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
import org.plainphone.ui.requests.*
import org.plainphone.ui.theme.PlainphoneTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.*
import org.junit.Assert.*

@RunWith(Parameterized::class)
class RequestsScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 360, screenHeight = 640,
        xdpi = 160, ydpi = 160, density = Density.MEDIUM, fontScale = scale, locale = "en"), showSystemUi = false)
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun shot(state: RequestsUiState) {
        var checked = false
        paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
            val view = LocalView.current as ViewRootForTest
            Box(Modifier.onGloballyPositioned {
                val nodes = view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                fun text(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
                val button = nodes.single { text(it) == (if (state.isSetUp && state.result == null && !state.historyOnly) "Send" else "OK") && it.config.contains(SemanticsActions.OnClick) }
                val scroll = nodes.single { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
                val range = scroll.config[SemanticsProperties.VerticalScrollAxisRange]
                assertTrue("Scroll must end above the button", scroll.boundsInRoot.bottom <= button.boundsInRoot.top)
                assertTrue("The whole action must fit", button.boundsInRoot.height >= button.size.height - 1f)
                state.rows.lastOrNull()?.let { last ->
                    val row = nodes.single { text(it) == last.text }
                    assertTrue("Whole request reachable by scrolling", row.positionInRoot.y + row.size.height - range.maxValue() <= scroll.boundsInRoot.bottom + 1f)
                }
                assertTrue(nodes.map(::text).none { it.contains("Delivered") })
                checked = true
            }) { RequestsScreen(state, {}) }
        } } }
        assertTrue(checked)
    }
    @Test fun unavailableHistory() = shot(RequestsUiState(historyOnly = true, result = RequestUiStatus.StorageError))
    @Test fun notSetUp() = shot(RequestsUiState())
    @Test fun composeLongText() = shot(RequestsUiState(isSetUp = true, recipientName = "Sam Jo Alex Sam Jo Alex", message = "The bus times app, to check the ferry bus. ".repeat(5), canSend = true))
    @Test fun notSent() = shot(RequestsUiState(isSetUp = true, result = RequestUiStatus.NoSignal))
    @Test fun notConfirmed() = shot(RequestsUiState(isSetUp = true, result = RequestUiStatus.NotConfirmed))
    @Test fun sent() = shot(RequestsUiState(isSetUp = true, result = RequestUiStatus.Sent))
    @Test fun history() = shot(RequestsUiState(historyOnly = true, rows = listOf(
        RequestUiRow("Bus times app\nFor the ferry", "14 Oct", RequestUiStatus.NoSignal),
        RequestUiRow("Maps", "13 Oct", RequestUiStatus.Sent), RequestUiRow("Calendar", "12 Oct", RequestUiStatus.NotConfirmed))))
    @Test fun recipient() = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        RequestRecipientScreen(RequestRecipientUiState(name = "Sam Jo Alex Sam Jo Alex", number = "555-0100", editing = true, canSave = true), {})
    } } }
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    @Test fun recipientEmpty() {
        var checked = false
        paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
            val view = LocalView.current as ViewRootForTest
            Box(Modifier.onGloballyPositioned {
                val nodes = view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                assertTrue("An empty recipient offers Set up", nodes.any { node ->
                    node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "Set up" } &&
                        node.config.contains(SemanticsActions.OnClick)
                })
                checked = true
            }) { RequestRecipientScreen(RequestRecipientUiState(), {}) }
        } } }
        assertTrue(checked)
    }
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    @Test fun recipientReadFailure() {
        var checked = false
        paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
            val view = LocalView.current as ViewRootForTest
            Box(Modifier.onGloballyPositioned {
                val nodes = view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                val texts = nodes.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
                assertTrue(texts.contains("Who gets requests could not be read. Check again later."))
                assertFalse(texts.any { it.contains("could not be saved") || it == "Set up" || it == "Change" })
                val ok = nodes.single { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "OK" } }
                assertTrue(ok.config.contains(SemanticsActions.OnClick))
                assertTrue(ok.boundsInRoot.height >= ok.size.height - 1f)
                checked = true
            }) { RequestRecipientScreen(RequestRecipientUiState(readFailed = true), {}) }
        } } }
        assertTrue(checked)
    }
    @Test fun recipientDetails() = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        RequestRecipientScreen(RequestRecipientUiState(name = "Sam", number = "555-0100"), {})
    } } }
    @Test fun recipientConfirm() = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        RequestRecipientScreen(RequestRecipientUiState(name = "Sam Jo Alex Sam Jo Alex", number = "555-0100", confirming = true), {})
    } } }
    @Test fun recipientRemove() = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        RequestRecipientScreen(RequestRecipientUiState(removing = true), {})
    } } }
    @Test fun moreReady() = paparazzi.snapshot { PlainphoneTheme { HomeTestFrame {
        org.plainphone.ui.user.MoreScreen(org.plainphone.ui.user.MoreUiState(org.plainphone.ui.user.MoreMode.SelfManaged(10),
            pinHolderOnly = true, requestsReady = true), {})
    } } }
    companion object { @JvmStatic @Parameterized.Parameters(name = "scale={0}") fun cases() = listOf(1f, 1.5f, 2f) }
}
