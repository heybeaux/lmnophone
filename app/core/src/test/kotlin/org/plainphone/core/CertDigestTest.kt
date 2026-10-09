// SPDX-License-Identifier: Apache-2.0
package org.plainphone.core

import kotlin.test.*

class CertDigestTest {
    private val canonical = "ab".repeat(32)

    @Test fun `constructor keeps the lane C canonical contract`() {
        assertEquals(canonical, CertDigest(canonical).value)
        for (invalid in listOf("", "a".repeat(63), "a".repeat(65), "g".repeat(64), canonical.uppercase())) {
            assertFailsWith<IllegalArgumentException> { CertDigest(invalid) }
        }
    }

    @Test fun `parse accepts colon separated uppercase digests`() {
        assertEquals(CertDigest(canonical), CertDigest.parse(canonical.uppercase().chunked(2).joinToString(":")))
        assertEquals(CertDigest(canonical), CertDigest.parse(canonical.uppercase()))
    }

    @Test fun `parse does not silently accept spaces or a different digest`() {
        assertFailsWith<IllegalArgumentException> { CertDigest.parse(" $canonical") }
        assertNotEquals(CertDigest(canonical), CertDigest("cd".repeat(32)))
    }
}
