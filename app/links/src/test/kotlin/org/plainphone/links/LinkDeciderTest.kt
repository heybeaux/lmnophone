// SPDX-License-Identifier: Apache-2.0
package org.plainphone.links

import org.plainphone.core.PackageName
import org.plainphone.links.BlockReason.*
import org.plainphone.links.IntentUriHandling.*
import org.plainphone.links.LinkDecision.*
import org.plainphone.links.NoHandlerReason.*
import kotlin.test.*

/**
 * The link matrix. Outcome names map to the PLA-23 result detector (`spikes/emulator/tools/detect.sh`):
 * `OpenIn` = TARGET_OPENED, `BlockedScreen(WEB_LINK)` = LINK_CAUGHT,
 * `BlockedScreen(NOT_APPROVED | CHOOSER)` = POLICY_DENIED, `NoHandler` = NO_HANDLER.
 */
class LinkDeciderTest {
    private val catcher = PackageName("com.plainphone.app")
    private val decider = LinkDecider(catcher)

    private val chrome = PackageName("com.android.chrome")
    private val fxBrowser = PackageName("com.plainphone.fx.browser")
    private val fxApplinks = PackageName("com.plainphone.fx.applinks")
    private val fxHttponly = PackageName("com.plainphone.fx.httponly")
    private val fxEmergency = PackageName("com.plainphone.fx.emergency")
    private val googleApp = PackageName("com.google.android.googlequicksearchbox")
    private val settings = PackageName("com.android.settings")
    private val vending = PackageName("com.android.vending")
    private val phone = PackageName("com.android.phone")
    private val dialer = PackageName("com.google.android.dialer")
    private val messages = PackageName("com.google.android.apps.messaging")
    private val gmail = PackageName("com.google.android.gm")

    /** Approved apps plus the lock-task UI exceptions from the PLA-23 final allowlist. */
    private val baseApproved = setOf(dialer, messages, phone, PackageName("com.android.server.telecom"))
    private val withApplinks = baseApproved + fxApplinks

    private fun any(pkg: PackageName) = Resolver(pkg)
    private fun verified(pkg: PackageName) = Resolver(pkg, verifiedForHost = true)

    private data class Row(val name: String, val facts: IntentFacts, val approved: Set<PackageName>, val expected: LinkDecision, val evidence: Evidence)

    private fun q4(case: String) = Evidence.Cited(EMU_RESULT, "Q4 link matrix: $case")
    private fun v3(case: String) = Evidence.Cited(EMU_RESULT, "PLA-23 V3: $case")
    private fun q1(case: String) = Evidence.Cited(EMU_RESULT, "Q1 blocked launches: $case")
    private fun t1(case: String) = Evidence.Cited(EMU_RESULT, "Q4 floor trust (T1) and PLA-23 V4: $case")

