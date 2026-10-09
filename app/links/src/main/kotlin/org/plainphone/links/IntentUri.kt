// SPDX-License-Identifier: Apache-2.0
package org.plainphone.links

import org.plainphone.core.PackageName

/** The parts of an `intent:` URI that decide where it goes. */
data class ParsedIntentUri(
    val action: String?,
    val data: String?,
    val packageName: PackageName?,
    val componentPackage: PackageName?,
    val categories: Set<String>,
    val browserFallbackUrl: String?,
    val selector: ParsedIntentUri?,
)

/**
 * Pure-Kotlin reading of `intent:` URIs, following `Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)`:
 * `intent:<rest>#Intent;key=value;...;end`, values percent-decoded, `SEL;` starts the selector,
 * `scheme=` is prefixed to `<rest>` to form the data.
 * Reference: https://developer.android.com/reference/android/content/Intent#parseUri(java.lang.String,%20int)
 */
object IntentUri {
    private const val PREFIX = "intent:"
    private const val MARKER = "#Intent;"

    private class Builder(var action: String?) {
        var data: String? = null
        var packageName: PackageName? = null
        var componentPackage: PackageName? = null
        val categories = linkedSetOf<String>()
        var fallback: String? = null

        fun build(selector: ParsedIntentUri?) =
            ParsedIntentUri(action, data, packageName, componentPackage, categories.toSet(), fallback, selector)
    }

    /** Returns null where `parseUri` would throw, or where a package name cannot be a real package. */
    fun parse(uri: String): ParsedIntentUri? {
        if (!uri.startsWith(PREFIX)) return null
        val hash = uri.lastIndexOf('#')
        if (hash < 0 || !uri.startsWith(MARKER, hash)) return null

        val outer = Builder(ACTION_VIEW)
        var selector: Builder? = null
        var scheme: String? = null
        var i = hash + MARKER.length
        while (!uri.startsWith("end", i)) {
            val semi = uri.indexOf(';', i)
            if (semi < 0) return null
            val segment = uri.substring(i, semi)
            i = semi + 1
            if (segment == "SEL") {
                selector = Builder(null)
                continue
            }
            val current = selector ?: outer
            val eq = segment.indexOf('=')
            val key = if (eq < 0) segment else segment.substring(0, eq)
            val value = if (eq < 0) "" else percentDecode(segment.substring(eq + 1))
            when (key) {
                "action" -> current.action = value
                "category" -> current.categories += value
                "package" -> current.packageName = packageOrNull(value) ?: return null
                "component" -> {
                    val slash = value.indexOf('/')
                    if (slash >= 0) current.componentPackage = packageOrNull(value.substring(0, slash)) ?: return null
                }
                "scheme" -> if (selector != null) selector.data = "$value:" else scheme = value
                "S.browser_fallback_url" -> current.fallback = value
            }
        }

        val rest = uri.substring(PREFIX.length, hash)
        val data = if (scheme != null) "$scheme:$rest" else rest
        outer.data = data.ifEmpty { null }
        return outer.build(selector?.build(null))
    }

    private fun packageOrNull(value: String): PackageName? = runCatching { PackageName(value) }.getOrNull()

    private fun hexByte(high: Char, low: Char): Int? {
        val h = Character.digit(high, 16)
        val l = Character.digit(low, 16)
        return if (h < 0 || l < 0) null else h * 16 + l
    }

    /** Like `Uri.decode`: `%XX` sequences become UTF-8 bytes; malformed escapes are kept as they are. */
    private fun percentDecode(text: String): String {
        if ('%' !in text) return text
        val out = StringBuilder()
        val bytes = java.io.ByteArrayOutputStream()
        fun flush() {
            if (bytes.size() > 0) { out.append(bytes.toByteArray().toString(Charsets.UTF_8)); bytes.reset() }
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val hex = if (c == '%' && i + 2 < text.length) hexByte(text[i + 1], text[i + 2]) else null
            if (hex != null) {
                bytes.write(hex)
                i += 3
            } else {
                flush()
                out.append(c)
                i++
            }
        }
        flush()
        return out.toString()
    }
}
