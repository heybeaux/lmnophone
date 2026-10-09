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

    /** The `intent:` URI cannot be reconstructed. */
    MALFORMED_INTENT_URI,
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
    /**
     * @param approved every package lock task allows: approved apps plus the lock-task UI exceptions.
     */
    fun decide(intent: IntentFacts, approved: Set<PackageName>): LinkDecision {
        if (intent.viaChooser) return LinkDecision.BlockedScreen(BlockReason.CHOOSER)

        var target = intent.targetPackage?.takeUnless { it == linkCatcher }
        var data = intent.data
        if (intent.intentUri != IntentUriHandling.NOT_PARSED && data != null && data.startsWith("intent:")) {
            val parsed = IntentUri.parse(data) ?: return LinkDecision.NoHandler(NoHandlerReason.MALFORMED_INTENT_URI)
            val selector = parsed.selector
            when {
                intent.intentUri == IntentUriHandling.SANITISED -> {
                    target = parsed.packageName
                    data = parsed.data
                }
                parsed.componentPackage != null -> {
                    target = parsed.componentPackage
                    data = parsed.data
                }
                selector != null -> {
                    target = selector.componentPackage ?: selector.packageName
                    data = selector.data
                }
                else -> {
                    target = parsed.packageName
                    data = parsed.data
                }
            }
            target = target?.takeUnless { it == linkCatcher }
        }

        val offered = intent.resolvers.filter { it.packageName != linkCatcher }.distinctBy { it.packageName to it.verifiedForHost }

        if (target != null) {
            if (offered.none { it.packageName == target }) return LinkDecision.NoHandler(NoHandlerReason.TARGET_NOT_RESOLVABLE)
            return if (target in approved) LinkDecision.OpenIn(target) else LinkDecision.BlockedScreen(BlockReason.NOT_APPROVED)
        }

        return if (isWeb(data)) decideWeb(offered, approved, intent.linkCatcherPreferred) else decideOther(offered, approved)
    }

    private fun decideWeb(offered: List<Resolver>, approved: Set<PackageName>, catcherPreferred: Boolean): LinkDecision {
        val verified = offered.filter { it.verifiedForHost }.map { it.packageName }.distinct()
        if (verified.size > 1) return LinkDecision.BlockedScreen(BlockReason.CHOOSER)
        verified.singleOrNull()?.let {
            return if (it in approved) LinkDecision.OpenIn(it) else LinkDecision.BlockedScreen(BlockReason.NOT_APPROVED)
        }
        if (offered.isNotEmpty() && !catcherPreferred) return LinkDecision.BlockedScreen(BlockReason.CHOOSER)
        return LinkDecision.BlockedScreen(BlockReason.WEB_LINK)
    }

    private fun decideOther(offered: List<Resolver>, approved: Set<PackageName>): LinkDecision {
        val packages = offered.map { it.packageName }.distinct()
        return when (packages.size) {
            0 -> LinkDecision.NoHandler(NoHandlerReason.NO_RESOLVER)
            1 -> if (packages[0] in approved) LinkDecision.OpenIn(packages[0]) else LinkDecision.BlockedScreen(BlockReason.NOT_APPROVED)
            else -> LinkDecision.BlockedScreen(BlockReason.CHOOSER)
        }
    }

    private fun isWeb(data: String?): Boolean {
        val scheme = data?.substringBefore(':', missingDelimiterValue = "")?.lowercase() ?: return false
        return scheme == "http" || scheme == "https"
    }
}
