/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.plainphone.ui.user.withoutBidiControls

class BidiDisplayTest {
    @Test fun removesEveryDirectionalControlFromHostPathAndPreview() {
        val controls = ('\u202A'..'\u202E') + ('\u2066'..'\u2069') + listOf('\u200E', '\u200F', '\u061C')
        for (control in controls) {
            assertEquals("example.invalid/path", "exam${control}ple.invalid/pa${control}th".withoutBidiControls())
            assertEquals("Plainphone: example.invalid/path", "${control}Plainphone: example.invalid/path$control".withoutBidiControls())
        }
    }
    @Test fun preservesOrdinaryUnicodeAndHostWrappingInput() {
        val display = "עברית example.invalid/a-long-path"
        assertEquals(display, display.withoutBidiControls())
    }
}
