/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import org.plainphone.ui.R
import org.plainphone.ui.home.HomeNoticeDay

/** Join supplied names using UI resources. Missing names suppress the dependent line. */
@Composable
internal fun trustedPeopleText(names: List<String>, everyone: Boolean = false): String? =
    when (names.size) {
        0 -> null
        1 -> names.first()
        else -> stringResource(if (everyone) R.string.names_and else R.string.names_or,
            names.first(), names.drop(1).let { trustedPeopleText(it, everyone) }.orEmpty())
    }

/** Presentation only: the host supplies the date and relative offset; no timers or clock reads. */
@Composable
internal fun waitingTimeText(time: String, day: HomeNoticeDay): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (day.daysFromToday) {
        1 -> stringResource(R.string.notice_time_tomorrow, time)
        in 2..6 -> stringResource(R.string.notice_time_day, time,
            day.date.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale))
        in 7..Int.MAX_VALUE -> stringResource(R.string.notice_time_day, time,
            day.date.format(DateTimeFormatter.ofPattern(
                stringResource(R.string.notice_short_date_format), locale)))
        else -> time
    }
}
