// SPDX-License-Identifier: Apache-2.0
package org.plainphone.links

import org.plainphone.core.NeverHideList
import org.plainphone.core.PackageName
import kotlin.test.*

class BrowserClassifierTest {
    private val plainphoneCert = CertDigest("a".repeat(64))
    private val otherCert = CertDigest("b".repeat(64))
    private val plainphone = PackageName("com.plainphone.app")
    private val classifier = BrowserClassifier(PlainphoneIdentity(plainphone, setOf(plainphoneCert)))

    private val neverHide = NeverHideList(
        setOf("com.google.android.gms", "com.android.vending", "com.google.android.dialer").map(::PackageName).toSet(),
    )

    /** Catalog used by the spike fixtures (the spike's URL-bar entry was a hardcoded review entry). */
    private val fixtureCatalog = BrowserCatalog.parse(
        """
        # SPDX-License-Identifier: Apache-2.0
        version=1
        com.google.android.googlequicksearchbox	ASSISTANT	EMULATOR	$EMU_RESULT Q4 classifier fixtures	Google app
        com.plainphone.fx.urlbar	REVIEW	EMULATOR	$EMU_RESULT Q4 classifier fixtures	In-app URL bar and WebView, no web intent filter
        org.example.catalogonly	BROWSER	UNVERIFIED	test-only entry	Browser without a probe match
        org.example.store	APP_STORE	UNVERIFIED	test-only entry	Third-party store
        """.trimIndent(),
    )

    private fun facts(
        pkg: String,
        http: Boolean = false,
        https: Boolean = false,
        system: Boolean = false,
        certs: Set<CertDigest> = setOf(otherCert),
        verified: Set<String> = emptySet(),
    ) = PackageFacts(PackageName(pkg), certs, system, handlesProbeHttp = http, handlesProbeHttps = https, verifiedDomains = verified)

    private data class Row(
        val name: String,
        val facts: PackageFacts,
        val verdict: Verdict,
        val reason: Reason,
        val evidence: Evidence,
        val inventoryKnown: Boolean = true,
    )

    private val q4 = Evidence.Cited(EMU_RESULT, "Q4 classifier fixtures (classify-dry, classify-noidentity)")
    private val v3Conflict = Evidence.Cited(EMU_RESULT, "PLA-23 V3: essential package that matches the browser heuristic")

