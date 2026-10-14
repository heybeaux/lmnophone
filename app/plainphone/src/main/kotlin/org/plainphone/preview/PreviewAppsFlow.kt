// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.preview

import org.plainphone.device.AppRemovalResult
import org.plainphone.device.AppAdditionResult
import org.plainphone.device.PreviewHiddenApp
import org.plainphone.device.authority.PinAccessResult
import org.plainphone.device.approval.AppPermissionSnapshot
import org.plainphone.policy.PermissionChoice
import org.plainphone.ui.trusted.PermissionDecision

/** Facts and navigation only. Runtime owns fresh authority, the floor guard and every write. */
data class PreviewAppRow(val id: String, val name: String, val version: String, val protected: Boolean)
interface PreviewAppsPort {
    fun permissions(id: String, hidden: Boolean = false, done: (org.plainphone.device.approval.AppPermissionRead?) -> Unit) = done(null)
    fun hidden(done: (List<PreviewHiddenApp>?) -> Unit)
    fun add(app: PreviewHiddenApp, selection: AppPermissionSnapshot, choices: Map<String, PermissionChoice>, pin: CharArray, active: () -> Boolean, done: (AppAdditionResult) -> Unit)
    fun read(done: (List<PreviewAppRow>?) -> Unit)
    fun move(id: String, delta: Int, active: () -> Boolean, done: (Boolean) -> Unit)
    fun remove(id: String, pin: CharArray, active: () -> Boolean, done: (AppRemovalResult) -> Unit)
}
enum class PreviewAppsStage { List, Details, Confirm, Pin, Removed, SaveFailed, CheckFailed, HiddenList, AddReview, AddUnreviewed, AddPermissions, PermissionFailed, AddConfirm, AddPin, Added, Hidden, Unsupported }
data class PreviewAppsState(val stage: PreviewAppsStage = PreviewAppsStage.List,
    val rows: List<PreviewAppRow> = emptyList(), val selected: PreviewAppRow? = null,
    val busy: Boolean = false, val wrong: Boolean = false, val waitMillis: Long = 0,
    val loadFailed: Boolean = false, val hidden: List<PreviewHiddenApp> = emptyList(),
    val selectedHidden: PreviewHiddenApp? = null,
    val permissions: org.plainphone.device.approval.AppPermissionRead? = null,
    val choices: Map<String, PermissionChoice> = emptyMap(), val failedGroups: List<String> = emptyList(),
    val unavailable: Set<String> = emptySet(), val failedRows: List<org.plainphone.ui.trusted.AppPermissionRow> = emptyList())

