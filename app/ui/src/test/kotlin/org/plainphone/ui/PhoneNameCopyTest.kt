// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.plainphone.ui.trusted.configuredUserName

class PhoneNameCopyTest {
    private val fallback = "the person using this phone"

    @Test fun noNameSetWhenBlankOrTheFallbackWords() {
        listOf("", "   ", fallback, " $fallback ", "\u2066$fallback\u2069").forEach { name ->
            assertNull("'$name' is not a configured name", configuredUserName(name, fallback))
        }
    }
    @Test fun configuredNamesAreKeptIncludingARealPersonNamedSam() {
        assertEquals("Sam", configuredUserName("Sam", fallback))
        assertEquals("Jo", configuredUserName("\u202EJo", fallback))
        assertEquals("The person using this phone", configuredUserName("The person using this phone", fallback))
    }
}