    private val rows = listOf(
        // Ordinary links.
        Row("https link, every browser hidden", IntentFacts(data = "https://example.com"), baseApproved, BlockedScreen(WEB_LINK), q4("https://example.com")),
        Row("https link, resolver list includes the link-catcher", IntentFacts(data = "https://example.com", resolvers = listOf(any(catcher))), baseApproved, BlockedScreen(WEB_LINK), q4("https://example.com")),
        Row("upper-case scheme still counts as web", IntentFacts(data = "HTTPS://Example.com/"), baseApproved, BlockedScreen(WEB_LINK), Evidence.Unverified("scheme case not run")),
        Row("http link with a visible unapproved http-only app: chooser", IntentFacts(data = "http://example.com", resolvers = listOf(any(fxHttponly))), baseApproved, BlockedScreen(CHOOSER), q4("http://example.com (chooser gap)")),
        Row("http link with that app but the link-catcher preferred", IntentFacts(data = "http://example.com", resolvers = listOf(any(fxHttponly)), linkCatcherPreferred = true), baseApproved, BlockedScreen(WEB_LINK),
            Evidence.Unverified("needs lane D's persistent preferred activity for VIEW http/https (RESULT.md Q4 follow-up)")),
        Row("approved http-only app is not an App Link: chooser without the preferred activity", IntentFacts(data = "http://example.com", resolvers = listOf(any(fxHttponly))), baseApproved + fxHttponly, BlockedScreen(CHOOSER),
            Evidence.Unverified("RESULT.md Q4: an approved http-only app causes the same chooser; not run")),
        Row("unverified App Link (state 1024) is not offered, so the link-catcher gets it", IntentFacts(data = "https://bank.plainphone-fx.example"), withApplinks, BlockedScreen(WEB_LINK), q4("App Link unverified, state 1024")),
        Row("Dialer Learn more link with Chrome hidden", IntentFacts(data = "https://support.google.com/phoneapp"), baseApproved, BlockedScreen(WEB_LINK), Evidence.Cited(EMU_RESULT, "UI exceptions list: Dialer Learn more links")),
        Row("Gmail add-account page's off-page link with Chrome hidden", IntentFacts(data = "https://privacy.microsoft.com/"), baseApproved + gmail, BlockedScreen(WEB_LINK),
            Evidence.Unverified("$MOTO_RESULT B-Q4 Gmail: link went to visible Chrome (blocked); MVP hides Chrome, not run")),
        Row("via chooser, even for an https link", IntentFacts(data = "https://example.com", viaChooser = true), baseApproved, BlockedScreen(CHOOSER), q4("via chooser column")),
        Row("explicit target is the link-catcher itself", IntentFacts(data = "https://example.com", targetPackage = catcher, resolvers = listOf(any(catcher))), baseApproved, BlockedScreen(WEB_LINK), Evidence.Unverified("not run")),

        // Verified App Links.
        Row("verified App Link, approved app", IntentFacts(data = "https://bank.plainphone-fx.example", resolvers = listOf(verified(fxApplinks))), withApplinks, OpenIn(fxApplinks), v3("Verified App Link, approved app")),
        Row("verified App Link, unapproved app still visible", IntentFacts(data = "https://bank.plainphone-fx.example", resolvers = listOf(verified(fxApplinks))), baseApproved, BlockedScreen(NOT_APPROVED), v3("Verified App Link, unapproved app still visible")),
        Row("verified App Link, unapproved app hidden", IntentFacts(data = "https://bank.plainphone-fx.example"), baseApproved, BlockedScreen(WEB_LINK), v3("Verified App Link, unapproved app hidden")),
        Row("verified App Link wins over a visible generic handler", IntentFacts(data = "https://bank.plainphone-fx.example", resolvers = listOf(verified(fxApplinks), any(fxHttponly))), withApplinks, OpenIn(fxApplinks),
            Evidence.Unverified("verified domains go straight to the app (V3); mixed case not run")),
        Row("two verified handlers for one host: chooser", IntentFacts(data = "https://shared.example", resolvers = listOf(verified(fxApplinks), verified(gmail))), withApplinks + gmail, BlockedScreen(CHOOSER), Evidence.Unverified("not run")),
        Row("App Link with setPackage to the unapproved visible app", IntentFacts(data = "https://bank.plainphone-fx.example", targetPackage = fxApplinks, resolvers = listOf(any(fxApplinks))), baseApproved, BlockedScreen(NOT_APPROVED), q4("App Link with setPackage(fx.applinks)")),
        Row("Play web link goes to the never-hide, unapproved Play Store", IntentFacts(data = "https://play.google.com/store/apps/details?id=org.example", resolvers = listOf(verified(vending))), baseApproved, BlockedScreen(NOT_APPROVED), q1("Play (web)")),

        // Custom schemes, market: and other implicit intents.
        Row("custom scheme to an unapproved visible app", IntentFacts(data = "ppfx://open", resolvers = listOf(any(fxApplinks))), baseApproved, BlockedScreen(NOT_APPROVED), q4("Custom scheme ppfx://open")),
        Row("custom scheme to a hidden app", IntentFacts(data = "ppfx://open"), baseApproved, NoHandler(NO_RESOLVER), Evidence.Unverified("hidden packages do not resolve (NO_HANDLER rows in PLA-23 V2); this scheme not run hidden")),
        Row("custom scheme to an approved app", IntentFacts(data = "ppfx://open", resolvers = listOf(any(fxApplinks))), withApplinks, OpenIn(fxApplinks), Evidence.Unverified("control (lock task off) opens fx-applinks; approved case not run")),
        Row("custom scheme with an approved and an unapproved handler: chooser", IntentFacts(data = "ppfx://open", resolvers = listOf(any(fxApplinks), any(fxHttponly))), withApplinks, BlockedScreen(CHOOSER), Evidence.Unverified("not run")),
        Row("market: link to the Play Store", IntentFacts(data = "market://details?id=org.example", resolvers = listOf(any(vending))), baseApproved, BlockedScreen(NOT_APPROVED), q1("Play (market:)")),
        Row("share sheet (ACTION_SEND) with several targets", IntentFacts(action = ACTION_SEND, resolvers = listOf(any(messages), any(gmail))), baseApproved, BlockedScreen(CHOOSER), q1("Share sheet (ACTION_SEND)")),
        Row("Settings action", IntentFacts(action = "android.settings.SETTINGS", resolvers = listOf(any(settings))), baseApproved, BlockedScreen(NOT_APPROVED), q1("Settings (action and explicit)")),
        Row("Call settings inside the com.android.phone UI exception", IntentFacts(action = "android.telecom.action.SHOW_CALL_SETTINGS", resolvers = listOf(any(phone))), baseApproved, OpenIn(phone), q1("Call settings (SHOW_CALL_SETTINGS), known gap")),
        Row("explicit RadioInfo in com.android.phone", IntentFacts(action = "android.intent.action.MAIN", targetPackage = phone, resolvers = listOf(any(phone))), baseApproved, OpenIn(phone), q1("com.android.phone/.settings.RadioInfo, known gap")),
        Row("implicit emergency dial with a fake emergency app installed: chooser", IntentFacts(action = "com.android.phone.EmergencyDialer.DIAL", resolvers = listOf(any(phone), any(fxEmergency))), baseApproved, BlockedScreen(CHOOSER), t1("implicit EmergencyDialer.DIAL resolves to ResolverActivity")),
        Row("emergency dial pinned to com.android.phone", IntentFacts(action = "com.android.phone.EmergencyDialer.DIAL", targetPackage = phone, resolvers = listOf(any(phone))), baseApproved, OpenIn(phone), t1("setPackage(com.android.phone) opens EmergencyDialer")),
        Row("MAIN + APP_BROWSER with every browser hidden", IntentFacts(action = "android.intent.action.MAIN"), baseApproved, NoHandler(NO_RESOLVER), q4("MAIN + APP_BROWSER")),
        Row("WEB_SEARCH with the Google app hidden", IntentFacts(action = "android.intent.action.WEB_SEARCH"), baseApproved, NoHandler(NO_RESOLVER), q4("WEB_SEARCH")),
        Row("ASSIST with the Google app hidden", IntentFacts(action = "android.intent.action.ASSIST"), baseApproved, NoHandler(NO_RESOLVER), q4("ASSIST")),
        Row("explicit Google app (hidden)", IntentFacts(action = "android.intent.action.MAIN", targetPackage = googleApp), baseApproved, NoHandler(TARGET_NOT_RESOLVABLE), q4("Google app explicit")),

        // Explicit intents to hidden browsers.
        Row("explicit component to hidden Chrome", IntentFacts(data = "https://example.com", targetPackage = chrome), baseApproved, NoHandler(TARGET_NOT_RESOLVABLE), q4("Explicit component to hidden Chrome")),
        Row("explicit component to hidden fx-browser", IntentFacts(data = "https://example.com", targetPackage = fxBrowser), baseApproved, NoHandler(TARGET_NOT_RESOLVABLE), q4("Explicit component to hidden fx-browser")),
        Row("explicit target whose resolver list names another package", IntentFacts(data = "https://example.com", targetPackage = chrome, resolvers = listOf(any(fxHttponly))), baseApproved, NoHandler(TARGET_NOT_RESOLVABLE), Evidence.Unverified("guard against inconsistent facts")),

        // Custom Tabs.
        Row("Custom Tabs client with no provider falls back to launchUrl", IntentFacts(data = "https://example.com", customTabs = true), baseApproved, BlockedScreen(WEB_LINK), v3("Real Custom Tabs client, providers=[] fallback launchUrl")),
        Row("Custom Tabs, no package", IntentFacts(data = "https://example.com", customTabs = true), withApplinks, BlockedScreen(WEB_LINK), q4("Custom Tabs, no package")),
        Row("Custom Tabs setPackage(hidden Chrome)", IntentFacts(data = "https://example.com", customTabs = true, targetPackage = chrome), baseApproved, NoHandler(TARGET_NOT_RESOLVABLE), q4("Custom Tabs setPackage(com.android.chrome)")),
        Row("Facebook SDK Custom Tab aimed at hidden Chrome", IntentFacts(data = "https://m.facebook.com/", customTabs = true, targetPackage = chrome), baseApproved + PackageName("com.spotify.music"), NoHandler(TARGET_NOT_RESOLVABLE),
            Evidence.Unverified("$MOTO_RESULT B-Q4 Spotify: Chrome was visible there (blocked); MVP hides Chrome, not run")),

        // intent: URIs.
        Row("intent: URI fired raw (Uri.parse), nothing handles the intent scheme", IntentFacts(data = "intent://example.com/#Intent;scheme=https;package=com.android.chrome;end", intentUri = NOT_PARSED), baseApproved, NoHandler(NO_RESOLVER),
            Evidence.Cited(EMU_RESULT, "Q4 link matrix intent:// row; PLA-23 V2 intent-uri")),
        Row("parseUri raw, package=hidden Chrome", IntentFacts(data = "intent://example.com/#Intent;scheme=https;package=com.android.chrome;end", intentUri = RAW), baseApproved, NoHandler(TARGET_NOT_RESOLVABLE), v3("parseUri raw, package=com.android.chrome")),
        Row("parseUri Chrome-style sanitised, package=hidden Chrome", IntentFacts(data = "intent://example.com/#Intent;scheme=https;package=com.android.chrome;end", intentUri = SANITISED), baseApproved, NoHandler(TARGET_NOT_RESOLVABLE), v3("parseUri Chrome-style sanitised")),
        Row("parseUri raw, no package", IntentFacts(data = "intent://example.com/#Intent;scheme=https;end", intentUri = RAW), baseApproved, BlockedScreen(WEB_LINK), v3("parseUri raw, no package")),
        Row("parseUri raw, component=Settings", IntentFacts(data = "intent:#Intent;component=com.android.settings/.Settings;end", intentUri = RAW, resolvers = listOf(any(settings))), baseApproved, BlockedScreen(NOT_APPROVED), v3("parseUri raw component=Settings")),
        Row("parseUri sanitised drops the component, action=SETTINGS still resolves to Settings", IntentFacts(data = "intent:#Intent;action=android.settings.SETTINGS;component=com.android.settings/.Settings;end", intentUri = SANITISED, resolvers = listOf(any(settings))), baseApproved, BlockedScreen(NOT_APPROVED), v3("sanitised action=SETTINGS")),
        Row("parseUri raw to the unapproved ppfx: app", IntentFacts(data = "intent://open#Intent;scheme=ppfx;package=com.plainphone.fx.applinks;end", intentUri = RAW, resolvers = listOf(any(fxApplinks))), baseApproved, BlockedScreen(NOT_APPROVED), v3("parseUri raw to the unapproved ppfx: app")),
        Row("parseUri raw to the approved ppfx: app", IntentFacts(data = "intent://open#Intent;scheme=ppfx;package=com.plainphone.fx.applinks;end", intentUri = RAW, resolvers = listOf(any(fxApplinks))), withApplinks, OpenIn(fxApplinks), Evidence.Unverified("control opens fx-applinks; approved case not run")),
        Row("parseUri raw, browser_fallback_url is never followed", IntentFacts(data = "intent://nothing#Intent;scheme=nosuchscheme;S.browser_fallback_url=https%3A%2F%2Fexample.com;end", intentUri = RAW), baseApproved, NoHandler(NO_RESOLVER), Evidence.Cited(EMU_RESULT, "PLA-23 V2: parse-raw-fallback-url")),
        Row("parseUri sanitised, verified App Link of an approved app", IntentFacts(data = "intent://bank.plainphone-fx.example/#Intent;scheme=https;end", intentUri = SANITISED, resolvers = listOf(verified(fxApplinks))), withApplinks, OpenIn(fxApplinks), Evidence.Unverified("not run")),
        Row("parseUri raw, selector package wins over the outer package", IntentFacts(data = "intent:#Intent;package=com.plainphone.fx.applinks;SEL;scheme=https;package=com.android.chrome;end", intentUri = RAW), withApplinks, NoHandler(TARGET_NOT_RESOLVABLE), Evidence.Unverified("selector not run")),
        Row("parseUri sanitised drops the selector", IntentFacts(data = "intent://open#Intent;scheme=ppfx;package=com.plainphone.fx.applinks;SEL;package=com.android.chrome;end", intentUri = SANITISED, resolvers = listOf(any(fxApplinks))), withApplinks, OpenIn(fxApplinks), Evidence.Unverified("selector not run")),
        Row("malformed intent: URI", IntentFacts(data = "intent://example.com#Intent;scheme=https", intentUri = RAW), baseApproved, NoHandler(MALFORMED_INTENT_URI), Evidence.Unverified("parseUri throws URISyntaxException; not run")),

        // Never-hide conflict.
        Row("never-hide package that matches the heuristic breaks link-catching", IntentFacts(data = "https://example.com", resolvers = listOf(any(fxBrowser))), baseApproved, BlockedScreen(CHOOSER), v3("Essential package that matches the browser heuristic")),
        Row("conflict with the link-catcher preferred", IntentFacts(data = "https://example.com", resolvers = listOf(any(fxBrowser)), linkCatcherPreferred = true), baseApproved, BlockedScreen(WEB_LINK),
            Evidence.Unverified("V3 spec implication 3: needs the persistent preferred activity; not run")),
    )

