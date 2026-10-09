/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
        var briefMore = largeText
        var noticeLines = 3
        var inlineClock = false
        fun bottom(brief: Boolean) = subcompose("bottom-$brief") {
            HomeBottom(brief, onEvent)
        }.single().measure(natural)
        fun top(lines: Int, inline: Boolean) = subcompose("top-$lines-$inline") {
            HomeTop(state, lines, inline, largeText, onEvent)
        }.single().measure(natural)

        var bottom = bottom(briefMore)
        var top = top(noticeLines, inlineClock)
        if (height - top.height - bottom.height < minimumApp && !briefMore) {
            briefMore = true
            bottom = bottom(true)
        }
        if (height - top.height - bottom.height < minimumApp) {
            noticeLines = 2
            top = top(noticeLines, false)
        }
        if (height - top.height - bottom.height < minimumApp) {
            inlineClock = true
            top = top(noticeLines, true)
        }
        val available = height - top.height - bottom.height
        if (available < 28.dp.roundToPx()) {
            // Unsupported smaller viewports: preserve text and touch sizes using one scroll page.
            val page = subcompose("whole-page") {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    HomeTop(state, noticeLines, inlineClock, largeText, onEvent)
                    state.apps.forEach { app ->
                        HomeAppRow(app.name) { onEvent(HomeEvent.OpenApp(app.id)) }
                    }
                    HomeBottom(briefMore, onEvent)
                }
            }.single().measure(constraints)
            layout(width, height) { page.placeRelative(0, 0) }
        } else {
            val apps = subcompose("apps") {
                LazyColumn(Modifier.fillMaxSize().testTag("home-apps"),
                    verticalArrangement = Arrangement.spacedBy(PlainphoneSpace.S3, Alignment.Bottom)) {
                    items(state.apps, key = { it.id }) { app ->
                        HomeAppRow(app.name) { onEvent(HomeEvent.OpenApp(app.id)) }
                    }
                }
            }.single().measure(Constraints.fixed(width, available))
            layout(width, height) {
                top.placeRelative(0, 0)
                apps.placeRelative(0, top.height)
                bottom.placeRelative(0, height - bottom.height)
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
        state.notice?.let { notice ->
            Spacer(Modifier.height(PlainphoneSpace.S6))
            val shape = RoundedCornerShape(PlainphoneSpace.BoxRadius)
            Row(Modifier.fillMaxWidth().testTag("home-notice")
                .background(PlainphoneColors.Fill1, shape)
                .plainphoneAction(shape) { onEvent(HomeEvent.OpenNotice) }
                .heightIn(min = 48.dp).padding(PlainphoneSpace.S4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S2)) {
                Text(noticeText(notice), style = PlainphoneType.Body.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f), maxLines = noticeLines, overflow = TextOverflow.Ellipsis)
                Text("›", style = PlainphoneType.Body.copy(color = PlainphoneColors.Ink2),
                    modifier = Modifier.clearAndSetSemantics {})
            }
        }
    }
}

@Composable
private fun HomeBottom(briefMore: Boolean, onEvent: (HomeEvent) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HomeAppRow(stringResource(R.string.home_messages)) { onEvent(HomeEvent.OpenMessages) }
        HomeAppRow(stringResource(R.string.home_phone)) { onEvent(HomeEvent.OpenPhone) }
        val shape = RoundedCornerShape(percent = 50)
        Column(Modifier.padding(horizontal = PlainphoneSpace.S6, vertical = PlainphoneSpace.S2)
            .fillMaxWidth().border(PlainphoneSpace.Border, PlainphoneColors.Ink3, shape)
            .plainphoneAction(shape) { onEvent(HomeEvent.OpenMore) }
            .heightIn(min = 64.dp).padding(horizontal = PlainphoneSpace.S6, vertical = PlainphoneSpace.S2),
            verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S2)) {
                Text(stringResource(R.string.home_more), style = PlainphoneType.Row)
                Text("^", style = PlainphoneType.Row.copy(color = PlainphoneColors.Ink3),
                    modifier = Modifier.clearAndSetSemantics {})
            }
            if (!briefMore) Text(stringResource(R.string.home_more_description), style = PlainphoneType.Small)
        }
    }
}

@Composable
private fun HomeAppRow(name: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().plainphoneAction(onClick = onClick)
        .heightIn(min = 56.dp).padding(horizontal = PlainphoneSpace.S6, vertical = PlainphoneSpace.S1),
        contentAlignment = Alignment.CenterStart) {
        Text(name, style = PlainphoneType.App)
    }
}

@Composable
private fun noticeText(notice: HomeNotice): String = when (notice) {
    HomeNotice.NeedsAttention -> stringResource(R.string.notice_attention)
    is HomeNotice.TurnOffWaiting -> stringResource(R.string.notice_turn_off, notice.time)
    HomeNotice.PeopleChangeWaiting -> stringResource(R.string.notice_people)
    HomeNotice.ModeChangeWaiting -> stringResource(R.string.notice_mode)
    is HomeNotice.OpeningUp -> stringResource(R.string.notice_opening, notice.minutes)
    HomeNotice.ReadyToStart -> stringResource(R.string.notice_ready)
    is HomeNotice.MonthlyCheck -> stringResource(R.string.notice_monthly, notice.trustedPeople)
    HomeNotice.SelfManagedMonthlyCheck -> stringResource(R.string.notice_monthly_self)
    HomeNotice.AlreadyInProgress -> stringResource(R.string.notice_in_progress)
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
