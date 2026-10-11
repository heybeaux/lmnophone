// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.counts

import java.io.Closeable

data class CommunicationCounts(val missedCalls: Int? = null, val unreadTexts: Int? = null)
data class CountQuery(val source: String, val uri: String, val projection: List<String>,
    val selection: String, val selectionArgs: Array<String>? = null, val sortOrder: String? = null)
interface CountCursor : Closeable { val count: Int }
interface CountQueryPort {
    fun isUserUnlocked(): Boolean
    fun hasPermission(permission: String): Boolean
    fun query(query: CountQuery): CountCursor?
}

/** The only query plan. A cursor exposes its count and close, never row values. */
class CommunicationCountSource(private val port: CountQueryPort,
    private val log: (String, String, String?) -> Unit = { _, _, _ -> }) {
    fun read(): CommunicationCounts = CommunicationCounts(read(CountKind.CALLS), read(CountKind.MESSAGES))
    fun read(kind: CountKind): Int? {
        if (!runCatching { port.isUserUnlocked() }.getOrDefault(false)) return null
        return permitted(kind.permission) {
            count(when (kind) {
                CountKind.CALLS -> CountQuery("calls", "content://call_log/calls", listOf("_id"),
                    "type = 3 AND new = 1 AND is_read IS NOT 1")
                CountKind.MESSAGES -> CountQuery("messages", "content://mms-sms/complete-conversations",
                    listOf("_id", "transport_type"), "read = 0")
            })
        }
    }
    private fun permitted(permission: String, read: () -> Int?): Int? = try {
        if (port.hasPermission(permission)) read() else null
    } catch (_: Exception) { null }
    private fun count(query: CountQuery): Int? = try {
        val result = port.query(query)?.use { it.count.takeIf { count -> count >= 0 } }
        log(query.source, if (result == null) "unavailable" else "available", null)
        result
    } catch (e: Exception) {
        log(query.source, "unavailable", e.javaClass.simpleName)
        null
    }
}

interface CountPreferenceStore {
    fun read(key: String): Boolean?
    fun write(key: String, value: Boolean)
}
class CountPreferences(private val store: CountPreferenceStore) {
    var enabled: Boolean
        get() = store.read("show_communication_counts") ?: false
        set(value) = store.write("show_communication_counts", value)
}
fun interface CountCancellation { fun cancel() }
interface CountWork { fun schedule(delayMillis: Long, block: () -> Unit): CountCancellation }

/** Last completed read, independent of whether a fresh number is currently shown. */
enum class CountAvailability { UNKNOWN, AVAILABLE, UNAVAILABLE }

enum class CountKind(val permission: String, val supportedPackage: String) {
    CALLS("android.permission.READ_CALL_LOG", "com.google.android.dialer"),
    MESSAGES("android.permission.READ_SMS", "com.google.android.apps.messaging"),
}
data class CountAccess(val unlocked: Boolean, val permissionGranted: Boolean,
    val rolePackage: String?, val installedAndEnabled: Boolean, val homePackage: String?) {
    fun supported(kind: CountKind) = installedAndEnabled && rolePackage == kind.supportedPackage && homePackage == kind.supportedPackage
    fun allowed(kind: CountKind) = unlocked && permissionGranted && supported(kind)
}
fun interface CountReader {
    /** Returns immediately. Completion must be delivered on the controller's scheduler. */
    fun read(kind: CountKind, complete: (Int?) -> Unit): CountCancellation
}

