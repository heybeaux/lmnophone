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
        val results = mutableMapOf<String, Int?>("calls" to 1, "messages" to 5)
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
    @Test fun exactQueriesUseOnlyIdsAndTransportNoSortAndCloseAllCursors() {
        val p = Port()
        assertEquals(CommunicationCounts(1, 5), CommunicationCountSource(p).read())
        assertEquals(listOf(
            CountQuery("calls", "content://call_log/calls", listOf("_id"), "type = 3 AND new = 1 AND is_read IS NOT 1"),
            CountQuery("messages", "content://mms-sms/complete-conversations", listOf("_id", "transport_type"), "read = 0")
        ), p.queries)
        assertTrue(p.queries.all { it.sortOrder == null && it.selectionArgs == null })
        assertEquals(2, p.closed)
    }
    @Test fun combinedMessageFailureNeverFallsBack() {
        for (source in listOf("messages")) for (throws in listOf(false, true)) {
            val p = Port()
            if (throws) p.failure = source else p.results[source] = null
            assertEquals(CommunicationCounts(1, null), CommunicationCountSource(p).read())
        }
    }
    @Test fun alternatingAtomicProviderSnapshotsNeverProduceTheTwelveRowSum() {
        val p = Port()
        val source = CommunicationCountSource(p)
        // The provider alternates whole database snapshots: (SMS=5,MMS=0), then
        // (SMS=0,MMS=7). Its single statement returns the count from one snapshot.
        for (snapshot in listOf(5, 7, 5, 7)) {
            p.results["messages"] = snapshot
            val value = source.read().unreadTexts
            assertEquals(snapshot, value)
            assertNotEquals(12, value)
        }
        assertEquals(4, p.queries.count { it.uri == "content://mms-sms/complete-conversations" })
        assertFalse(p.queries.any { it.uri == "content://sms" || it.uri == "content://mms" })
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
}
