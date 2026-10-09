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

    /** AOSP `parseUriInternal`: `if (baseIntent.mPackage == null) baseIntent.setSelector(intent)`. */
    @Test fun `the selector is dropped when the outer intent has a package`() {
        val parsed = assertNotNull(IntentUri.parse("intent:#Intent;package=org.example.outer;SEL;scheme=https;package=org.example.sel;end"))
        assertEquals(PackageName("org.example.outer"), parsed.packageName)
        assertNull(parsed.selector)
    }

    @Test fun `the selector is kept when the outer intent has no package`() {
        val parsed = assertNotNull(IntentUri.parse("intent:#Intent;SEL;scheme=https;package=org.example.sel;end"))
        val selector = assertNotNull(parsed.selector)
        assertEquals(PackageName("org.example.sel"), selector.packageName)
        assertEquals("https:", selector.data)
        assertNull(parsed.packageName)
    }

    @Test fun `the selector is kept next to an outer component`() {
        val parsed = assertNotNull(IntentUri.parse("intent:#Intent;component=org.example.outer/.Main;SEL;package=org.example.sel;end"))
        assertEquals(PackageName("org.example.outer"), parsed.componentPackage)
        assertEquals(PackageName("org.example.sel"), assertNotNull(parsed.selector).packageName)
    }

    /** AOSP: with no '#', `parseUri` does not throw; it returns `VIEW` with the whole `intent:` string as data. */
    @Test fun `no hash gives a plain VIEW of the raw data`() {
        val parsed = assertNotNull(IntentUri.parse("intent://example.com/path"))
        assertEquals(ACTION_VIEW, parsed.action)
        assertEquals("intent://example.com/path", parsed.data)
        assertNull(parsed.packageName)
        assertNull(parsed.componentPackage)
    }

    /** AOSP `getIntentOld`: a '#' that is not '#Intent;' is the old fragment format. */
    @Test fun `old format fragments`() {
        val settings = assertNotNull(IntentUri.parse("intent:x#action(android.settings.SETTINGS)component(com.android.settings!com.android.settings.Settings)"))
        assertEquals("android.settings.SETTINGS", settings.action)
        assertEquals(PackageName("com.android.settings"), settings.componentPackage)
        assertEquals("intent:x", settings.data)

        val plain = assertNotNull(IntentUri.parse("intent://example.com/#frag"))
        assertEquals(ACTION_VIEW, plain.action)
        assertEquals("intent://example.com/#frag", plain.data)
        assertNull(plain.componentPackage)

        val unclosedComponent = assertNotNull(IntentUri.parse("intent:x#component(com.android.settings"))
        assertNull(unclosedComponent.componentPackage)
        assertEquals("intent:x", unclosedComponent.data)

        val fallback = assertNotNull(IntentUri.parse("intent:x#extras(Sbrowser_fallback_url=https%3A%2F%2Fexample.com)"))
        assertEquals("https://example.com", fallback.browserFallbackUrl)
    }

    /** AOSP: `android-app://pkg[/scheme[/authority[/path]]]`; the package is set after the selector is attached. */
    @Test fun `android-app URIs`() {
        val web = assertNotNull(IntentUri.parse("android-app://com.plainphone.app/https/example.com/a?b=c"))
        assertEquals(PackageName("com.plainphone.app"), web.packageName)
        assertEquals("https://example.com/a?b=c", web.data)
        assertEquals(ACTION_VIEW, web.action)

        val pkgOnly = assertNotNull(IntentUri.parse("android-app://org.example.app"))
        assertEquals("android.intent.action.MAIN", pkgOnly.action)
        assertNull(pkgOnly.data)

        assertEquals("ppfx:", assertNotNull(IntentUri.parse("android-app://org.example.app/ppfx")).data)
        assertEquals(ACTION_VIEW, assertNotNull(IntentUri.parse("android-app://org.example.app/#Intent;action=android.intent.action.VIEW;end")).action)
        assertNull(IntentUri.parse("android-app:org.example.app").let { it?.packageName })

        val withSelector = assertNotNull(IntentUri.parse("android-app://org.example.app/https/e.example/#Intent;SEL;package=org.example.sel;end"))
        assertEquals(PackageName("org.example.app"), withSelector.packageName)
        assertEquals(PackageName("org.example.sel"), assertNotNull(withSelector.selector).packageName)
        assertNull(IntentUri.parse("android-app:/"))
    }

    @Test fun `a later malformed component clears the earlier one`() {
        assertNull(assertNotNull(IntentUri.parse("intent:#Intent;component=com.android.chrome/.Main;component=noslash;end")).componentPackage)
    }

    /** AOSP stops at the first item that starts with "end", even "endpoint=". */
    @Test fun `an item starting with end ends the list`() {
        val parsed = assertNotNull(IntentUri.parse("intent://x/#Intent;scheme=https;endpoint=1;package=com.android.chrome;end"))
        assertNull(parsed.packageName)
        assertEquals("https://x/", parsed.data)
    }

    @Test fun `typed extras and flags that parse are accepted`() {
        listOf(
            "intent:#Intent;S.k=v;B.k=maybe;i.n=-3;l.n=4;s.n=5;b.n=6;c.k=x;d.n=1.5;f.n=2;end",
            "intent:#Intent;launchFlags=0x10000000;type=text/plain;category=android.intent.category.BROWSABLE;end",
        ).forEach { assertNotNull(IntentUri.parse(it), it) }
    }

    @Test fun `malformed intent URIs are rejected`() {
        listOf(
            "https://example.com",
            "intent://example.com#Intent;scheme=https",
            "intent://example.com#Intent;scheme=https;package=bad..pkg;end",
            "intent://example.com/#Intent;scheme=https;foo=bar;end",
            "intent:#Intent;package;end",
            "intent:#Intent;i.n=abc;end",
            "intent:#Intent;c.k=;end",
            "intent:#Intent;launchFlags=zz;end",
            "intent:x#action(foo",
            "intent:x#extras(Ifoo=1)",
            "intent:x#extras(ifoo=abc)",
        ).forEach { assertNull(IntentUri.parse(it), it) }
    }
}

/** Mirrors `ComponentName.unflattenFromString` and `Uri.decode` (AOSP `UriCodec.decode`, no throw on failure). */
class IntentUriLenientTest {
    @Test fun `slashless or classless components are no component`() {
        assertNull(assertNotNull(IntentUri.parse("intent:#Intent;component=noslash;end")).componentPackage)
        assertNull(assertNotNull(IntentUri.parse("intent:#Intent;component=com.android.settings/;end")).componentPackage)
    }
}

class IntentUriDecodeTest {
    @Test fun `percent decoding follows UriCodec`() {
        fun fallback(raw: String) = IntentUri.parse("intent:#Intent;S.browser_fallback_url=$raw;end")?.browserFallbackUrl
        assertEquals("caf\u00e9", fallback("caf%C3%A9"))
        assertEquals("a+b", fallback("a+b"))
        assertEquals("\ufffd", fallback("%C3"))
        assertEquals("\ufffd\u0000z", fallback("%zz"))
        assertEquals("\ufffd\u00001", fallback("%+1"))
        assertEquals("a\ufffd", fallback("a%"))
        assertEquals("a\ufffd", fallback("a%2"))
        assertEquals("\ufffd\u0000\uff11", fallback("%\uff11\uff11"))
    }

    @Test fun `extra keys are percent-decoded too`() {
        assertEquals("https://e.example", IntentUri.parse("intent:#Intent;S.browser%5Ffallback_url=https://e.example;end")?.browserFallbackUrl)
    }
}
