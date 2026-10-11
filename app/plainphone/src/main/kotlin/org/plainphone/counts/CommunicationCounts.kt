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
    fun read(): CommunicationCounts {
        if (!runCatching { port.isUserUnlocked() }.getOrDefault(false)) return CommunicationCounts()
        val calls = permitted("android.permission.READ_CALL_LOG") {
            count(CountQuery("calls", "content://call_log/calls", listOf("_id"), "type = 3 AND new = 1 AND is_read IS NOT 1"))
        }
        val messages = permitted("android.permission.READ_SMS") {
            val sms = count(CountQuery("sms", "content://sms", listOf("_id"), "type = 1 AND read = 0"))
            val mms = count(CountQuery("mms", "content://mms", listOf("_id"), "msg_box = 1 AND read = 0"))
            if (sms == null || mms == null) null else Math.addExact(sms, mms)
        }
        return CommunicationCounts(calls, messages)
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

/** Serialized state transitions; reads run outside the monitor and stale completions are discarded. */
class CountController(
    private val preferences: CountPreferences,
    private val work: CountWork,
    private val read: () -> CommunicationCounts,
    private val observe: (String, Boolean, () -> Unit) -> CountCancellation,
    private val grant: () -> Unit,
    private val changed: () -> Unit = {},
) {
    @Volatile var counts = CommunicationCounts(); private set
    @Volatile var enabled = preferences.enabled; private set
    private var visible = false
    private var settingsVisible = false
    private var generation = 0L
    private var pending: CountCancellation? = null
    private val observers = mutableListOf<CountCancellation>()

    @Synchronized fun setEnabled(value: Boolean) {
        if (value == enabled) return
        preferences.enabled = value
        enabled = value
        stop()
        if (value) {
            // Never called on startup/resume: a revoked permission stays unavailable.
            runCatching { grant() }
            start()
        }
        changed()
    }
    @Synchronized fun setVisible(value: Boolean) {
        if (visible == value) return
        visible = value
        stop(); start()
    }
    @Synchronized fun setSettingsVisible(value: Boolean) {
        if (settingsVisible == value) return
        settingsVisible = value
        stop(); start()
    }
    private fun start() {
        if (!enabled || (!visible && !settingsVisible)) return
        if (visible) {
            for (uri in listOf("content://call_log/calls", "content://mms-sms/")) {
                try { observers += observe(uri, true) { refresh(150) } }
                catch (_: Exception) { /* Resume still performs a fresh read. */ }
            }
        }
        refresh(0)
    }
    @Synchronized private fun refresh(delay: Long) {
        if (!enabled || (!visible && !settingsVisible)) return
        pending?.cancel()
        val request = ++generation
        pending = work.schedule(delay) {
            val current = synchronized(this) { request == generation && enabled }
            if (current) {
                val result = try { read() } catch (_: Exception) { CommunicationCounts() }
                synchronized(this) {
                    if (request == generation && enabled) { counts = result; changed() }
                }
            }
        }
    }
    private fun stop() {
        ++generation
        pending?.cancel(); pending = null
        observers.forEach { runCatching { it.cancel() } }; observers.clear()
        counts = CommunicationCounts()
        changed()
    }
    @Synchronized fun close() { visible = false; settingsVisible = false; stop() }
}
