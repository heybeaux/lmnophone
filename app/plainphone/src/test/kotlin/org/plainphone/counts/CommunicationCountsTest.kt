// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.counts

import org.junit.Assert.*
import org.junit.Test

class CommunicationCountsTest {
    private class Store : CountPreferenceStore {
        val data = mutableMapOf<String, Boolean>()
        override fun read(key: String) = data[key]
        override fun write(key: String, value: Boolean) { data[key] = value }
    }
    private class Port : CountQueryPort {
        var unlocked = true
        val denied = mutableSetOf<String>()
        val queries = mutableListOf<CountQuery>()
        val results = mutableMapOf<String, Int?>("calls" to 1, "sms" to 2, "mms" to 3)
        var failure: String? = null
        var closed = 0
        override fun isUserUnlocked() = unlocked
        override fun hasPermission(permission: String) = permission !in denied
        override fun query(query: CountQuery): CountCursor? {
            queries += query
            if (failure == query.source) throw IllegalArgumentException("must never be logged")
            val value = results[query.source] ?: return null
            return object : CountCursor {
                override val count = value
                override fun close() { closed++ }
            }
        }
    }
    @Test fun exactQueriesUseOnlyIdsNoSortAndCloseAllCursors() {
        val p = Port()
        assertEquals(CommunicationCounts(1, 5), CommunicationCountSource(p).read())
        assertEquals(listOf(
            CountQuery("calls", "content://call_log/calls", listOf("_id"), "type = 3 AND new = 1 AND is_read IS NOT 1"),
            CountQuery("sms", "content://sms", listOf("_id"), "type = 1 AND read = 0"),
            CountQuery("mms", "content://mms", listOf("_id"), "msg_box = 1 AND read = 0")
        ), p.queries)
        assertTrue(p.queries.all { it.sortOrder == null && it.selectionArgs == null })
        assertEquals(3, p.closed)
    }
    @Test fun eitherMessageFailureHidesTheWholeSum() {
        for (source in listOf("sms", "mms")) for (throws in listOf(false, true)) {
            val p = Port()
            if (throws) p.failure = source else p.results[source] = null
            assertEquals(CommunicationCounts(1, null), CommunicationCountSource(p).read())
        }
    }
    @Test fun callFailureDoesNotHideMessages() {
        val p = Port(); p.failure = "calls"
        assertEquals(CommunicationCounts(null, 5), CommunicationCountSource(p).read())
    }
    @Test fun lockedAndMissingPermissionsNeverQuery() {
        val p = Port(); p.unlocked = false
        assertEquals(CommunicationCounts(), CommunicationCountSource(p).read()); assertTrue(p.queries.isEmpty())
        p.unlocked = true; p.denied += listOf("android.permission.READ_SMS", "android.permission.READ_CALL_LOG")
        assertEquals(CommunicationCounts(), CommunicationCountSource(p).read()); assertTrue(p.queries.isEmpty())
    }
    @Test fun successfulZeroIsDifferentFromUnavailable() {
        val p = Port(); p.results.keys.toList().forEach { p.results[it] = 0 }
        assertEquals(CommunicationCounts(0, 0), CommunicationCountSource(p).read())
    }
    @Test fun diagnosticsNeverIncludeExceptionMessageOrQueryValues() {
        val p = Port(); p.failure = "calls"
        val logs = mutableListOf<String>()
        CommunicationCountSource(p) { source, outcome, type -> logs += "$source $outcome $type" }.read()
        assertTrue(logs.contains("calls unavailable IllegalArgumentException"))
        assertFalse(logs.joinToString().contains("must never"))
    }
    @Test fun newInstallAndUpgradeWithoutKeyDefaultOffAndPersistOnlyPreference() {
        val store = Store(); store.data["old_setting"] = true
        assertFalse(CountPreferences(store).enabled)
        CountPreferences(store).enabled = true
        assertTrue(CountPreferences(store).enabled)
        CountPreferences(store).enabled = false
        assertFalse(CountPreferences(store).enabled)
        assertEquals(setOf("old_setting", "show_communication_counts"), store.data.keys)
    }
    private class Work : CountWork {
        data class Job(val delay: Long, val block: () -> Unit, var cancelled: Boolean = false)
        val jobs = mutableListOf<Job>()
        override fun schedule(delayMillis: Long, block: () -> Unit): CountCancellation {
            val job = Job(delayMillis, block); jobs += job
            return CountCancellation { job.cancelled = true }
        }
        fun runLast() { jobs.last().block() }
    }
    @Test fun visibilityRefreshesAndObservesCombinedUriWithDescendants() {
        val work = Work(); val observed = mutableListOf<Pair<String, Boolean>>()
        var stopped = 0; var reads = 0
        val c = CountController(CountPreferences(Store()), work, { reads++; CommunicationCounts(1, 2) },
            { uri, descendants, _ -> observed += uri to descendants; CountCancellation { stopped++ } }, {})
        c.setVisible(true); assertTrue(work.jobs.isEmpty())
        c.setEnabled(true); work.runLast()
        assertEquals(CommunicationCounts(1, 2), c.counts)
        assertEquals(listOf("content://call_log/calls" to true, "content://mms-sms/" to true), observed)
        c.setVisible(false); assertEquals(2, stopped); assertEquals(CommunicationCounts(), c.counts)
        c.setVisible(true); work.runLast(); assertEquals(2, reads)
    }
    @Test fun offCancelsWorkClearsImmediatelyAndLateResultsCannotReturn() {
        val work = Work(); val callbacks = mutableListOf<() -> Unit>(); var grants = 0
        lateinit var c: CountController
        c = CountController(CountPreferences(Store()), work, {
            c.setEnabled(false) // A result already in flight when off was selected.
            CommunicationCounts(8, 9)
        }, { _, _, cb -> callbacks += cb; CountCancellation {} }, { grants++ })
        c.setVisible(true); c.setEnabled(true)
        callbacks.first()(); callbacks.last()()
        assertTrue(work.jobs.dropLast(1).all { it.cancelled })
        work.runLast(); assertEquals(CommunicationCounts(), c.counts)
        val size = work.jobs.size; callbacks.first()(); assertEquals(size, work.jobs.size)
        assertEquals(1, grants)
    }
    @Test fun restartDoesNotGrantAgainAndUnavailableClearsOldCount() {
        val store = Store(); CountPreferences(store).enabled = true
        val work = Work(); var value = CommunicationCounts(1, 2); var grants = 0
        val c = CountController(CountPreferences(store), work, { value }, { _, _, _ -> CountCancellation {} }, { grants++ })
        c.setVisible(true); work.runLast(); assertEquals(value, c.counts)
        c.setVisible(false); value = CommunicationCounts(); c.setVisible(true); work.runLast()
        assertEquals(value, c.counts); assertEquals(0, grants)
    }
}
