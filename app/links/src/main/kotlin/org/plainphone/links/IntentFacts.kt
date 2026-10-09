// SPDX-License-Identifier: Apache-2.0
package org.plainphone.links

import org.plainphone.core.PackageName

const val ACTION_VIEW: String = "android.intent.action.VIEW"
const val ACTION_SEND: String = "android.intent.action.SEND"

/**
 * One activity Android would offer for the intent as dispatched.
 *
 * @property verifiedForHost the activity handles the link's host through a verified (approved-state) App Link.
 *   A web resolver without this flag is a generic web handler. Android 12+ does not offer unverified
 *   domain handlers for web links (spikes/emulator/RESULT.md Q4, "App Link (unverified, state 1024)").
 */
data class Resolver(val packageName: PackageName, val verifiedForHost: Boolean = false)

/** How the sender treats an `intent:` URI in [IntentFacts.data]. */
enum class IntentUriHandling {
    /** Fired as raw `VIEW` data (`Uri.parse`). Nothing handles the `intent` scheme once browsers are hidden. */
    NOT_PARSED,

    /** Reconstructed with `Intent.parseUri(..., URI_INTENT_SCHEME)` and fired as is (a naive app or WebView host). */
    RAW,

    /** Reconstructed, then sanitised the way Chrome does: component and selector dropped, `BROWSABLE` added. */
    SANITISED,
}

/**
 * Plain facts about one outgoing intent. Lane D fills these; this module never calls Android.
 *
 * @property data the raw data URI (`http`, `https`, `intent:`, a custom scheme, `market:`), or null.
 * @property targetPackage the package from `setPackage` or the explicit component, if any.
 * @property viaChooser the intent is wrapped in `Intent.createChooser`.
 * @property customTabs a Custom Tabs launch. With no package it is the client's `launchUrl` fallback
 *   (Plainphone provides no Custom Tabs, so the provider list is empty once browsers are hidden).
 * @property resolvers what `queryIntentActivities` returns for the intent as dispatched (after `parseUri`
 *   reconstruction and sanitising, and limited to the target package when one is set). Hidden packages never appear.
 *   Plainphone's own link-catcher may appear; it is ignored.
 * @property linkCatcherPreferred lane D has set a persistent preferred activity for `VIEW http/https` to the
 *   link-catcher, so a visible generic web handler no longer causes a chooser.
 */
data class IntentFacts(
    val data: String? = null,
    val action: String = ACTION_VIEW,
    val targetPackage: PackageName? = null,
    val viaChooser: Boolean = false,
    val customTabs: Boolean = false,
    val intentUri: IntentUriHandling = IntentUriHandling.NOT_PARSED,
    val resolvers: List<Resolver> = emptyList(),
    val linkCatcherPreferred: Boolean = false,
)
