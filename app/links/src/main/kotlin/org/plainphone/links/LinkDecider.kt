// SPDX-License-Identifier: Apache-2.0
package org.plainphone.links

import org.plainphone.core.PackageName

/** Which blocked screen the user sees. */
enum class BlockSurface {
    /** Plainphone's own blocked-link screen (the link-catcher). */
    PLAINPHONE_LINK_SCREEN,

    /** Android's lock-task "… is not available right now" screen. */
    LOCK_TASK_BLOCKED,
}

enum class BlockReason(val surface: BlockSurface) {
    /** An outside web link with no approved verified App Link. */
    WEB_LINK(BlockSurface.PLAINPHONE_LINK_SCREEN),

    /** The only handler is a visible package that is not approved or a lock-task UI exception. */
    NOT_APPROVED(BlockSurface.LOCK_TASK_BLOCKED),

    /** Android would show a chooser or resolver, which lock task blocks. */
    CHOOSER(BlockSurface.LOCK_TASK_BLOCKED),
}

enum class NoHandlerReason {
    /** No visible activity handles the intent. */
    NO_RESOLVER,

    /** The intent names a package that is hidden, missing or does not handle it. */
    TARGET_NOT_RESOLVABLE,

    /** The `intent:` or `android-app:` URI cannot be reconstructed. */
    MALFORMED_INTENT_URI,

    /** The facts contradict each other, so no decision is safe. Fails closed: nothing opens. */
    INCONSISTENT_FACTS,
}

/**
 * Where a link ends up. Matches the PLA-23 result detector: `OpenIn` = TARGET_OPENED,
 * `BlockedScreen(WEB_LINK)` = LINK_CAUGHT, other blocks = POLICY_DENIED, `NoHandler` = NO_HANDLER.
 */
sealed interface LinkDecision {
    data class OpenIn(val packageName: PackageName) : LinkDecision
    data class BlockedScreen(val reason: BlockReason) : LinkDecision
    data class NoHandler(val reason: NoHandlerReason) : LinkDecision
}

/**
 * The link matrix (web-containment spec: "Outside links go to the blocked-link screen").
 *
 * Navigation inside an approved app's own WebView sends no intent, so it never reaches this decision. That leak is
 * disclosed in the catalog notes.
 *
 * @param linkCatcher Plainphone's package (it holds the link-catcher activity).
 */
class LinkDecider(private val linkCatcher: PackageName) {
    private data class Dispatch(val target: PackageName?, val explicit: Boolean, val action: String?, val data: String?)

    private sealed interface Pick {
        data class Activity(val packageName: PackageName) : Pick
        data object Chooser : Pick
    }

    /**
     * @param approved every package lock task allows: approved apps plus the lock-task UI exceptions.
     */
    fun decide(intent: IntentFacts, approved: Set<PackageName>): LinkDecision {
        if (intent.viaChooser) return LinkDecision.BlockedScreen(BlockReason.CHOOSER)
        if (intent.explicitComponent && intent.targetPackage == null) return inconsistent()

        val dispatch = dispatched(intent) ?: return LinkDecision.NoHandler(NoHandlerReason.MALFORMED_INTENT_URI)
        if (intent.targetPackage != null &&
            (intent.targetPackage != dispatch.target || intent.explicitComponent != dispatch.explicit)
        ) return inconsistent()

        val candidates = intent.resolvers
        val target = dispatch.target
        if (target != null && candidates.any { it.packageName != target }) return inconsistent()
        val empty = if (target != null) NoHandlerReason.TARGET_NOT_RESOLVABLE else NoHandlerReason.NO_RESOLVER

        val pick = when (val resolved = intent.resolved) {
            is Resolution.Activity ->
                if (candidates.none { it.packageName == resolved.packageName }) return inconsistent()
                else Pick.Activity(resolved.packageName)
            Resolution.Chooser -> if (dispatch.explicit || candidates.size < 2) return inconsistent() else Pick.Chooser
            Resolution.None -> return if (candidates.isNotEmpty()) inconsistent() else LinkDecision.NoHandler(empty)
            null -> infer(dispatch, candidates, intent.linkCatcherPreferred) ?: return LinkDecision.NoHandler(empty)
        }

        return when (pick) {
            Pick.Chooser -> LinkDecision.BlockedScreen(BlockReason.CHOOSER)
            is Pick.Activity -> when (pick.packageName) {
                linkCatcher -> LinkDecision.BlockedScreen(BlockReason.WEB_LINK)
                in approved -> LinkDecision.OpenIn(pick.packageName)
                else -> LinkDecision.BlockedScreen(BlockReason.NOT_APPROVED)
            }
        }
    }

    /**
     * The intent Android resolves. An `intent:`/`android-app:` URI is reconstructed: raw, an explicit component wins,
     * then the selector's component, then the selector's intent restricted only by the outer package (AOSP
     * `ComputerEngine.queryIntentActivitiesInternal` reads the package before swapping in the selector); sanitised
     * Chrome-style, the component and selector are dropped.
     */
    private fun dispatched(intent: IntentFacts): Dispatch? {
        val data = intent.data
        val reconstruct = intent.intentUri != IntentUriHandling.NOT_PARSED && data != null &&
            (data.startsWith("intent:") || data.startsWith("android-app:"))
        if (!reconstruct) return Dispatch(intent.targetPackage, intent.explicitComponent, intent.action, data)

        val parsed = IntentUri.parse(data!!) ?: return null
        val selector = parsed.selector
        return when {
            intent.intentUri == IntentUriHandling.SANITISED -> Dispatch(parsed.packageName, false, parsed.action, parsed.data)
            parsed.componentPackage != null -> Dispatch(parsed.componentPackage, true, parsed.action, parsed.data)
            selector?.componentPackage != null -> Dispatch(selector.componentPackage, true, selector.action, selector.data)
            selector != null -> Dispatch(parsed.packageName, false, selector.action, selector.data)
            else -> Dispatch(parsed.packageName, false, parsed.action, parsed.data)
        }
    }

    /** Android's choice when lane D did not report it: one candidate opens, more are a chooser. */
    private fun infer(dispatch: Dispatch, candidates: List<Resolver>, catcherPreferred: Boolean): Pick? {
        if (candidates.isEmpty()) return null
        dispatch.target?.takeIf { dispatch.explicit }?.let { return Pick.Activity(it) }

        val web = dispatch.target == null && isWebIntent(dispatch.action, dispatch.data)
        val pool = candidates.filter { it.verifiedForHost }.takeIf { web && it.isNotEmpty() } ?: candidates
        return when {
            pool.size == 1 -> Pick.Activity(pool[0].packageName)
            web && catcherPreferred && pool.any { it.packageName == linkCatcher } -> Pick.Activity(linkCatcher)
            else -> Pick.Chooser
        }
    }

    private fun inconsistent() = LinkDecision.NoHandler(NoHandlerReason.INCONSISTENT_FACTS)

    /** `Intent.isWebIntent`: `ACTION_VIEW` and a scheme exactly `http` or `https` (filters match schemes case-sensitively). */
    private fun isWebIntent(action: String?, data: String?): Boolean {
        if (action != ACTION_VIEW || data == null || ':' !in data) return false
        val scheme = data.substringBefore(':')
        return scheme == "http" || scheme == "https"
    }
}
