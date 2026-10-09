// SPDX-License-Identifier: Apache-2.0
package org.plainphone.links

import org.plainphone.core.PackageName
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

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
 * Pure-Kotlin port of `Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)` for `intent:` URIs, reduced to the fields
 * the link decision needs. Source: AOSP `frameworks/base/core/java/android/content/Intent.java`
 * (`parseUriInternal`, `getIntentOld`), `ComponentName.unflattenFromString` and `Uri.decode` (`UriCodec.decode`).
 *
 * - `intent:<rest>#Intent;key=value;...;end`: the new format. `SEL;` starts the selector, which is kept only when the
 *   outer intent has no package (AOSP drops it otherwise). Unknown keys and unparsable typed extras make it fail.
 * - `intent:<rest>` with no `#`: not an error. A plain `VIEW` with the whole `intent:` string as data.
 * - `intent:<rest>#<fragment>` without `#Intent;`: the old format (`action(...)categories(...)type(...)
 *   launchFlags(...)component(pkg!cls)extras(...)`), which can name a component.
 */
object IntentUri {
    private const val PREFIX = "intent:"
    private const val MARKER = "#Intent;"
    private const val INVALID_INPUT_CHARACTER = '\ufffd'

    private class Malformed : Exception()

    private class Draft(var action: String?) {
        var data: String? = null
        var packageName: String? = null
        var componentPackage: String? = null
        val categories = linkedSetOf<String>()
        var fallback: String? = null
    }

