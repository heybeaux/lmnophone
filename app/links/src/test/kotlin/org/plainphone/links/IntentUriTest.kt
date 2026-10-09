// SPDX-License-Identifier: Apache-2.0
package org.plainphone.links

import org.plainphone.core.PackageName
import kotlin.test.*

/** Mirrors `Intent.parseUri(uri, URI_INTENT_SCHEME)` for the fields the link decision needs. */
class IntentUriTest {
    private data class Row(val uri: String, val data: String?, val pkg: String?, val component: String?, val action: String?, val fallback: String? = null)

    private val rows = listOf(
        // Cases from spikes/emulator/tools/q4-links-v3.txt (PLA-23 V3).
        Row("intent://example.com/#Intent;scheme=https;package=com.android.chrome;end", "https://example.com/", "com.android.chrome", null, "android.intent.action.VIEW"),
        Row("intent://example.com/#Intent;scheme=https;end", "https://example.com/", null, null, "android.intent.action.VIEW"),
        Row("intent:#Intent;component=com.android.settings/.Settings;end", null, null, "com.android.settings", "android.intent.action.VIEW"),
        Row("intent:#Intent;action=android.settings.SETTINGS;component=com.android.settings/.Settings;end", null, null, "com.android.settings", "android.settings.SETTINGS"),
        Row("intent://open#Intent;scheme=ppfx;package=com.plainphone.fx.applinks;end", "ppfx://open", "com.plainphone.fx.applinks", null, "android.intent.action.VIEW"),
        Row("intent://nothing#Intent;scheme=nosuchscheme;S.browser_fallback_url=https%3A%2F%2Fexample.com;end", "nosuchscheme://nothing", null, null, "android.intent.action.VIEW", "https://example.com"),
        // Percent-decoded values, last '#' wins, no scheme keeps the bare remainder.
        Row("intent://a.example/p#x#Intent;scheme=https;action=a%2Eb;end", "https://a.example/p#x", null, null, "a.b"),
        Row("intent:foo#Intent;end", "foo", null, null, "android.intent.action.VIEW"),
    )

    @Test fun `parse table`() {
        rows.forEach { row ->
            val parsed = assertNotNull(IntentUri.parse(row.uri), row.uri)
            assertEquals(row.data, parsed.data, row.uri)
            assertEquals(row.pkg?.let(::PackageName), parsed.packageName, row.uri)
            assertEquals(row.component?.let(::PackageName), parsed.componentPackage, row.uri)
            assertEquals(row.action, parsed.action, row.uri)
            assertEquals(row.fallback, parsed.browserFallbackUrl, row.uri)
        }
    }

    @Test fun `selector fields go to the selector`() {
        val parsed = assertNotNull(IntentUri.parse("intent:#Intent;package=org.example.outer;SEL;scheme=https;package=org.example.sel;end"))
        assertEquals(PackageName("org.example.outer"), parsed.packageName)
        val selector = assertNotNull(parsed.selector)
        assertEquals(PackageName("org.example.sel"), selector.packageName)
    }

    @Test fun `malformed intent URIs are rejected`() {
        listOf(
            "https://example.com",
            "intent://example.com",
            "intent://example.com#Intent;scheme=https",
            "intent://example.com#Intent;scheme=https;package=bad..pkg;end",
        ).forEach { assertNull(IntentUri.parse(it), it) }
    }
}

class IntentUriLenientTest {
    @Test fun `like Uri decode and ComponentName unflatten, bad escapes and slashless components are not errors`() {
        val parsed = assertNotNull(IntentUri.parse("intent:#Intent;component=noslash;S.browser_fallback_url=%zz;end"))
        assertNull(parsed.componentPackage)
        assertEquals("%zz", parsed.browserFallbackUrl)
    }
}

class IntentUriDecodeTest {
    @Test fun `percent decoding handles UTF-8, signs and trailing percent`() {
        fun fallback(raw: String) = IntentUri.parse("intent:#Intent;S.browser_fallback_url=$raw;end")?.browserFallbackUrl
        assertEquals("caf\u00e9", fallback("caf%C3%A9"))
        assertEquals("%+1", fallback("%+1"))
        assertEquals("a%", fallback("a%"))
        assertEquals("a%2", fallback("a%2"))
    }
}
