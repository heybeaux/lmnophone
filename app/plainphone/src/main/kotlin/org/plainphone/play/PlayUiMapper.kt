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
        view.left <= 0 -> OpeningStage.NoOpenings
        else -> OpeningStage.Before
    }, OpeningKind.Play, waitMinutes = minutes(view.waitMillis), durationMinutes = 10,
    minutes = minutes(view.countdownMillis ?: view.readyMillis ?: 0), left = view.left, limit = view.limit,
    time = time, alreadyOpen = view.active, sending = busy, playDisclosure = true)

private fun minutes(millis: Long) = ((millis + 59999) / 60000).coerceAtLeast(1).toInt()

/** Attention and an existing waiting change stay ahead of an opening on the single Home line. */
fun playHomeNotice(view: PlaySessionView, base: org.plainphone.ui.home.HomeNotice?): org.plainphone.ui.home.HomeNotice? {
    val attention = org.plainphone.ui.home.HomeNotice.NeedsAttention
    if (!view.handbackReady && !view.active) return attention
    if (base != null && base != org.plainphone.ui.home.HomeNotice.SoftwareUpdateWaiting &&
        base != org.plainphone.ui.home.HomeNotice.PreviewTurnOffClosed) return base
    return when {
        view.countdownMillis != null -> org.plainphone.ui.home.HomeNotice.OpeningUp(minutes(view.countdownMillis!!))
        view.readyMillis != null -> org.plainphone.ui.home.HomeNotice.ReadyToStart
        else -> base
    }
}

/** A closed package window can retain its approval grant until Done; it is not counting down. */
fun playNeedsClosure(view: PlaySessionView): Boolean = view.id != null && !view.active &&
    view.countdownMillis == null && view.readyMillis == null

fun playApprovalListUi(context: AdminContext, apps: List<HiddenAppRow>, expired: Boolean = false,
    unavailable: Boolean = false, busy: Boolean = false) =
    PlayApprovalListUiState(context, apps, expired, unavailable, busy, showGrantNotice = true)

/** A refusal is not failed readback. Keep Checking until launch-failure cleanup completes. */
fun playSessionStage(view: PlaySessionView, failed: Boolean = false, returned: Boolean = false,
    busy: Boolean = false): SessionStage? = when {
    busy && (failed || returned || playNeedsClosure(view)) -> SessionStage.Checking
    !view.handbackReady && !view.active -> SessionStage.NotReady
    failed -> null
    returned || playNeedsClosure(view) -> SessionStage.Restored
    else -> null
}
