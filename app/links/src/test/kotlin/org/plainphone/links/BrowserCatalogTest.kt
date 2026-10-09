// SPDX-License-Identifier: Apache-2.0
package org.plainphone.links

import org.plainphone.core.PackageName
import kotlin.test.*

class BrowserCatalogTest {
    @Test fun `bundled catalog has a version and a source for every entry`() {
        val catalog = BrowserCatalog.bundled()
        assertTrue(catalog.version >= 1)
        assertTrue(catalog.entries.isNotEmpty())
        catalog.entries.forEach {
            assertTrue(it.source.isNotBlank(), it.packageName.value)
            assertTrue(it.notes.isNotBlank(), it.packageName.value)
        }
    }

    @Test fun `bundled catalog holds the measured entries`() {
        val catalog = BrowserCatalog.bundled()
        val expected = mapOf(
            "com.android.chrome" to (EntryKind.BROWSER to EntryStatus.EMULATOR),
            "com.google.android.googlequicksearchbox" to (EntryKind.ASSISTANT to EntryStatus.EMULATOR),
            "com.google.android.apps.bard" to (EntryKind.ASSISTANT to EntryStatus.UNVERIFIED),
            "com.microsoft.office.outlook" to (EntryKind.REVIEW to EntryStatus.DEVICE),
            "com.google.android.gm" to (EntryKind.REVIEW to EntryStatus.DEVICE),
        )
        expected.forEach { (pkg, kindStatus) ->
            val entry = catalog[PackageName(pkg)]
            assertNotNull(entry, pkg)
            assertEquals(kindStatus, entry.kind to entry.status, pkg)
        }
    }

    @Test fun `bundled catalog never lists Play Store, Play services or Plainphone`() {
        val catalog = BrowserCatalog.bundled()
        listOf("com.android.vending", "com.google.android.gms").forEach { assertNull(catalog[PackageName(it)], it) }
        assertTrue(catalog.entries.none { it.packageName.value.startsWith("com.plainphone.") })
    }

    @Test fun `review entries disclose in-app WebView browsing`() {
        BrowserCatalog.bundled().entries.filter { it.kind == EntryKind.REVIEW }.forEach {
            assertTrue(it.notes.contains("WebView"), it.packageName.value)
        }
    }

    @Test fun `parser accepts comments and blank lines`() {
        val catalog = BrowserCatalog.parse("# c\n\nversion=7\n# c\norg.example.b\tBROWSER\tUNVERIFIED\tsrc\tnote\n\n")
        assertEquals(7, catalog.version)
        assertEquals(listOf(PackageName("org.example.b")), catalog.entries.map { it.packageName })
    }

    @Test fun `parser rejects malformed catalogs`() {
        val bad = mapOf(
            "missing version" to "org.example.b\tBROWSER\tUNVERIFIED\tsrc\tnote",
            "version after entries" to "org.example.b\tBROWSER\tUNVERIFIED\tsrc\tnote\nversion=1",
            "zero version" to "version=0",
            "two versions" to "version=1\nversion=2",
            "unknown kind" to "version=1\norg.example.b\tSEARCH\tUNVERIFIED\tsrc\tnote",
            "unknown status" to "version=1\norg.example.b\tBROWSER\tMAYBE\tsrc\tnote",
            "missing source" to "version=1\norg.example.b\tBROWSER\tUNVERIFIED\t \tnote",
            "missing notes" to "version=1\norg.example.b\tBROWSER\tUNVERIFIED\tsrc\t",
            "too few columns" to "version=1\norg.example.b\tBROWSER\tUNVERIFIED\tsrc",
            "too many columns" to "version=1\norg.example.b\tBROWSER\tUNVERIFIED\tsrc\tnote\textra",
            "bad package" to "version=1\norg..b\tBROWSER\tUNVERIFIED\tsrc\tnote",
            "duplicate package" to "version=1\norg.example.b\tBROWSER\tUNVERIFIED\tsrc\tnote\norg.example.b\tREVIEW\tUNVERIFIED\tsrc\tnote WebView",
        )
        bad.forEach { (name, text) -> assertFailsWith<IllegalArgumentException>(name) { BrowserCatalog.parse(text) } }
    }
}
