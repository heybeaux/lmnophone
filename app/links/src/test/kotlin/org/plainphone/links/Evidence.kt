// SPDX-License-Identifier: Apache-2.0
package org.plainphone.links

/** Where a table row's expected result comes from. Paths are relative to the repository root. */
sealed interface Evidence {
    /** A measured result: the evidence file and the case inside it. */
    data class Cited(val file: String, val case: String) : Evidence {
        init { require(file.isNotBlank() && case.isNotBlank()) }
    }

    /** No measured result yet. Lane D's emulator run confirms or corrects the row. */
    data class Unverified(val why: String) : Evidence {
        init { require(why.isNotBlank()) }
    }
}

internal const val EMU_RESULT = "spikes/emulator/RESULT.md"
internal const val PLA13_REVIEW = "docs/reviews/PLA-13-emulator-spike-review.md"
internal const val MOTO_RESULT = "spikes/device-moto/RESULT.md"
internal const val WEB_SPEC = "openspec/changes/add-plainphone-spike-and-mvp/specs/web-containment/spec.md"

/** Android's own source for `Intent.parseUri`, used where no emulator run exists yet. */
internal const val AOSP_INTENT = "AOSP frameworks/base/core/java/android/content/Intent.java parseUri"
internal const val SELECTOR_RULE = "the SEL; selector is attached only when the outer intent has no package"
internal const val AOSP_RESOLVE = "AOSP frameworks/base/services/core/java/com/android/server/pm/ResolveIntentHelper.java chooseBestActivity"
internal const val AOSP_QUERY = "AOSP frameworks/base/services/core/java/com/android/server/pm/ComputerEngine.java queryIntentActivitiesInternal"
internal const val CHROME_SANITISER = "https://chromium.googlesource.com/chromium/src/+/refs/tags/138.0.7204.50/components/external_intents/android/java/src/org/chromium/components/external_intents/ExternalNavigationHandler.java#1843"