    private val rows = listOf(
        Row("Plainphone itself, cert matches", facts(plainphone.value, http = true, https = true, certs = setOf(plainphoneCert)), Verdict.SELF, Reason.PLAINPHONE_IDENTITY, q4),
        Row("Plainphone package name with another cert is not Plainphone", facts(plainphone.value, http = true, https = true), Verdict.HIDE, Reason.PROBE_HTTP_AND_HTTPS,
            Evidence.Unverified("negative control classify-noidentity shows the heuristic match; a foreign-cert copy was not installed")),
        Row("Plainphone with an extra signer is not exact", facts(plainphone.value, http = true, https = true, certs = setOf(plainphoneCert, otherCert)), Verdict.HIDE, Reason.PROBE_HTTP_AND_HTTPS,
            Evidence.Unverified("spec requires exact signing certificate; multi-signer copy not tested")),
        Row("Chrome (system, general browser)", facts("com.android.chrome", http = true, https = true, system = true), Verdict.HIDE, Reason.PROBE_HTTP_AND_HTTPS, q4),
        Row("fx-browser (user-installed general browser)", facts("com.plainphone.fx.browser", http = true, https = true), Verdict.HIDE, Reason.PROBE_HTTP_AND_HTTPS, q4),
        Row("fx-applinks, one verified domain only", facts("com.plainphone.fx.applinks", verified = setOf("bank.plainphone-fx.example")), Verdict.ALLOWED, Reason.VERIFIED_DOMAINS_ONLY, q4),
        Row("fx-httponly, one scheme only", facts("com.plainphone.fx.httponly", http = true), Verdict.ALLOWED, Reason.ONE_WEB_SCHEME_ONLY, q4),
        Row("https-only app, one scheme only", facts("org.example.httpsonly", https = true), Verdict.ALLOWED, Reason.ONE_WEB_SCHEME_ONLY,
            Evidence.Unverified("mirror of fx-httponly; no https-only fixture")),
        Row("fx-urlbar, in-app URL bar flagged by the catalog", facts("com.plainphone.fx.urlbar"), Verdict.FLAG_REVIEW, Reason.CATALOG_REVIEW, q4),
        Row("Google app, named hide", facts("com.google.android.googlequicksearchbox", system = true), Verdict.HIDE, Reason.CATALOG_ASSISTANT, q4),
        Row("Play services is never-hide", facts("com.google.android.gms", system = true), Verdict.NEVER_HIDE, Reason.NEVER_HIDE_LIST, q4),
        Row("Play Store is never-hide", facts("com.android.vending", system = true), Verdict.NEVER_HIDE, Reason.NEVER_HIDE_LIST, q4),
        Row("Dialer is never-hide", facts("com.google.android.dialer", system = true), Verdict.NEVER_HIDE, Reason.NEVER_HIDE_LIST, q4),
        Row("Never-hide package matching the heuristic is a conflict, not hidden", facts("com.google.android.dialer", http = true, https = true, system = true),
            Verdict.NEVER_HIDE_CONFLICT, Reason.PROBE_HTTP_AND_HTTPS, v3Conflict),
        Row("Catalog-only browser without a probe match is hidden", facts("org.example.catalogonly"), Verdict.HIDE, Reason.CATALOG_BROWSER,
            Evidence.Unverified("catalog route measured only for the URL-bar review entry and the Google app")),
        Row("Third-party app store is hidden", facts("org.example.store"), Verdict.HIDE, Reason.CATALOG_APP_STORE,
            Evidence.Unverified("spec: hide app stores other than the Play Store; no store fixture on the emulator")),
        Row("App with no web handler", facts("org.example.notes"), Verdict.ALLOWED, Reason.NO_WEB_HANDLER,
            Evidence.Unverified("no plain fixture; follows from the probe definition in $WEB_SPEC")),
        Row("Unknown build: system browser stays unhidden", facts("com.android.chrome", http = true, https = true, system = true),
            Verdict.KEEP_SYSTEM_NO_INVENTORY, Reason.PROBE_HTTP_AND_HTTPS, Evidence.Unverified("spec rule for builds with no inventory; not run on emulator"), inventoryKnown = false),
        Row("Unknown build: system Google app stays unhidden", facts("com.google.android.googlequicksearchbox", system = true),
            Verdict.KEEP_SYSTEM_NO_INVENTORY, Reason.CATALOG_ASSISTANT, Evidence.Unverified("spec rule for builds with no inventory; not run on emulator"), inventoryKnown = false),
        Row("Unknown build: user-installed browser is still hidden", facts("com.plainphone.fx.browser", http = true, https = true),
            Verdict.HIDE, Reason.PROBE_HTTP_AND_HTTPS, Evidence.Unverified("spec rule for builds with no inventory; not run on emulator"), inventoryKnown = false),
        Row("Verified domains plus a generic web filter is still a browser", facts("org.example.both", http = true, https = true, verified = setOf("example.org")),
            Verdict.HIDE, Reason.PROBE_HTTP_AND_HTTPS, Evidence.Unverified("probe definition: any app answering the probe host is general")),
    )

    @Test fun `every row cites evidence or is marked unverified`() {
        rows.forEach { assertNotNull(it.evidence, it.name) }
        assertEquals(rows.size, rows.map { it.name }.toSet().size, "row names are unique")
    }

