// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CoreTypesTest {
    @Test
    fun windowKindsMatchTheLanesDoc() {
        assertEquals(
            listOf("PLAY", "SETTINGS", "APPROVE", "UPDATE", "WEB", "WIFI_SIGNIN"),
            WindowKind.entries.map { it.name },
        )
    }
}
