/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import org.plainphone.ui.R
import org.plainphone.ui.components.plainphoneAction
import org.plainphone.ui.theme.PlainphoneColors
import org.plainphone.ui.theme.PlainphoneSpace
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.theme.PlainphoneType
import org.plainphone.ui.components.waitingTimeText
import org.plainphone.ui.components.trustedPeopleText

/** Host supplies the safe content area, after system bars. No clock, policy or navigation here. */
@Composable
fun HomeScreen(state: HomeUiState, onEvent: (HomeEvent) -> Unit) {
    val largeText = LocalDensity.current.fontScale >= 2f
    SubcomposeLayout(Modifier.fillMaxSize().background(PlainphoneColors.Paper)
        .semantics { isTraversalGroup = true }) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val natural = Constraints.fixedWidth(width)
        val minimumApp = 56.dp.roundToPx()

        // Measure real scaled text, then use the token document's fallback order.
        var noticeLines = 3
        var inlineClock = false
        fun bottom() = subcompose("measure-bottom") {
            // Probes must never contribute duplicate controls to the semantics tree.
            Box(Modifier.clearAndSetSemantics {}) { HomeBottom(state, exposeTags = false, onEvent = {}) }
        }.single().measure(natural)
        fun top(lines: Int, inline: Boolean) = subcompose("measure-top-$lines-$inline") {
            Box(Modifier.clearAndSetSemantics {}) {
                HomeTop(state, lines, inline, largeText, onEvent = {})
            }
        }.single().measure(natural)

        val bottom = bottom()
        var top = top(noticeLines, inlineClock)
        if (height - top.height - bottom.height < minimumApp) {
            inlineClock = true
            top = top(noticeLines, true)
        }
        if (height - top.height - bottom.height < minimumApp) {
            noticeLines = 2
            top = top(noticeLines, true)
        }
        val available = height - top.height - bottom.height
        if (available < 28.dp.roundToPx()) {
            // Unsupported smaller viewports: preserve text and touch sizes using one scroll page.
            val page = subcompose("whole-page") {
                Column(Modifier.fillMaxSize().testTag("home-whole-page")
                    .verticalScroll(rememberScrollState())) {
                    HomeTop(state, noticeLines, inlineClock, largeText, onEvent)
                    Column(verticalArrangement = Arrangement.spacedBy(PlainphoneSpace.S3)) {
                        state.apps.forEach { app ->
                            HomeAppRow(app.name) { onEvent(HomeEvent.OpenApp(app.id)) }
                        }
                    }
                    HomeBottom(state, onEvent = onEvent)
                }
            }.single().measure(constraints)
            layout(width, height) { page.placeRelative(0, 0) }
        } else {
            val visibleTop = subcompose("visible-top") {
                HomeTop(state, noticeLines, inlineClock, largeText, onEvent)
            }.single().measure(natural)
            val visibleBottom = subcompose("visible-bottom") {
                HomeBottom(state, onEvent = onEvent)
            }.single().measure(natural)
            val apps = subcompose("apps") {
                LazyColumn(Modifier.fillMaxSize().testTag("home-apps"), reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(PlainphoneSpace.S3, Alignment.Bottom)) {
                    // Reversing both keeps the supplied visual order, with the last app at the bottom.
                    // Positional keys tolerate duplicate IDs; host insertions may move focus/press state.
                    items(state.apps.asReversed()) { app ->
                        HomeAppRow(app.name) { onEvent(HomeEvent.OpenApp(app.id)) }
                    }
                }
            }.single().measure(Constraints.fixed(width, available))
            layout(width, height) {
                visibleTop.placeRelative(0, 0)
                apps.placeRelative(0, top.height)
                visibleBottom.placeRelative(0, height - visibleBottom.height)
            }
        }
    }
}

@Composable
private fun HomeTop(state: HomeUiState, noticeLines: Int, inlineClock: Boolean,
                    largeText: Boolean, onEvent: (HomeEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = PlainphoneSpace.S6)
        .padding(top = PlainphoneSpace.S6)) {
        if (inlineClock) {
            Row(horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S2),
                verticalAlignment = Alignment.CenterVertically) {
                Text(state.time, style = PlainphoneType.Title.copy(fontFeatureSettings = "tnum"))
                Text(state.shortDate, style = PlainphoneType.Date, modifier = Modifier.weight(1f))
            }
        } else {
            Text(state.time, style = PlainphoneType.Clock)
            Text(if (largeText) state.shortDate else state.date, style = PlainphoneType.Date)
        }
        state.notice?.let { noticeText(it) }?.let { text ->
            Spacer(Modifier.height(PlainphoneSpace.S6))
            val shape = RoundedCornerShape(PlainphoneSpace.BoxRadius)
            Row(Modifier.fillMaxWidth().testTag("home-notice")
                .background(PlainphoneColors.Fill1, shape)
                .plainphoneAction(shape) { onEvent(HomeEvent.OpenNotice) }
                .heightIn(min = 48.dp).padding(PlainphoneSpace.S4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S2)) {
                Text(text, style = PlainphoneType.Body.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f), maxLines = noticeLines, overflow = TextOverflow.Ellipsis)
                Text("›", style = PlainphoneType.Body.copy(color = PlainphoneColors.Ink2),
                    modifier = Modifier.clearAndSetSemantics {})
            }
        }
    }
}

