// SPDX-License-Identifier: Apache-2.0
package org.plainphone.links

import org.plainphone.core.PackageName

/** What a reviewed catalog entry says about a package. */
enum class EntryKind(val hidden: Boolean) {
    /** A general web browser. Hidden; cannot be approved. */
    BROWSER(true),

    /** The Google app or Gemini. Hidden; cannot be approved. */
    ASSISTANT(true),

    /** An app store other than the Play Store. Hidden; cannot be approved. */
    APP_STORE(true),

    /** Can browse inside its own WebView or URL bar without a web intent filter. Not hidden; flagged and disclosed. */
    REVIEW(false),
}

/** How the entry was confirmed. */
enum class EntryStatus { EMULATOR, DEVICE, UNVERIFIED }

data class CatalogEntry(
    val packageName: PackageName,
    val kind: EntryKind,
    val status: EntryStatus,
    val source: String,
    val notes: String,
) {
    init { require(source.isNotBlank() && notes.isNotBlank()) }
}

/**
 * The reviewed catalog of known browsers and other always-hidden or flagged packages.
 *
 * Format (UTF-8, tab-separated): `#` comments and blank lines are ignored; the first data line is `version=<n>`;
 * every other line is `package, kind, status, source, notes`.
 */
class BrowserCatalog private constructor(val version: Int, entries: List<CatalogEntry>) {
    val entries: List<CatalogEntry> = entries.toList()
    private val byPackage = entries.associateBy { it.packageName }

    operator fun get(packageName: PackageName): CatalogEntry? = byPackage[packageName]

    companion object {
        const val RESOURCE: String = "/org/plainphone/links/browser-catalog.tsv"

        fun bundled(): BrowserCatalog {
            val stream = requireNotNull(BrowserCatalog::class.java.getResourceAsStream(RESOURCE)) { "Missing $RESOURCE" }
            return parse(stream.use { it.readBytes().toString(Charsets.UTF_8) })
        }

        fun parse(text: String): BrowserCatalog {
            var version: Int? = null
            val entries = mutableListOf<CatalogEntry>()
            text.lines().forEachIndexed { index, raw ->
                val line = raw.trimEnd('\r')
                if (line.isBlank() || line.trimStart().startsWith("#")) return@forEachIndexed
                val where = "line ${index + 1}"
                if (version == null) {
                    val value = line.trim().removePrefix("version=")
                    require(value != line.trim()) { "$where: expected version=<n> first" }
                    version = requireNotNull(value.toIntOrNull()?.takeIf { it > 0 }) { "$where: bad version" }
                    return@forEachIndexed
                }
                val cols = line.split('\t')
                require(cols.size == 5) { "$where: expected 5 tab-separated columns" }
                val pkg = runCatching { PackageName(cols[0].trim()) }.getOrElse { throw IllegalArgumentException("$where: bad package") }
                val kind = requireNotNull(EntryKind.entries.find { it.name == cols[1].trim() }) { "$where: unknown kind" }
                val status = requireNotNull(EntryStatus.entries.find { it.name == cols[2].trim() }) { "$where: unknown status" }
                require(cols[3].isNotBlank() && cols[4].isNotBlank()) { "$where: source and notes are required" }
                require(entries.none { it.packageName == pkg }) { "$where: duplicate package ${pkg.value}" }
                entries += CatalogEntry(pkg, kind, status, cols[3].trim(), cols[4].trim())
            }
            return BrowserCatalog(requireNotNull(version) { "Missing version line" }, entries)
        }
    }
}
