// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.plainphone.ui.trusted.FamilyEvent

class FamilyEventTest {
    @Test fun entryEventsDoNotExposePersonalDetails() {
        assertEquals("Name(redacted)", FamilyEvent.Name("Sam").toString())
        assertEquals("Number(redacted)", FamilyEvent.Number("555-0100").toString())
        assertEquals("SpokenName(redacted)", FamilyEvent.SpokenName("Jo").toString())
    }
}