    @Test fun `row names are unique`() {
        assertEquals(rows.size, rows.map { it.name }.toSet().size)
    }

    @Test fun `link matrix`() {
        val failures = rows.mapNotNull { row ->
            val actual = decider.decide(row.facts, row.approved)
            if (actual != row.expected) "${row.name}: expected ${row.expected}, got $actual (${row.evidence})" else null
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test fun `block reasons name the screen the user sees`() {
        assertEquals(BlockSurface.PLAINPHONE_LINK_SCREEN, WEB_LINK.surface)
        assertEquals(BlockSurface.LOCK_TASK_BLOCKED, NOT_APPROVED.surface)
        assertEquals(BlockSurface.LOCK_TASK_BLOCKED, CHOOSER.surface)
    }

    @Test fun `spec scenarios`() {
        // Tap a web link in Messages to a site with no approved app.
        assertEquals(BlockedScreen(WEB_LINK), decider.decide(IntentFacts(data = "https://news.example/story"), baseApproved))
        // Tap an approved app's verified link.
        assertEquals(OpenIn(fxApplinks), decider.decide(IntentFacts(data = "https://bank.plainphone-fx.example/a", resolvers = listOf(verified(fxApplinks))), withApplinks))
    }

    @Test fun `the link-catcher is never an OpenIn target`() {
        rows.forEach { row ->
            val actual = decider.decide(row.facts, row.approved + catcher)
            assertNotEquals<LinkDecision>(OpenIn(catcher), actual, row.name)
        }
    }

    @Test fun `approval is the only thing that turns a block into OpenIn`() {
        rows.filter { it.expected is OpenIn }.forEach { row ->
            val target = (row.expected as OpenIn).packageName
            val actual = decider.decide(row.facts, row.approved - target)
            assertTrue(actual !is OpenIn, "${row.name}: unapproving $target must not open it")
        }
    }
}