@Composable
private fun HomeBottom(state: HomeUiState, exposeTags: Boolean = true, onEvent: (HomeEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = PlainphoneSpace.S3)) {
        Column(verticalArrangement = Arrangement.spacedBy(PlainphoneSpace.S3)) {
            CommunicationRow(stringResource(R.string.home_messages), state.unreadTexts,
                R.plurals.home_unread_texts, if (exposeTags) "home-messages" else null) { onEvent(HomeEvent.OpenMessages) }
            CommunicationRow(stringResource(R.string.home_phone), state.missedCalls,
                R.plurals.home_missed_calls, if (exposeTags) "home-phone" else null) { onEvent(HomeEvent.OpenPhone) }
        }
        val shape = RoundedCornerShape(percent = 50)
        Box(Modifier.padding(horizontal = PlainphoneSpace.S6, vertical = PlainphoneSpace.S2)
            .fillMaxWidth()
            .plainphoneAction(shape) { onEvent(HomeEvent.OpenMore) }
            .heightIn(min = 64.dp).padding(horizontal = PlainphoneSpace.S6, vertical = PlainphoneSpace.S2),
            contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.home_more), style = PlainphoneType.Row)
        }
    }
}

/** Label and number wrap together within one action; no count is inferred or retained. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommunicationRow(name: String, count: Int?, spokenPlural: Int, tag: String?, onClick: () -> Unit) {
    val positive = count?.takeIf { it > 0 }
    val spoken = positive?.let { pluralStringResource(spokenPlural, it, it) }
    HomeRow(if (tag == null) Modifier else Modifier.testTag(tag), onClick) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S4),
            modifier = if (spoken == null) Modifier else Modifier.clearAndSetSemantics {
                contentDescription = spoken
            }) {
            Text(name, style = PlainphoneType.App)
            positive?.let {
                Text(if (it > 99) stringResource(R.string.home_count_overflow)
                    else stringResource(R.string.home_count, it),
                    style = PlainphoneType.App.copy(fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"))
            }
        }
    }
}

@Composable
private fun HomeAppRow(name: String, onClick: () -> Unit) {
    HomeRow(onClick = onClick) { Text(name, style = PlainphoneType.App) }
}

/** All app actions, including Messages and Phone, use the same target and padding. */
@Composable
private fun HomeRow(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().plainphoneAction(onClick = onClick)
        .heightIn(min = 56.dp).padding(horizontal = PlainphoneSpace.S6, vertical = PlainphoneSpace.S1),
        contentAlignment = Alignment.CenterStart) { content() }
}

@Composable
private fun noticeText(notice: HomeNotice): String? = when (notice) {
    HomeNotice.NeedsAttention -> stringResource(R.string.notice_attention)
    HomeNotice.PreviewTurnOffClosed -> stringResource(R.string.preview_turn_off_closed)
    is HomeNotice.TurnOffWaiting -> stringResource(R.string.notice_turn_off,
        waitingTimeText(notice.time, notice.day))
    HomeNotice.PeopleChangeWaiting -> stringResource(R.string.notice_people)
    HomeNotice.ModeChangeWaiting -> stringResource(R.string.notice_mode)
    is HomeNotice.OpeningUp -> pluralStringResource(R.plurals.notice_opening, notice.minutes, notice.minutes)
    HomeNotice.ReadyToStart -> stringResource(R.string.notice_ready)
    is HomeNotice.MonthlyCheck -> trustedPeopleText(notice.trustedPeople)?.let {
        stringResource(R.string.notice_monthly, it)
    }
    HomeNotice.SelfManagedMonthlyCheck -> stringResource(R.string.notice_monthly_self)
    HomeNotice.AlreadyInProgress -> stringResource(R.string.notice_in_progress)
    HomeNotice.Cancelling -> stringResource(R.string.notice_cancelling)
    HomeNotice.SoftwareUpdateWaiting -> stringResource(R.string.notice_system_update)
}

@Preview(name = "Home 100%", widthDp = 360, heightDp = 640, fontScale = 1f)
@Preview(name = "Home 200%", widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun HomePreview() {
    PlainphoneTheme {
        Box(Modifier.fillMaxSize().background(PlainphoneColors.Paper).padding(top = 32.dp, bottom = 48.dp)) {
            HomeScreen(HomeUiState("10:42", "Thursday 8 October", "Thu 8 Oct",
                listOf(HomeApp("music", "Spotify"), HomeApp("chat", "Slack"), HomeApp("camera", "Camera")),
                HomeNotice.ModeChangeWaiting), onEvent = {})
        }
    }
}
