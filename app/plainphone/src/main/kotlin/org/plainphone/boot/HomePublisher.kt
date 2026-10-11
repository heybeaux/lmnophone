// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.plainphone.device.boot.BootOutcome
import org.plainphone.ui.home.HomeNotice

/**
 * What one unlocked run tells Home: the notice, and a [revision] that goes up on every ready run, so
 * Home re-reads its app list even when the notice is unchanged (PLA-266 finding 2).
 */
data class HomeRefresh(val notice: HomeNotice?, val revision: Long)

/** Publishes [PolicyRuntime] results to Home. Called only on the runtime's serial dispatcher. */
class HomePublisher {
    private val gateState = MutableStateFlow(HomeGate.CHECKING)
    private val refreshState = MutableStateFlow(HomeRefresh(null, 0))

    val gate: StateFlow<HomeGate> = gateState
    val refresh: StateFlow<HomeRefresh> = refreshState

    fun notOwner() {
        gateState.value = HomeGate.NOT_READY
    }

    /** A locked run never opens Home: the full plan has not run yet. */
    fun afterRun(locked: Boolean, outcome: BootOutcome?) {
        if (locked) return
        val ready = outcome?.ready == true
        refreshState.update { HomeRefresh(homeNotice(outcome), if (ready) it.revision + 1 else it.revision) }
        gateState.value = if (ready) HomeGate.READY else HomeGate.NOT_READY
    }
}
