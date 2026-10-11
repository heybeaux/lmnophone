// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package com.plainphone.spike.counts

import org.junit.Assert.assertEquals
import org.junit.Test

/** THROWAWAY: privacy contract written before the probe. */
class QueryPlanTest {
    @Test fun exactProjectionAndSelection() {
        assertEquals(listOf("_id"), QueryPlan.projection)
        assertEquals("type = 3 AND new = 1", QueryPlan.callsNew)
        assertEquals("type = 3 AND is_read = 0", QueryPlan.callsUnread)
        assertEquals("type = 3 AND new = 1 AND is_read IS NOT 1", QueryPlan.callsUnacknowledged)
        assertEquals("type = 1 AND read = 0", QueryPlan.sms)
        assertEquals("msg_box = 1 AND read = 0", QueryPlan.mms)
    }
}