    @Test fun `classifier table`() {
        val failures = rows.mapNotNull { row ->
            val result = classifier.classify(listOf(row.facts), fixtureCatalog, neverHide, row.inventoryKnown)[row.facts.packageName]
            val actual = result?.verdict to result?.reason
            if (actual != row.verdict to row.reason) "${row.name}: expected ${row.verdict}/${row.reason}, got $actual (${row.evidence})" else null
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test fun `spec scenario classifier fixtures`() {
        val all = listOf(
            facts(plainphone.value, http = true, https = true, certs = setOf(plainphoneCert)),
            facts("com.plainphone.fx.browser", http = true, https = true),
            facts("com.plainphone.fx.applinks", verified = setOf("bank.plainphone-fx.example")),
            facts("com.plainphone.fx.httponly", http = true),
            facts("com.plainphone.fx.urlbar"),
            facts("com.google.android.gms", system = true),
            facts("com.android.vending", system = true),
            facts("com.google.android.dialer", system = true),
        )
        val result = classifier.classify(all, fixtureCatalog, neverHide, inventoryKnown = true)
        assertEquals(setOf(PackageName("com.plainphone.fx.browser")), result.hide)
        assertEquals(setOf(PackageName("com.plainphone.fx.urlbar")), result.review)
        assertEquals(emptySet(), result.conflicts)
        assertEquals(Verdict.SELF, result[plainphone]?.verdict)
        neverHide.packages.forEach { assertEquals(Verdict.NEVER_HIDE, result[it]?.verdict, it.value) }
        assertEquals(all.map { it.packageName }.sortedBy { it.value }, result.results.map { it.packageName }, "sorted, one result per package")
    }

    @Test fun `conflicts are reported and never hidden`() {
        val result = classifier.classify(
            listOf(facts("com.google.android.dialer", http = true, https = true, system = true)), fixtureCatalog, neverHide, inventoryKnown = true,
        )
        assertEquals(setOf(PackageName("com.google.android.dialer")), result.conflicts)
        assertTrue(result.hide.isEmpty())
        assertTrue(result.notApprovable.isEmpty(), "a never-hide package stays usable")
    }

    @Test fun `never-hide package listed in the catalog is a conflict`() {
        val catalog = BrowserCatalog.parse("version=1\ncom.google.android.dialer\tBROWSER\tUNVERIFIED\ttest-only entry\tsynthetic")
        val result = classifier.classify(listOf(facts("com.google.android.dialer", system = true)), catalog, neverHide, inventoryKnown = true)
        assertEquals(Verdict.NEVER_HIDE_CONFLICT, result[PackageName("com.google.android.dialer")]?.verdict)
        assertEquals(Reason.CATALOG_BROWSER, result[PackageName("com.google.android.dialer")]?.reason)
    }

    @Test fun `hidden kinds cannot be approved, including system ones held on unknown builds`() {
        val result = classifier.classify(
            listOf(
                facts("com.android.chrome", http = true, https = true, system = true),
                facts("com.plainphone.fx.browser", http = true, https = true),
                facts("org.example.store"),
                facts("com.plainphone.fx.urlbar"),
                facts("com.plainphone.fx.applinks", verified = setOf("bank.plainphone-fx.example")),
            ),
            fixtureCatalog, neverHide, inventoryKnown = false,
        )
        assertEquals(setOf("com.plainphone.fx.browser", "org.example.store").map(::PackageName).toSet(), result.hide)
        assertEquals(setOf(PackageName("com.android.chrome")), result.heldSystem)
        assertEquals(setOf("com.android.chrome", "com.plainphone.fx.browser", "org.example.store").map(::PackageName).toSet(), result.notApprovable)
    }

    @Test fun `empty input gives an empty classification`() {
        val result = classifier.classify(emptyList(), fixtureCatalog, neverHide, inventoryKnown = true)
        assertTrue(result.results.isEmpty() && result.hide.isEmpty() && result.review.isEmpty() && result.conflicts.isEmpty())
    }

    @Test fun `duplicate package facts are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            classifier.classify(listOf(facts("org.example.a"), facts("org.example.a", http = true)), fixtureCatalog, neverHide, inventoryKnown = true)
        }
    }

    @Test fun `identity and digest validation`() {
        assertFailsWith<IllegalArgumentException> { CertDigest("A".repeat(64)) }
        assertFailsWith<IllegalArgumentException> { CertDigest("a".repeat(63)) }
        assertFailsWith<IllegalArgumentException> { PlainphoneIdentity(plainphone, emptySet()) }
        assertEquals(CertDigest("ab".repeat(32)), CertDigest.parse("AB:".repeat(31) + "AB"))
    }
}
