// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import org.plainphone.policy.*
import org.plainphone.ui.home.HomeNotice
import org.plainphone.ui.trusted.*

/** The activity supplies localized copy; state and ordering stay testable without Android. */
data class NeedsYouCopy(val waiting: (WaitingChangeKind) -> String, val attention: String, val requests: (Int, String) -> String = { _, _ -> "" })

private fun WaitingNotice.active() = phase in setOf(WaitingPhase.PENDING, WaitingPhase.COMMITTING, WaitingPhase.ROLLING_BACK)

fun pinMenuUiState(context: AdminContext, waiting: WaitingNotice?, notice: HomeNotice?,
    copy: NeedsYouCopy, requests: RequestView? = null, playDetail: String = ""): AdminMenuUiState {
    val count = requests?.takeIf { it.available }?.requests?.size
    return pinMenuUiState(context, count, playDetail).copy(needsYou = buildList {
    waiting?.takeIf { it.active() }?.let { add(AdminNotice("waiting:${it.requestId}", copy.waiting(it.kind))) }
    if (notice == HomeNotice.NeedsAttention) add(AdminNotice("attention", copy.attention))
    if (count != null && count > 0) add(AdminNotice("requests", copy.requests(count, context.userName)))
    })
}

sealed interface NeedsYouTarget {
    data class Waiting(val requestId: String) : NeedsYouTarget
    data object Attention : NeedsYouTarget
    data object Requests : NeedsYouTarget
}

/** A waiting row carries its operation ID: a stale tap must not open a replacement change. */
fun needsYouTarget(id: String, waiting: WaitingNotice?): NeedsYouTarget? = when {
    id == "requests" -> NeedsYouTarget.Requests
    id == "attention" -> NeedsYouTarget.Attention
    waiting != null && waiting.active() && id == "waiting:${waiting.requestId}" -> NeedsYouTarget.Waiting(waiting.requestId)
    else -> null
}

const val EXTRA_NEEDS_YOU_WAITING = "needs_you_waiting"
const val EXTRA_NEEDS_YOU_ATTENTION = "needs_you_attention"