/** Timers never share the query worker. All transitions run on one scheduler (Android main). */
class CountController(
    private val preferences: CountPreferences,
    private val work: CountWork,
    private val reader: CountReader,
    private val access: (CountKind) -> CountAccess,
    private val elapsedRealtime: () -> Long,
    private val observe: (String, Boolean, () -> Unit) -> CountCancellation,
    private val grant: () -> Unit,
    private val changed: () -> Unit = {},
) {
    var counts = CommunicationCounts(); private set
    var enabled = preferences.enabled; private set
    var unsupportedApps = false; private set
    private var visible = false
    private var settingsVisible = false
    private var generation = 0L
    private var pending: CountCancellation? = null
    private var periodic: CountCancellation? = null
    private var gateCheck: CountCancellation? = null
    private var deadline: CountCancellation? = null
    private val observers = mutableListOf<CountCancellation>()
    private val gates = mutableMapOf<CountKind, CountAccess>()
    private class Slot {
        var availability = CountAvailability.UNKNOWN
        var attempt = 0L
        var running = false
        var dirty = false
        var query: CountCancellation? = null
        var timeout: CountCancellation? = null
        var expiry: CountCancellation? = null
    }
    private val slots = CountKind.entries.associateWith { Slot() }
    fun availability(kind: CountKind): CountAvailability =
        if (gates[kind]?.allowed(kind) == false) CountAvailability.UNAVAILABLE
        else slots.getValue(kind).availability

    private fun active() = enabled && (visible || settingsVisible)
    private fun gate(kind: CountKind) = runCatching { access(kind) }.getOrElse {
        CountAccess(false, false, null, false, null)
    }
    private fun put(kind: CountKind, value: Int?) {
        counts = when (kind) {
            CountKind.CALLS -> counts.copy(missedCalls = value)
            CountKind.MESSAGES -> counts.copy(unreadTexts = value)
        }
        changed()
    }
    fun setEnabled(value: Boolean) {
        if (value == enabled) return
        preferences.enabled = value
        enabled = value
        stop()
        if (value) {
            // A fresh explicit on only. Startup/resume never restores a revoked grant.
            runCatching { grant() }
            start()
        }
        changed()
    }
    fun setVisible(value: Boolean) {
        if (visible == value) return
        visible = value; stop(); start()
    }
    fun setSettingsVisible(value: Boolean) {
        if (settingsVisible == value) return
        settingsVisible = value; stop(); start()
    }
    private fun start() {
        if (!active()) return
        // Registration precedes even the first read.
        if (visible) for (uri in listOf("content://call_log/calls", "content://mms-sms/")) {
            runCatching { observers += observe(uri, true, ::externalChange) }
        }
        refreshGates()
        refresh(0)
        schedulePeriodic()
        scheduleGateCheck()
    }
    private fun refreshGates(): Boolean {
        val next = CountKind.entries.associateWith(::gate)
        val different = gates.isNotEmpty() && gates != next
        gates.clear(); gates.putAll(next)
        unsupportedApps = next.any { (kind, state) -> !state.supported(kind) }
        next.filter { (kind, state) -> !state.allowed(kind) }.forEach { (kind, _) -> put(kind, null) }
        changed()
        return different
    }
    private fun scheduleGateCheck() {
        gateCheck = work.schedule(1_000) {
            if (active()) {
                // SMS role-change broadcasts target only the old/new holders. Poll read-only gates.
                if (refreshGates()) externalChange()
                scheduleGateCheck()
            }
        }
    }
    private fun schedulePeriodic() {
        periodic = work.schedule(10_000) {
            if (active()) { refresh(0); schedulePeriodic() }
        }
    }
    /** Package/role/permission changes and provider callbacks share the publication fence. */
    fun externalChange() {
        if (!enabled) return
        ++generation
        if (!active()) return
        slots.values.forEach { it.dirty = true }
        // Never postpone this deadline during a callback stream.
        if (deadline == null) deadline = work.schedule(1_000) {
            deadline = null
            slots.forEach { (kind, slot) -> if (slot.dirty) put(kind, null) }
        }
        refreshGates()
        refresh(150)
    }
    private fun cancelRead(slot: Slot) {
        slot.running = false
        slot.query?.cancel(); slot.query = null
        slot.timeout?.cancel(); slot.timeout = null
    }
    private fun refresh(delay: Long) {
        if (!active()) return
        pending?.cancel()
        slots.values.forEach(::cancelRead)
        val request = ++generation
        pending = work.schedule(delay) {
            if (request != generation || !active()) return@schedule
            refreshGates()
            CountKind.entries.forEach { kind -> begin(kind, request) }
        }
    }
    private fun begin(kind: CountKind, request: Long) {
        val slot = slots.getValue(kind)
        if (!gate(kind).allowed(kind)) { refreshGates(); put(kind, null); return }
        val started = elapsedRealtime()
        val attempt = ++slot.attempt
        slot.running = true
        slot.timeout = work.schedule(3_000) {
            if (slot.running && slot.attempt == attempt && request == generation) {
                slot.availability = CountAvailability.UNAVAILABLE
                cancelRead(slot); put(kind, null)
            }
        }
        slot.query = reader.read(kind) { value ->
            if (!active() || request != generation || !slot.running || slot.attempt != attempt) return@read
            slot.running = false
            slot.timeout?.cancel(); slot.timeout = null
            val finalGate = gate(kind)
            if (finalGate != gates[kind]) {
                // A gate changed without a broadcast while this query ran.
                externalChange()
                return@read
            }
            val allowed = finalGate.allowed(kind)
            // Recheck all gates at publication, even when no callback was received.
            val valid = allowed && elapsedRealtime() - started <= 3_000
            slot.expiry?.cancel()
            slot.dirty = false
            val result = value?.takeIf { valid && it >= 0 }
            slot.availability = if (result == null) CountAvailability.UNAVAILABLE else CountAvailability.AVAILABLE
            put(kind, result)
            if (!allowed) {
                refreshGates()
            } else if (value != null && valid) {
                slot.expiry = work.schedule((started + 13_000 - elapsedRealtime()).coerceAtLeast(0)) {
                    put(kind, null)
                }
            }
            if (slots.values.none { it.dirty }) { deadline?.cancel(); deadline = null }
        }
    }
    private fun stop() {
        ++generation
        pending?.cancel(); pending = null
        periodic?.cancel(); periodic = null
        gateCheck?.cancel(); gateCheck = null
        deadline?.cancel(); deadline = null
        slots.values.forEach { cancelRead(it); it.expiry?.cancel(); it.expiry = null; it.dirty = false }
        observers.forEach { runCatching { it.cancel() } }; observers.clear()
        gates.clear()
        counts = CommunicationCounts()
        if (!enabled) slots.values.forEach { it.availability = CountAvailability.UNKNOWN }
        changed()
    }
    fun close() { visible = false; settingsVisible = false; stop() }
}
