// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

data class RingtoneChoice(val id: String, val title: String)
data class RingtoneState(val choices: List<RingtoneChoice> = emptyList(), val selectedId: String? = null,
    val currentUnlisted: Boolean = false, val canSave: Boolean = false, val failed: Boolean = false)

/** IDs are normalized content URIs. The adapter supplies built-in TYPE_RINGTONE entries only. */
interface RingtonePort {
    fun choices(): List<RingtoneChoice>
    fun read(): String?
    fun title(uri: String): String?
    fun canWrite(): Boolean
    fun write(uri: String)
    /** Play at the existing ring volume/mode, stopping automatically within about five seconds. */
    fun preview(uri: String)
    fun stopPreview()
}

class RingtoneController(private val port: RingtonePort) {
    var state = RingtoneState()
        private set
    private var opened = false
    private var original: String? = null
    private var readable = false
    private var saveFailed = false

    fun currentTitle(): String? = try {
        port.read()?.let { port.title(it) }?.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }

    fun onOpen() {
        stopPreview()
        opened = true
        saveFailed = false
        try {
            val choices = port.choices().distinctBy { it.id }.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            original = port.read()
            readable = true
            val selected = choices.firstOrNull { it.id == original }?.id
            state = RingtoneState(choices, selected, selected == null)
        } catch (_: Exception) {
            readable = false
            state = RingtoneState()
        }
        refreshPermission()
    }

    fun refreshPermission() {
        val allowed = try { port.canWrite() } catch (_: Exception) { false }
        state = state.copy(canSave = opened && readable && allowed,
            failed = !readable || !allowed || saveFailed)
    }

    fun select(id: String) {
        if (!opened || state.choices.none { it.id == id }) return
        stopPreview()
        state = state.copy(selectedId = id)
        try { port.preview(id) } catch (_: Exception) { stopPreview() }
    }

    /** True only when the caller may return to Sound. Never publishes a requested title. */
    fun done(): Boolean {
        stopPreview()
        refreshPermission()
        if (!state.canSave) return false
        val selected = state.selectedId
        if (selected == null || selected == original) return true
        saveFailed = try {
            port.write(selected)
            port.read() != selected
        } catch (_: Exception) { true }
        refreshPermission()
        return !saveFailed
    }

    fun onLeave() { stopPreview(); opened = false; state = RingtoneState() }
    fun stopPreview() { try { port.stopPreview() } catch (_: Exception) { /* Best effort cleanup. */ } }
}
