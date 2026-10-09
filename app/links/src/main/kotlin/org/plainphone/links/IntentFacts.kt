// SPDX-License-Identifier: Apache-2.0
package org.plainphone.links

import org.plainphone.core.PackageName

const val ACTION_VIEW: String = "android.intent.action.VIEW"
const val ACTION_SEND: String = "android.intent.action.SEND"

/**
 * One activity (one `ResolveInfo`) Android would consider for the intent as dispatched. Two entries with the same
 * package are two activities: with equal priority and no saved preference Android shows its resolver
 * (AOSP `ResolveIntentHelper.chooseBestActivity`).
 *
 * @property verifiedForHost the activity handles the link's host through a verified (approved-state) App Link.
 *   A web resolver without this flag is a generic web handler. Android 12+ does not offer unverified
 *   domain handlers for web links (spikes/emulator/RESULT.md Q4, "App Link (unverified, state 1024)").
 */
data class Resolver(val packageName: PackageName, val verifiedForHost: Boolean = false)

/** What `PackageManager.resolveActivity(intent, MATCH_DEFAULT_ONLY)` returned for the intent as dispatched. */
sealed interface Resolution {
    /** Android picked one activity in this package (the only one, a priority winner or a saved preference). */
    data class Activity(val packageName: PackageName) : Resolution

    /** Android returned its resolver (`ResolverActivity`), which lock task blocks. */
    data object Chooser : Resolution

    /** Nothing resolves. */
    data object None : Resolution
}

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
 * @property action read only to tell a web intent (`ACTION_VIEW` with an exact `http`/`https` scheme, as in
 *   `Intent.isWebIntent`) from any other. Which apps match it is already in [resolvers].
 * @property targetPackage the package from `setPackage` or the explicit component, if any. It must agree with a
 *   reconstructed `intent:` URI; disagreement is [NoHandlerReason.INCONSISTENT_FACTS].
 * @property explicitComponent [targetPackage] comes from an explicit component (one activity), not `setPackage`
 *   (every matching activity of the package).
 * @property viaChooser the intent is wrapped in `Intent.createChooser`.
 * @property customTabs a Custom Tabs launch. With no package it is the client's `launchUrl` fallback
 *   (Plainphone provides no Custom Tabs, so the provider list is empty once browsers are hidden).
 *   Context only: [LinkDecider] never reads it. The fallback is an ordinary `VIEW` whose outcome comes from
 *   [data], [targetPackage] and [resolvers].
 * @property resolvers what `queryIntentActivities` returns for the intent as dispatched (after `parseUri`
 *   reconstruction and sanitising, and limited to the target package when one is set), one entry per activity.
 *   Hidden packages never appear. Plainphone's link-catcher appears exactly when it is a candidate; a web link reaches
 *   the blocked-link screen only then. An empty list is [NoHandlerReason.NO_RESOLVER], never link catching.
 * @property resolved Android's own pick, when lane D has it. It decides between several candidates (saved preferred
 *   activity, priority); without it, two or more candidates are a chooser. It must agree with [resolvers].
 * @property linkCatcherPreferred lane D has set a persistent preferred activity for `VIEW http/https` to the
 *   link-catcher, so a visible generic web handler no longer causes a chooser.
 */
data class IntentFacts(
    val data: String? = null,
    val action: String = ACTION_VIEW,
    val targetPackage: PackageName? = null,
    val explicitComponent: Boolean = false,
    val viaChooser: Boolean = false,
    val customTabs: Boolean = false,
    val intentUri: IntentUriHandling = IntentUriHandling.NOT_PARSED,
    val resolvers: List<Resolver> = emptyList(),
    val linkCatcherPreferred: Boolean = false,
    val resolved: Resolution? = null,
)
