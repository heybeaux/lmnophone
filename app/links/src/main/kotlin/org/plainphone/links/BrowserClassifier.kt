// SPDX-License-Identifier: Apache-2.0
package org.plainphone.links

import org.plainphone.core.NeverHideList
import org.plainphone.core.PackageName

/** What Plainphone does with one package. */
enum class Verdict {
    /** Plainphone itself (exact package and signing certificates). Never classified. */
    SELF,

    /** On the never-hide list and not browser-like. Untouched. */
    NEVER_HIDE,

    /** On the never-hide list but browser-like. Reported for review, never hidden. */
    NEVER_HIDE_CONFLICT,

    /** Hidden, and the trusted person cannot approve it. */
    HIDE,

    /** Would be hidden, but it is a system package on a build with no inventory. Left unhidden (disclosed risk); still not approvable. */
    KEEP_SYSTEM_NO_INVENTORY,

    /** Not hidden; the catalog flags in-app browsing for review and disclosure. */
    FLAG_REVIEW,

    /** Not a browser. */
    ALLOWED,
}

/** Why the verdict was given. */
enum class Reason {
    PLAINPHONE_IDENTITY,
    NEVER_HIDE_LIST,
    PROBE_HTTP_AND_HTTPS,
    CATALOG_BROWSER,
    CATALOG_ASSISTANT,
    CATALOG_APP_STORE,
    CATALOG_REVIEW,
    VERIFIED_DOMAINS_ONLY,
    ONE_WEB_SCHEME_ONLY,
    NO_WEB_HANDLER,
}

data class PackageClassification(val packageName: PackageName, val verdict: Verdict, val reason: Reason)

/** One result per input package, sorted by package name. */
class Classification(results: List<PackageClassification>) {
    val results: List<PackageClassification> = results.sortedBy { it.packageName.value }
    private val byPackage = this.results.associateBy { it.packageName }

    operator fun get(packageName: PackageName): PackageClassification? = byPackage[packageName]

    private fun having(verdict: Verdict) = results.filter { it.verdict == verdict }.map { it.packageName }.toSet()

    /** Packages to hide now. */
    val hide: Set<PackageName> get() = having(Verdict.HIDE)

    /** System packages left unhidden because the build has no inventory. */
    val heldSystem: Set<PackageName> get() = having(Verdict.KEEP_SYSTEM_NO_INVENTORY)

    /** Never-hide packages that look like browsers. They break link-catching, so they need a decision, not just a report. */
    val conflicts: Set<PackageName> get() = having(Verdict.NEVER_HIDE_CONFLICT)

    /** Packages flagged for catalog review (in-app browsing without a web intent filter). */
    val review: Set<PackageName> get() = having(Verdict.FLAG_REVIEW)

    /** Packages the trusted person must not be able to approve. */
    val notApprovable: Set<PackageName> get() = hide + heldSystem
}

/**
 * Classifies installed packages for web containment (web-containment spec, design D4).
 *
 * Order: Plainphone itself first, then the never-hide list, then the browser heuristic and the catalog.
 */
class BrowserClassifier(private val self: PlainphoneIdentity) {
    fun classify(
        facts: List<PackageFacts>,
        catalog: BrowserCatalog,
        neverHide: NeverHideList,
        inventoryKnown: Boolean,
    ): Classification {
        val duplicates = facts.groupBy { it.packageName }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "One PackageFacts per package; duplicated: ${duplicates.joinToString { it.value }}" }
        return Classification(facts.map { classifyOne(it, catalog, neverHide, inventoryKnown) })
    }

    private fun classifyOne(facts: PackageFacts, catalog: BrowserCatalog, neverHide: NeverHideList, inventoryKnown: Boolean): PackageClassification {
        val pkg = facts.packageName
        if (self.matches(facts)) return PackageClassification(pkg, Verdict.SELF, Reason.PLAINPHONE_IDENTITY)

        val entry = catalog[pkg]
        val match: Reason? = when {
            facts.handlesProbeHttp && facts.handlesProbeHttps -> Reason.PROBE_HTTP_AND_HTTPS
            entry != null -> when (entry.kind) {
                EntryKind.BROWSER -> Reason.CATALOG_BROWSER
                EntryKind.ASSISTANT -> Reason.CATALOG_ASSISTANT
                EntryKind.APP_STORE -> Reason.CATALOG_APP_STORE
                EntryKind.REVIEW -> Reason.CATALOG_REVIEW
            }
            else -> null
        }

        if (pkg in neverHide) {
            return if (match == null) PackageClassification(pkg, Verdict.NEVER_HIDE, Reason.NEVER_HIDE_LIST)
            else PackageClassification(pkg, Verdict.NEVER_HIDE_CONFLICT, match)
        }

        return when (match) {
            null -> PackageClassification(pkg, Verdict.ALLOWED, notBrowserReason(facts))
            Reason.CATALOG_REVIEW -> PackageClassification(pkg, Verdict.FLAG_REVIEW, match)
            else -> {
                val verdict = if (facts.isSystem && !inventoryKnown) Verdict.KEEP_SYSTEM_NO_INVENTORY else Verdict.HIDE
                PackageClassification(pkg, verdict, match)
            }
        }
    }

    private fun notBrowserReason(facts: PackageFacts): Reason = when {
        facts.handlesProbeHttp || facts.handlesProbeHttps -> Reason.ONE_WEB_SCHEME_ONLY
        facts.verifiedDomains.isNotEmpty() -> Reason.VERIFIED_DOMAINS_ONLY
        else -> Reason.NO_WEB_HANDLER
    }
}
