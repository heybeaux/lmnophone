// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.counts

import org.junit.Assert.assertEquals
import org.junit.Test

/** Relational DISTINCT model of the provider projection; no message data or Android dependency.
 * The provider also selects normalized_date (SMS ms; MMS seconds * 1000).
 * With these non-null integral/text fixtures, Kotlin distinct has SQL UNION equality semantics.
 * Emulator instrumentation separately proves the real provider's count under the target UID.
 */
class CombinedUnionModelTest {
    private data class Row(val id: Long, val normalizedDate: Long, val transport: String)
    private fun union(smsDate: Long, mmsDate: Long) = listOf(
        Row(800001, smsDate, "sms"), Row(800001, mmsDate * 1000, "mms"))

    @Test fun equalIdAndNormalizedDateNeedTransportToKeepBothRows() {
        val rows = union(1791670000000, 1791670000)
        assertEquals(2, rows.distinct().size)
        assertEquals(1, rows.map { it.id to it.normalizedDate }.distinct().size)
    }
    @Test fun unequalDatesWouldMaskAMissingTransportDiscriminator() {
        val rows = union(1791670000001, 1791670000)
        assertEquals(2, rows.distinct().size)
        assertEquals(2, rows.map { it.id to it.normalizedDate }.distinct().size)
    }
}