class PreviewAppsFlow(private val port: PreviewAppsPort, private val changed: (PreviewAppsState) -> Unit) {
    var state = PreviewAppsState()
        private set
    @Volatile private var generation = 0L
    private fun show(next: PreviewAppsState) { state = next; changed(next) }
    private var fromPinSettings = false
    fun close() { generation++ }
    fun open() {
        fromPinSettings = false
        val request = ++generation
        show(PreviewAppsState(busy = true))
        port.read { rows -> if (request == generation) show(PreviewAppsState(rows = rows.orEmpty(), loadFailed = rows == null)) }
    }
    fun select(id: String) {
        if (state.busy || state.stage != PreviewAppsStage.List) return
        val row = state.rows.firstOrNull { it.id == id && !it.protected } ?: return
        val request = ++generation
        show(state.copy(stage = PreviewAppsStage.Details, selected = row, permissions = null, busy = true))
        port.permissions(row.id) { read -> if (request == generation)
            show(state.copy(permissions = read, busy = false)) }

    }
    fun move(id: String, delta: Int) {
        if (state.busy || state.stage != PreviewAppsStage.List || state.rows.none { it.id == id && !it.protected }) return
        val request = ++generation
        show(state.copy(busy = true))
        port.move(id, delta, { request == generation }) { saved -> if (request == generation) {
            if (saved) open() else show(state.copy(stage = PreviewAppsStage.SaveFailed, busy = false))
        } }
    }
    fun askRemove() { if (!state.busy && state.stage == PreviewAppsStage.Details && state.rows.any { it.id == state.selected?.id && !it.protected }) show(state.copy(stage = PreviewAppsStage.Confirm)) }
    fun confirm() { if (!state.busy && state.stage == PreviewAppsStage.Confirm) show(state.copy(stage = PreviewAppsStage.Pin, wrong = false, waitMillis = 0)) }
    fun hidden() {
        val request = ++generation
        show(PreviewAppsState(stage = PreviewAppsStage.HiddenList, busy = true))
        port.hidden { rows -> if (request == generation) show(state.copy(hidden = rows.orEmpty(), busy = false, loadFailed = rows == null)) }
    }
    /** PIN settings › Add a hidden app: the same add flow and fresh PIN; Back from the list returns to PIN settings. */
    fun openHidden() { fromPinSettings = true; hidden() }
    fun leavesOnBack(): Boolean = state.stage == PreviewAppsStage.List || (fromPinSettings && state.stage == PreviewAppsStage.HiddenList)
    fun selectHidden(id: String) {
        if (state.busy || state.stage != PreviewAppsStage.HiddenList) return
        val app = state.hidden.firstOrNull { it.id == id } ?: return
        show(state.copy(stage = PreviewAppsStage.AddReview, selectedHidden = app))
    }
    fun nextAdd() {
        if (state.busy) return
        if (state.stage == PreviewAppsStage.AddUnreviewed) { loadChoices(); return }
        if (state.stage == PreviewAppsStage.PermissionFailed) { loadChoices(retainRefusals = true); return }
        val stage = when (state.stage) {
            PreviewAppsStage.AddReview -> PreviewAppsStage.AddUnreviewed
            PreviewAppsStage.AddPermissions -> if (canContinuePermissions()) PreviewAppsStage.AddConfirm else return
            PreviewAppsStage.AddConfirm -> PreviewAppsStage.AddPin
            else -> return
        }
        show(state.copy(stage = stage, wrong = false, waitMillis = 0))
    }
    private fun loadChoices(retainRefusals: Boolean = false) {
        val app = state.selectedHidden ?: return
        val previous = state.permissions?.snapshot
        val refused = if (retainRefusals) state.unavailable else emptySet()
        val request = ++generation
        show(state.copy(stage = PreviewAppsStage.AddPermissions, busy = true, choices = emptyMap(), permissions = null))
        port.permissions(app.id, true) { read -> if (request == generation) {
            val current = read?.takeIf { it.snapshot.selectionIdentity == app.identity }
            val unavailable = if (current?.snapshot == previous) refused else emptySet()
            show(state.copy(permissions = current, busy = false, unavailable = unavailable,
                choices = unavailable.associateWith { PermissionChoice.DENY }))
        } }
    }
    fun canContinuePermissions(): Boolean = !state.busy && state.permissions?.snapshot?.groups?.all { it.id in state.choices } == true
    fun choosePermission(id: String, decision: PermissionDecision) {
        if (state.busy || state.stage != PreviewAppsStage.AddPermissions || id in state.unavailable ||
            state.permissions?.snapshot?.groups?.none { it.id == id } != false) return
        show(state.copy(choices = state.choices + (id to if (decision == PermissionDecision.Allow) PermissionChoice.ALLOW else PermissionChoice.DENY)))
    }
    fun dontAdd() {
        if (!state.busy && state.stage in setOf(PreviewAppsStage.AddReview, PreviewAppsStage.AddUnreviewed))
            show(state.copy(stage = PreviewAppsStage.Hidden))
    }
    fun unsupported() { generation++; show(state.copy(stage = PreviewAppsStage.Unsupported, busy = false)) }
    private fun submitAdd(pin: CharArray) {
        val app = state.selectedHidden ?: run { pin.fill('\u0000'); return }
        val selection = state.permissions?.snapshot ?: run { pin.fill('\u0000'); return }
        if (!canContinuePermissions()) { pin.fill('\u0000'); return }
        val request = ++generation
        show(state.copy(busy = true, wrong = false))
        port.add(app, selection, state.choices.toMap(), pin, { request == generation }) { result -> if (request == generation) {
            val next = state.copy(busy = false)
            show(when (result) {
                is AppAdditionResult.PermissionFailed -> next.copy(stage = PreviewAppsStage.PermissionFailed,
                    permissions = result.readback, failedGroups = result.groups, unavailable = result.unavailable,
                    failedRows = appPermissionsUi(state.permissions).rows.filter { it.id in result.groups })
                AppAdditionResult.Added -> next.copy(stage = PreviewAppsStage.Added)
                AppAdditionResult.Cancelled -> next.copy(stage = PreviewAppsStage.AddConfirm)
                AppAdditionResult.SaveFailed -> next.copy(stage = PreviewAppsStage.SaveFailed)
                AppAdditionResult.CheckFailed -> next.copy(stage = PreviewAppsStage.CheckFailed)
                is AppAdditionResult.PinRejected -> when (val auth = result.result) {
                    is PinAccessResult.Wrong -> next.copy(wrong = true, waitMillis = auth.remainingMillis)
                    is PinAccessResult.Delayed -> next.copy(waitMillis = auth.remainingMillis)
                    else -> next.copy(stage = PreviewAppsStage.SaveFailed)
                }
            })
        } }
    }
    fun submit(pin: CharArray) {
        if (!state.busy && state.stage == PreviewAppsStage.AddPin) { submitAdd(pin); return }
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
        if (state.busy) { open(); return }
        if (state.stage == PreviewAppsStage.AddPin) { show(state.copy(stage = PreviewAppsStage.AddConfirm, wrong = false, waitMillis = 0)); return }
        if (state.stage in setOf(PreviewAppsStage.AddReview, PreviewAppsStage.AddUnreviewed, PreviewAppsStage.AddPermissions, PreviewAppsStage.PermissionFailed, PreviewAppsStage.AddConfirm, PreviewAppsStage.Hidden)) { hidden(); return }
        if (state.stage in setOf(PreviewAppsStage.Confirm, PreviewAppsStage.Pin))
            show(state.copy(stage = PreviewAppsStage.Details, busy = false, wrong = false, waitMillis = 0))
        else open()
    }
}
