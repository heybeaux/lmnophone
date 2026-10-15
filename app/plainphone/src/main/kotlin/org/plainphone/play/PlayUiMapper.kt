// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.play

import org.plainphone.device.session.PlaySessionView
import org.plainphone.ui.trusted.*

fun playOpeningUi(view: PlaySessionView, context: AdminContext, stage: OpeningStage? = null,
    time: String = "", busy: Boolean = false): OpeningUiState = OpeningUiState(context,
    stage ?: when {
        view.countdownMillis != null -> OpeningStage.Countdown
        view.readyMillis != null -> OpeningStage.Ready
        else -> OpeningStage.Before
    }, OpeningKind.Play, waitMinutes = minutes(view.waitMillis), durationMinutes = 10,
    minutes = minutes(view.countdownMillis ?: view.readyMillis ?: 0), left = view.left, limit = view.limit,
    time = time, alreadyOpen = view.active, sending = busy, playDisclosure = true)

private fun minutes(millis: Long) = ((millis + 59999) / 60000).coerceAtLeast(1).toInt()