    /**
     * Returns null where `parseUri` throws (`URISyntaxException`, or the `NumberFormatException` it lets through for
     * bad launch flags), and, as a stricter rule of this module, where a package value cannot be a real package name.
     * Input that is not an `intent:` URI is not reconstructed here and also gives null.
     */
    fun parse(uri: String): ParsedIntentUri? {
        if (!uri.startsWith(PREFIX)) return null
        return try {
            val hash = uri.lastIndexOf('#')
            when {
                hash < 0 -> finish(Draft(ACTION_VIEW).apply { data = uri }, null)
                !uri.startsWith(MARKER, hash) -> parseOld(uri, hash)
                else -> parseNew(uri, hash)
            }
        } catch (_: Malformed) {
            null
        } catch (_: IndexOutOfBoundsException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun parseNew(uri: String, hash: Int): ParsedIntentUri {
        val base = Draft(ACTION_VIEW)
        var current = base
        var inSelector = false
        var scheme: String? = null
        var i = hash + MARKER.length

        while (!uri.startsWith("end", i)) {
            var eq = uri.indexOf('=', i)
            if (eq < 0) eq = i - 1
            val semi = uri.indexOf(';', i)
            if (semi < 0) throw Malformed()
            val value = if (eq < semi) decode(uri.substring(eq + 1, semi)) else ""

            when {
                uri.startsWith("action=", i) -> current.action = value
                uri.startsWith("category=", i) -> current.categories += value
                uri.startsWith("type=", i) || uri.startsWith("identifier=", i) || uri.startsWith("sourceBounds=", i) -> Unit
                uri.startsWith("launchFlags=", i) || uri.startsWith("extendedLaunchFlags=", i) -> decodeInteger(value)
                uri.startsWith("package=", i) -> current.packageName = value
                uri.startsWith("component=", i) -> current.componentPackage = unflattenPackage(value)
                uri.startsWith("scheme=", i) -> if (inSelector) current.data = "$value:" else scheme = value
                semi == i + 3 && uri.startsWith("SEL", i) -> {
                    current = Draft(null)
                    inSelector = true
                }
                else -> {
                    val key = decode(uri.substring(i + 2, eq))
                    when {
                        uri.startsWith("S.", i) -> if (key == "browser_fallback_url") current.fallback = value
                        uri.startsWith("B.", i) -> Unit
                        uri.startsWith("b.", i) -> java.lang.Byte.parseByte(value)
                        uri.startsWith("c.", i) -> value[0]
                        uri.startsWith("d.", i) -> java.lang.Double.parseDouble(value)
                        uri.startsWith("f.", i) -> java.lang.Float.parseFloat(value)
                        uri.startsWith("i.", i) -> java.lang.Integer.parseInt(value)
                        uri.startsWith("l.", i) -> java.lang.Long.parseLong(value)
                        uri.startsWith("s.", i) -> java.lang.Short.parseShort(value)
                        else -> throw Malformed()
                    }
                }
            }
            i = semi + 1
        }

        val selector = if (inSelector && base.packageName == null) current else null
        val rest = uri.substring(PREFIX.length, hash)
        val data = if (scheme != null) "$scheme:$rest" else rest
        if (data.isNotEmpty()) base.data = data
        return finish(base, selector?.let { finish(it, null) })
    }

    private fun parseOld(uri: String, hash: Int): ParsedIntentUri {
        var i = hash + 1
        var fragment = false
        var action: String? = null

        if (uri.regionMatches(i, "action(", 0, 7)) {
            fragment = true
            i += 7
            val j = uri.indexOf(')', i)
            action = uri.substring(i, j)
            i = j + 1
        }
        val draft = Draft(action)

        if (uri.regionMatches(i, "categories(", 0, 11)) {
            fragment = true
            i += 11
            val j = uri.indexOf(')', i)
            while (i < j) {
                var sep = uri.indexOf('!', i)
                if (sep < 0 || sep > j) sep = j
                if (i < sep) draft.categories += uri.substring(i, sep)
                i = sep + 1
            }
            i = j + 1
        }
        if (uri.regionMatches(i, "type(", 0, 5)) {
            fragment = true
            i += 5
            val j = uri.indexOf(')', i)
            uri.substring(i, j)
            i = j + 1
        }
        if (uri.regionMatches(i, "launchFlags(", 0, 12)) {
            fragment = true
            i += 12
            val j = uri.indexOf(')', i)
            decodeInteger(uri.substring(i, j))
            i = j + 1
        }
        if (uri.regionMatches(i, "component(", 0, 10)) {
            fragment = true
            i += 10
            val j = uri.indexOf(')', i)
            val sep = uri.indexOf('!', i)
            if (sep in 0 until j) draft.componentPackage = uri.substring(i, sep)
            i = j + 1
        }
        if (uri.regionMatches(i, "extras(", 0, 7)) {
            fragment = true
            i += 7
            val close = uri.indexOf(')', i)
            if (close == -1) throw Malformed()
            while (i < close) {
                var j = uri.indexOf('=', i)
                if (j <= i + 1 || i >= close) throw Malformed()
                val type = uri[i]
                i++
                val key = uri.substring(i, j)
                i = j + 1
                j = uri.indexOf('!', i)
                if (j == -1 || j >= close) j = close
                if (i >= j) throw Malformed()
                val value = uri.substring(i, j)
                i = j
                try {
                    when (type) {
                        'S' -> decode(value).let { if (key == "browser_fallback_url") draft.fallback = it }
                        'B' -> Unit
                        'b' -> java.lang.Byte.parseByte(value)
                        'c' -> value[0]
                        'd' -> java.lang.Double.parseDouble(value)
                        'f' -> java.lang.Float.parseFloat(value)
                        'i' -> java.lang.Integer.parseInt(value)
                        'l' -> java.lang.Long.parseLong(value)
                        's' -> java.lang.Short.parseShort(value)
                        else -> throw Malformed()
                    }
                } catch (_: NumberFormatException) {
                    throw Malformed()
                }
                val ch = uri[i]
                if (ch == ')') break
                if (ch != '!') throw Malformed()
                i++
            }
        }

        draft.data = if (fragment) uri.substring(0, hash) else uri
        if (draft.action == null) draft.action = ACTION_VIEW
        return finish(draft, null)
    }

    private fun finish(draft: Draft, selector: ParsedIntentUri?) = ParsedIntentUri(
        action = draft.action,
        data = draft.data,
        packageName = draft.packageName?.let(::packageOrThrow),
        componentPackage = draft.componentPackage?.let(::packageOrThrow),
        categories = draft.categories.toSet(),
        browserFallbackUrl = draft.fallback,
        selector = selector,
    )

    private fun packageOrThrow(value: String): PackageName =
        runCatching { PackageName(value) }.getOrNull() ?: throw Malformed()

    /** `ComponentName.unflattenFromString`: needs a '/' with a class name after it, otherwise no component. */
    private fun unflattenPackage(value: String): String? {
        val sep = value.indexOf('/')
        return if (sep < 0 || sep + 1 >= value.length) null else value.substring(0, sep)
    }

    /** `Intent.decodeInteger`: `Integer.decode`, then unsigned hex for `0x…`; otherwise the first error escapes. */
    private fun decodeInteger(value: String): Int = try {
        Integer.decode(value)
    } catch (e: NumberFormatException) {
        if (value.startsWith("0x")) runCatching { Integer.parseUnsignedInt(value.substring(2), 16) }.getOrNull() ?: throw e
        else throw e
    }

    /** `UriCodec.hexCharToValue`: ASCII hex digits only. */
    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }

    /**
     * `Uri.decode`, which is `UriCodec.decode(s, convertPlus = false, UTF_8, throwOnFailure = false)`: malformed UTF-8
     * becomes U+FFFD; a bad escape appends U+FFFD and, as AOSP does, a zero byte; a truncated escape appends U+FFFD
     * and stops.
     */
    private fun decode(text: String): String {
        val out = StringBuilder(text.length)
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .replaceWith(INVALID_INPUT_CHARACTER.toString())
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val bytes = ByteBuffer.allocate(text.length)
        fun flush() {
            if (bytes.position() == 0) return
            bytes.flip()
            out.append(decoder.decode(bytes))
            bytes.clear()
        }
        var i = 0
        while (i < text.length) {
            val c = text[i++]
            if (c != '%') {
                flush()
                out.append(c)
                continue
            }
            var hex = 0
            for (digit in 0 until 2) {
                if (i >= text.length) {
                    flush()
                    out.append(INVALID_INPUT_CHARACTER)
                    return out.toString()
                }
                val value = hexValue(text[i++])
                if (value < 0) {
                    flush()
                    out.append(INVALID_INPUT_CHARACTER)
                    break
                }
                hex = hex * 16 + value
            }
            bytes.put(hex.toByte())
        }
        flush()
        return out.toString()
    }
}
