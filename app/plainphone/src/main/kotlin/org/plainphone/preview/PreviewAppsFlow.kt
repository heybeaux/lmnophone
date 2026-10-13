// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.preview

import org.plainphone.device.AppRemovalResult
import org.plainphone.device.authority.PinAccessResult

/** Facts and navigation only. Runtime owns fresh authority, the floor guard and every write. */
data class PreviewAppRow(val id: String, val name: String, val version: String, val protected: Boolean)
interface PreviewAppsPort {
    fun read(done: (List<PreviewAppRow>?) -> Unit)
    fun move(id: String, delta: Int, active: () -> Boolean, done: (Boolean) -> Unit)
    fun remove(id: String, pin: CharArray, active: () -> Boolean, done: (AppRemovalResult) -> Unit)
}
enum class PreviewAppsStage { List, Details, Confirm, Pin, Removed, SaveFailed, CheckFailed }
data class PreviewAppsState(val stage: PreviewAppsStage = PreviewAppsStage.List,
    val rows: List<PreviewAppRow> = emptyList(), val selected: PreviewAppRow? = null,
    val busy: Boolean = false, val wrong: Boolean = false, val waitMillis: Long = 0,
    val loadFailed: Boolean = false)

class PreviewAppsFlow(private val port: PreviewAppsPort, private val changed: (PreviewAppsState) -> Unit) {
    var state = PreviewAppsState()
        private set
    @Volatile private var generation = 0L
    private fun show(next: PreviewAppsState) { state = next; changed(next) }
    fun close() { generation++ }
    fun open() {
        val request = ++generation
        show(PreviewAppsState(busy = true))
        port.read { rows -> if (request == generation) show(PreviewAppsState(rows = rows.orEmpty(), loadFailed = rows == null)) }
    }
    fun select(id: String) {
        if (state.busy || state.stage != PreviewAppsStage.List) return
        val row = state.rows.firstOrNull { it.id == id && !it.protected } ?: return
        show(state.copy(stage = PreviewAppsStage.Details, selected = row))
    }
    fun move(id: String, delta: Int) {
        if (state.busy || state.stage != PreviewAppsStage.List || state.rows.none { it.id == id && !it.protected }) return
        val request = ++generation
        show(state.copy(busy = true))
        port.move(id, delta, { request == generation }) { saved -> if (request == generation) {
            if (saved) open() else show(state.copy(stage = PreviewAppsStage.SaveFailed, busy = false))
        } }
    }
    fun askRemove() { if (!state.busy && state.stage == PreviewAppsStage.Details) show(state.copy(stage = PreviewAppsStage.Confirm)) }
    fun confirm() { if (!state.busy && state.stage == PreviewAppsStage.Confirm) show(state.copy(stage = PreviewAppsStage.Pin, wrong = false, waitMillis = 0)) }
    fun submit(pin: CharArray) {
        if (state.busy || state.stage != PreviewAppsStage.Pin) { pin.fill('\u0000'); return }
        val id = state.selected?.id ?: run { pin.fill('\u0000'); return }
        val request = ++generation
        show(state.copy(busy = true, wrong = false))
        port.remove(id, pin, { request == generation }) { result -> if (request == generation) {
            val next = state.copy(busy = false)
            show(when (result) {
                AppRemovalResult.Removed -> next.copy(stage = PreviewAppsStage.Removed)
                AppRemovalResult.SaveFailed -> next.copy(stage = PreviewAppsStage.SaveFailed)
                AppRemovalResult.CheckFailed -> next.copy(stage = PreviewAppsStage.CheckFailed)
                AppRemovalResult.Cancelled -> next.copy(stage = PreviewAppsStage.Details)
                is AppRemovalResult.PinRejected -> when (val auth = result.result) {
                    is PinAccessResult.Wrong -> next.copy(wrong = true, waitMillis = auth.remainingMillis)
                    is PinAccessResult.Delayed -> next.copy(waitMillis = auth.remainingMillis)
                    else -> next.copy(stage = PreviewAppsStage.SaveFailed)
                }
            })
        } }
    }
    /** Back invalidates even a queued PIN check. The serial commit is the point of no return. */
    fun back() {
        generation++
        if (state.stage in setOf(PreviewAppsStage.Confirm, PreviewAppsStage.Pin))
            show(state.copy(stage = PreviewAppsStage.Details, busy = false, wrong = false, waitMillis = 0))
        else open()
    }
}
